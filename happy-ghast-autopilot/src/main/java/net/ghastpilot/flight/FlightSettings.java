package net.ghastpilot.flight;

public record FlightSettings(
		boolean useFixedCruiseAltitude,
		int fixedCruiseAltitude,
		int terrainClearance,
		boolean descendOnArrival,
		double arrivalRadius,
		double worldMinY,
		double worldMaxY
) {
	public static FlightSettings defaults() {
		return new FlightSettings(false, 130, 12, true, 2.5, -64.0, 320.0);
	}
}
