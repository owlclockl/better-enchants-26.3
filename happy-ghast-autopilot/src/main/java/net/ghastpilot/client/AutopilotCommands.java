package net.ghastpilot.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.ghastpilot.flight.EtaFormatter;
import net.ghastpilot.flight.FlightPhase;
import net.ghastpilot.flight.FlightTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Registers client commands {@code /ghastpilot} and shorthand {@code /gp}.
 *
 * <ul>
 *   <li>{@code /gp} or {@code /gp gui} — open autopilot screen</li>
 *   <li>{@code /gp <x> <z>} — fly to (X, Z) at safe cruise altitude</li>
 *   <li>{@code /gp <x> <y> <z>} — fly to (X, Y, Z) with landing at Y</li>
 *   <li>{@code /gp stop} / {@code pause} — pause autopilot</li>
 *   <li>{@code /gp resume} / {@code start} — resume flying to saved target</li>
 *   <li>{@code /gp clear} — clear target and stop</li>
 *   <li>{@code /gp altitude auto} / {@code /gp altitude <y>} — configure cruise altitude</li>
 *   <li>{@code /gp clearance <blocks>} — set terrain clearance in blocks</li>
 *   <li>{@code /gp freelook <true|false>} — toggle free camera look</li>
 *   <li>{@code /gp descend <true|false>} — toggle landing at target Y</li>
 *   <li>{@code /gp status} — display current target, distance, speed & ETA</li>
 * </ul>
 */
public final class AutopilotCommands {

