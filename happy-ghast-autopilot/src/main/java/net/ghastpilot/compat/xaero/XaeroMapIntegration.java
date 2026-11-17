package net.ghastpilot.compat.xaero;

import net.ghastpilot.client.AutopilotConfig;
import net.ghastpilot.client.AutopilotController;
import net.ghastpilot.client.ScreenCompat;
import net.ghastpilot.flight.FlightTarget;
import net.ghastpilot.flight.MapLineClipper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.gui.IRightClickableElement;
import xaero.map.gui.dropdown.rightclick.RightClickOption;
import xaero.map.mods.gui.Waypoint;
import xaero.map.world.MapWorld;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles right-click destination selection, waypoint targeting, and route/pin overlay rendering
 * inside Xaero's World Map.
 */
public final class XaeroMapIntegration {

	/** Xaero uses Short.MAX_VALUE (32767) or -1/invalid out-of-world Y when height at cursor is unknown. */
	private static final int MIN_VALID_MAP_Y = -2048;
	private static final int MAX_VALID_MAP_Y = 4096;

	private static final int ROUTE_COLOR_ACTIVE = 0xCC38BDF8;
	private static final int ROUTE_COLOR_PAUSED = 0x9994A3B8;
	private static final int ROUTE_SHADOW = 0x77000000;
	private static final int PIN_OUTLINE = 0xEE0B1220;
	private static final int PIN_ACTIVE = 0xFF34D399;
	private static final int PIN_PAUSED = 0xFFFBBF24;
	private static final int LABEL_BG = 0xCC0B1220;

	private static final Field FIELD_RIGHT_CLICK_X = findField("rightClickX");
	private static final Field FIELD_RIGHT_CLICK_Y = findField("rightClickY");
	private static final Field FIELD_RIGHT_CLICK_Z = findField("rightClickZ");
	private static final Field FIELD_RIGHT_CLICK_DIM = findField("rightClickDim");
	private static final Field FIELD_MOUSE_BLOCK_X = findField("mouseBlockPosX");
	private static final Field FIELD_MOUSE_BLOCK_Y = findField("mouseBlockPosY");
	private static final Field FIELD_MOUSE_BLOCK_Z = findField("mouseBlockPosZ");
	private static final Field FIELD_CAMERA_X = findField("cameraX");
	private static final Field FIELD_CAMERA_Z = findField("cameraZ");
	private static final Field FIELD_SCALE = findField("scale");
	private static final Field FIELD_SCREEN_SCALE = findField("screenScale");

	private XaeroMapIntegration() {
	}

	public static void appendMapOptions(GuiMap guiMap, ArrayList<RightClickOption> options) {
		Integer xObj = readBoxedInt(FIELD_RIGHT_CLICK_X, guiMap);
		Integer zObj = readBoxedInt(FIELD_RIGHT_CLICK_Z, guiMap);
		Integer yObj = readBoxedInt(FIELD_RIGHT_CLICK_Y, guiMap);

		if (xObj == null || zObj == null) {
			xObj = readBoxedInt(FIELD_MOUSE_BLOCK_X, guiMap);
			zObj = readBoxedInt(FIELD_MOUSE_BLOCK_Z, guiMap);
			yObj = readBoxedInt(FIELD_MOUSE_BLOCK_Y, guiMap);
		}
		if (xObj == null || zObj == null) {
			return;
		}

		final int targetX = xObj;
		final int targetZ = zObj;
		final Integer landingY = (yObj != null && yObj >= MIN_VALID_MAP_Y && yObj <= MAX_VALID_MAP_Y)
				? (yObj + 1)
				: null;
		final String dimension = resolveRightClickDimension(guiMap);

		Minecraft client = Minecraft.getInstance();
		boolean drivingGhast = AutopilotController.isControllingHappyGhast(client)
				&& FlightTarget.normalizeDimension(dimension)
				.equals(FlightTarget.normalizeDimension(AutopilotController.getPlayerDimension(client)));

		List<RightClickOption> toInsert = new ArrayList<>(3);

		// Option 1: Fly at safe cruise altitude to (X, Z)
		String flyKey = drivingGhast
				? "happy_ghast_autopilot.xaero.fly_here"
				: "happy_ghast_autopilot.xaero.set_target";
		String flyLabel = escapePercent(Component.translatable(flyKey, targetX, targetZ).getString());
		toInsert.add(new RightClickOption(flyLabel, options.size(), guiMap) {
			@Override
			public void onAction(Screen screen) {
				selectFromMap(new FlightTarget(targetX, null, targetZ, dimension, null), screen);
			}
		});

		// Option 2: Fly to (X, Z) AND gently descend right to surface Y + 1 (when tile height is known)
		if (landingY != null) {
			String landKey = drivingGhast
					? "happy_ghast_autopilot.xaero.fly_here_y"
					: "happy_ghast_autopilot.xaero.set_target_y";
			String landLabel = escapePercent(Component.translatable(landKey, targetX, landingY, targetZ).getString());
			toInsert.add(new RightClickOption(landLabel, options.size() + 1, guiMap) {
				@Override
				public void onAction(Screen screen) {
					AutopilotConfig.get().descendOnArrival = true;
					selectFromMap(new FlightTarget(targetX, landingY, targetZ, dimension, null), screen);
				}
			});
		}

		// Option 3: Clear current destination if one is set
		if (AutopilotController.getTarget() != null) {
			String clearLabel = escapePercent(
					Component.translatable("happy_ghast_autopilot.xaero.clear_target").getString()
			);
			toInsert.add(new RightClickOption(clearLabel, options.size() + toInsert.size(), guiMap) {
				@Override
				public void onAction(Screen screen) {
					AutopilotController.clearTarget(true);
				}
			});
		}

		// Place right near the top (just below the coordinate header) while preserving proper option indices
		int insertPos = Math.min(2, options.size());
		options.addAll(insertPos, toInsert);
	}

