package net.ghastpilot.client;

import net.ghastpilot.flight.EnvironmentScan;
import net.ghastpilot.flight.FlightPlanner;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Scans loaded terrain, obstacles, ceilings, and environmental hazards around a Happy Ghast.
 * Accounts for the full 4x4x4 ghast body PLUS 2.1 blocks of passenger headroom on top.
 */
public final class WorldScanner {

	private static final double RIDER_HEADROOM = 2.1;
	private static final double HORIZONTAL_MARGIN = 0.35;
	private static final double[] TERRAIN_SAMPLE_DISTANCES = {4.0, 9.0, 15.0, 22.0, 28.0};
	private static final double[] TERRAIN_LATERAL_OFFSETS = {-2.2, 0.0, 2.2};
	private static final float[] DETOUR_ANGLE_MAGNITUDES = {25.0f, 50.0f, 75.0f, 105.0f, 135.0f};

	private WorldScanner() {
	}

	public static EnvironmentScan scan(
			ClientLevel level,
			HappyGhast ghast,
			float headingYaw,
			double horizDistToTarget,
			int preferredDetourSign
	) {
		AABB ghastBox = ghast.getBoundingBox();
		AABB fullBox = new AABB(
				ghastBox.minX - HORIZONTAL_MARGIN,
				ghastBox.minY,
				ghastBox.minZ - HORIZONTAL_MARGIN,
				ghastBox.maxX + HORIZONTAL_MARGIN,
				ghastBox.maxY + RIDER_HEADROOM,
				ghastBox.maxZ + HORIZONTAL_MARGIN
		);

		double rad = Math.toRadians(headingYaw);
		double fx = -Math.sin(rad);
		double fz = Math.cos(rad);
		double rx = -fz;
		double rz = fx;

		// 1. Check whether the chunk immediately ahead is loaded
		double chunkProbeDist = Math.min(Math.max(4.0, horizDistToTarget), 12.0);
		int probeChunkX = Mth.floor(ghast.getX() + fx * chunkProbeDist) >> 4;
		int probeChunkZ = Mth.floor(ghast.getZ() + fz * chunkProbeDist) >> 4;
		boolean chunkAheadLoaded = level.getChunkSource().hasChunk(probeChunkX, probeChunkZ);

		double minY = level.getMinY();
		double maxY = level.getMaxY();

		// 2. Vertical clearance above: check strictly the airspace slice above the ghast (+ rider headroom)
		// so standing directly beside a wall, tree, or docking tower never falsely blocks vertical takeoff.
		AABB upperSlice = new AABB(
				ghastBox.minX + 0.02,
				ghastBox.maxY,
				ghastBox.minZ + 0.02,
				ghastBox.maxX - 0.02,
				ghastBox.maxY + RIDER_HEADROOM + 2.4,
				ghastBox.maxZ - 0.02
		);
		boolean canAscend = upperSlice.maxY < maxY - 1.5 && isBoxSafe(level, ghast, upperSlice);

		// 3. Detect dimension ceiling (Nether) or local overhead roof (cave / floating island / bridge)
		boolean hasCeiling = level.dimensionType().hasCeiling() || hasOverheadRoof(level, ghast.getX(), fullBox.maxY, ghast.getZ());

		// 4. Downward clearance, ground height & hazard (lava / fire / void) check
		AABB lowerSlice = new AABB(
				ghastBox.minX + 0.02,
				ghastBox.minY - 1.5,
				ghastBox.minZ + 0.02,
				ghastBox.maxX - 0.02,
				ghastBox.minY - 0.02,
				ghastBox.maxZ - 0.02
		);
		boolean canDescend = lowerSlice.minY > minY + 4.0 && isBoxSafe(level, ghast, lowerSlice);

		double groundBelowY = Double.NaN;
		boolean hazardBelow = ghastBox.minY < minY + 6.0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		double[][] footprint = {
				{0.0, 0.0},
				{-1.75, -1.75},
				{1.75, -1.75},
				{-1.75, 1.75},
				{1.75, 1.75}
		};
		int startScanY = Mth.floor(ghastBox.minY - 0.1);
		int minScanY = Math.max(level.getMinY(), startScanY - 36);
		for (double[] offset : footprint) {
			int bx = Mth.floor(ghast.getX() + offset[0]);
			int bz = Mth.floor(ghast.getZ() + offset[1]);
			if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
				continue;
			}
			for (int by = startScanY; by >= minScanY; by--) {
				cursor.set(bx, by, bz);
				BlockState state = level.getBlockState(cursor);
				if (state.isAir()) {
					continue;
				}
				FluidState fluid = state.getFluidState();
				boolean isHazard = isHazardousBlock(state, fluid);
				if (isHazard && (startScanY - by) <= 5) {
					hazardBelow = true;
				}
				if (isHazard || !fluid.isEmpty() || !state.getCollisionShape(level, cursor).isEmpty()) {
					double topY = by + 1.0;
					if (Double.isNaN(groundBelowY) || topY > groundBelowY) {
						groundBelowY = topY;
					}
					break;
				}
			}
		}

