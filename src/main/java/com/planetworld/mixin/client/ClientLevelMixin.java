package com.planetworld.mixin.client;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.render.LocalSkyHandler;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LevelTimeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sun/moon disc spin uses the longitude-local clock (world dayTime ± X).
 * Tip is applied in {@link LevelRendererMixin}; sky darken / color / stars use
 * tip-aware exposure via {@link ClientLevelLightingMixin}.
 */
@Mixin(LevelTimeAccess.class)
public interface ClientLevelMixin {
    @Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
    private void planetworld$worldTimeOfDay(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!PlanetWorldConfig.enableLocalizedTime()) {
            return;
        }
        if (!(this instanceof ClientLevel level) || !WrapMath.isWrappedDimension(level)) {
            return;
        }
        cir.setReturnValue(LocalSkyHandler.localCelestialAngle());
    }
}
