package net.ghastpilot.flight;

import java.util.Objects;

/**
 * Immutable autopilot destination in a specific dimension.
 * When {@code y} is {@code null}, the ghast flies to {@code (x, z)} at safe cruise altitude and hovers.
 * When {@code y} is non-null, the ghast can optionally descend/ascend to {@code y} upon reaching {@code (x, z)}.
 */
public record FlightTarget(int x, Integer y, int z, String dimension, String label) {

	public static final String DEFAULT_DIMENSION = "minecraft:overworld";

	public FlightTarget {
		if (dimension == null || dimension.isBlank()) {
			dimension = DEFAULT_DIMENSION;
		}
		if (label != null) {
			label = label.trim();
			if (label.isEmpty()) {
				label = null;
			}
		}
	}

	public static FlightTarget ofXZ(int x, int z, String dimension) {
		return new FlightTarget(x, null, z, dimension, null);
	}

	public static FlightTarget ofXYZ(int x, int y, int z, String dimension) {
		return new FlightTarget(x, y, z, dimension, null);
	}

	public boolean hasY() {
		return y != null;
	}

	public double centerX() {
		return x + 0.5;
	}

	public double centerZ() {
		return z + 0.5;
	}

	public double horizontalDistanceTo(double px, double pz) {
		return Math.hypot(centerX() - px, centerZ() - pz);
	}

	public double totalPathDistanceFrom(double px, double py, double pz, boolean includeVertical) {
		double horiz = horizontalDistanceTo(px, pz);
		if (!includeVertical || y == null) {
			return horiz;
		}
		return horiz + Math.abs(y - py);
	}

	public boolean isInDimension(String currentDimension) {
		if (currentDimension == null || currentDimension.isBlank()) {
			return true;
		}
		return normalizeDimension(dimension).equals(normalizeDimension(currentDimension));
	}

	public static String normalizeDimension(String dim) {
		if (dim == null || dim.isBlank()) {
			return DEFAULT_DIMENSION;
		}
		String clean = dim.trim().toLowerCase();
		if (!clean.contains(":")) {
			if (clean.equals("theend") || clean.equals("end")) {
				return "minecraft:the_end";
			}
			if (clean.equals("nether")) {
				return "minecraft:the_nether";
			}
			return "minecraft:" + clean;
		}
		return clean;
	}

	public String formatCoords() {
		if (y != null) {
			return x + ", " + y + ", " + z;
		}
		return x + ", " + z;
	}

	public String formatDisplay() {
		if (label != null) {
			return label + " (" + formatCoords() + ")";
		}
		return formatCoords();
	}

	public String shortDimensionName() {
		String norm = normalizeDimension(dimension);
		return switch (norm) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> norm.startsWith("minecraft:") ? norm.substring("minecraft:".length()) : norm;
		};
	}

	public boolean sameCoordinatesAndDimension(FlightTarget other) {
		if (other == null) {
			return false;
		}
		return x == other.x
				&& z == other.z
				&& Objects.equals(y, other.y)
				&& isInDimension(other.dimension);
	}
}
