package com.planetworld.mixin;

import com.planetworld.time.LocalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sunlit crops advance while their local X-band is in daytime; artificially lit crops keep growing
 * around the clock as they do in vanilla.
 */
@Mixin(CropBlock.class)
public abstract class CropBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void planetworld$localDayGrowth(BlockState state, ServerLevel level, BlockPos pos,
                                            RandomSource random, CallbackInfo ci) {
        if (LocalTime.holdsBackSunlitGrowth(level, pos)) {
            ci.cancel();
        }
    }
}
