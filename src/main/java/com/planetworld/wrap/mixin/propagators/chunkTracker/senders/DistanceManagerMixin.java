/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.propagators.chunkTracker.senders;

import com.planetworld.wrap.accessors.TransformerAccessor;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Pass to {@link com.planetworld.wrap.mixin.chunk.ChunkTrackerMixin} instances
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements TransformerAccessor {
	@Mutable @Shadow @Final private DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter;
	@Mutable @Shadow @Final private DistanceManager.PlayerTicketTracker playerTicketManager;
	@Mutable @Shadow @Final private DistanceManager.ChunkTicketTracker ticketTracker;
	@Mutable @Shadow @Final private TickingTracker tickingTicketsTracker;

	/**
	 * Sets the transformers for the trackers. This cannot be called before DistanceManager's constructor because the child class ChunkMap.DistanceManager needs to call its super method (DistanceManager's constructor) before it can assign a transformer to the class.
	 */
	@Unique
	public void assignTransformers() {
		this.naturalSpawnChunkCounter.setTransformer(this.getTransformer());
		this.playerTicketManager.setTransformer(this.getTransformer());
		this.ticketTracker.setTransformer(this.getTransformer());
		this.tickingTicketsTracker.setTransformer(this.getTransformer());
	}

	/**
	 * Every public ticket entry point funnels through this pair, so keeping the domain here means a
	 * caller that never went through {@code ServerChunkCache} still cannot ticket — and therefore
	 * cannot generate — a chunk outside the torus. Sable's physics prediction is one such caller: it
	 * derives chunk bounds from a sub-level's world bounding box and tickets them directly, which
	 * runs past the domain as soon as a vehicle approaches a bound.
	 * <p>
	 * Add and remove share the mapping, so a ticket can always be released with the key it was
	 * stored under. Inside the domain the wrap is the identity, which is every request away from a
	 * bound.
	 */
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
		DimensionTransformer transformer = this.getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return chunkPos;
		}
		return transformer.Chunk.wrapLong(chunkPos);
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
