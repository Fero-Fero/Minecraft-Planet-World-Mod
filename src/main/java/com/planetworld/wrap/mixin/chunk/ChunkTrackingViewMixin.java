/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.chunk;

import com.planetworld.wrap.accessors.TransformerAccessor;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ChunkTrackingView.class)
public interface ChunkTrackingViewMixin {
	/**
	 * Toroidal view-distance test. Uses thread-local transformer set by ChunkMap /
	 * Positioned.contains so client occlusion cannot race the server.
	 */
	@Inject(method = "isWithinDistance", at = @At("HEAD"), cancellable = true)
	private static void checkWrappedChunks(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeOuterChunksAdjacentToViewBorder, CallbackInfoReturnable<Boolean> cir) {
		DimensionTransformer raw = TransformerRequests.getChunkMapTransformer();
		if (raw == null) {
			return;
		}
		// Client occlusion uses continuous coords; SSO() disables wrapping there.
		DimensionTransformer transformer = raw.SSO();
		if (!transformer.isWrapped()) {
			return;
		}

		// Packed server chunk positions outside the torus are never tracked.
		if (transformer.Chunk.X.isOver(x) || transformer.Chunk.Z.isOver(z)) {
			cir.setReturnValue(false);
			return;
		}

		int ux = transformer.Chunk.X.unwrap(centerX, x);
		int uz = transformer.Chunk.Z.unwrap(centerZ, z);

		int i = Math.max(0, Math.abs(ux - centerX) - 1);
		int j = Math.max(0, Math.abs(uz - centerZ) - 1);
		long k = Math.max(0, Math.max(i, j) - (includeOuterChunksAdjacentToViewBorder ? 1 : 0));
		long l = Math.min(i, j);
		long distSq = l * l + k * k;
		cir.setReturnValue(distSq < (long) viewDistance * (long) viewDistance);
	}

	/**
	 * @author Famro Fexl
	 * @reason World wrapping includes wrapped chunks, and each chunk must be wrapped in order to be properly checked.
	 */
	@Overwrite
	static void difference(ChunkTrackingView oldChunkTrackingView, ChunkTrackingView newChunkTrackingView, Consumer<ChunkPos> chunkMarker, Consumer<ChunkPos> chunkDropper) {
		if (oldChunkTrackingView.equals(newChunkTrackingView)) return;

		if (oldChunkTrackingView instanceof ChunkTrackingView.Positioned positioned
			&& newChunkTrackingView instanceof ChunkTrackingView.Positioned positioned2)
			if (((PositionedAccessorMixin) (Object) positioned).squareIntersectsAM(positioned2)) {

				DimensionTransformer transformer = ((TransformerAccessor) (Object) positioned).getTransformer();
				if (transformer != null) {
					TransformerRequests.setChunkMapTransformer(transformer);
				}

				int minX = Math.min(positioned.minX(), transformer.Chunk.X.unwrap(positioned.minX(), positioned2.minX()));
				int minZ = Math.min(positioned.minZ(), transformer.Chunk.Z.unwrap(positioned.minZ(), positioned2.minZ()));
				int maxX = Math.max(positioned.maxX(), transformer.Chunk.X.unwrap(positioned.maxX(), positioned2.maxX()));
				int maxZ = Math.max(positioned.maxZ(), transformer.Chunk.Z.unwrap(positioned.maxZ(), positioned2.maxZ()));

				for (int x = minX; x <= maxX; x++) {
					for (int z = minZ; z <= maxZ; z++) {

						int wrappedX = transformer.Chunk.X.wrap(x);
						int wrappedZ = transformer.Chunk.Z.wrap(z);

						boolean inOld = positioned.contains(wrappedX, wrappedZ);
						boolean inNew = positioned2.contains(wrappedX, wrappedZ);
						if (inOld != inNew) {
							if (inNew) {
								chunkMarker.accept(new ChunkPos(wrappedX, wrappedZ));
							} else {
								chunkDropper.accept(new ChunkPos(wrappedX, wrappedZ));
							}
						}
					}
				}

				return;
			}

		oldChunkTrackingView.forEach(chunkDropper);
		newChunkTrackingView.forEach(chunkMarker);
	}
}
