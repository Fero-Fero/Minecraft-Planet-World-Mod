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
 * Client sky / sun / moon use observer local angle (X longitude + meridian longitude).
 * Latitude tip is applied separately in {@code LevelRendererMixin}.
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
