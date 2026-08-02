/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.chunk;

import com.planetworld.wrap.accessors.TransformerAccessor;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.storage.TransformerRequests;
import com.google.common.annotations.VisibleForTesting;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ChunkTrackingView.Positioned;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Consumer;

@Mixin(Positioned.class)
public abstract class ChunkTrackingView$PositionedMixin implements TransformerAccessor {

	@Unique private Positioned thiz = (Positioned) (Object) this;

	/**
	 * Ensure the thread-local wrap transformer matches this view before distance checks.
	 * Critical for {@code onChunkReadyToSend → contains()} which never went through ChunkMap injects.
	 */
	@Overwrite
	public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
		if (transformer != null) {
			TransformerRequests.setChunkMapTransformer(transformer);
		}
		return ChunkTrackingView.isWithinDistance(
				thiz.center().x, thiz.center().z, thiz.viewDistance(), x, z, includeOuterChunksAdjacentToViewBorder);
	}

	/**
	 * @author Famro Fexl
	 * @reason Wrapped Worlds require including wrapped chunks in calculations.
	 */
	@Overwrite
	public void forEach(Consumer<ChunkPos> action) {
		if (transformer == null || !transformer.isWrapped()) {
			for (int x = thiz.minX(); x <= thiz.maxX(); x++) {
				for (int z = thiz.minZ(); z <= thiz.maxZ(); z++) {
					if (thiz.contains(x, z)) {
						action.accept(new ChunkPos(x, z));
					}
				}
			}
			return;
		}

		TransformerRequests.setChunkMapTransformer(transformer);
		for (int x = thiz.minX(); x <= thiz.maxX(); x++) {
			for (int z = thiz.minZ(); z <= thiz.maxZ(); z++) {
				int wrappedX = transformer.Chunk.X.wrap(x);
				int wrappedZ = transformer.Chunk.Z.wrap(z);

				if (thiz.contains(wrappedX, wrappedZ)) {
					action.accept(new ChunkPos(wrappedX, wrappedZ));
				}
			}
		}
	}

	/**
	 * @author Famro Fexl
	 * @reason Intersections must be wrapped in a Wrapped World.
	 */
	@VisibleForTesting
	@Overwrite
	public boolean squareIntersects(Positioned other) {
		if (transformer == null || !transformer.isWrapped()) {
			return thiz.minX() <= other.maxX() && thiz.maxX() >= other.minX()
					&& thiz.minZ() <= other.maxZ() && thiz.maxZ() >= other.minZ();
		}

		boolean xIntersects = (thiz.minX() <= other.maxX() && thiz.maxX() >= other.minX()) ||
			(thiz.minX() + transformer.xWidth <= other.maxX() && thiz.maxX() + transformer.xWidth >= other.minX()) ||
			(thiz.minX() <= other.maxX() + transformer.xWidth && thiz.maxX() >= other.minX() + transformer.xWidth);

		boolean zIntersects = (thiz.minZ() <= other.maxZ() && thiz.maxZ() >= other.minZ()) ||
			(thiz.minZ() + transformer.zWidth <= other.maxZ() && thiz.maxZ() + transformer.zWidth >= other.minZ()) ||
			(thiz.minZ() <= other.maxZ() + transformer.zWidth && thiz.maxZ() >= other.minZ() + transformer.zWidth);

		return xIntersects && zIntersects;
	}

	DimensionTransformer transformer;

	@Override
	public DimensionTransformer getTransformer() {
		return this.transformer;
	}

	@Override
	public void setTransformer(DimensionTransformer transformer) {
		this.transformer = transformer;
	}
}