	public static void appendWaypointOptions(
			Waypoint waypoint,
			IRightClickableElement target,
			ArrayList<RightClickOption> options
	) {
		final int wx = waypoint.getX();
		final int wz = waypoint.getZ();
		final Integer wy = waypoint.isyIncluded() && waypoint.getY() >= MIN_VALID_MAP_Y && waypoint.getY() <= MAX_VALID_MAP_Y
				? waypoint.getY()
				: null;
		final String wpName = waypoint.getName();

		Minecraft client = Minecraft.getInstance();
		Screen currentScreen = ScreenCompat.getCurrentScreen(client);
		final String dimension = (currentScreen instanceof GuiMap guiMap)
				? resolveMapDimension(guiMap)
				: AutopilotController.getPlayerDimension(client);

		String label = escapePercent(Component.translatable("happy_ghast_autopilot.xaero.fly_waypoint").getString());
		RightClickOption option = new RightClickOption(label, options.size(), target) {
			@Override
			public void onAction(Screen screen) {
				String dim = (screen instanceof GuiMap gm) ? resolveMapDimension(gm) : dimension;
				selectFromMap(new FlightTarget(wx, wy, wz, dim, wpName), screen);
			}
		};

		int insertPos = Math.min(3, options.size());
		options.add(insertPos, option);
	}

	private static void selectFromMap(FlightTarget chosen, Screen mapScreen) {
		AutopilotConfig cfg = AutopilotConfig.get();
		AutopilotController.setTarget(chosen, cfg.autoStartFromMap);
		// Close map automatically only when the ghast actually started flying, so on-foot players can see their new pin.
		if (AutopilotController.isActive() && cfg.autoStartFromMap && mapScreen != null) {
			mapScreen.onClose();
		}
	}

