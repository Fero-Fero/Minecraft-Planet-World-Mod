/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Shadow
	public abstract ServerLevel serverLevel();

	/**
	 * Wrap bed block distance check.
	 */
	@ModifyVariable(method = "isReachableBedBlock", at = @At("HEAD"), index = 1, argsOnly = true)
	public BlockPos modifyBlockPos(BlockPos blockPos) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		return this.serverLevel().getTransformer().Block.unwrap(self.blockPosition(), blockPos);
	}

	/**
	 * Distance travelled feeds movement statistics and hunger exhaustion. Callers derive it from raw
	 * positions sampled before and after a tick, so a bound crossing looks like one step across the
	 * whole world and drains the food bar at once. Measure the short path instead.
	 */
	@ModifyVariable(method = "checkMovementStatistics", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double planetworld$shortestMovementX(double deltaX) {
		return this.serverLevel().getTransformer().Coord.X.shortestDelta(deltaX);
	}

	@ModifyVariable(method = "checkMovementStatistics", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private double planetworld$shortestMovementZ(double deltaZ) {
		return this.serverLevel().getTransformer().Coord.Z.shortestDelta(deltaZ);
	}

	@ModifyVariable(method = "checkRidingStatistics", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private double planetworld$shortestRidingX(double deltaX) {
		return this.serverLevel().getTransformer().Coord.X.shortestDelta(deltaX);
	}

	@ModifyVariable(method = "checkRidingStatistics", at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private double planetworld$shortestRidingZ(double deltaZ) {
		return this.serverLevel().getTransformer().Coord.Z.shortestDelta(deltaZ);
	}
}
