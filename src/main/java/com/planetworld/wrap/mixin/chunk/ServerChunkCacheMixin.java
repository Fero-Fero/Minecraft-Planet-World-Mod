/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.chunk;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {
	@Shadow @Final ServerLevel level;

	@ModifyVariable(method = "getChunkNow", at = @At("HEAD"), argsOnly = true, index = 1)
	public int modifyGetChunkNowX(int chunkX) {
		return level.getTransformer().Chunk.X.wrap(chunkX);
	}

	@ModifyVariable(method = "getChunkNow", at = @At("HEAD"), argsOnly = true, index = 2)
	public int modifyGetChunkNowZ(int chunkZ) {
		return level.getTransformer().Chunk.Z.wrap(chunkZ);
	}

	/**
	 * Main load/gen path (Create train lookahead, force-loads, etc.). Without this, out-of-torus
	 * chunk coords can stall generation forever after a wrap jump.
	 * <p>
	 * 1.21.1 signature returns {@code ChunkAccess}, not {@code LevelChunk}.
	 */
	@ModifyVariable(
			method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
			at = @At("HEAD"),
			argsOnly = true,
			index = 1
	)
	public int modifyGetChunkX(int chunkX) {
		return level.getTransformer().Chunk.X.wrap(chunkX);
	}

	@ModifyVariable(
			method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
			at = @At("HEAD"),
			argsOnly = true,
			index = 2
	)
	public int modifyGetChunkZ(int chunkZ) {
		return level.getTransformer().Chunk.Z.wrap(chunkZ);
	}

	@ModifyVariable(
			method = "getChunkFutureMainThread(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;",
			at = @At("HEAD"),
			argsOnly = true,
			index = 1
	)
	public int modifyGetChunkFutureX(int chunkX) {
		return level.getTransformer().Chunk.X.wrap(chunkX);
	}

	@ModifyVariable(
			method = "getChunkFutureMainThread(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Ljava/util/concurrent/CompletableFuture;",
			at = @At("HEAD"),
			argsOnly = true,
			index = 2
	)
	public int modifyGetChunkFutureZ(int chunkZ) {
		return level.getTransformer().Chunk.Z.wrap(chunkZ);
	}
}
