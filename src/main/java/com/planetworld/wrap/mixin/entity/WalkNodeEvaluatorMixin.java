/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.entity;

import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When a path node sits on a torus bound, vanilla never samples the neighbour on the far side.
 * Append that wrapped neighbour so mobs can step across the cut instead of only relying on
 * post-hoc path rebase in {@link PathNavigationMixin}.
 * <p>
 * Extends {@link NodeEvaluator} so {@code getNode} / {@code currentContext} resolve from the
 * superclass (Mixin cannot {@code @Shadow} those onto {@link WalkNodeEvaluator} alone).
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin extends NodeEvaluator {

	@Inject(method = "getNeighbors", at = @At("RETURN"))
	private void planetworld$appendWrappedNeighbors(
			Node[] neighbors,
			Node node,
			CallbackInfoReturnable<Integer> cir
	) {
		int count = cir.getReturnValueI();
		if (count <= 0 || count >= neighbors.length || this.currentContext == null) {
			return;
		}

		DimensionTransformer transformer;
		if (this.currentContext.level() instanceof Level level) {
			transformer = level.getTransformer();
		} else {
			return;
		}
		if (transformer == null || !transformer.isWrapped()) {
			return;
		}
		transformer = transformer.SSO();

		boolean nearSeam = transformer.Coord.X.isOver(node.x + 1)
				|| transformer.Coord.X.isOver(node.x - 1)
				|| transformer.Coord.Z.isOver(node.z + 1)
				|| transformer.Coord.Z.isOver(node.z - 1);
		if (!nearSeam) {
			return;
		}

		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] step : steps) {
			if (count >= neighbors.length) {
				break;
			}
			BlockPos sample = transformer.Block.wrap(new BlockPos(node.x + step[0], node.y, node.z + step[1]));
			if (sample.getX() == node.x + step[0] && sample.getZ() == node.z + step[1]) {
				continue;
			}
			boolean already = false;
			for (int i = 0; i < count; i++) {
				Node existing = neighbors[i];
				if (existing != null && existing.x == sample.getX() && existing.y == sample.getY() && existing.z == sample.getZ()) {
					already = true;
					break;
				}
			}
			if (already) {
				continue;
			}
			Node mirror = this.getNode(sample.getX(), sample.getY(), sample.getZ());
			if (mirror != null && mirror.type != PathType.BLOCKED && mirror.costMalus >= 0.0F) {
				neighbors[count++] = mirror;
			}
		}
		cir.setReturnValue(count);
	}
}
