/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.chunk;

import com.planetworld.wrap.accessors.TransformerAccessor;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

	@Final @Shadow public ServerLevel level;
	
	/**
	 * Players will track wrapped chunks as part of their tracking view.
	 */
	@Inject(method = "isChunkTracked", at = @At("HEAD"), cancellable = true)
	public void isChunkTracked(ServerPlayer player, int x, int z, CallbackInfoReturnable<Boolean> cir) {
		DimensionTransformer transformer = player.serverLevel().getTransformer();
		TransformerRequests.setChunkMapTransformer(transformer);

		// pendingChunks is keyed by server ChunkPos — do not unwrap for isPending.
		cir.setReturnValue(player.getChunkTrackingView().contains(x, z)
				&& !player.connection.chunkSender.isPending(ChunkPos.asLong(x, z)));
	}

	/**
	 * Gives ChunkTrackingView.Positioned instances a WorldTransformer when they are created (this is the only place they are created)
	 */
	@Redirect(method = "updateChunkTracking", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkTrackingView;of(Lnet/minecraft/world/level/ChunkPos;I)Lnet/minecraft/server/level/ChunkTrackingView;"))
	public ChunkTrackingView setChunkTransformer(ChunkPos center, int viewDistance) {
		ChunkTrackingView.Positioned newView = (ChunkTrackingView.Positioned) ChunkTrackingView.of(center, viewDistance);
		((TransformerAccessor) (Object) newView).setTransformer(level.getTransformer());
		return newView;
	}

	/**
	 * Stores the serverLevel for usage further down the call chain where it was not passed.
	 */
	@Inject(method = "applyChunkTrackingView", at = @At("HEAD"))
	public void captureLevel(ServerPlayer player, ChunkTrackingView chunkTrackingView, CallbackInfo ci) {
		TransformerRequests.setChunkMapTransformer(player.serverLevel().getTransformer());
	}

	/**
	 * Keep continuous client coords (used to remap chunk packets) in sync with the wrapped
	 * server position. While riding, MovePlayer packets may never run — after a torus jump
	 * that freezes client chunk loading until the player dismounts and walks.
	 */
	@Inject(method = "move", at = @At("HEAD"))
	private void planetworld$advanceClientCoords(ServerPlayer player, CallbackInfo ci) {
		DimensionTransformer transformer = player.serverLevel().getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return;
		}
		player.setClientX(transformer.Coord.X.unwrap(player.getClientX(), player.getX()));
		player.setClientZ(transformer.Coord.Z.unwrap(player.getClientZ(), player.getZ()));
	}

	/**
	 * Support wrapped distances as closest Euclidean distance.
	 */
	@Inject(method = "euclideanDistanceSquared", at = @At("HEAD"), cancellable = true)
    private static void euclideanDistanceSquared(ChunkPos chunkPos, Entity entity, CallbackInfoReturnable<Double> cir) {
		double d = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
		double e = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
		cir.setReturnValue(entity.level().getTransformer().Coord.sqrDistToBounds(entity.getX(), 0, entity.getZ(), d, 0, e));
	}

	/**
	 * Never write cancelled out-of-bounds generation shells to region files. Those empty FULL
	 * chunks become permanent voids if the world is opened without Planet World.
	 */
	@Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
	private void planetworld$skipOutOfBoundsSave(net.minecraft.world.level.chunk.ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
		DimensionTransformer transformer = level.getTransformer();
		if (transformer != null && transformer.isWrapped() && transformer.Chunk.isOver(chunk.getPos())) {
			cir.setReturnValue(false);
		}
	}
}
