package com.planetworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.time.LocalTime;
import com.planetworld.wrap.WrapMath;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DaylightDetectorBlock.class)
public abstract class DaylightDetectorBlockMixin {
    @WrapOperation(
            method = "updateSignalStrength",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSunAngle(F)F")
    )
    private static float planetworld$localSunAngle(Level level, float partialTick, Operation<Float> original,
                                                   BlockState state, Level level2, BlockPos pos) {
        if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
            return original.call(level, partialTick);
        }
        return LocalTime.celestialAngle(level, pos.getX()) * ((float) Math.PI * 2f);
    }

    @WrapOperation(
            method = "updateSignalStrength",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSkyDarken()I")
    )
    private static int planetworld$localSkyDarken(Level level, Operation<Integer> original,
                                                  BlockState state, Level level2, BlockPos pos) {
        if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
            return original.call(level);
        }
        // Approximate sky darken 0..11 from local celestial angle
        double brightness = Mth.clamp(Math.cos(LocalTime.celestialAngle(level, pos.getX()) * Math.PI * 2.0) * 2.0 + 0.5, 0.0, 1.0);
        return (int) ((1.0 - brightness) * 11.0);
    }
}
