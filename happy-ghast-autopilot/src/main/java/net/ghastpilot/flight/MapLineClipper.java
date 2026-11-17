package net.ghastpilot.flight;

/**
 * Cohen-Sutherland 2D line clipper in double precision.
 * Allows rendering routes tens of thousands of blocks long on Xaero's World Map
 * without integer coordinate overflow or off-screen overdraw.
 */
public final class MapLineClipper {

	private static final int INSIDE = 0;
	private static final int LEFT = 1;
	private static final int RIGHT = 2;
	private static final int TOP = 4;
	private static final int BOTTOM = 8;

	private MapLineClipper() {
	}

	private static int outCode(double x, double y, double minX, double minY, double maxX, double maxY) {
		int code = INSIDE;
		if (x < minX) {
			code |= LEFT;
		} else if (x > maxX) {
			code |= RIGHT;
		}
		if (y < minY) {
			code |= TOP;
		} else if (y > maxY) {
			code |= BOTTOM;
		}
		return code;
	}

	/**
	 * Clips segment (x0, y0) -> (x1, y1) against rectangle [minX, maxX] x [minY, maxY].
	 * Returns {@code double[]{cx0, cy0, cx1, cy1}} if visible, or {@code null} if wholly outside.
	 */
	public static double[] clip(
			double x0, double y0,
			double x1, double y1,
			double minX, double minY,
			double maxX, double maxY
	) {
		if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1) || !Double.isFinite(y1)) {
			return null;
		}
		int code0 = outCode(x0, y0, minX, minY, maxX, maxY);
		int code1 = outCode(x1, y1, minX, minY, maxX, maxY);

		for (int iter = 0; iter < 16; iter++) {
			if ((code0 | code1) == 0) {
				return new double[]{x0, y0, x1, y1};
			}
			if ((code0 & code1) != 0) {
				return null;
			}
			int out = code0 != 0 ? code0 : code1;
			double x;
			double y;
			double dx = x1 - x0;
			double dy = y1 - y0;
			if ((out & BOTTOM) != 0) {
				x = Math.abs(dy) > 1.0e-12 ? x0 + dx * (maxY - y0) / dy : x0;
				y = maxY;
			} else if ((out & TOP) != 0) {
				x = Math.abs(dy) > 1.0e-12 ? x0 + dx * (minY - y0) / dy : x0;
				y = minY;
			} else if ((out & RIGHT) != 0) {
				y = Math.abs(dx) > 1.0e-12 ? y0 + dy * (maxX - x0) / dx : y0;
				x = maxX;
			} else {
				y = Math.abs(dx) > 1.0e-12 ? y0 + dy * (minX - x0) / dx : y0;
				x = minX;
			}
			if (out == code0) {
				x0 = x;
				y0 = y;
				code0 = outCode(x0, y0, minX, minY, maxX, maxY);
			} else {
				x1 = x;
				y1 = y;
				code1 = outCode(x1, y1, minX, minY, maxX, maxY);
			}
		}
		return null;
	}
}
