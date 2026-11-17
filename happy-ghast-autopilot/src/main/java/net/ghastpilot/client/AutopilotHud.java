package net.ghastpilot.client;

import net.ghastpilot.flight.EtaFormatter;
import net.ghastpilot.flight.FlightPhase;
import net.ghastpilot.flight.FlightPlanner;
import net.ghastpilot.flight.FlightTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;

/**
 * Compact, non-intrusive top-center HUD panel shown while actively flying or while riding a Happy Ghast
 * with a saved autopilot destination.
 */
public final class AutopilotHud {

	private static final int BG_COLOR = 0xAA0D1622;
	private static final int BORDER_SUBTLE = 0x5538BDF8;
	private static final int TEXT_PRIMARY = 0xFFF1F5F9;
	private static final int TEXT_SECONDARY = 0xFFCBD5E1;
	private static final int TEXT_HINT = 0xFF94A3B8;

	private AutopilotHud() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || ScreenCompat.isHudHidden(client)) {
			return;
		}

		AutopilotConfig cfg = AutopilotConfig.get();
		if (!cfg.showHud) {
			return;
		}

		FlightTarget target = AutopilotController.getTarget();
		if (target == null) {
			return;
		}

		boolean active = AutopilotController.isActive();
		HappyGhast ghast = AutopilotController.getRiddenHappyGhast(player);
		// Only show the paused card when actually mounted on a Happy Ghast so on-foot HUD stays clutter-free.
		if (!active && ghast == null) {
			return;
		}

		FlightPhase phase = AutopilotController.getPhase();
		double refX = ghast != null ? ghast.getX() : player.getX();
		double refY = ghast != null ? ghast.getY() : player.getY();
		double refZ = ghast != null ? ghast.getZ() : player.getZ();
		float refYaw = ghast != null ? ghast.getYRot() : player.getYRot();

		double dist = target.totalPathDistanceFrom(refX, refY, refZ, cfg.descendOnArrival);
		float bearingYaw = FlightPlanner.computeYawTo(refX, refZ, target.centerX(), target.centerZ());
		String arrow = EtaFormatter.formatBearingIndicator(refYaw, bearingYaw);

		Component line1 = Component.literal("● ")
				.append(Component.translatable(phase.translationKey()))
				.append(Component.literal("  " + arrow));

		Component line2 = Component.translatable("happy_ghast_autopilot.hud.target", target.formatDisplay());

		Component distComp = AutopilotController.formatDistanceComponent(dist);
		Component line3;
		if (active) {
			String speedStr = EtaFormatter.formatSpeed(AutopilotController.getSmoothedSpeedBps());
			String etaStr = EtaFormatter.formatEta(dist, AutopilotController.getSmoothedSpeedBps());
			line3 = Component.translatable("happy_ghast_autopilot.hud.metrics", distComp, speedStr, etaStr);
		} else {
			line3 = Component.translatable(
					"happy_ghast_autopilot.hud.paused_hint",
					distComp,
					AutopilotKeybinds.toggleKeyName()
			);
		}

		Font font = client.font;
		int lineH = font.lineHeight + 2;
		int contentW = Math.max(font.width(line1), Math.max(font.width(line2), font.width(line3)));
		int padX = 8;
		int padY = 5;
		int boxW = contentW + padX * 2;
		int boxH = lineH * 3 + padY * 2 - 1;

		int screenW = client.getWindow().getGuiScaledWidth();
		int boxX = (screenW - boxW) / 2;
		int boxY = 6;

		// Soft translucent card + top status accent bar
		graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, BG_COLOR);
		graphics.fill(boxX, boxY, boxX + boxW, boxY + 2, phase.color());
		graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, BORDER_SUBTLE);

		int cy = boxY + padY;
		graphics.text(font, line1, boxX + (boxW - font.width(line1)) / 2, cy, phase.color(), true);
		cy += lineH;
		graphics.text(font, line2, boxX + (boxW - font.width(line2)) / 2, cy, TEXT_PRIMARY, true);
		cy += lineH;
		graphics.text(font, line3, boxX + (boxW - font.width(line3)) / 2, cy, active ? TEXT_SECONDARY : TEXT_HINT, true);
	}
}