	private AutopilotCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			registerRoot(dispatcher, "ghastpilot");
			registerRoot(dispatcher, "gp");
		});
	}

	private static void registerRoot(CommandDispatcher<FabricClientCommandSource> dispatcher, String name) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = literal(name)
				.executes(ctx -> openGui())
				.then(literal("gui").executes(ctx -> openGui()))
				.then(literal("stop").executes(ctx -> stop()))
				.then(literal("pause").executes(ctx -> stop()))
				.then(literal("resume").executes(ctx -> resume()))
				.then(literal("start").executes(ctx -> resume()))
				.then(literal("clear").executes(ctx -> clear()))
				.then(literal("status").executes(ctx -> status()))
				.then(literal("altitude")
						.then(literal("auto").executes(ctx -> setAltitudeAuto()))
						.then(argument("y", IntegerArgumentType.integer(-60, 316))
								.executes(ctx -> setAltitudeFixed(IntegerArgumentType.getInteger(ctx, "y")))))
				.then(literal("clearance")
						.then(argument("blocks", IntegerArgumentType.integer(4, 64))
								.executes(ctx -> setClearance(IntegerArgumentType.getInteger(ctx, "blocks")))))
				.then(literal("freelook")
						.then(argument("enabled", BoolArgumentType.bool())
								.executes(ctx -> setFreeLook(BoolArgumentType.getBool(ctx, "enabled")))))
				.then(literal("descend")
						.then(argument("enabled", BoolArgumentType.bool())
								.executes(ctx -> setDescend(BoolArgumentType.getBool(ctx, "enabled")))))
				.then(argument("x", IntegerArgumentType.integer(-30_000_000, 30_000_000))
						.then(argument("second", IntegerArgumentType.integer(-30_000_000, 30_000_000))
								.executes(ctx -> flyToXZ(
										IntegerArgumentType.getInteger(ctx, "x"),
										IntegerArgumentType.getInteger(ctx, "second")
								))
								.then(argument("z", IntegerArgumentType.integer(-30_000_000, 30_000_000))
										.executes(ctx -> flyToXYZ(
												IntegerArgumentType.getInteger(ctx, "x"),
												IntegerArgumentType.getInteger(ctx, "second"),
												IntegerArgumentType.getInteger(ctx, "z")
										)))));

		dispatcher.register(root);
	}

	private static int openGui() {
		Minecraft client = Minecraft.getInstance();
		// Defer by one tick so closing the chat command screen doesn't immediately overwrite our screen.
		client.execute(() -> ScreenCompat.openScreen(client, new AutopilotScreen(null)));
		return 1;
	}

	private static int flyToXZ(int x, int z) {
		Minecraft client = Minecraft.getInstance();
		String dim = AutopilotController.getPlayerDimension(client);
		AutopilotController.setTarget(FlightTarget.ofXZ(x, z, dim), true);
		return 1;
	}

	private static int flyToXYZ(int x, int y, int z) {
		Minecraft client = Minecraft.getInstance();
		String dim = AutopilotController.getPlayerDimension(client);
		int clampedY = Math.clamp(y, -2048, 4096);
		AutopilotController.setTarget(FlightTarget.ofXYZ(x, clampedY, z, dim), true);
		return 1;
	}

	private static int stop() {
		FlightTarget t = AutopilotController.getTarget();
		AutopilotController.pause(Component.translatable(
				"happy_ghast_autopilot.msg.paused",
				Component.literal(t != null ? t.formatDisplay() : "-").withStyle(ChatFormatting.AQUA),
				AutopilotKeybinds.toggleKeyName().copy().withStyle(ChatFormatting.YELLOW)
		));
		return 1;
	}

	private static int resume() {
		AutopilotController.resume();
		return 1;
	}

	private static int clear() {
		AutopilotController.clearTarget(true);
		return 1;
	}

	private static int setAltitudeAuto() {
		AutopilotConfig cfg = AutopilotConfig.get();
		cfg.useFixedCruiseAltitude = false;
		AutopilotConfig.save();
		AutopilotController.sendChat(Minecraft.getInstance(), Component.translatable(
				"happy_ghast_autopilot.msg.altitude_auto",
				Component.literal(Integer.toString(cfg.terrainClearance)).withStyle(ChatFormatting.AQUA)
		));
		return 1;
	}

	private static int setAltitudeFixed(int y) {
		AutopilotConfig cfg = AutopilotConfig.get();
		cfg.useFixedCruiseAltitude = true;
		cfg.fixedCruiseAltitude = y;
		AutopilotConfig.save();
		AutopilotController.sendChat(Minecraft.getInstance(), Component.translatable(
				"happy_ghast_autopilot.msg.altitude_fixed",
				Component.literal(Integer.toString(y)).withStyle(ChatFormatting.AQUA)
		));
		return 1;
	}

	private static int setClearance(int blocks) {
		AutopilotConfig cfg = AutopilotConfig.get();
		cfg.terrainClearance = blocks;
		cfg.useFixedCruiseAltitude = false;
		AutopilotConfig.save();
		AutopilotController.sendChat(Minecraft.getInstance(), Component.translatable(
				"happy_ghast_autopilot.msg.altitude_auto",
				Component.literal(Integer.toString(blocks)).withStyle(ChatFormatting.AQUA)
		));
		return 1;
	}

	private static int setFreeLook(boolean enabled) {
		AutopilotConfig cfg = AutopilotConfig.get();
		cfg.freeLook = enabled;
		AutopilotConfig.save();
		AutopilotController.sendChat(Minecraft.getInstance(), Component.translatable(
				enabled ? "happy_ghast_autopilot.msg.freelook_on" : "happy_ghast_autopilot.msg.freelook_off"
		));
		return 1;
	}

	private static int setDescend(boolean enabled) {
		AutopilotConfig cfg = AutopilotConfig.get();
		cfg.descendOnArrival = enabled;
		AutopilotConfig.save();
		AutopilotController.sendChat(Minecraft.getInstance(), Component.translatable(
				enabled ? "happy_ghast_autopilot.msg.descend_on" : "happy_ghast_autopilot.msg.descend_off"
		));
		return 1;
	}

	private static int status() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		AutopilotConfig cfg = AutopilotConfig.get();
		FlightTarget target = AutopilotController.getTarget();
		FlightPhase phase = AutopilotController.getPhase();

		AutopilotController.sendChat(client,
				Component.translatable("happy_ghast_autopilot.msg.status_header").withStyle(ChatFormatting.AQUA));
		if (target == null) {
			AutopilotController.sendChat(client,
					Component.translatable("happy_ghast_autopilot.msg.status_no_target").withStyle(ChatFormatting.GRAY));
		} else {
			double dist = target.totalPathDistanceFrom(
					client.player.getX(), client.player.getY(), client.player.getZ(), cfg.descendOnArrival
			);
			String eta = EtaFormatter.formatEta(dist, AutopilotController.getSmoothedSpeedBps());
			AutopilotController.sendChat(client, Component.translatable(
					"happy_ghast_autopilot.msg.status_target",
					Component.literal(target.formatDisplay()).withStyle(ChatFormatting.GREEN),
					Component.literal(target.shortDimensionName()).withStyle(ChatFormatting.GOLD),
					AutopilotController.formatDistanceComponent(dist).copy().withStyle(ChatFormatting.WHITE),
					Component.literal(eta).withStyle(ChatFormatting.YELLOW)
			));
		}
		Component altText = cfg.useFixedCruiseAltitude
				? Component.translatable("happy_ghast_autopilot.screen.altitude_fixed", cfg.fixedCruiseAltitude)
				: Component.translatable("happy_ghast_autopilot.screen.altitude_auto", cfg.terrainClearance);
		Component freeText = Component.translatable(
				cfg.freeLook ? "happy_ghast_autopilot.state.on" : "happy_ghast_autopilot.state.off"
		);
		AutopilotController.sendChat(client, Component.translatable(
				"happy_ghast_autopilot.msg.status_mode",
				Component.translatable(phase.translationKey()).withStyle(ChatFormatting.AQUA),
				altText.copy().withStyle(ChatFormatting.WHITE),
				freeText.copy().withStyle(cfg.freeLook ? ChatFormatting.GREEN : ChatFormatting.RED)
		));
		return 1;
	}
}
