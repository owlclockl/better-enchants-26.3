package net.ghastpilot.client;

import net.ghastpilot.flight.EnvironmentScan;
import net.ghastpilot.flight.EtaFormatter;
import net.ghastpilot.flight.FlightPhase;
import net.ghastpilot.flight.FlightPlanner;
import net.ghastpilot.flight.FlightSettings;
import net.ghastpilot.flight.FlightTarget;
import net.ghastpilot.flight.SteeringCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;

/**
 * Central client-side autopilot state machine for riding and steering a Happy Ghast.
 */
public final class AutopilotController {

	private static final int STUCK_GIVE_UP_TICKS = 160; // 8 seconds without real movement
	private static final int DETOUR_FLIP_TICKS = 70;

	private static FlightTarget target;
	private static boolean active;
	private static FlightPhase phase = FlightPhase.IDLE;
	private static SteeringCommand activeCommand;
	private static float virtualPitch;
	private static double sessionCruiseY = Double.NaN;

	// Live speed smoothing
	private static double lastX = Double.NaN;
	private static double lastY = Double.NaN;
	private static double lastZ = Double.NaN;
	private static double smoothedSpeedBps;

	// Stuck detector
	private static double progressAnchorX = Double.NaN;
	private static double progressAnchorY = Double.NaN;
	private static double progressAnchorZ = Double.NaN;
	private static int stuckTicks;
	private static int preferredDetourSign = 1;
	private static int manualInputGraceTicks;

	private AutopilotController() {
	}

	public static FlightTarget getTarget() {
		return target;
	}

	public static boolean isActive() {
		return active;
	}

	public static FlightPhase getPhase() {
		return phase;
	}

	public static double getSmoothedSpeedBps() {
		return smoothedSpeedBps;
	}

	public static String getPlayerDimension(Minecraft client) {
		if (client.level == null) {
			return FlightTarget.DEFAULT_DIMENSION;
		}
		return client.level.dimension().identifier().toString();
	}

	public static HappyGhast getRiddenHappyGhast(Player player) {
		if (player == null) {
			return null;
		}
		Entity vehicle = player.getVehicle();
		return vehicle instanceof HappyGhast ghast ? ghast : null;
	}

	public static HappyGhast getControlledHappyGhast(Player player) {
		HappyGhast ghast = getRiddenHappyGhast(player);
		if (ghast == null) {
			return null;
		}
		return ghast.getControllingPassenger() == player ? ghast : null;
	}

	public static boolean isControllingHappyGhast(Minecraft client) {
		return client != null && getControlledHappyGhast(client.player) != null;
	}

	/**
	 * Called from {@code HappyGhastMixin} during client entity ticking.
	 * Returns non-null ONLY when autopilot is actively steering the local player's Happy Ghast.
	 */
	public static SteeringCommand getActiveCommandFor(HappyGhast ghast, Player rider) {
		if (!active || activeCommand == null || ghast == null || rider == null) {
			return null;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || rider != client.player) {
			return null;
		}
		if (ghast.getControllingPassenger() != rider) {
			return null;
		}
		return activeCommand;
	}

	public static void setTarget(FlightTarget newTarget, boolean engageNow) {
		if (newTarget == null) {
			clearTarget(true);
			return;
		}
		target = newTarget;
		AutopilotConfig.get().recordRecent(newTarget);
		resetFlightTracking();

		Minecraft client = Minecraft.getInstance();
		if (!engageNow) {
			active = false;
			activeCommand = null;
			updateIdlePhase(client);
			sendChat(client, Component.translatable(
					"happy_ghast_autopilot.msg.target_saved",
					Component.literal(newTarget.formatDisplay()).withStyle(ChatFormatting.AQUA),
					AutopilotKeybinds.toggleKeyName().copy().withStyle(ChatFormatting.YELLOW)
			));
			return;
		}

		tryEngage(client, true);
	}

