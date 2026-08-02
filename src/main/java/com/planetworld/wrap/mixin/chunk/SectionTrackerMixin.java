/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.chunk;

import com.planetworld.wrap.accessors.TransformerAccessor;
import com.planetworld.wrap.core.DimensionTransformer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.SectionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionTracker.class)
public class SectionTrackerMixin implements TransformerAccessor {
	SectionTracker thiz = (SectionTracker) (Object) this;

	/**
	 * Propagate section graph updates across the wrap seam.
	 * {@link SectionPos#offset} takes deltas — wrap the resulting absolute X/Z, not the deltas.
	 */
	@WrapOperation(method = "checkNeighborsAfterUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;offset(JIII)J"))
	private long wrapChunkPos(long pos, int dx, int dy, int dz, Operation<Long> original, @Local(argsOnly = true) int level, @Local(argsOnly = true) boolean isDecreasing) {
		long vanillaNeighbor = original.call(pos, dx, dy, dz);

		DimensionTransformer transformer = getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return vanillaNeighbor;
		}

		int nx = SectionPos.x(pos) + dx;
		int ny = SectionPos.y(pos) + dy;
		int nz = SectionPos.z(pos) + dz;
		int wx = transformer.Chunk.X.wrap(nx);
		int wz = transformer.Chunk.Z.wrap(nz);

		if (wx != nx || wz != nz) {
			long wrappedNeighbor = SectionPos.asLong(wx, ny, wz);
			if (wrappedNeighbor != pos) {
				thiz.checkNeighbor(pos, wrappedNeighbor, level, isDecreasing);
			}
		}

		return vanillaNeighbor;
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
