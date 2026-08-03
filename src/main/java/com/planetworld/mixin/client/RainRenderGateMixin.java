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
 * matching the server's localized weather logic. Polar winter forces blizzard
 * precipitation even when the X-band is clear.
 */
@Mixin(Level.class)
public abstract class RainRenderGateMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void planetworld$gateRainRender(float delta, CallbackInfoReturnable<Float> cir) {
        Level self = (Level) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (!self.isClientSide() || mc.player == null || mc.level != self) {
            return;
        }
        if (!WrapMath.isWrappedDimension(self)) {
            return;
        }
        if (LocalizedWeatherHandler.isPolarBlizzard(self, mc.player.getZ())) {
            cir.setReturnValue(Math.max(cir.getReturnValueF(), 1.0f));
            return;
        }
        if (cir.getReturnValueF() <= 0.0f) {
            return;
        }
        if (!PlanetWorldConfig.enableLocalizedWeather()) {
            return;
        }
        if (!LocalizedWeatherHandler.isBandRainy(self, mc.player.getX())) {
            cir.setReturnValue(0.0f);
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void planetworld$gateThunderRender(float delta, CallbackInfoReturnable<Float> cir) {
        Level self = (Level) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (!self.isClientSide() || mc.player == null || mc.level != self) {
            return;
        }
        if (!WrapMath.isWrappedDimension(self)) {
            return;
        }
        // Blizzards are snowy, not thunderous
        if (LocalizedWeatherHandler.isPolarBlizzard(self, mc.player.getZ())) {
            cir.setReturnValue(0.0f);
            return;
        }
        if (cir.getReturnValueF() <= 0.0f) {
            return;
        }
        if (!PlanetWorldConfig.enableLocalizedWeather()) {
            return;
        }
        if (!LocalizedWeatherHandler.isBandRainy(self, mc.player.getX())) {
            cir.setReturnValue(0.0f);
        }
    }
}
