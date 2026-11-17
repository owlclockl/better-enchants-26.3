package net.ghastpilot.flight;

import java.util.Locale;

/**
 * Formatting helpers for distance, ground speed, and estimated time of arrival (ETA).
 */
public final class EtaFormatter {

	/** Nominal cruise speed of a vanilla Happy Ghast (~3.9 - 4.3 blocks/s). */
	public static final double NOMINAL_GHAST_SPEED_BPS = 3.9;

	private EtaFormatter() {
	}

	public static String formatSpeed(double blocksPerSecond) {
		if (!Double.isFinite(blocksPerSecond) || blocksPerSecond < 0.0) {
			return "0.0";
		}
		return String.format(Locale.ROOT, "%.1f", blocksPerSecond);
	}

	public static String formatEta(double distanceBlocks, double currentSpeedBps) {
		if (!Double.isFinite(distanceBlocks) || distanceBlocks <= 0.5) {
			return "00:00";
		}
		double effectiveSpeed = (Double.isFinite(currentSpeedBps) && currentSpeedBps >= 0.4)
				? currentSpeedBps
				: NOMINAL_GHAST_SPEED_BPS;
		long totalSeconds = Math.round(distanceBlocks / effectiveSpeed);
		if (totalSeconds < 0 || totalSeconds > 359_999L) {
			return "--:--";
		}
		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;
		if (hours > 0) {
			return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
	}

	public static String formatKilometers(double distanceBlocks) {
		return String.format(Locale.ROOT, "%.2f", distanceBlocks / 1000.0);
	}

	public static String formatBlocks(double distanceBlocks) {
		return Long.toString(Math.round(Math.max(0.0, distanceBlocks)));
	}

	/**
	 * Builds a compact 5-character directional arrow indicator showing relative bearing to target.
	 */
	public static String formatBearingIndicator(float currentYaw, float targetYaw) {
		float delta = FlightPlanner.angleDelta(currentYaw, targetYaw);
		if (Math.abs(delta) <= 10.0f) {
			return "▲";
		}
		if (delta > 0.0f) {
			return delta > 45.0f ? "▶▶" : "▶";
		}
		return delta < -45.0f ? "◀◀" : "◀";
	}
}
