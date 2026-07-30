/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.packet;

import com.planetworld.wrap.core.DimensionTransformer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", ordinal = 0), require = 1)
	private Vec3 unwrapVec(Vec3 instance, Vec3 vec, Operation<Vec3> original) {
		DimensionTransformer transformer = player.serverLevel().getTransformer();
		return original.call(instance, transformer.Vector3D.unwrap(instance, vec));
	}

	@WrapOperation(method = "handlePlayerAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;handleBlockBreakAction(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V"), require = 1)
	private void wrapBlockPos(ServerPlayerGameMode instance, BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int worldHeight, int sequence, Operation<Void> original) {
		original.call(instance, player.serverLevel().getTransformer().Block.wrap(pos), action, direction, worldHeight, sequence);
	}

	@WrapOperation(method = "handleUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundUseItemOnPacket;getHitResult()Lnet/minecraft/world/phys/BlockHitResult;"), require = 1)
	private BlockHitResult wrapLocationAndBlockPos(ServerboundUseItemOnPacket instance, Operation<BlockHitResult> original) {
		DimensionTransformer transformer = player.serverLevel().getTransformer();
		BlockHitResult blockHit = original.call(instance);

		return new BlockHitResult(transformer.Vector3D.wrap(blockHit.getLocation()), blockHit.getDirection(), transformer.Block.wrap(blockHit.getBlockPos()), blockHit.isInside());
	}

	/**
	 * Vanilla stores the X move delta three times in handleMovePlayer (first-good, last-good,
	 * post-move). NeoForge production bytecode does not keep a single LVT name for all three, so
	 * slot indexes (with require/expect) are the reliable bind — a renumber fails loud.
	 */
	@ModifyVariable(method = "handleMovePlayer", at = @At("STORE"), index = 17, require = 3, expect = 3)
	private double normalizePlayerMoveDeltaX(double deltaX) {
		return player.serverLevel().getTransformer().Coord.X.shortestDelta(deltaX);
	}

	@ModifyVariable(method = "handleMovePlayer", at = @At("STORE"), index = 21, require = 3, expect = 3)
	private double normalizePlayerMoveDeltaZ(double deltaZ) {
		return player.serverLevel().getTransformer().Coord.Z.shortestDelta(deltaZ);
	}

	@ModifyVariable(method = "handleMoveVehicle", at = @At("STORE"), index = 18, require = 3, expect = 3)
	private double normalizeVehicleMoveDeltaX(double deltaX) {
		return player.serverLevel().getTransformer().Coord.X.shortestDelta(deltaX);
	}

	@ModifyVariable(method = "handleMoveVehicle", at = @At("STORE"), index = 22, require = 3, expect = 3)
	private double normalizeVehicleMoveDeltaZ(double deltaZ) {
		return player.serverLevel().getTransformer().Coord.Z.shortestDelta(deltaZ);
	}
}
