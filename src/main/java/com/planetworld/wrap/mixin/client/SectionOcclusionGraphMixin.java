/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.client;

import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {
	/**
	 * ChunkTrackingView is mainly used server-side. This is the only usage of it client-side.
	 * Harmless under Sodium (Sodium uses its own occlusion graph); we only stash the wrap
	 * transformer for any vanilla distance checks that still run.
	 */
	@Inject(method = "isInViewDistance", at = @At("HEAD"))
	public void isInViewDistance(BlockPos pos, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
		TransformerRequests.setChunkMapTransformer(Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getTransformer() : DimensionTransformer.DISABLED);
	}
}
