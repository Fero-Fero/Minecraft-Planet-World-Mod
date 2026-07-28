package com.planetworld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.time.LocalTime;
import com.planetworld.wrap.WrapMath;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Undead sun-burn uses local day at the mob's X instead of global day.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @WrapOperation(
            method = "isSunBurnTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z")
    )
    private boolean planetworld$localIsDay(Level level, Operation<Boolean> original) {
        if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
            return original.call(level);
        }
        Mob self = (Mob) (Object) this;
        return LocalTime.isDay(level, self.getX());
    }
}
