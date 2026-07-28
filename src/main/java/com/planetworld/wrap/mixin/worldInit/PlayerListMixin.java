/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.worldInit;

import com.planetworld.wrap.core.DimensionTransformer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Shadow public abstract MinecraftServer getServer();
	@Shadow public abstract void broadcastAll(Packet<?> packet);
	@Shadow private int viewDistance;
	@Shadow private int simulationDistance;

	/**
	 * Initializes the player's client-side positioning so they can be used for unwrapping operations.
	 */
	@Inject(method = "placeNewPlayer", at = @At("TAIL"))
	public void placeNewPlayer2(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
		player.setClientX(player.getX());
		player.setClientZ(player.getZ());
	}

	/**
	 * Keep the requested view distance in PlayerList (so IntegratedServer does not spam),
	 * but cap the per-level chunk source distance for wrapped dimensions.
	 * Client effective render distance is also capped via OptionsMixin.
	 */
	@Inject(method = "setViewDistance", at = @At("HEAD"), cancellable = true)
	public void setViewDistance(int viewDistance, CallbackInfo ci) {
		ci.cancel();

		this.viewDistance = viewDistance;
		this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(viewDistance));

		for (ServerLevel serverLevel : this.getServer().getAllLevels()) {
			if (serverLevel != null) {
				DimensionTransformer levelTransformer = serverLevel.getTransformer();
				if (levelTransformer == null) {
					serverLevel.getChunkSource().setViewDistance(viewDistance);
				} else {
					serverLevel.getChunkSource().setViewDistance(levelTransformer.limitViewDistance(viewDistance));
				}
			}
		}
	}

	@Inject(method = "setSimulationDistance", at = @At("HEAD"), cancellable = true)
	public void setSimulationDistance(int simulationDistance, CallbackInfo ci) {
		ci.cancel();

		this.simulationDistance = simulationDistance;
		this.broadcastAll(new ClientboundSetSimulationDistancePacket(simulationDistance));

		for (ServerLevel serverLevel : this.getServer().getAllLevels()) {
			if (serverLevel != null) {
				DimensionTransformer levelTransformer = serverLevel.getTransformer();
				if (levelTransformer == null) {
					serverLevel.getChunkSource().setSimulationDistance(simulationDistance);
				} else {
					serverLevel.getChunkSource().setSimulationDistance(levelTransformer.limitViewDistance(simulationDistance));
				}
			}
		}
	}

	@Redirect(method = "broadcast", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getX()D"))
	public double broadcast_X(ServerPlayer instance, @Local(ordinal = 0, argsOnly = true) double x) {
		return instance.serverLevel().getTransformer().Coord.X.unwrap(x, instance.getX());
	}

	@Redirect(method = "broadcast", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getZ()D"))
	public double broadcast_Z(ServerPlayer instance, @Local(ordinal = 2, argsOnly = true) double z) {
		return instance.serverLevel().getTransformer().Coord.Z.unwrap(z, instance.getZ());
	}
}
