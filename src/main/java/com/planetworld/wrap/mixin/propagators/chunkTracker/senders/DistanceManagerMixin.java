/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.propagators.chunkTracker.senders;

import com.planetworld.wrap.accessors.TransformerAccessor;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Passes the level transformer into every chunk-tracker the distance manager owns.
 * <p>
 * Ticket <em>positions</em> are intentionally not remapped here. Player tickets near a bound are
 * placed on a Euclidean square that can include out-of-domain neighbours; wrapping those onto the
 * far side of a small torus collapses the loaded area to a few chunks. Out-of-domain generation is
 * still impossible because {@code ServerChunkCache} wraps every getChunk path.
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
