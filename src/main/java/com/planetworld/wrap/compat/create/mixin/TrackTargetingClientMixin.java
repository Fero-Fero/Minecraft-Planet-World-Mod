package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.track.TrackTargetingClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Pre-place station/signal overlays translate {@code lastHovered - camera}. When the selected
 * track is stored in wrapped space and the camera is continuous past the cut, that subtract
 * jumps ~a full circumference and the directional icon never appears near the player.
 */
@Mixin(TrackTargetingClient.class)
public abstract class TrackTargetingClientMixin {

	@WrapOperation(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
			)
	)
	private static Vec3 planetworld$torusOverlayTranslate(Vec3 hoveredCorner, Vec3 camera, Operation<Vec3> original) {
		DimensionTransformer t = CreateWrapMath.transformer(Minecraft.getInstance().level);
		if (!t.isWrapped()) {
			return original.call(hoveredCorner, camera);
		}
		return CreateWrapMath.unwrapRelative(t, camera, hoveredCorner).subtract(camera);
	}
}
