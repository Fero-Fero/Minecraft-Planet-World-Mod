package com.planetworld.mixin.client;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.render.CurvatureRenderer;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Optional visual Y offset for entities to match horizon curvature.
 * <p>
 * Disabled while terrain itself is not curvature-remapped: offsetting only
 * entities makes mobs float up into cages or sink into floors as you approach
 * from different directions. Re-enable when block/terrain curvature is live.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	/** Set true once terrain vertices receive the same distance-based Y drop. */
	private static final boolean APPLY_ENTITY_CURVATURE = false;

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private double planetworld$curveEntityY(double y, Entity entity) {
		if (!APPLY_ENTITY_CURVATURE || !PlanetWorldConfig.enableCurvatureShader()) {
			return y;
		}
		if (entity.level() == null || !WrapMath.isWrappedDimension(entity.level())) {
			return y;
		}
		return y + CurvatureRenderer.curvatureDropFromCamera(entity.getX(), entity.getZ());
	}
}
