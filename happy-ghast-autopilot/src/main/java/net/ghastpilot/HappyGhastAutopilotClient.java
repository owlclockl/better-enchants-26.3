package net.ghastpilot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.ghastpilot.client.AutopilotCommands;
import net.ghastpilot.client.AutopilotConfig;
import net.ghastpilot.client.AutopilotController;
import net.ghastpilot.client.AutopilotHud;
import net.ghastpilot.client.AutopilotKeybinds;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HappyGhastAutopilotClient implements ClientModInitializer {

	public static final String MOD_ID = "happy-ghast-autopilot";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		AutopilotConfig.load();
		AutopilotKeybinds.init();
		AutopilotCommands.register();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AutopilotController.onDisconnect());
		ClientTickEvents.START_CLIENT_TICK.register(AutopilotController::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(AutopilotController::onEndTick);

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				id("autopilot_hud"),
				AutopilotHud::extract
		);

		boolean xaeroLoaded = FabricLoader.getInstance().isModLoaded("xaeroworldmap");
		LOGGER.info("Happy Ghast Autopilot initialized (Xaero's World Map integration: {})",
				xaeroLoaded ? "enabled" : "not installed");
	}
}