		// 5. Forward obstacle corridor scan (immediate < 5.5m, near < 11.5m)
		boolean immediateObstacleAhead = false;
		boolean nearObstacleAhead = false;
		if (chunkAheadLoaded) {
			if ((horizDistToTarget > 2.0 && !isBoxSafe(level, ghast, fullBox.move(fx * 2.2, 0.0, fz * 2.2)))
					|| (horizDistToTarget > 4.5 && !isBoxSafe(level, ghast, fullBox.move(fx * 4.8, 0.0, fz * 4.8)))) {
				immediateObstacleAhead = true;
			}
			if (immediateObstacleAhead
					|| (horizDistToTarget > 7.5 && !isBoxSafe(level, ghast, fullBox.move(fx * 7.8, 0.0, fz * 7.8)))
					|| (horizDistToTarget > 11.0 && !isBoxSafe(level, ghast, fullBox.move(fx * 11.2, 0.0, fz * 11.2)))) {
				nearObstacleAhead = true;
			}
		}

		// 6. Heightmap lookahead for graceful mountain & forest clearance in open-sky dimensions
		double highestTerrainAhead = Double.NaN;
		if (!level.dimensionType().hasCeiling() && !hasCeiling) {
			for (double dist : TERRAIN_SAMPLE_DISTANCES) {
				if (dist > horizDistToTarget + 2.5) {
					break;
				}
				double centerX = ghast.getX() + fx * dist;
				double centerZ = ghast.getZ() + fz * dist;
				for (double lat : TERRAIN_LATERAL_OFFSETS) {
					int bx = Mth.floor(centerX + rx * lat);
					int bz = Mth.floor(centerZ + rz * lat);
					if (!level.getChunkSource().hasChunk(bx >> 4, bz >> 4)) {
						continue;
					}
					int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
					if (h > level.getMinY() && (Double.isNaN(highestTerrainAhead) || h > highestTerrainAhead)) {
						highestTerrainAhead = h;
					}
				}
			}
		}

		// 7. Find cleanest lateral detour angle when forward corridor is obstructed
		Float clearDetourYaw = null;
		if (immediateObstacleAhead || nearObstacleAhead) {
			int sign = preferredDetourSign >= 0 ? 1 : -1;
			outer:
			for (float mag : DETOUR_ANGLE_MAGNITUDES) {
				for (int attempt = 0; attempt < 2; attempt++) {
					float dirSign = (attempt == 0) ? sign : -sign;
					float candidateYaw = FlightPlanner.wrapDegrees(headingYaw + dirSign * mag);
					if (isCorridorClear(level, ghast, fullBox, candidateYaw)) {
						clearDetourYaw = candidateYaw;
						break outer;
					}
				}
			}
		}

