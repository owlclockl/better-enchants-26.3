package net.ghastpilot.flight;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FlightPlannerTest {

	@Test
	void cardinalYawMatchesMinecraftConvention() {
		assertEquals(0.0f, FlightPlanner.computeYawTo(0, 0, 0, 10), 1e-4f);     // South (+Z)
		assertEquals(90.0f, FlightPlanner.computeYawTo(0, 0, -10, 0), 1e-4f);   // West (-X)
		assertEquals(180.0f, Math.abs(FlightPlanner.computeYawTo(0, 0, 0, -10)), 1e-4f); // North (-Z)
		assertEquals(-90.0f, FlightPlanner.computeYawTo(0, 0, 10, 0), 1e-4f);   // East (+X)
	}

	@Test
	void simulatedCruiseReachesXZDestinationSmoothly() {
		FlightTarget target = FlightTarget.ofXZ(120, -160, "minecraft:overworld");
		FlightSettings settings = FlightSettings.defaults();
		EnvironmentScan openSky = new EnvironmentScan(
				true, false, false, true, true, false, false, 64.0, 64.0, null
		);

		double x = 0.0, y = 90.0, z = 0.0;
		float yaw = 0.0f, pitch = 0.0f;
		boolean arrived = false;

		for (int tick = 0; tick < 3000; tick++) {
			SteeringCommand cmd = FlightPlanner.plan(
					x, y, z, yaw, pitch, target, 90.0, settings, openSky, 0, 1
			);
			if (cmd.arrived()) {
				arrived = true;
				break;
			}
			double[] step = toWorldVelocity(cmd, 0.20); // ~4.0 blocks/s
			x += step[0];
			y += step[1];
			z += step[2];
			yaw = cmd.yaw();
			pitch = cmd.pitch();
		}

		assertTrue(arrived, "Happy Ghast should arrive at target within time budget");
		assertTrue(target.horizontalDistanceTo(x, z) <= settings.arrivalRadius(),
				"Should finish inside horizontal arrival radius");
		assertEquals(90.0, y, 2.0, "Should hold level cruise altitude");
	}

	@Test
	void simulatedFlightWithTargetYDescendsCenteredOntoLandingSpot() {
		FlightTarget target = FlightTarget.ofXYZ(-60, 70, 80, "minecraft:overworld");
		FlightSettings settings = FlightSettings.defaults();

		double x = 0.0, y = 125.0, z = 0.0;
		float yaw = -90.0f, pitch = 0.0f;
		boolean arrived = false;

		for (int tick = 0; tick < 4000; tick++) {
			boolean canDescend = y > 69.5;
			EnvironmentScan scan = new EnvironmentScan(
					true, false, false, true, canDescend, false, false, 69.0, 69.0, null
			);
			SteeringCommand cmd = FlightPlanner.plan(
					x, y, z, yaw, pitch, target, 125.0, settings, scan, 0, 1
			);
			if (cmd.arrived()) {
				arrived = true;
				break;
			}
			double[] step = toWorldVelocity(cmd, 0.20);
			x += step[0];
			y += step[1];
			z += step[2];
			yaw = cmd.yaw();
			pitch = cmd.pitch();
		}

		assertTrue(arrived, "Happy Ghast should complete horizontal flight and vertical landing");
		assertTrue(target.horizontalDistanceTo(x, z) <= settings.arrivalRadius(),
				"Must stay centered on (X, Z) throughout descent");
		assertEquals(70.0, y, 2.0, "Should stop right at target Y");
	}

	@Test
	void immediateObstacleStopsForwardThrustAndClimbsVertically() {
		FlightTarget target = FlightTarget.ofXZ(100, 0, "minecraft:overworld");
		EnvironmentScan blockedAhead = new EnvironmentScan(
				true, true, true, true, true, false, false, 110.0, 70.0, null
		);
		SteeringCommand cmd = FlightPlanner.plan(
				0, 85, 0, -90.0f, 0.0f, target, 85.0, FlightSettings.defaults(), blockedAhead, 0, 1
		);
		assertFalse(cmd.arrived());
		assertEquals(FlightPhase.CLIMBING, cmd.phase());
		assertEquals(0.0f, cmd.forward(), 1e-4f, "Must not ram obstacle while ascending");
		assertTrue(cmd.jump(), "Must engage vertical jump climb");
	}

	@Test
	void netherCeilingObstacleUsesLateralDetourInsteadOfClimbingIntoRoof() {
		FlightTarget target = FlightTarget.ofXZ(0, 200, "minecraft:the_nether");
		EnvironmentScan netherPillar = new EnvironmentScan(
				true, true, true, false, true, true, false, Double.NaN, 50.0, 50.0f
		);
		SteeringCommand cmd = FlightPlanner.plan(
				0, 70, 0, 45.0f, 0.0f, target, 70.0, FlightSettings.defaults(), netherPillar, 0, 1
		);
		assertEquals(FlightPhase.AVOIDING, cmd.phase());
		assertFalse(cmd.jump(), "Must not jump into Nether ceiling");
		assertEquals(50.0f, cmd.yaw(), 1e-4f);
		assertTrue(cmd.forward() > 0.5f, "Should move cleanly along open detour");
	}

	@Test
	void unloadedChunkAheadHoldsGhastSafelyInPlace() {
		FlightTarget target = FlightTarget.ofXZ(500, 500, "minecraft:overworld");
		EnvironmentScan unloaded = new EnvironmentScan(
				false, false, false, true, true, false, false, Double.NaN, Double.NaN, null
		);
		SteeringCommand cmd = FlightPlanner.plan(
				0, 110, 0, 0.0f, 0.0f, target, 110.0, FlightSettings.defaults(), unloaded, 0, 1
		);
		assertEquals(FlightPhase.WAITING_CHUNKS, cmd.phase());
		assertEquals(0.0f, cmd.forward(), 1e-5f);
		assertEquals(0.0f, cmd.strafe(), 1e-5f);
		assertFalse(cmd.jump());
	}

	@Test
	void lavaBelowPreventsUnsafeLanding() {
		FlightTarget target = FlightTarget.ofXYZ(0, 31, 0, "minecraft:the_nether");
		EnvironmentScan lavaPool = new EnvironmentScan(
				true, false, false, true, true, true, true, Double.NaN, 32.0, null
		);
		SteeringCommand cmd = FlightPlanner.plan(
				0.5, 36.0, 0.5, 0.0f, 0.0f, target, 36.0, FlightSettings.defaults(), lavaPool, 0, 1
		);
		assertTrue(cmd.arrived(), "Should halt safely above lava rather than descending into it");
	}

	@Test
	void coordinateParserHandlesCommonAndXaeroFormats() {
		Optional<FlightTarget> simpleXZ = CoordinateParser.parse("250 -1400", "minecraft:overworld");
		assertTrue(simpleXZ.isPresent());
		assertEquals(250, simpleXZ.get().x());
		assertNull(simpleXZ.get().y());
		assertEquals(-1400, simpleXZ.get().z());

		Optional<FlightTarget> xyz = CoordinateParser.parse("X: -120, Y: 84, Z: 330", "minecraft:overworld");
		assertTrue(xyz.isPresent());
		assertEquals(-120, xyz.get().x());
		assertEquals(84, xyz.get().y());
		assertEquals(330, xyz.get().z());

		Optional<FlightTarget> tildeY = CoordinateParser.parse("X: 42, Y: ~, Z: -99", "minecraft:overworld");
		assertTrue(tildeY.isPresent());
		assertEquals(42, tildeY.get().x());
		assertNull(tildeY.get().y());
		assertEquals(-99, tildeY.get().z());

		Optional<FlightTarget> xaeroWp = CoordinateParser.parse(
				"xaero-waypoint:База:Б:1024:78:-2048:11:false:0:gui.xaero_default:false:0:0:Internal-the-nether-waypoints",
				"minecraft:overworld"
		);
		assertTrue(xaeroWp.isPresent());
		assertEquals("База", xaeroWp.get().label());
		assertEquals(1024, xaeroWp.get().x());
		assertEquals(78, xaeroWp.get().y());
		assertEquals(-2048, xaeroWp.get().z());
		assertEquals("minecraft:the_nether", xaeroWp.get().dimension());

		Optional<FlightTarget> f3c = CoordinateParser.parse(
				"/execute in minecraft:the_end run tp @s 100.50 62.00 -45.25 180.00 0.00",
				"minecraft:overworld"
		);
		assertTrue(f3c.isPresent());
		assertEquals(100, f3c.get().x());
		assertEquals(62, f3c.get().y());
		assertEquals(-46, f3c.get().z());
		assertEquals("minecraft:the_end", f3c.get().dimension());
	}

	@Test
	void etaAndLineClipperHandleExtremeValues() {
		assertEquals("00:00", EtaFormatter.formatEta(0.1, 4.0));
		assertEquals("01:40", EtaFormatter.formatEta(400.0, 4.0));
		assertEquals("1:00:00", EtaFormatter.formatEta(14400.0, 4.0));

		assertEquals("▲", EtaFormatter.formatBearingIndicator(0f, 5f));
		assertEquals("▶▶", EtaFormatter.formatBearingIndicator(0f, 90f));
		assertEquals("◀◀", EtaFormatter.formatBearingIndicator(0f, -90f));

		double[] clipped = MapLineClipper.clip(500, 300, 1_000_000, 1_000_000, 0, 0, 1920, 1080);
		assertNotNull(clipped);
		assertTrue(clipped[2] <= 1920.001 && clipped[3] <= 1080.001);

		assertNull(MapLineClipper.clip(-500, -500, -100, -200, 0, 0, 1920, 1080));
	}

	/** Converts local (strafe, up, forward) into world delta (dx, dy, dz) using Minecraft entity yaw. */
	private static double[] toWorldVelocity(SteeringCommand cmd, double blocksPerTick) {
		double[] local = cmd.computeLocalInputComponents();
		double rad = Math.toRadians(cmd.yaw());
		double sin = Math.sin(rad);
		double cos = Math.cos(rad);
		double dx = (local[0] * cos - local[2] * sin) * blocksPerTick;
		double dy = local[1] * blocksPerTick;
		double dz = (local[2] * cos + local[0] * sin) * blocksPerTick;
		return new double[]{dx, dy, dz};
	}
}
