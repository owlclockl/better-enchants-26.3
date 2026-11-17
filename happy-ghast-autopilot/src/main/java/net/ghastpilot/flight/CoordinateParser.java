package net.ghastpilot.flight;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flexible parser for coordinates pasted from chat, F3+C, or shared Xaero waypoints.
 */
public final class CoordinateParser {

	private static final int MAX_MC_COORD = 30_000_000;
	private static final int MIN_MC_Y = -2048;
	private static final int MAX_MC_Y = 4096;

	private static final Pattern F3C_TP_PATTERN = Pattern.compile(
			"/execute\\s+in\\s+([a-z0-9_.:-]+)\\s+run\\s+tp\\s+\\S+\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)",
			Pattern.CASE_INSENSITIVE
	);

	private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![a-zA-Z0-9_])-?\\d+(?:\\.\\d+)?");

	private CoordinateParser() {
	}

	public static Optional<FlightTarget> parse(String raw, String fallbackDimension) {
		if (raw == null) {
			return Optional.empty();
		}
		String text = raw.trim();
		if (text.isEmpty()) {
			return Optional.empty();
		}

		// 1. Xaero shared waypoint format:
		// xaero-waypoint:Name:Initial:X:Y:Z:Color:Disabled:Type:Set:Rotate:Yaw:Visibility:Dimension
		int xaeroIdx = text.toLowerCase().indexOf("xaero-waypoint:");
		if (xaeroIdx >= 0) {
			Optional<FlightTarget> wp = parseXaeroWaypoint(text.substring(xaeroIdx), fallbackDimension);
			if (wp.isPresent()) {
				return wp;
			}
		}

		// 2. Vanilla F3+C debug teleport string:
		// /execute in minecraft:overworld run tp @s 123.45 78.00 -456.78 ...
		Matcher tpMatcher = F3C_TP_PATTERN.matcher(text);
		if (tpMatcher.find()) {
			String dim = FlightTarget.normalizeDimension(tpMatcher.group(1));
			Integer x = toCoord(tpMatcher.group(2));
			Integer y = toHeight(tpMatcher.group(3));
			Integer z = toCoord(tpMatcher.group(4));
			if (x != null && z != null) {
				return Optional.of(new FlightTarget(x, y, z, dim, null));
			}
		}

		// 3. Generic X Z or X Y Z ("120 -340", "120, 80, -340", "X: 120, Y: ~, Z: -340", "[x:120, z:-340]")
		boolean hasTildeY = text.matches("(?i).*y\\s*[:=]?\\s*~.*");
		List<Double> numbers = new ArrayList<>();
		Matcher numMatcher = NUMBER_PATTERN.matcher(text);
		while (numMatcher.find() && numbers.size() < 6) {
			try {
				numbers.add(Double.parseDouble(numMatcher.group()));
			} catch (NumberFormatException ignored) {
			}
		}

		String dim = FlightTarget.normalizeDimension(fallbackDimension);
		if (numbers.size() == 2 || (numbers.size() >= 2 && hasTildeY)) {
			Integer x = clampCoord(numbers.get(0));
			Integer z = clampCoord(numbers.get(1));
			if (x != null && z != null) {
				return Optional.of(new FlightTarget(x, null, z, dim, null));
			}
		} else if (numbers.size() >= 3) {
			Integer x = clampCoord(numbers.get(0));
			Integer y = clampHeight(numbers.get(1));
			Integer z = clampCoord(numbers.get(2));
			if (x != null && z != null) {
				return Optional.of(new FlightTarget(x, y, z, dim, null));
			}
		}

		return Optional.empty();
	}

	private static Optional<FlightTarget> parseXaeroWaypoint(String wpText, String fallbackDimension) {
		String[] parts = wpText.split(":");
		if (parts.length < 6) {
			return Optional.empty();
		}
		String name = parts[1].replace("^col^", ":").trim();
		Integer x = parseOptionalInt(parts[3]);
		Integer y = parts[4].equals("~") ? null : parseOptionalInt(parts[4]);
		Integer z = parseOptionalInt(parts[5]);
		if (x == null || z == null || Math.abs(x) > MAX_MC_COORD || Math.abs(z) > MAX_MC_COORD) {
			return Optional.empty();
		}
		if (y != null && (y < MIN_MC_Y || y > MAX_MC_Y)) {
			y = null;
		}
		String dim = FlightTarget.normalizeDimension(fallbackDimension);
		for (int i = 6; i < parts.length; i++) {
			String token = parts[i].toLowerCase();
			if (token.contains("the-nether") || token.contains("the_nether")) {
				dim = "minecraft:the_nether";
				break;
			} else if (token.contains("the-end") || token.contains("the_end")) {
				dim = "minecraft:the_end";
				break;
			} else if (token.contains("overworld")) {
				dim = "minecraft:overworld";
				break;
			}
		}
		return Optional.of(new FlightTarget(x, y, z, dim, name.isEmpty() ? null : name));
	}

	public static Optional<Integer> parseSingleInt(String text) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		try {
			double d = Double.parseDouble(text.trim());
			if (!Double.isFinite(d)) {
				return Optional.empty();
			}
			long rounded = (long) Math.floor(d);
			if (Math.abs(rounded) > MAX_MC_COORD) {
				return Optional.empty();
			}
			return Optional.of((int) rounded);
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static Integer parseOptionalInt(String s) {
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer toCoord(String s) {
		try {
			return clampCoord(Double.parseDouble(s));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer toHeight(String s) {
		try {
			return clampHeight(Double.parseDouble(s));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Integer clampCoord(double v) {
		if (!Double.isFinite(v) || Math.abs(v) > MAX_MC_COORD) {
			return null;
		}
		return (int) Math.floor(v);
	}

	private static Integer clampHeight(double v) {
		if (!Double.isFinite(v)) {
			return null;
		}
		int iv = (int) Math.floor(v);
		if (iv < MIN_MC_Y || iv > MAX_MC_Y) {
			return null;
		}
		return iv;
	}
}
