package net.ghastpilot.flight;

/**
 * Pure, deterministic 3D flight controller for a Happy Ghast.
 * Handles smooth turning, smart cruise altitude over terrain, ceiling/Nether safety,
 * obstacle climbing & lateral detours, unloaded-chunk hold, approach deceleration,
 * and centered vertical landing when a target Y coordinate is set.
 */
public final class FlightPlanner {

	public static final float MAX_YAW_STEP = 12.0f;
	public static final float MAX_PITCH_STEP = 8.0f;
	public static final double VERTICAL_ARRIVAL_TOLERANCE = 1.75;
	public static final double APPROACH_DISTANCE = 10.0;
	public static final double WORLD_FLOOR_MARGIN = 6.0;
	public static final double WORLD_CEILING_MARGIN = 6.0;

	private FlightPlanner() {
	}

	/**
	 * Computes the Minecraft yaw angle (in degrees, wrapped to [-180, 180]) from (fromX, fromZ) to (toX, toZ).
	 * South (+Z) = 0, West (-X) = +90, North (-Z) = +/-180, East (+X) = -90.
	 */
	public static float computeYawTo(double fromX, double fromZ, double toX, double toZ) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		if (dx * dx + dz * dz < 1.0e-8) {
			return 0.0f;
		}
		double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
		return wrapDegrees((float) angle);
	}

	public static float wrapDegrees(float degrees) {
		float wrapped = degrees % 360.0f;
		if (wrapped >= 180.0f) {
			wrapped -= 360.0f;
		}
		if (wrapped < -180.0f) {
			wrapped += 360.0f;
		}
		return wrapped;
	}

	public static float angleDelta(float from, float to) {
		return wrapDegrees(to - from);
	}

	public static float approachAngle(float current, float target, float maxStep) {
		float delta = angleDelta(current, target);
		if (Math.abs(delta) <= maxStep) {
			return wrapDegrees(target);
		}
		return wrapDegrees(current + Math.copySign(maxStep, delta));
	}

	public static SteeringCommand plan(
			double x,
			double y,
			double z,
			float currentYaw,
			float currentPitch,
			FlightTarget target,
			double sessionCruiseY,
			FlightSettings settings,
			EnvironmentScan scan,
			int stuckTicks,
			int preferredDetourSign
	) {
		double arrivalRadius = Math.max(1.0, settings.arrivalRadius());
		double horizDist = target.horizontalDistanceTo(x, z);
		float targetYaw = horizDist > 0.2 ? computeYawTo(x, z, target.centerX(), target.centerZ()) : currentYaw;

		// 1. Horizontal arrival & optional vertical climb/descent
		if (horizDist <= arrivalRadius) {
			if (!target.hasY() || !settings.descendOnArrival()) {
				return SteeringCommand.arrived(currentYaw, y);
			}
			double safeTargetY = Math.clamp(
					target.y(),
					settings.worldMinY() + WORLD_FLOOR_MARGIN,
					settings.worldMaxY() - WORLD_CEILING_MARGIN
			);
			if (!Double.isNaN(scan.groundBelowY())) {
				safeTargetY = Math.max(safeTargetY, scan.groundBelowY() + 0.75);
			}
			double dy = safeTargetY - y;
			if (Math.abs(dy) <= VERTICAL_ARRIVAL_TOLERANCE) {
				return SteeringCommand.arrived(currentYaw, safeTargetY);
			}
			if (dy > 0.0) {
				if (!scan.canAscend()) {
					return SteeringCommand.arrived(currentYaw, y);
				}
				float pitch = approachAngle(currentPitch, -25.0f, MAX_PITCH_STEP);
				return new SteeringCommand(false, currentYaw, pitch, 0.0f, 0.0f, true, FlightPhase.CLIMBING, safeTargetY);
			} else {
				if (!scan.canDescend() || scan.hazardBelow()) {
					return SteeringCommand.arrived(currentYaw, y);
				}
				// Point toward (target.centerX, target.centerZ) so the tiny cos(83deg) horizontal component
				// keeps the Happy Ghast centered directly over the target column while descending.
				// Vanilla HappyGhast#tickRidden smoothly damps yaw toward cmd.yaw() (diff * 0.08f per tick).
				float descendYaw = horizDist > 0.25 ? targetYaw : currentYaw;
				float descendPitch = approachAngle(currentPitch, 83.0f, MAX_PITCH_STEP * 1.5f);
				// Wait until pitched cleanly downward before pushing forward so we don't drift off target.
				float fwd = descendPitch >= 55.0f
						? (float) Math.clamp((-dy) / 8.0, 0.40, 0.95)
						: 0.0f;
				return new SteeringCommand(false, descendYaw, descendPitch, fwd, 0.0f, false, FlightPhase.DESCENDING, safeTargetY);
			}
		}

		// 2. Pause in place if the chunk ahead hasn't streamed in yet
		if (!scan.chunkAheadLoaded() && horizDist > 8.0) {
			return SteeringCommand.hold(targetYaw, FlightPhase.WAITING_CHUNKS, y);
		}

		// 3. Emergency hazard avoidance (lava / fire / void right below)
		if (scan.hazardBelow() && scan.canAscend()) {
			float nextPitch = approachAngle(currentPitch, -40.0f, MAX_PITCH_STEP);
			float fwd = scan.immediateObstacleAhead() ? 0.0f : 0.75f;
			return new SteeringCommand(false, targetYaw, nextPitch, fwd, 0.0f, true, FlightPhase.CLIMBING, y + 6.0);
		}

		// 4. Immediate obstacle or stuck recovery
		if (scan.immediateObstacleAhead() || stuckTicks >= 35) {
			// Prefer climbing vertically in open sky, or whenever no clean lateral gap exists.
			if (scan.canAscend() && (!scan.hasCeiling() || scan.clearDetourYaw() == null)) {
				float nextPitch = approachAngle(currentPitch, -42.0f, MAX_PITCH_STEP);
				return new SteeringCommand(false, targetYaw, nextPitch, 0.0f, 0.0f, true, FlightPhase.CLIMBING, y + 8.0);
			}
			// Under a ceiling (Nether / cave / overhang), steer through the clearest lateral opening.
			if (scan.clearDetourYaw() != null) {
				float detourYaw = scan.clearDetourYaw();
				float err = Math.abs(angleDelta(currentYaw, detourYaw));
				float nextPitch = approachAngle(currentPitch, 0.0f, MAX_PITCH_STEP);
				float fwd = err <= 40.0f ? 0.85f : 0.0f;
				return new SteeringCommand(false, detourYaw, nextPitch, fwd, 0.0f, false, FlightPhase.AVOIDING, y);
			}
			// Try ducking cleanly under a low overhang if space below is safe.
			if (scan.canDescend() && !scan.hazardBelow()) {
				float nextPitch = approachAngle(currentPitch, 74.0f, MAX_PITCH_STEP * 1.25f);
				float fwd = nextPitch >= 50.0f ? 0.55f : 0.0f;
				return new SteeringCommand(false, targetYaw, nextPitch, fwd, 0.0f, false, FlightPhase.AVOIDING, y - 4.0);
			}
			// Last resort: gentle sidestep while stuck detector tracks progress.
			float strafe = (preferredDetourSign >= 0 ? 1.0f : -1.0f) * 0.75f;
			return new SteeringCommand(false, targetYaw, 0.0f, 0.0f, strafe, scan.canAscend(), FlightPhase.AVOIDING, y);
		}

		// 5. Compute desired cruise altitude
		double desiredY = computeDesiredCruiseY(y, horizDist, target, sessionCruiseY, settings, scan);
		if (scan.nearObstacleAhead() && scan.canAscend() && !scan.hasCeiling()) {
			desiredY = Math.max(desiredY, Math.min(y + 6.0, settings.worldMaxY() - WORLD_CEILING_MARGIN));
		}

		// 6. Choose steering heading (HappyGhast#tickRidden applies vanilla 0.08f/tick rotational damping)
		boolean usingDetour = false;
		float desiredYaw = targetYaw;
		if (scan.nearObstacleAhead() && (scan.hasCeiling() || !scan.canAscend()) && scan.clearDetourYaw() != null) {
			desiredYaw = scan.clearDetourYaw();
			usingDetour = true;
		}
		float yawError = Math.abs(angleDelta(currentYaw, desiredYaw));

		// 7. Compute smooth pitch & vertical jump boost to hold desiredY without oscillation
		double dy = desiredY - y;
		float targetPitch = 0.0f;
		boolean jump = false;
		if (Math.abs(dy) > 0.65) {
			if (dy > 0.0) {
				if (scan.canAscend()) {
					targetPitch = (float) -Math.min(40.0, dy * 4.2);
					jump = dy > 2.5;
				}
			} else if (scan.canDescend() && !scan.hazardBelow()) {
				// On final horizontal approach with an explicit Y below us, stay roughly level first
				// so we lock onto exact (X, Z) and then descend vertically.
				boolean holdForVerticalLanding = horizDist < 7.0 && target.hasY() && settings.descendOnArrival();
				if (!holdForVerticalLanding) {
					targetPitch = (float) Math.min(35.0, (-dy) * 3.8);
				}
			}
		}
		float nextPitch = approachAngle(currentPitch, targetPitch, MAX_PITCH_STEP);

		// 8. Compute forward thrust with banking reduction & smooth arrival deceleration
		float forward;
		if (yawError > 70.0f) {
			forward = 0.0f;
		} else if (yawError > 25.0f) {
			forward = 0.55f;
		} else {
			forward = 1.0f;
		}

		if (horizDist < APPROACH_DISTANCE) {
			float approachScale = (float) Math.clamp(0.28 + 0.72 * (horizDist / APPROACH_DISTANCE), 0.28, 1.0);
			forward = Math.min(forward, approachScale);
		}

		FlightPhase phase;
		if (usingDetour) {
			phase = FlightPhase.AVOIDING;
		} else if (jump && dy > 3.5) {
			phase = FlightPhase.CLIMBING;
		} else if (horizDist < APPROACH_DISTANCE + 2.0) {
			phase = FlightPhase.APPROACHING;
		} else {
			phase = FlightPhase.CRUISING;
		}

		return new SteeringCommand(false, desiredYaw, nextPitch, forward, 0.0f, jump, phase, desiredY);
	}

	public static double computeDesiredCruiseY(
			double currentY,
			double horizDist,
			FlightTarget target,
			double sessionCruiseY,
			FlightSettings settings,
			EnvironmentScan scan
	) {
		double minAllowed = settings.worldMinY() + WORLD_FLOOR_MARGIN;
		double maxAllowed = settings.worldMaxY() - WORLD_CEILING_MARGIN;
		int clearance = Math.max(4, settings.terrainClearance());

		if (scan.hasCeiling()) {
			// Closed dimension / cave (e.g. Nether): stay level in the clear airspace,
			// gently converging toward explicit target Y when approaching.
			double desired = settings.useFixedCruiseAltitude() ? settings.fixedCruiseAltitude() : currentY;
			if (target.hasY() && horizDist < 48.0) {
				desired = target.y();
			}
			if (!Double.isNaN(scan.groundBelowY()) && horizDist > 12.0) {
				desired = Math.max(desired, scan.groundBelowY() + Math.min(4.0, clearance));
			}
			if (!scan.canAscend()) {
				desired = Math.min(desired, currentY);
			}
			return Math.clamp(desired, minAllowed, maxAllowed);
		}

		double baseY;
		if (settings.useFixedCruiseAltitude()) {
			baseY = settings.fixedCruiseAltitude();
		} else {
			baseY = Double.isFinite(sessionCruiseY) ? sessionCruiseY : currentY;
			if (target.hasY() && target.y() + 2.0 > baseY) {
				baseY = target.y() + 2.0;
			}
		}

		if (!Double.isNaN(scan.highestTerrainAhead())) {
			baseY = Math.max(baseY, scan.highestTerrainAhead() + clearance);
		}
		if (!Double.isNaN(scan.groundBelowY()) && horizDist > 14.0) {
			baseY = Math.max(baseY, scan.groundBelowY() + clearance);
		}

		return Math.clamp(baseY, minAllowed, maxAllowed);
	}
}
