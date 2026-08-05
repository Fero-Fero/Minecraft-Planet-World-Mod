package com.planetworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.time.LocalTime;
import com.planetworld.wrap.WrapMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Undead sun-burn must follow local day at the mob's X.
 * <p>
 * Patching only {@code Level.isDay()} is not enough: during global night the sky-light map is
 * dark planet-wide, so {@code getLightLevelDependentMagicValue()} stays below the burn threshold
 * even when this longitude is local noon. Rebuild the vanilla check with local sun exposure.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
	@WrapMethod(method = "isSunBurnTick")
	private boolean planetworld$localSunBurn(Operation<Boolean> original) {
		Mob self = (Mob) (Object) this;
		Level level = self.level();
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level) || level.isClientSide) {
			return original.call();
		}
		if (!LocalTime.isSunBurnTime(self)) {
			return false;
		}
		float exposure = LocalTime.sunExposure(self);
		BlockPos eye = BlockPos.containing(self.getX(), self.getEyeY(), self.getZ());
		boolean sheltered = self.isInWaterRainOrBubble() || self.isInPowderSnow || self.wasInPowderSnow;
		return exposure > 0.5F
				&& self.getRandom().nextFloat() * 30.0F < (exposure - 0.4F) * 2.0F
				&& !sheltered
				&& level.canSeeSky(eye);
	}
}
