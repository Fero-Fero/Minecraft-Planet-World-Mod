package com.planetworld.polar;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.season.SereneSeasonsCompat;
import com.planetworld.wrap.WrapMath;
import com.planetworld.worldgen.compat.WorldgenPackIds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cold-climate farm soil: poles always; mid-lat winter only with Serene Seasons.
 * Planted crops do not protect the soil — they are destroyed with drops on revert.
 */
public final class ColdFarmland {
	public static final double POLAR_ABS_LAT = 0.70;
	public static final double EQUATOR_ABS_LAT = 0.22;
	/** Horizontal heat reach (campfire / glowstone beside plots). */
	public static final int HEAT_RADIUS = 6;
	/** Vertical heat reach — farming heat sits underfoot or a block above, not ±6. */
	public static final int HEAT_RADIUS_Y = 2;
	/** Ticks after tilling before cold reverts soil. */
	public static final int REVERT_DELAY_TICKS = 40;

	private static final ResourceLocation FD_RICH_SOIL_FARMLAND =
			ResourceLocation.fromNamespaceAndPath("farmersdelight", "rich_soil_farmland");
	private static final ResourceLocation FD_RICH_SOIL =
			ResourceLocation.fromNamespaceAndPath("farmersdelight", "rich_soil");

	private ColdFarmland() {
	}

	public static boolean isTooCold(Level level, BlockPos pos) {
		if (level == null || !WrapMath.isWrappedDimension(level)) {
			return false;
		}
		if (!PlanetWorldConfig.enableLocalizedTime()) {
			return false;
		}
		double absLat = Math.abs(SeasonAuthority.latitude(level, pos.getZ()));
		if (absLat >= POLAR_ABS_LAT) {
			return true;
		}
		if (!SereneSeasonsCompat.isLoaded()) {
			return false;
		}
		if (absLat <= EQUATOR_ABS_LAT) {
			return false;
		}
		return SeasonAuthority.isLocalWinter(level, pos.getZ());
	}

	/**
	 * Sphere-ish heat probe: horizontal {@link #HEAT_RADIUS}, vertical {@link #HEAT_RADIUS_Y}.
	 * Early-outs on first hit. Callers must already know the site is cold-sensitive —
	 * never call this on warm latitudes (see {@link #shouldRevert}).
	 */
	public static boolean hasHeatNearby(Level level, BlockPos pos) {
		int rh = HEAT_RADIUS;
		int rv = HEAT_RADIUS_Y;
		int rh2 = rh * rh;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		// Prefer under/around the soil first (campfires, magma under path)
		for (int dy = -rv; dy <= rv; dy++) {
			for (int dx = -rh; dx <= rh; dx++) {
				for (int dz = -rh; dz <= rh; dz++) {
					if (dx * dx + dz * dz > rh2) {
						continue;
					}
					cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
					if (isHeatSource(level.getBlockState(cursor))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static boolean isHeatSource(BlockState state) {
		Block block = state.getBlock();
		if (block == Blocks.GLOWSTONE
				|| block == Blocks.FIRE
				|| block == Blocks.SOUL_FIRE
				|| block == Blocks.LAVA
				|| block == Blocks.MAGMA_BLOCK) {
			return true;
		}
		if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) {
			return state.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT)
					&& state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT);
		}
		return false;
	}

	public static boolean isColdSensitiveFarmSoil(BlockState state) {
		if (state.getBlock() instanceof FarmBlock) {
			return true;
		}
		if (!WorldgenPackIds.isFarmersDelightLoaded()) {
			return false;
		}
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(FD_RICH_SOIL_FARMLAND);
	}

	/**
	 * Destroy crop above (with drops), then revert farm soil to untilled.
	 */
	public static void revertSoil(ServerLevel level, BlockPos pos) {
		BlockState soil = level.getBlockState(pos);
		if (!isColdSensitiveFarmSoil(soil)) {
			return;
		}
		BlockPos above = pos.above();
		BlockState crop = level.getBlockState(above);
		if (!crop.isAir()) {
			Block.dropResources(crop, level, above);
			level.removeBlock(above, false);
		}
		BlockState untilled = untilledFor(soil);
		level.setBlock(pos, untilled, Block.UPDATE_ALL);
	}

	public static boolean shouldRevert(Level level, BlockPos pos) {
		BlockState soil = level.getBlockState(pos);
		if (!isColdSensitiveFarmSoil(soil)) {
			return false;
		}
		if (!isTooCold(level, pos)) {
			return false;
		}
		return !hasHeatNearby(level, pos);
	}

	private static BlockState untilledFor(BlockState farmSoil) {
		if (WorldgenPackIds.isFarmersDelightLoaded()
				&& BuiltInRegistries.BLOCK.getKey(farmSoil.getBlock()).equals(FD_RICH_SOIL_FARMLAND)) {
			Block rich = BuiltInRegistries.BLOCK.get(FD_RICH_SOIL);
			if (rich != Blocks.AIR) {
				return rich.defaultBlockState();
			}
		}
		return Blocks.DIRT.defaultBlockState();
	}

	/** Used by growth mixins — cold without heat stalls sunlit growth slightly via existing local-time path. */
	public static boolean blocksFarming(Level level, BlockPos pos) {
		return PlanetWorldConfig.enableLocalizedTime()
				&& WrapMath.isWrappedDimension(level)
				&& isTooCold(level, pos)
				&& !hasHeatNearby(level, pos);
	}
}
