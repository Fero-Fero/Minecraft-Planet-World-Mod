package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BiConsumer;

/**
 * Placing a signal / station / observer against a track rejects targets that are wrap-adjacent but
 * Euclidean-far. Relative TargetTrack offsets and graph lookups must also use the short path so
 * assemble / overlay work when the station sits past a bound.
 */
@Mixin(TrackTargetingBlockItem.class)
public abstract class TrackTargetingBlockItemMixin {

	@WrapOperation(
			method = "useOn",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z")
	)
	private boolean planetworld$torusTargetRange(BlockPos self, Vec3i other, double distance, Operation<Boolean> original, UseOnContext context) {
		DimensionTransformer t = CreateWrapMath.transformer(context.getLevel());
		if (!t.isWrapped()) {
			return original.call(self, other, distance);
		}
		return CreateWrapMath.unwrapDistSqr(context.getLevel(), self, other) < distance * distance;
	}

	/**
	 * {@code selected.subtract(placePos)} becomes the NBT TargetTrack offset. Across a bound that
	 * must be the short torus delta, or {@code getGlobalPosition} points a world-width away.
	 */
	@WrapOperation(
			method = "useOn",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;subtract(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/core/BlockPos;")
	)
	private BlockPos planetworld$torusRelativeTarget(BlockPos selected, Vec3i placePos, Operation<BlockPos> original, UseOnContext context) {
		DimensionTransformer t = CreateWrapMath.transformer(context.getLevel());
		if (!t.isWrapped()) {
			return original.call(selected, placePos);
		}
		BlockPos from = new BlockPos(placePos.getX(), placePos.getY(), placePos.getZ());
		return CreateWrapMath.shortestBlockOffset(t, from, selected);
	}

	@WrapOperation(
			method = {"useOn", "withGraphLocation"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
			)
	)
	private static BlockState planetworld$wrapLookup(Level level, BlockPos pos, Operation<BlockState> original) {
		return original.call(level, CreateWrapMath.wrapPos(level, pos));
	}

	@WrapMethod(method = "withGraphLocation")
	private static void planetworld$wrapGraphLocationPos(
			Level level,
			BlockPos pos,
			boolean direction,
			BezierTrackPointLocation bezier,
			EdgePointType<?> type,
			BiConsumer<TrackTargetingBlockItem.OverlapResult, TrackGraphLocation> callback,
			Operation<Void> original
	) {
		original.call(level, CreateWrapMath.wrapPos(level, pos), direction, bezier, type, callback);
	}
}
