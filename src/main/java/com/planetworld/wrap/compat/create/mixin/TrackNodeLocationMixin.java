package com.planetworld.wrap.compat.create.mixin;

import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Objects;

/**
 * Keeps Create track-graph nodes in wrapped coordinate space so opposite-edge rail ends
 * share one node (e.g. world z=256 and z=-256), and expands adjacency across the seam.
 */
@Mixin(TrackNodeLocation.class)
public abstract class TrackNodeLocationMixin {

	@Shadow public int yOffsetPixels;

	@ModifyVariable(method = "<init>(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static double planetworld$wrapCtorX(double x) {
		return CreateWrapMath.wrapX(x);
	}

	@ModifyVariable(method = "<init>(DDD)V", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private static double planetworld$wrapCtorZ(double z) {
		return CreateWrapMath.wrapZ(z);
	}

	@ModifyVariable(method = "<init>(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), argsOnly = true)
	private static Vec3 planetworld$wrapCtorVec(Vec3 vec) {
		return CreateWrapMath.wrapVec(vec);
	}

	@ModifyVariable(method = "<init>(Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"), argsOnly = true)
	private static BlockPos planetworld$wrapPackedPos(BlockPos packed) {
		int cx = CreateWrapMath.canonicalPackedX(packed.getX());
		int cz = CreateWrapMath.canonicalPackedZ(packed.getZ());
		if (cx == packed.getX() && cz == packed.getZ()) {
			return packed;
		}
		return new BlockPos(cx, packed.getY(), cz);
	}

	@Inject(method = "allAdjacent", at = @At("RETURN"), cancellable = true)
	private void planetworld$wrapAdjacents(CallbackInfoReturnable<Collection<BlockPos>> cir) {
		TrackNodeLocation self = (TrackNodeLocation) (Object) this;
		cir.setReturnValue(CreateWrapMath.withWrappedAdjacents(self.dimension, cir.getReturnValue()));
	}

	@Inject(method = "getLocation", at = @At("RETURN"), cancellable = true)
	private void planetworld$wrapGetLocation(CallbackInfoReturnable<Vec3> cir) {
		Vec3 raw = cir.getReturnValue();
		DimensionTransformer t = CreateWrapMath.bestEffortTransformer();
		if (!t.isWrapped() || (!t.Coord.X.isOver(raw.x) && !t.Coord.Z.isOver(raw.z))) {
			return;
		}
		cir.setReturnValue(t.Vector3D.wrap(raw));
	}

	/**
	 * When wrapping is active, always compare via canonical packed coords so z=256 and
	 * z=-256 (and any saved out-of-domain nodes) share identity.
	 */
	@Inject(method = "equalsIgnoreDim", at = @At("HEAD"), cancellable = true)
	private void planetworld$wrapEqualsIgnoreDim(Object other, CallbackInfoReturnable<Boolean> cir) {
		if (!(other instanceof TrackNodeLocation tnl)) {
			return;
		}
		if (!CreateWrapMath.wrappingActive()) {
			return;
		}
		TrackNodeLocation self = (TrackNodeLocation) (Object) this;
		cir.setReturnValue(CreateWrapMath.samePackedLocation(
				self.getX(), self.getY(), self.getZ(), self.yOffsetPixels,
				tnl.getX(), tnl.getY(), tnl.getZ(), tnl.yOffsetPixels
		));
	}

	@Inject(method = "equals", at = @At("HEAD"), cancellable = true)
	private void planetworld$wrapEquals(Object other, CallbackInfoReturnable<Boolean> cir) {
		if (!(other instanceof TrackNodeLocation tnl)) {
			return;
		}
		if (!CreateWrapMath.wrappingActive()) {
			return;
		}
		TrackNodeLocation self = (TrackNodeLocation) (Object) this;
		boolean samePos = CreateWrapMath.samePackedLocation(
				self.getX(), self.getY(), self.getZ(), self.yOffsetPixels,
				tnl.getX(), tnl.getY(), tnl.getZ(), tnl.yOffsetPixels
		);
		cir.setReturnValue(samePos && Objects.equals(self.dimension, tnl.dimension));
	}

	@Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
	private void planetworld$wrapHashCode(CallbackInfoReturnable<Integer> cir) {
		if (!CreateWrapMath.wrappingActive()) {
			return;
		}
		TrackNodeLocation self = (TrackNodeLocation) (Object) this;
		int dimHash = self.dimension == null ? 0 : self.dimension.hashCode();
		cir.setReturnValue(CreateWrapMath.packedLocationHash(
				self.getX(), self.getY(), self.getZ(), self.yOffsetPixels, dimHash
		));
	}
}