		return new EnvironmentScan(
				chunkAheadLoaded,
				immediateObstacleAhead,
				nearObstacleAhead,
				canAscend,
				canDescend,
				hasCeiling,
				hazardBelow,
				highestTerrainAhead,
				groundBelowY,
				clearDetourYaw
		);
	}

	private static boolean hasOverheadRoof(ClientLevel level, double x, double topY, double z) {
		BlockPos base = BlockPos.containing(x, topY + 0.5, z);
		if (!level.getChunkSource().hasChunk(base.getX() >> 4, base.getZ() >> 4)) {
			return false;
		}
		if (level.canSeeSky(base)) {
			return false;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int maxCheckY = Math.min(level.getMaxY() - 1, base.getY() + 14);
		for (int y = base.getY(); y <= maxCheckY; y++) {
			cursor.set(base.getX(), y, base.getZ());
			BlockState state = level.getBlockState(cursor);
			if (!state.isAir() && !state.getCollisionShape(level, cursor).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCorridorClear(ClientLevel level, HappyGhast ghast, AABB fullBox, float yaw) {
		double rad = Math.toRadians(yaw);
		double dx = -Math.sin(rad);
		double dz = Math.cos(rad);
		int cx = Mth.floor(ghast.getX() + dx * 9.0) >> 4;
		int cz = Mth.floor(ghast.getZ() + dz * 9.0) >> 4;
		if (!level.getChunkSource().hasChunk(cx, cz)) {
			return false;
		}
		return isBoxSafe(level, ghast, fullBox.move(dx * 2.8, 0.0, dz * 2.8))
				&& isBoxSafe(level, ghast, fullBox.move(dx * 6.0, 0.0, dz * 6.0))
				&& isBoxSafe(level, ghast, fullBox.move(dx * 9.5, 0.0, dz * 9.5));
	}

	private static boolean isBoxSafe(ClientLevel level, HappyGhast ghast, AABB box) {
		int minCX = Mth.floor(box.minX) >> 4;
		int maxCX = Mth.floor(box.maxX) >> 4;
		int minCZ = Mth.floor(box.minZ) >> 4;
		int maxCZ = Mth.floor(box.maxZ) >> 4;
		for (int cx = minCX; cx <= maxCX; cx++) {
			for (int cz = minCZ; cz <= maxCZ; cz++) {
				if (!level.getChunkSource().hasChunk(cx, cz)) {
					return false;
				}
			}
		}
		if (!level.noCollision(ghast, box)) {
			return false;
		}
		// Check for non-colliding hazards (lava, fire, berry bushes, powder snow, cobwebs)
		int minX = Mth.floor(box.minX);
		int maxX = Mth.floor(box.maxX);
		int minY = Math.max(level.getMinY(), Mth.floor(box.minY));
		int maxY = Math.min(level.getMaxY() - 1, Mth.floor(box.maxY));
		int minZ = Mth.floor(box.minZ);
		int maxZ = Mth.floor(box.maxZ);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					if (!state.isAir() && isHazardousBlock(state, state.getFluidState())) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean isHazardousBlock(BlockState state, FluidState fluid) {
		if (fluid.is(FluidTags.LAVA)) {
			return true;
		}
		return state.is(Blocks.FIRE)
				|| state.is(Blocks.SOUL_FIRE)
				|| state.is(Blocks.CAMPFIRE)
				|| state.is(Blocks.SOUL_CAMPFIRE)
				|| state.is(Blocks.LAVA)
				|| state.is(Blocks.MAGMA_BLOCK)
				|| state.is(Blocks.CACTUS)
				|| state.is(Blocks.SWEET_BERRY_BUSH)
				|| state.is(Blocks.WITHER_ROSE)
				|| state.is(Blocks.POWDER_SNOW)
				|| state.is(Blocks.COBWEB);
	}
}
