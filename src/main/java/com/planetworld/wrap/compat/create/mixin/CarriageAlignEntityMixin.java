package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageEntityHandler;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Objects;

/**
 * Carriage anchors live in wrapped (server/graph) space. PlanetWorld's client keeps entities in
 * continuous unwrapped coordinates so chunk packets stay coherent. Forcing {@code setPos} to a
 * wrapped anchor on the client therefore teleports the train (and its rider's camera) across the
 * whole world the instant a bogey crosses a bound — black void, thrashing clouds, locked yaw.
 * <p>
 * Server: keep the entity inside the domain and snap prev-pos on a torus hop.
 * Client: rebase the wrapped anchor onto the entity's current continuous frame before {@code setPos}.
 */
@Mixin(Carriage.DimensionalCarriageEntity.class)
public abstract class CarriageAlignEntityMixin {

	@Shadow public Vec3 positionAnchor;
	@Shadow public Couple<Vec3> rotationAnchors;

	@WrapMethod(method = "alignEntity")
	private void planetworld$wrapAlignEntity(CarriageContraptionEntity entity, Operation<Void> original) {
		DimensionTransformer t = CreateWrapMath.transformer(entity.level());
		if (!t.isWrapped()) {
			original.call(entity);
			return;
		}
		if (this.rotationAnchors.either(Objects::isNull)) {
			return;
		}

		Vec3 positionVec = this.rotationAnchors.getFirst();
		Vec3 coupledVec = this.rotationAnchors.getSecond();
		Vec3 coupledInFrame = CreateWrapMath.unwrapRelative(t, positionVec, coupledVec);
		double diffX = positionVec.x - coupledInFrame.x;
		double diffY = positionVec.y - coupledInFrame.y;
		double diffZ = positionVec.z - coupledInFrame.z;

		entity.prevYaw = entity.yaw;
		entity.prevPitch = entity.pitch;

		Carriage carriage = entity.getCarriage();
		boolean client = entity.level().isClientSide();

		if (!client && this.positionAnchor != null) {
			Vec3 entityPos = entity.position();
			Vec3 anchorUnwrapped = CreateWrapMath.unwrapRelative(t, entityPos, this.positionAnchor);
			Vec3 delta = anchorUnwrapped.subtract(entityPos);
			Vec3 lookahead = this.positionAnchor;
			if (delta.lengthSqr() > 1.0e-8) {
				lookahead = t.Vector3D.wrap(entityPos.add(delta.normalize().scale(16.0)));
			}
			for (Entity e : entity.getPassengers()) {
				if (!(e instanceof Player) || e.distanceToSqr(entity) > 1024.0) {
					continue;
				}
				if (CarriageEntityHandler.isActiveChunk(entity.level(), BlockPos.containing((Position) lookahead))) {
					break;
				}
				if (carriage != null) {
					carriage.train.carriageWaitingForChunks = carriage.id;
				}
				return;
			}
			if (carriage != null && carriage.train.carriageWaitingForChunks == carriage.id) {
				carriage.train.carriageWaitingForChunks = -1;
			}
			entity.setServerSidePrevPosition();
		}

		if (this.positionAnchor != null) {
			double prevX = entity.getX();
			double prevZ = entity.getZ();
			Vec3 target = client
					? CreateWrapMath.unwrapRelative(t, entity.position(), this.positionAnchor)
					: t.Vector3D.wrap(this.positionAnchor);
			entity.setPos(target);
			// Server torus hops jump by nearly a full period; snap prev so riders/interpolation
			// do not lerp through the world center. Client targets stay continuous, so this is a no-op.
			planetworld$snapPrevIfLargeHop(entity, prevX, prevZ);
		}

		entity.yaw = (float) (Mth.atan2(diffZ, diffX) * 180.0 / Math.PI) + 180.0f;
		entity.pitch = (float) (Math.atan2(diffY, Math.sqrt(diffX * diffX + diffZ * diffZ)) * 180.0 / Math.PI) * -1.0f;

		if (!entity.firstPositionUpdate) {
			return;
		}
		entity.xo = entity.getX();
		entity.yo = entity.getY();
		entity.zo = entity.getZ();
		entity.xOld = entity.getX();
		entity.yOld = entity.getY();
		entity.zOld = entity.getZ();
		entity.prevYaw = entity.yaw;
		entity.prevPitch = entity.pitch;
	}

	private static void planetworld$snapPrevIfLargeHop(Entity entity, double prevX, double prevZ) {
		if (Mth.square(entity.getX() - prevX) + Mth.square(entity.getZ() - prevZ) <= 64.0) {
			return;
		}
		planetworld$snapPrev(entity);
		for (Entity passenger : entity.getIndirectPassengers()) {
			planetworld$snapPrev(passenger);
		}
	}

	private static void planetworld$snapPrev(Entity entity) {
		entity.xo = entity.getX();
		entity.yo = entity.getY();
		entity.zo = entity.getZ();
		entity.xOld = entity.getX();
		entity.yOld = entity.getY();
		entity.zOld = entity.getZ();
	}
}
