package net.ghastpilot.client;

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
		return client.gui.hud.isHidden();
	}

	/**
	 * Returns true if the player is actively holding any manual WASD movement key while no GUI screen is open.
	 * Works for both keyboard and mouse-button keybinds because autopilot steers via HappyGhast directly
	 * without mutating Options key mappings.
	 */
	public static boolean isMovementKeyPhysicallyPressed(Minecraft client) {
		if (getCurrentScreen(client) != null) {
			return false;
		}
		return isDown(client.options.keyUp)
				|| isDown(client.options.keyDown)
				|| isDown(client.options.keyLeft)
				|| isDown(client.options.keyRight);
	}

	private static boolean isDown(KeyMapping mapping) {
		return mapping != null && !mapping.isUnbound() && mapping.isDown();
	}
}
