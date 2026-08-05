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
 * Atmosphere clock ({@code getTimeOfDay}): tip-aware sky lighting so fog / sky color /
 * darken / stars match the tipped sun. Disc spin stays X-only via
 * {@link LevelRendererMixin} wrapping the {@code XP(time)} call in {@code renderSky}.
 */
@Mixin(LevelTimeAccess.class)
public interface ClientLevelMixin {
	@Inject(method = "getTimeOfDay", at = @At("HEAD"), cancellable = true)
	private void planetworld$skyLightingTimeOfDay(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (!PlanetWorldConfig.enableLocalizedTime()) {
			return;
		}
		if (!(this instanceof ClientLevel level) || !WrapMath.isWrappedDimension(level)) {
			return;
		}
		cir.setReturnValue(LocalSkyHandler.localLightingCelestialAngle());
	}
}
