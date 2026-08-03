package com.planetworld.mixin;

import com.planetworld.polar.ColdFarmland;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cold poles / SS winter: farmland reverts to dirt and ejects crop drops unless heated.
 */
@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {
	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void planetworld$coldRevert(
			BlockState state,
			ServerLevel level,
			BlockPos pos,
			RandomSource random,
			CallbackInfo ci
	) {
		if (ColdFarmland.shouldRevert(level, pos)) {
			ColdFarmland.revertSoil(level, pos);
			ci.cancel();
		}
	}
}