	public static void toggle() {
		Minecraft client = Minecraft.getInstance();
		if (active) {
			pause(Component.translatable(
					"happy_ghast_autopilot.msg.paused",
					Component.literal(target != null ? target.formatDisplay() : "").withStyle(ChatFormatting.AQUA),
					AutopilotKeybinds.toggleKeyName().copy().withStyle(ChatFormatting.YELLOW)
			));
		} else {
			resume();
		}
	}

	public static void resume() {
		Minecraft client = Minecraft.getInstance();
		if (target == null) {
			sendChat(client, Component.translatable(
					"happy_ghast_autopilot.msg.no_target",
					AutopilotKeybinds.menuKeyName().copy().withStyle(ChatFormatting.YELLOW)
			).withStyle(ChatFormatting.YELLOW));
			return;
		}
		resetFlightTracking();
		tryEngage(client, false);
	}

	private static void tryEngage(Minecraft client, boolean fromNewTarget) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			active = false;
			activeCommand = null;
			phase = FlightPhase.PAUSED;
			return;
		}

		String playerDim = getPlayerDimension(client);
		if (!target.isInDimension(playerDim)) {
			active = false;
			activeCommand = null;
			phase = FlightPhase.WRONG_DIMENSION;
			sendChat(client, Component.translatable(
					"happy_ghast_autopilot.msg.wrong_dimension",
					Component.literal(target.shortDimensionName()).withStyle(ChatFormatting.GOLD)
			).withStyle(ChatFormatting.RED));
			return;
		}

		HappyGhast ridden = getRiddenHappyGhast(player);
		if (ridden == null) {
			active = false;
			activeCommand = null;
			phase = FlightPhase.PAUSED;
			String key = fromNewTarget
					? "happy_ghast_autopilot.msg.target_saved"
					: "happy_ghast_autopilot.msg.not_riding";
			sendChat(client, Component.translatable(
					key,
					Component.literal(target.formatDisplay()).withStyle(ChatFormatting.AQUA),
					AutopilotKeybinds.toggleKeyName().copy().withStyle(ChatFormatting.YELLOW)
			));
			return;
		}

		if (ridden.getControllingPassenger() != player) {
			active = false;
			activeCommand = null;
			phase = FlightPhase.PAUSED;
			sendChat(client, Component.translatable("happy_ghast_autopilot.msg.not_driver")
					.withStyle(ChatFormatting.RED));
			return;
		}

		active = true;
		phase = FlightPhase.CRUISING;
		virtualPitch = 0.0f;
		sessionCruiseY = ridden.getY();
		manualInputGraceTicks = 8; // brief grace window after engaging via hotkey/map
		lastX = ridden.getX();
		lastY = ridden.getY();
		lastZ = ridden.getZ();
		progressAnchorX = ridden.getX();
		progressAnchorY = ridden.getY();
		progressAnchorZ = ridden.getZ();
		stuckTicks = 0;

		sendActionBar(client, Component.translatable(
				"happy_ghast_autopilot.msg.started",
				Component.literal(target.formatDisplay()).withStyle(ChatFormatting.GREEN),
				AutopilotKeybinds.toggleKeyName().copy().withStyle(ChatFormatting.YELLOW)
		));
	}

	public static void pause(Component message) {
		active = false;
		activeCommand = null;
		smoothedSpeedBps = 0.0;
		Minecraft client = Minecraft.getInstance();
		updateIdlePhase(client);
		if (message != null) {
			sendActionBar(client, message);
		}
	}

	public static void clearTarget(boolean notify) {
		active = false;
		activeCommand = null;
		target = null;
		phase = FlightPhase.IDLE;
		resetFlightTracking();
		if (notify) {
			sendActionBar(Minecraft.getInstance(),
					Component.translatable("happy_ghast_autopilot.msg.cleared").withStyle(ChatFormatting.GRAY));
		}
	}

	public static void onDisconnect() {
		active = false;
		activeCommand = null;
		target = null;
		phase = FlightPhase.IDLE;
		resetFlightTracking();
	}

	private static void resetFlightTracking() {
		sessionCruiseY = Double.NaN;
		virtualPitch = 0.0f;
		lastX = Double.NaN;
		lastY = Double.NaN;
		lastZ = Double.NaN;
		progressAnchorX = Double.NaN;
		progressAnchorY = Double.NaN;
		progressAnchorZ = Double.NaN;
		smoothedSpeedBps = 0.0;
		stuckTicks = 0;
		preferredDetourSign = 1;
	}

	private static void updateIdlePhase(Minecraft client) {
		if (target == null) {
			phase = FlightPhase.IDLE;
		} else if (client != null && client.level != null && !target.isInDimension(getPlayerDimension(client))) {
			phase = FlightPhase.WRONG_DIMENSION;
		} else {
			phase = FlightPhase.PAUSED;
		}
	}

	/**
	 * Runs right before entities tick so {@code HappyGhastMixin} receives fresh steering controls.
	 */
	public static void onStartTick(Minecraft client) {
		if (client.isPaused()) {
			return;
		}
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			active = false;
			activeCommand = null;
			return;
		}

		if (!active || target == null) {
			activeCommand = null;
			updateIdlePhase(client);
			return;
		}

		// Dimension safety guard
		if (!target.isInDimension(getPlayerDimension(client))) {
			pause(Component.translatable(
					"happy_ghast_autopilot.msg.wrong_dimension",
					Component.literal(target.shortDimensionName()).withStyle(ChatFormatting.GOLD)
			).withStyle(ChatFormatting.RED));
			return;
		}

		// Ensure player is still driving a harnessed Happy Ghast
		HappyGhast ghast = getControlledHappyGhast(player);
		if (ghast == null || !player.isAlive() || !ghast.isAlive()) {
			pause(Component.translatable("happy_ghast_autopilot.msg.dismounted")
					.withStyle(ChatFormatting.YELLOW));
			return;
		}

		// Instant manual override when player presses WASD (ignored while a GUI/chat screen is open)
		AutopilotConfig cfg = AutopilotConfig.get();
		if (manualInputGraceTicks > 0) {
			manualInputGraceTicks--;
		} else if (cfg.pauseOnManualInput && ScreenCompat.isMovementKeyPhysicallyPressed(client)) {
			pause(Component.translatable(
					"happy_ghast_autopilot.msg.paused_manual",
					AutopilotKeybinds.toggleKeyName().copy().withStyle(ChatFormatting.YELLOW)
			).withStyle(ChatFormatting.YELLOW));
			return;
		}

		double gx = ghast.getX();
		double gy = ghast.getY();
		double gz = ghast.getZ();

		// Update smoothed ground speed (blocks/sec, 20 ticks/sec)
		if (Double.isFinite(lastX)) {
			double stepDist = Math.sqrt(
					(gx - lastX) * (gx - lastX)
							+ (gy - lastY) * (gy - lastY)
							+ (gz - lastZ) * (gz - lastZ)
			);
			double instantBps = stepDist * 20.0;
			if (instantBps < 60.0) { // ignore teleport spikes
				smoothedSpeedBps = smoothedSpeedBps <= 0.01
						? instantBps
						: smoothedSpeedBps * 0.85 + instantBps * 0.15;
			}
		}
		lastX = gx;
		lastY = gy;
		lastZ = gz;

		if (!Double.isFinite(sessionCruiseY)) {
			sessionCruiseY = gy;
		}

		double horizDist = target.horizontalDistanceTo(gx, gz);
		float directYaw = FlightPlanner.computeYawTo(gx, gz, target.centerX(), target.centerZ());
		EnvironmentScan scan = WorldScanner.scan(level, ghast, directYaw, horizDist, preferredDetourSign);

		// Update stuck tracker (skip while intentionally holding for chunk loading)
		if (scan.chunkAheadLoaded()) {
			if (!Double.isFinite(progressAnchorX)) {
				progressAnchorX = gx;
				progressAnchorY = gy;
				progressAnchorZ = gz;
				stuckTicks = 0;
			} else {
				double moved = Math.sqrt(
						(gx - progressAnchorX) * (gx - progressAnchorX)
								+ (gy - progressAnchorY) * (gy - progressAnchorY)
								+ (gz - progressAnchorZ) * (gz - progressAnchorZ)
				);
				if (moved >= 1.1) {
					progressAnchorX = gx;
					progressAnchorY = gy;
					progressAnchorZ = gz;
					stuckTicks = 0;
				} else {
					stuckTicks++;
					if (stuckTicks == DETOUR_FLIP_TICKS) {
						preferredDetourSign = -preferredDetourSign;
					} else if (stuckTicks >= STUCK_GIVE_UP_TICKS) {
						if (cfg.playArrivalSound) {
							player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.8f);
						}
						pause(Component.translatable(
								"happy_ghast_autopilot.msg.stuck",
								AutopilotKeybinds.toggleKeyName().copy().withStyle(ChatFormatting.YELLOW)
						).withStyle(ChatFormatting.RED));
						sendChat(client, Component.translatable(
								"happy_ghast_autopilot.msg.stuck",
								AutopilotKeybinds.toggleKeyName().copy().withStyle(ChatFormatting.YELLOW)
						).withStyle(ChatFormatting.RED));
						return;
					}
				}
			}
		}

		FlightSettings settings = cfg.toFlightSettings(level.getMinY(), level.getMaxY());
		SteeringCommand cmd = FlightPlanner.plan(
				gx, gy, gz,
				ghast.getYRot(), virtualPitch,
				target, sessionCruiseY, settings, scan,
				stuckTicks, preferredDetourSign
		);

		if (cmd.arrived()) {
			FlightTarget reached = target;
			active = false;
			activeCommand = null;
			target = null;
			phase = FlightPhase.IDLE;
			resetFlightTracking();

			if (cfg.playArrivalSound) {
				player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f, 1.15f);
			}
			Component msg = Component.translatable(
					"happy_ghast_autopilot.msg.arrived",
					Component.literal(reached.formatDisplay()).withStyle(ChatFormatting.GREEN)
			).withStyle(ChatFormatting.AQUA);
			sendActionBar(client, msg);
			sendChat(client, msg);
			return;
		}

		// Raise session cruise altitude softly when climbing over tall mountain ranges in auto mode,
		// so the ghast doesn't immediately dip into small crevices between peaks.
		if (!cfg.useFixedCruiseAltitude && !scan.hasCeiling() && cmd.desiredY() > sessionCruiseY) {
			sessionCruiseY = Math.min(cmd.desiredY(), sessionCruiseY + 0.35);
		}

		activeCommand = cmd;
		virtualPitch = cmd.pitch();
		phase = cmd.phase();

		// When Free Look is turned OFF, gently align the player's camera with the ghast's flight direction.
		if (!cfg.freeLook) {
			player.setYRot(FlightPlanner.approachAngle(player.getYRot(), cmd.yaw(), 8.0f));
			float camPitch = Math.clamp(cmd.pitch() * 0.45f, -35.0f, 45.0f);
			player.setXRot(FlightPlanner.approachAngle(player.getXRot(), camPitch, 5.0f));
		}
	}

	public static void onEndTick(Minecraft client) {
		AutopilotKeybinds.poll(client);
	}

	public static Component formatDistanceComponent(double distanceBlocks) {
		if (distanceBlocks >= 1000.0) {
			return Component.translatable("happy_ghast_autopilot.unit.km", EtaFormatter.formatKilometers(distanceBlocks));
		}
		return Component.translatable("happy_ghast_autopilot.unit.blocks", EtaFormatter.formatBlocks(distanceBlocks));
	}

	public static void sendChat(Minecraft client, Component body) {
		if (client == null || client.player == null) {
			return;
		}
		MutableComponent full = Component.translatable("happy_ghast_autopilot.prefix")
				.withStyle(ChatFormatting.DARK_AQUA)
				.append(body.copy());
		client.player.sendSystemMessage(full);
	}

	public static void sendActionBar(Minecraft client, Component body) {
		if (client == null || client.player == null) {
			return;
		}
		client.gui.hud.setOverlayMessage(body, false);
	}
}
