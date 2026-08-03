package com.planetworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.time.LocalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Saplings follow the same rule as crops: local daytime drives sunlit growth, artificial light keeps
 * working at night like vanilla. Local summer grows 25% faster.
 */
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockMixin {
	@WrapMethod(method = "randomTick")
	private void planetworld$localDayGrowth(
			BlockState state,
			ServerLevel level,
			BlockPos pos,
			RandomSource random,
			Operation<Void> original
	) {
		BlockPos lightPos = pos.above();
		if (LocalTime.holdsBackSunlitGrowth(level, lightPos)) {
			return;
		}
		original.call(state, level, pos, random);
		float mult = SeasonAuthority.localGrowthMultiplier(level, pos.getZ());
		if (mult > 1.0f && random.nextFloat() < (1.0f - 1.0f / mult)) {
			if (!LocalTime.holdsBackSunlitGrowth(level, lightPos)) {
				original.call(state, level, pos, random);
			}
		}
	}
}
