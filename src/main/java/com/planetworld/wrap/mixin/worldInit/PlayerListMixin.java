/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.mixin.worldInit;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.core.WorldSpaceProjector;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Player list hooks. Broadcast distance redirects cannot target {@code getX}/{@code getZ} once Sable
 * {@code @Overwrite}s {@code broadcast} to use sub-level-aware distance — those INVOKEs disappear and
 * mixin apply fatals. When the dimension is wrapped, reimplement the radius test as
 * <em>project, then measure on the torus</em> (see {@link WorldSpaceProjector}); otherwise defer to
 * vanilla/Sable unchanged.
 */
@Mixin(value = PlayerList.class, priority = 2000)
public abstract class PlayerListMixin {
	@Shadow public abstract MinecraftServer getServer();
	@Shadow public abstract void broadcastAll(Packet<?> packet);
	@Shadow @Final private List<ServerPlayer> players;
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

	@WrapMethod(method = "broadcast(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V")
	private void planetworld$torusBroadcast(
			Player except,
			double x,
			double y,
			double z,
			double radius,
			ResourceKey<Level> dimension,
			Packet<?> packet,
			Operation<Void> original
	) {
		ServerLevel level = this.getServer().getLevel(dimension);
		DimensionTransformer transformer = level == null ? null : level.getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			original.call(except, x, y, z, radius, dimension, packet);
			return;
		}

		Vec3 event = WorldSpaceProjector.project(level, x, y, z);
		double radiusSq = radius * radius;
		for (ServerPlayer player : this.players) {
			if (player == except || player.level().dimension() != dimension) {
				continue;
			}
			Vec3 listener = WorldSpaceProjector.project(level, player.getX(), player.getY(), player.getZ());
			if (transformer.Coord.sqrDistToBounds(
					event.x, event.y, event.z,
					listener.x, listener.y, listener.z
			) < radiusSq) {
				player.connection.send(packet);
			}
		}
	}
}
