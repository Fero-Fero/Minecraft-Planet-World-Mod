package com.planetworld.polar;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.debug.CelestialDebugState;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.wrap.WrapMath;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Polar winter freeze, powder-snow emphasis, and nearby cold-farmland sweeps
 * (covers Farmer's Delight rich soil when it does not share FarmBlock.randomTick).
 */
public final class PolarHazardHandler {
	private static final float POLAR_ABS_LAT = 0.72f;
	private static final int FARM_SCAN_RADIUS = 6;

	private PolarHazardHandler() {
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		Level level = player.level();
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return;
		}
		if (!PlanetWorldConfig.isRealism()) {
			return;
		}
		double absLat = Math.abs(SeasonAuthority.latitude(level, player.getZ()));
		boolean polar = absLat >= POLAR_ABS_LAT;
		boolean winter = SeasonAuthority.isLocalWinter(level, player.getZ());
		boolean forced = CelestialDebugState.isForcedPolarStorm(level, SeasonAuthority.latitude(level, player.getZ()));

		if (player.tickCount % 80 == 0) {
			scanNearbyFarmland(player);
		}

		if (!forced && (!polar || !winter)) {
			return;
		}

		if (player.tickCount % 80 == 0) {
			emphasizePowderSnow(player);
		}

		int freezeInterval = forced ? 10 : 40;
		if (player.tickCount % freezeInterval != 0) {
			return;
		}
		if (isWarmEnough(player)) {
			return;
		}
		int freezeBoost = forced ? 35 : 15;
		player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 20, player.getTicksFrozen() + freezeBoost));
	}

	private static void scanNearbyFarmland(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos origin = player.blockPosition();
		// Whole sweep shares latitude — skip mid-lat summer / non-SS worlds entirely.
		if (!ColdFarmland.isTooCold(level, origin)) {
			return;
		}
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -FARM_SCAN_RADIUS; dx <= FARM_SCAN_RADIUS; dx++) {
			for (int dy = -2; dy <= 1; dy++) {
				for (int dz = -FARM_SCAN_RADIUS; dz <= FARM_SCAN_RADIUS; dz++) {
					cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					BlockState state = level.getBlockState(cursor);
					if (!ColdFarmland.isColdSensitiveFarmSoil(state)) {
						continue;
					}
					if (ColdFarmland.hasHeatNearby(level, cursor)) {
						continue;
					}
					ColdFarmland.revertSoil(level, cursor.immutable());
				}
			}
		}
	}

	/** Convert snow under/around the player to powder snow so freeze pressure is real. */
	private static void emphasizePowderSnow(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos under = player.blockPosition().below();
		BlockState state = level.getBlockState(under);
		if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.SNOW)) {
			level.setBlock(under, Blocks.POWDER_SNOW.defaultBlockState(), 3);
		}
		BlockPos feet = player.blockPosition();
		if (level.getBlockState(feet).is(Blocks.SNOW)) {
			level.setBlock(feet, Blocks.POWDER_SNOW.defaultBlockState(), 3);
		}
	}

	private static boolean isWarmEnough(ServerPlayer player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.isEmpty() && (chest.is(Items.LEATHER_CHESTPLATE) || chest.is(Items.NETHERITE_CHESTPLATE))) {
			return true;
		}
		if (player.isOnFire() || player.isInLava()) {
			return true;
		}
		return ColdFarmland.hasHeatNearby(player.level(), player.blockPosition());
	}
}
