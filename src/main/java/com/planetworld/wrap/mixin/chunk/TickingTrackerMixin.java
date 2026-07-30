/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.chunk;

import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The ticking half of the chunk-ticket domain guard.
 * <p>
 * {@code DistanceManager.addRegionTicket} hands the same packed position to its own ticket map and to
 * this tracker, and mods reach the tracker directly as well, so both need the same domain. The
 * transformer comes from {@code ChunkTracker}, which {@code DistanceManager} populates for every
 * tracker it owns.
 *
 * @see com.planetworld.wrap.mixin.propagators.chunkTracker.senders.DistanceManagerMixin
 */
@Mixin(TickingTracker.class)
public abstract class TickingTrackerMixin {
	@ModifyVariable(method = "addTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("HEAD"), argsOnly = true, index = 1)
	private long planetworld$wrapAddedTicketPos(long chunkPos) {
		return planetworld$wrapTicketPos(chunkPos);
	}

	@ModifyVariable(method = "removeTicket(JLnet/minecraft/server/level/Ticket;)V", at = @At("HEAD"), argsOnly = true, index = 1)
	private long planetworld$wrapRemovedTicketPos(long chunkPos) {
		return planetworld$wrapTicketPos(chunkPos);
	}

	@Unique
	private long planetworld$wrapTicketPos(long chunkPos) {
		DimensionTransformer transformer = ((TickingTracker) (Object) this).getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return chunkPos;
		}
		return transformer.Chunk.wrapLong(chunkPos);
	}
}
