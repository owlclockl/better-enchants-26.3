package net.ghastpilot.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Thin wrapper around Minecraft 26.3 screen/HUD/key state helpers.
 */
public final class ScreenCompat {

	private ScreenCompat() {
	}

	public static Screen getCurrentScreen(Minecraft client) {
		return client.gui.screen();
	}

	public static void openScreen(Minecraft client, Screen screen) {
		client.gui.setScreen(screen);
	}

	public static boolean isHudHidden(Minecraft client) {
		return client.options.hideGui;
	}

	public static boolean isMovementKeyPhysicallyPressed(Minecraft client) {
		if (getCurrentScreen(client) != null) {
			return false;
		}
		return isKeyPhysicallyDown(client, client.options.keyUp)
				|| isKeyPhysicallyDown(client, client.options.keyDown)
				|| isKeyPhysicallyDown(client, client.options.keyLeft)
				|| isKeyPhysicallyDown(client, client.options.keyRight);
	}

	private static boolean isKeyPhysicallyDown(Minecraft client, KeyMapping mapping) {
		if (mapping == null || mapping.isUnbound()) {
			return false;
		}
		if (mapping.isDown()) {
			return true;
		}
		try {
			InputConstants.Key key = KeyMapping.getKey(mapping.saveString());
			if (key != null && key.getType() == InputConstants.Type.KEYSYM && key.getValue() > 0) {
				return InputConstants.isKeyDown(client.getWindow(), key.getValue());
			}
		} catch (Exception ignored) {
		}
		return false;
	}
}
