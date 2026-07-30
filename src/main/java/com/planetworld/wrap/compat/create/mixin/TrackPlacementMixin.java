package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.compat.create.CreateWrapContext;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.track.TrackPlacement;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Wrap-aware track placement distance and block writes across the torus seam.
 */
@Mixin(TrackPlacement.class)
public abstract class TrackPlacementMixin {

	@WrapMethod(method = "tryConnect")
	private static TrackPlacement.PlacementInfo wrapTryConnect(
			Level level,
			Player player,
			BlockPos pos2,
			BlockState state2,
			ItemStack stack,
			boolean girder,
			boolean maximiseTurn,
			Operation<TrackPlacement.PlacementInfo> original
	) {
		Level previous = CreateWrapContext.push(level);
		try {
			return original.call(level, player, pos2, state2, stack, girder, maximiseTurn);
		} finally {
			CreateWrapContext.pop(previous);
		}
	}

	@Redirect(
			method = "tryConnect",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/BlockPos;distSqr(Lnet/minecraft/core/Vec3i;)D"
			)
	)
	private static double planetworld$wrapDistSqr(BlockPos self, Vec3i other) {
		return CreateWrapMath.unwrapDistSqr(CreateWrapContext.level(), self, other);
	}

	@Redirect(
			method = "tryConnect",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D"
			)
	)
	private static double planetworld$wrapDistanceTo(Vec3 self, Vec3 other) {
		return CreateWrapMath.unwrapDistance(CreateWrapContext.level(), self, other);
	}

	@Redirect(
			method = "tryConnect",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
			)
	)
	private static Vec3 planetworld$wrapSubtract(Vec3 self, Vec3 other) {
		Level level = CreateWrapContext.level();
		DimensionTransformer t = CreateWrapMath.transformer(level);
		if (!t.isWrapped()) {
			return self.subtract(other);
		}
		// Create uses end2.subtract(end1): unwrap self into other's frame before subtracting.
		return CreateWrapMath.unwrapRelative(t, other, self).subtract(other);
	}

	@Redirect(
			method = "tryConnect",
			at = @At(
					value = "INVOKE",
					target = "Lnet/createmod/catnip/math/VecHelper;intersect(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/Direction$Axis;)[D"
			)
	)
	private static double[] planetworld$wrapIntersect(Vec3 p1, Vec3 p2, Vec3 r1, Vec3 r2, Direction.Axis axis) {
		Level level = CreateWrapContext.level();
		DimensionTransformer t = CreateWrapMath.transformer(level);
		if (!t.isWrapped()) {
			return VecHelper.intersect(p1, p2, r1, r2, axis);
		}
		return VecHelper.intersect(p1, CreateWrapMath.unwrapRelative(t, p1, p2), r1, r2, axis);
	}

	@WrapMethod(method = "placeTracks")
	private static TrackPlacement.PlacementInfo wrapPlaceTracks(
			Level level,
			TrackPlacement.PlacementInfo info,
			BlockState state1,
			BlockState state2,
			BlockPos targetPos1,
			BlockPos targetPos2,
			boolean simulate,
			Operation<TrackPlacement.PlacementInfo> original
	) {
		Level previous = CreateWrapContext.push(level);
		try {
			DimensionTransformer t = CreateWrapMath.transformer(level);
			BlockPos wrapped1 = t.isWrapped() ? t.Block.wrap(targetPos1) : targetPos1;
			BlockPos wrapped2 = t.isWrapped() ? t.Block.wrap(targetPos2) : targetPos2;
			return original.call(level, info, state1, state2, wrapped1, wrapped2, simulate);
		} finally {
			CreateWrapContext.pop(previous);
		}
	}

	@Redirect(
			method = "placeTracks",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
			)
	)
	private static BlockState planetworld$wrapGetBlockState(Level level, BlockPos pos) {
		return level.getBlockState(CreateWrapMath.wrapPos(level, pos));
	}

	@Redirect(
			method = "placeTracks",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
			)
	)
	private static boolean planetworld$wrapSetBlock(Level level, BlockPos pos, BlockState state, int flags) {
		return level.setBlock(CreateWrapMath.wrapPos(level, pos), state, flags);
	}

	@Redirect(
			method = "placeTracks",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"
			)
	)
	private static net.minecraft.world.level.block.entity.BlockEntity planetworld$wrapGetBlockEntity(Level level, BlockPos pos) {
		return level.getBlockEntity(CreateWrapMath.wrapPos(level, pos));
	}
}
