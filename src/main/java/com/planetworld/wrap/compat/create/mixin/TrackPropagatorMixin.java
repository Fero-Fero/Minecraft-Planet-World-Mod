package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.compat.create.CreateTrackGraphSeam;
import com.planetworld.wrap.compat.create.CreateWrapContext;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.track.TrackPropagator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Pushes wrap context during graph rebuild, scales Create's walk budget to planet size,
 * and stitches seam-spanning edges afterward.
 */
@Mixin(TrackPropagator.class)
public abstract class TrackPropagatorMixin {

	/**
	 * Vanilla uses {@code emergencyExit = 1000}. A full loop on a C=2048 world is a 4096-block
	 * period — raise the budget from the active transformer so discovery can finish.
	 */
	@ModifyConstant(method = "onRailAdded", constant = @Constant(intValue = 1000))
	private static int planetworld$scaleGraphWalkBudget(int original) {
		return CreateWrapMath.graphWalkBudget(original);
	}

	@WrapMethod(method = "onRailAdded")
	private static TrackGraph planetworld$wrapOnRailAdded(
			LevelAccessor reader,
			BlockPos pos,
			BlockState state,
			Operation<TrackGraph> original
	) {
		Level level = reader instanceof Level l ? l : CreateWrapContext.level();
		Level previous = CreateWrapContext.push(level);
		try {
			TrackGraph graph = original.call(reader, pos, state);
			if (graph != null) {
				CreateTrackGraphSeam.stitchSeamEdges(reader, graph);
			}
			return graph;
		} finally {
			CreateWrapContext.pop(previous);
		}
	}
}
