/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.interfaceInjects;

import com.planetworld.wrap.injected.ServerPlayerInjector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Continuous client-space X/Z used to remap chunk/entity packets on the torus.
 * <p>
 * Do not capture position in a field initializer — {@code ServerPlayer} is often still
 * at spawn when mixin fields run. Also do not sync at {@code placeNewPlayer} HEAD:
 * NBT load happens after HEAD, so early sync locks spawn (0,0) and remapped chunks
 * land outside the client's view (void on existing-world load).
 */
@Mixin(ServerPlayer.class)
public class PlayerClientInjectorMixin implements ServerPlayerInjector {
	@Unique
	private double clientX;
	@Unique
	private double clientZ;
	@Unique
	private boolean clientPosValid;

	@Unique
	private void planetworld$ensureClientPos() {
		if (clientPosValid) {
			return;
		}
		ServerPlayer self = (ServerPlayer) (Object) this;
		clientX = self.getX();
		clientZ = self.getZ();
		clientPosValid = true;
	}

	@Override
	public double getClientX() {
		planetworld$ensureClientPos();
		return clientX;
	}

	@Override
	public double getClientZ() {
		planetworld$ensureClientPos();
		return clientZ;
	}

	@Override
	public ChunkPos getClientChunk() {
		planetworld$ensureClientPos();
		return new ChunkPos(Mth.floor(clientX) >> 4, Mth.floor(clientZ) >> 4);
	}

	@Override
	public BlockPos getClientBlock() {
		planetworld$ensureClientPos();
		return new BlockPos(Mth.floor(clientX), ((ServerPlayer) (Object) this).blockPosition().getY(), Mth.floor(clientZ));
	}

	@Override
	public Vec3 getClientPosition() {
		planetworld$ensureClientPos();
		return new Vec3(clientX, ((ServerPlayer) (Object) this).position().y, clientZ);
	}

	@Override
	public void setClientX(double clientX) {
		this.clientX = clientX;
		this.clientPosValid = true;
	}

	@Override
	public void setClientZ(double clientZ) {
		this.clientZ = clientZ;
		this.clientPosValid = true;
	}

	@Override
	public void invalidateClientPos() {
		this.clientPosValid = false;
	}

	@Override
	public void setClientPos(double clientX, double clientZ) {
		this.clientX = clientX;
		this.clientZ = clientZ;
		this.clientPosValid = true;
	}
}
