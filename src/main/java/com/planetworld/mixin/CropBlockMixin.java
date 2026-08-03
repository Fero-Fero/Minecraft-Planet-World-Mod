package com.planetworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.time.LocalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Sunlit crops advance while their local X-band is in daytime; artificially lit crops keep growing
 * around the clock as they do in vanilla. Local summer grows 25% faster.
 */
@Mixin(CropBlock.class)
public abstract class CropBlockMixin {
	@WrapMethod(method = "randomTick")
	private void planetworld$localDayGrowth(
			BlockState state,
			ServerLevel level,
			BlockPos pos,
			RandomSource random,
			Operation<Void> original
	) {
		if (LocalTime.holdsBackSunlitGrowth(level, pos)) {
			return;
		}
		original.call(state, level, pos, random);
		float mult = SeasonAuthority.localGrowthMultiplier(level, pos.getZ());
		if (mult > 1.0f && random.nextFloat() < (1.0f - 1.0f / mult)) {
			if (!LocalTime.holdsBackSunlitGrowth(level, pos)) {
				original.call(state, level, pos, random);
			}
		}
	}
}
