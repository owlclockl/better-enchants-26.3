package net.ghastpilot.flight;

/**
 * Per-tick control output for a ridden Happy Ghast.
 * All values obey vanilla Happy Ghast control ranges:
 * yaw in [-180, 180], pitch in [-89, 89], forward in [-1, 1], strafe in [-1, 1].
 */
public record SteeringCommand(
		boolean arrived,
		float yaw,
		float pitch,
		float forward,
		float strafe,
		boolean jump,
		FlightPhase phase,
		double desiredY
) {
	public SteeringCommand {
		yaw = FlightPlanner.wrapDegrees(yaw);
		pitch = Math.clamp(pitch, -89.0f, 89.0f);
		forward = Math.clamp(forward, -1.0f, 1.0f);
		strafe = Math.clamp(strafe, -1.0f, 1.0f);
	}

	public static SteeringCommand arrived(float currentYaw, double currentY) {
		return new SteeringCommand(true, currentYaw, 0.0f, 0.0f, 0.0f, false, FlightPhase.IDLE, currentY);
	}

	public static SteeringCommand hold(float yaw, FlightPhase phase, double currentY) {
		return new SteeringCommand(false, yaw, 0.0f, 0.0f, 0.0f, false, phase, currentY);
	}

	/**
	 * Computes the exact vanilla HappyGhast local ridden-input components (x, y, z)
	 * before applying {@code 3.9F * getAttributeValue(Attributes.FLYING_SPEED)}.
	 *
	 * <p>In vanilla {@code HappyGhast#getRiddenInput(Player, Vec3)}:
	 * <ul>
	 *   <li>{@code x = strafe}</li>
	 *   <li>when {@code forward != 0}: {@code z = cos(pitch)}, {@code y = -sin(pitch)} (scaled by -0.5 if backward)</li>
	 *   <li>when {@code jump}: {@code y += 0.5f}</li>
	 * </ul>
	 * When {@code 0 < forward < 1} (smooth approach deceleration), we scale the forward/vertical component
	 * strictly within {@code [0, 1]} so speed never exceeds the vanilla Happy Ghast maximum.
	 */
	public double[] computeLocalInputComponents() {
		float f = this.strafe;
		float yComp = 0.0f;
		float zComp = 0.0f;
		if (Math.abs(this.forward) > 1.0e-4f) {
			double rad = Math.toRadians(this.pitch);
			float horiz = (float) Math.cos(rad);
			float vert = (float) -Math.sin(rad);
			if (this.forward < 0.0f) {
				float backScale = -0.5f * Math.min(1.0f, -this.forward);
				horiz *= backScale;
				vert *= backScale;
			} else {
				float fwdScale = Math.min(1.0f, this.forward);
				horiz *= fwdScale;
				vert *= fwdScale;
			}
			yComp = vert;
			zComp = horiz;
		}
		if (this.jump) {
			yComp += 0.5f;
		}
		return new double[]{f, yComp, zComp};
	}
}
