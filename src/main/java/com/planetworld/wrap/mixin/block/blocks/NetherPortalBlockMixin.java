/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.block.blocks;

import com.planetworld.wrap.core.CoordinateConstants;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
	@Shadow
	protected abstract DimensionTransition getExitPortal(
			ServerLevel level,
			Entity entity,
			BlockPos pos,
			BlockPos exitPos,
			boolean isNether,
			WorldBorder worldBorder
	);

	/**
	 * Portal linking uses wrapped source coords, dimension scale, then wraps into
	 * the destination torus so Overworld↔Nether stays inside the planet grid.
	 */
	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	public void getPortalDestination(
			ServerLevel level,
			Entity entity,
			BlockPos pos,
			CallbackInfoReturnable<DimensionTransition> cir
	) {
		ResourceKey<Level> resourceKey = level.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER;
		ServerLevel serverLevel = level.getServer().getLevel(resourceKey);
		if (serverLevel == null) {
			cir.setReturnValue(null);
			return;
		}

		boolean toNether = serverLevel.dimension() == Level.NETHER;
		WorldBorder worldBorder = serverLevel.getWorldBorder();
		DimensionTransformer src = level.getTransformer();
		DimensionTransformer dst = serverLevel.getTransformer();

		Vec3 source = entity.position();
		if (isFiniteWrap(src)) {
			source = src.Vector3D.wrap(source);
		}

		double scaleX = dimensionScale(src, dst, true);
		double scaleZ = dimensionScale(src, dst, false);
		double destX = source.x * scaleX;
		double destZ = source.z * scaleZ;
		BlockPos exit = BlockPos.containing(destX, entity.getY(), destZ);
		if (isFiniteWrap(dst)) {
			exit = dst.Block.wrap(exit);
		}
		exit = worldBorder.clampToBounds(exit.getX(), exit.getY(), exit.getZ());
		cir.setReturnValue(this.getExitPortal(serverLevel, entity, pos, exit, toNether, worldBorder));
	}

	private static boolean isFiniteWrap(DimensionTransformer transformer) {
		if (transformer == null || transformer == DimensionTransformer.DISABLED) {
			return false;
		}
		int max = Math.max(
				Math.abs(transformer.wrappingSettings.xChunkBoundMax()),
				Math.abs(transformer.wrappingSettings.xChunkBoundMin())
		);
		return max < CoordinateConstants.DISABLING_CHUNK_POS / 2;
	}

	private static double dimensionScale(DimensionTransformer src, DimensionTransformer dst, boolean xAxis) {
		int srcW = Math.max(1, xAxis ? src.xWidth : src.zWidth);
		int dstW = Math.max(1, xAxis ? dst.xWidth : dst.zWidth);
		if (isFiniteWrap(src) && isFiniteWrap(dst)) {
			return (double) dstW / (double) srcW;
		}
		// One side still DISABLED: keep vanilla 8:1 instead of astronomical DISABLED widths
		if (isFiniteWrap(src) && !isFiniteWrap(dst)) {
			return 1.0 / 8.0; // Overworld → unwrapped Nether placeholder
		}
		if (!isFiniteWrap(src) && isFiniteWrap(dst)) {
			return 8.0; // Nether placeholder → Overworld
		}
		return (double) dstW / (double) srcW;
	}
}
