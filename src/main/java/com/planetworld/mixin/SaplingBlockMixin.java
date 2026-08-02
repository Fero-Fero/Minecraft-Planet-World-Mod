package com.planetworld.mixin;

import com.planetworld.time.LocalTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Saplings follow the same rule as crops: local daytime drives sunlit growth, artificial light keeps
 * working at night like vanilla. Vanilla samples the light above the sapling, so this does too.
 */
@Mixin(SaplingBlock.class)
public abstract class SaplingBlockMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void planetworld$localDayGrowth(BlockState state, ServerLevel level, BlockPos pos,
                                            RandomSource random, CallbackInfo ci) {
        if (LocalTime.holdsBackSunlitGrowth(level, pos.above())) {
            ci.cancel();
        }
    }
}
