package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.planetworld.wrap.compat.create.CreateWrapContext;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.compat.create.CreateTrackGraphSeam;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Ensures Create track discovery uses wrapped coordinates and wrap-aware end matching.
 */
@Mixin(ITrackBlock.class)
public interface ITrackBlockMixin {

	@WrapMethod(method = "walkConnectedTracks")
	private static Collection<TrackNodeLocation.DiscoveredLocation> planetworld$wrapWalk(
			BlockGetter worldIn,
			TrackNodeLocation location,
			boolean linear,
			Operation<Collection<TrackNodeLocation.DiscoveredLocation>> original
	) {
		Level level = worldIn instanceof Level l ? l : CreateWrapContext.level();
		Level previous = CreateWrapContext.push(level);
		try {
			return original.call(worldIn, location, linear);
		} finally {
			CreateWrapContext.pop(previous);
		}
	}

	@WrapOperation(
			method = "walkConnectedTracks",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
			)
	)
	private static BlockState planetworld$wrapWalkGetBlockState(
			BlockGetter world,
			BlockPos pos,
			Operation<BlockState> original
	) {
		return original.call(world, CreateWrapMath.wrapPos(world, pos));
	}

	@WrapOperation(
			method = "getMaterialSimple(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/phys/Vec3;Lcom/simibubi/create/content/trains/track/TrackMaterial;)Lcom/simibubi/create/content/trains/track/TrackMaterial;",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/BlockGetter;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
			)
	)
	private static BlockState planetworld$wrapMaterialGetBlockState(
			BlockGetter world,
			BlockPos pos,
			Operation<BlockState> original
	) {
		return original.call(world, CreateWrapMath.wrapPos(world, pos));
	}

	/**
	 * Wrap offset factories into domain space and match {@code fromEnd} with wrap-aware equality
	 * so discovery continues across the seam instead of treating opposite-edge ends as unrelated.
	 */
	@WrapMethod(method = "addToListIfConnected")
	private static void planetworld$wrapAddToListIfConnected(
			TrackNodeLocation fromEnd,
			Collection<TrackNodeLocation.DiscoveredLocation> list,
			BiFunction<Double, Boolean, Vec3> offsetFactory,
			Function<Boolean, Vec3> normalFactory,
			Function<Boolean, ResourceKey<Level>> dimensionFactory,
			Function<Vec3, Integer> yOffsetFactory,
			Vec3 axis,
			BezierConnection viaTurn,
			BiFunction<Boolean, Vec3, TrackMaterial> materialFactory,
			Operation<Void> original
	) {
		BiFunction<Double, Boolean, Vec3> wrappedOffsets =
				(d, b) -> CreateWrapMath.wrapVec(offsetFactory.apply(d, b));

		if (fromEnd == null || !CreateWrapMath.wrappingActive()) {
			original.call(
					fromEnd, list, wrappedOffsets, normalFactory, dimensionFactory,
					yOffsetFactory, axis, viaTurn, materialFactory
			);
			forceSeamNodes(list);
			return;
		}

		ArrayList<TrackNodeLocation.DiscoveredLocation> temp = new ArrayList<>(2);
		original.call(
				null, temp, wrappedOffsets, normalFactory, dimensionFactory,
				yOffsetFactory, axis, viaTurn, materialFactory
		);
		boolean matched = false;
		for (TrackNodeLocation.DiscoveredLocation loc : temp) {
			if (CreateTrackGraphSeam.locationsMatch(loc, fromEnd)) {
				matched = true;
			}
		}
		if (!matched) {
			return;
		}
		for (TrackNodeLocation.DiscoveredLocation loc : temp) {
			if (!CreateTrackGraphSeam.locationsMatch(loc, fromEnd)) {
				if (CreateTrackGraphSeam.isSeamLocation(loc)) {
					loc.forceNode();
				}
				list.add(loc);
			}
		}
	}

	private static void forceSeamNodes(Collection<TrackNodeLocation.DiscoveredLocation> list) {
		if (!CreateWrapMath.wrappingActive()) {
			return;
		}
		for (TrackNodeLocation.DiscoveredLocation loc : list) {
			if (CreateTrackGraphSeam.isSeamLocation(loc)) {
				loc.forceNode();
			}
		}
	}
}