	public static void renderRouteOverlay(GuiMap guiMap, GuiGraphicsExtractor graphics) {
		if (!AutopilotConfig.get().showXaeroOverlay) {
			return;
		}
		FlightTarget target = AutopilotController.getTarget();
		if (target == null) {
			return;
		}

		String mapDim = resolveMapDimension(guiMap);
		if (mapDim == null || !target.isInDimension(mapDim)) {
			return;
		}

		Double camX = readBoxedDouble(FIELD_CAMERA_X, guiMap);
		Double camZ = readBoxedDouble(FIELD_CAMERA_Z, guiMap);
		Double scale = readBoxedDouble(FIELD_SCALE, guiMap);
		Double screenScale = readBoxedDouble(FIELD_SCREEN_SCALE, guiMap);
		if (camX == null || camZ == null || scale == null || screenScale == null) {
			return;
		}

		double pixelsPerBlock = scale / screenScale;
		if (!Double.isFinite(pixelsPerBlock) || pixelsPerBlock <= 0.0) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		int screenW = client.getWindow().getGuiScaledWidth();
		int screenH = client.getWindow().getGuiScaledHeight();
		double halfW = screenW * 0.5;
		double halfH = screenH * 0.5;

		double dstScreenX = halfW + (target.centerX() - camX) * pixelsPerBlock;
		double dstScreenY = halfH + (target.centerZ() - camZ) * pixelsPerBlock;

		boolean active = AutopilotController.isActive();
		LocalPlayer player = client.player;
		double distForLabel = Double.NaN;

		// 1. Draw flight line from player to target if player is in the viewed dimension
		if (player != null && target.isInDimension(AutopilotController.getPlayerDimension(client))) {
			double srcWorldX = player.getX();
			double srcWorldZ = player.getZ();
			distForLabel = target.horizontalDistanceTo(srcWorldX, srcWorldZ);

			double srcScreenX = halfW + (srcWorldX - camX) * pixelsPerBlock;
			double srcScreenY = halfH + (srcWorldZ - camZ) * pixelsPerBlock;

			double[] clipped = MapLineClipper.clip(
					srcScreenX, srcScreenY,
					dstScreenX, dstScreenY,
					-4.0, -4.0,
					screenW + 4.0, screenH + 4.0
			);
			if (clipped != null) {
				drawDashedLine(
						graphics,
						clipped[0], clipped[1],
						clipped[2], clipped[3],
						active ? ROUTE_COLOR_ACTIVE : ROUTE_COLOR_PAUSED
				);
			}
		}

		// 2. Draw crisp target pin + coordinate/distance badge when destination is inside viewport
		if (dstScreenX >= -24 && dstScreenX <= screenW + 24 && dstScreenY >= -24 && dstScreenY <= screenH + 24) {
			int px = (int) Math.round(dstScreenX);
			int py = (int) Math.round(dstScreenY);
			int pinColor = active ? PIN_ACTIVE : PIN_PAUSED;

			// Crosshair arms with dark outline
			graphics.fill(px - 7, py - 2, px + 8, py + 3, PIN_OUTLINE);
			graphics.fill(px - 2, py - 7, px + 3, py + 8, PIN_OUTLINE);
			graphics.fill(px - 6, py - 1, px + 7, py + 2, pinColor);
			graphics.fill(px - 1, py - 6, px + 2, py + 7, pinColor);

			// Center ring + white core
			graphics.fill(px - 3, py - 3, px + 4, py + 4, PIN_OUTLINE);
			graphics.fill(px - 2, py - 2, px + 3, py + 3, pinColor);
			graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFFFFFF);

			// Compact label pill above pin
			Font font = client.font;
			Component pinText;
			if (Double.isFinite(distForLabel)) {
				pinText = Component.translatable(
						"happy_ghast_autopilot.xaero.map_pin",
						target.formatDisplay(),
						AutopilotController.formatDistanceComponent(distForLabel)
				);
			} else {
				pinText = Component.literal(target.formatDisplay());
			}
			int tw = font.width(pinText);
			int lx = Math.clamp(px - tw / 2, 4, Math.max(4, screenW - tw - 4));
			int ly = py - 19;
			if (ly < 6) {
				ly = py + 10;
			}
			graphics.fill(lx - 3, ly - 2, lx + tw + 3, ly + font.lineHeight + 2, LABEL_BG);
			graphics.fill(lx - 3, ly - 2, lx + tw + 3, ly - 1, pinColor);
			graphics.text(font, pinText, lx, ly, 0xFFFFFFFF, true);
		}
	}

	private static void drawDashedLine(
			GuiGraphicsExtractor graphics,
			double x0, double y0,
			double x1, double y1,
			int color
	) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		double length = Math.hypot(dx, dy);
		if (length < 2.0) {
			return;
		}
		// Dash every 8px, dot size 2x2, capped for ultra-fast rendering on 4K displays
		double step = Math.max(3.0, length / 600.0);
		int count = (int) (length / step);
		double ux = dx / length;
		double uy = dy / length;
		for (int i = 0; i <= count; i++) {
			double d = i * step;
			// Dashed pattern: 9px on, 5px gap
			if (((int) d % 14) >= 9) {
				continue;
			}
			int sx = (int) Math.round(x0 + ux * d);
			int sy = (int) Math.round(y0 + uy * d);
			graphics.fill(sx, sy, sx + 3, sy + 3, ROUTE_SHADOW);
			graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);
		}
	}

	private static String resolveRightClickDimension(GuiMap guiMap) {
		if (FIELD_RIGHT_CLICK_DIM != null) {
			try {
				Object raw = FIELD_RIGHT_CLICK_DIM.get(guiMap);
				String extracted = extractDimensionId(raw);
				if (extracted != null) {
					return extracted;
				}
			} catch (Exception ignored) {
			}
		}
		String mapDim = resolveMapDimension(guiMap);
		if (mapDim != null) {
			return mapDim;
		}
		return AutopilotController.getPlayerDimension(Minecraft.getInstance());
	}

	private static String resolveMapDimension(GuiMap guiMap) {
		try {
			MapProcessor processor = guiMap.getMapProcessor();
			if (processor != null) {
				MapWorld mapWorld = processor.getMapWorld();
				if (mapWorld != null) {
					ResourceKey<Level> dimKey = mapWorld.getCurrentDimensionId();
					if (dimKey != null) {
						return dimKey.identifier().toString();
					}
				}
			}
		} catch (Throwable ignored) {
		}
		Minecraft client = Minecraft.getInstance();
		return client.level != null ? AutopilotController.getPlayerDimension(client) : null;
	}

	private static String extractDimensionId(Object dimKeyObj) {
		if (dimKeyObj instanceof ResourceKey<?> rk) {
			return rk.identifier().toString();
		}
		return null;
	}

	/** Escapes '%' because Xaero passes right-click option titles through String.format / I18n.get. */
	private static String escapePercent(String text) {
		return text == null ? "" : text.replace("%", "%%");
	}

	private static Field findField(String name) {
		try {
			Field f = GuiMap.class.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Integer readBoxedInt(Field field, Object instance) {
		if (field == null) {
			return null;
		}
		try {
			return field.getInt(instance);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Double readBoxedDouble(Field field, Object instance) {
		if (field == null) {
			return null;
		}
		try {
			return field.getDouble(instance);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
