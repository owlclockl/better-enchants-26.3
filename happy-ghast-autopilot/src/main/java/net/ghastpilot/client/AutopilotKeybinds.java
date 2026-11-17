package net.ghastpilot.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.ghastpilot.HappyGhastAutopilotClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class AutopilotKeybinds {

	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(HappyGhastAutopilotClient.id("main"));

	public static final KeyMapping TOGGLE_AUTOPILOT = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key." + HappyGhastAutopilotClient.MOD_ID + ".toggle",
			InputConstants.KEY_G,
			CATEGORY
	));

	public static final KeyMapping OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key." + HappyGhastAutopilotClient.MOD_ID + ".open_menu",
			InputConstants.KEY_H,
			CATEGORY
	));

	private AutopilotKeybinds() {
	}

	public static void init() {
	}

	public static Component toggleKeyName() {
		return TOGGLE_AUTOPILOT.getTranslatedKeyMessage();
	}

	public static Component menuKeyName() {
		return OPEN_MENU.getTranslatedKeyMessage();
	}

	public static void poll(Minecraft client) {
		while (TOGGLE_AUTOPILOT.consumeClick()) {
			if (client.player != null) {
				if (AutopilotController.getTarget() == null && ScreenCompat.getCurrentScreen(client) == null) {
					ScreenCompat.openScreen(client, new AutopilotScreen(null));
				} else {
					AutopilotController.toggle();
				}
			}
		}
		while (OPEN_MENU.consumeClick()) {
			if (client.player != null && ScreenCompat.getCurrentScreen(client) == null) {
				ScreenCompat.openScreen(client, new AutopilotScreen(null));
			}
		}
	}
}
