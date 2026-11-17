package net.ghastpilot.flight;

/**
 * Snapshot of the 3D environment surrounding the Happy Ghast (4x4x4 hull + rider headroom).
 */
public record EnvironmentScan(
		boolean chunkAheadLoaded,
		boolean immediateObstacleAhead,
		boolean nearObstacleAhead,
		boolean canAscend,
		boolean canDescend,
		boolean hasCeiling,
		boolean hazardBelow,
		double highestTerrainAhead,
		double groundBelowY,
		Float clearDetourYaw
) {
	public static EnvironmentScan clearOpenSky(double groundY) {
		return new EnvironmentScan(
				true,
				false,
				false,
				true,
				Double.isNaN(groundY),
				false,
				false,
				groundY,
				groundY,
				null
		);
	}
}
