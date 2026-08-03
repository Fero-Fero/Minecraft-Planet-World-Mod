package com.planetworld.mixin;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.weather.LocalizedWeatherHandler;
import com.planetworld.wrap.WrapMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Outside a local weather band, fire ignores rain extinguish/spread suppression.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "isNearRain", at = @At("HEAD"), cancellable = true)
    private void planetworld$localizedNearRain(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!PlanetWorldConfig.enableLocalizedWeather() || !WrapMath.isWrappedDimension(level)) {
            return;
        }
        if (!LocalizedWeatherHandler.shouldExtinguishFire(level, pos.getX(), pos.getZ())) {
            cir.setReturnValue(false);
        }
    }
}
