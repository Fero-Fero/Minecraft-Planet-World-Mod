package com.planetworld.mixin.client;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.weather.LocalizedWeatherHandler;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rain and thunder only render (and sound) inside the local weather band,
 * matching the server's localized weather logic.
 */
@Mixin(Level.class)
public abstract class RainRenderGateMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void planetworld$gateRainRender(float delta, CallbackInfoReturnable<Float> cir) {
        if (planetworld$outsideLocalBand(cir.getReturnValueF())) {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void planetworld$gateThunderRender(float delta, CallbackInfoReturnable<Float> cir) {
        if (planetworld$outsideLocalBand(cir.getReturnValueF())) {
            cir.setReturnValue(0.0f);
        }
    }

    private boolean planetworld$outsideLocalBand(float currentLevel) {
        Level self = (Level) (Object) this;
        if (!self.isClientSide() || currentLevel <= 0.0f) {
            return false;
        }
        if (!PlanetWorldConfig.enableLocalizedWeather() || !WrapMath.isWrappedDimension(self)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level != self) {
            return false;
        }
        return !LocalizedWeatherHandler.isBandRainy(self, mc.player.getX());
    }
}
