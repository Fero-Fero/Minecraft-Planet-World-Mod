package com.planetworld.wrap.mixin.entity.collisions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Priority 2000 so this wrapper sits outside anything else that owns the method — Sable
 * {@code @Overwrite}s it to route through sub-level-aware entity distance.
 */
@Mixin(value = HitResult.class, priority = 2000)
public class HitResultMixin {
    @Final @Shadow protected Vec3 location;

    /**
     * Vanilla measures the hit location against the entity inline, so the short-path answer has to be
     * computed here rather than by correcting an argument. Only claim the answer when the pair actually
     * straddles a bound; anywhere else the readings agree and deferring keeps the other owner's result.
     */
    @WrapMethod(method = "distanceTo")
    private double planetworld$torusDistanceTo(Entity entity, Operation<Double> original) {
        DimensionTransformer transformer = entity.level().getTransformer();
        if (transformer == null) {
            return original.call(entity);
        }
        transformer = transformer.SSO();
        boolean acrossBound = transformer.isWrapped()
                && (transformer.Coord.X.needsUnwrap(entity.getX(), location.x)
                || transformer.Coord.Z.needsUnwrap(entity.getZ(), location.z));
        if (!acrossBound) {
            return original.call(entity);
        }
        return transformer.Coord.sqrDistToBounds(
                entity.getX(), entity.getY(), entity.getZ(),
                location.x, location.y, location.z
        );
    }
}
