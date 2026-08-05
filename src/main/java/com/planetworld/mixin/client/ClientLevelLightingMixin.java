package com.planetworld.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.render.LocalSkyHandler;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sky brightness / color / stars follow tip-aware sun exposure.
 * Disc spin still uses X-only {@code getTimeOfDay} (see {@link ClientLevelMixin}).
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelLightingMixin {
	@WrapOperation(
			method = {"getSkyDarken", "getSkyColor", "getCloudColor", "getStarBrightness"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F"
			)
	)
	private float planetworld$lightingTimeOfDay(ClientLevel level, float partialTick, Operation<Float> original) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return original.call(level, partialTick);
		}
		return LocalSkyHandler.localLightingCelestialAngle();
	}
}
