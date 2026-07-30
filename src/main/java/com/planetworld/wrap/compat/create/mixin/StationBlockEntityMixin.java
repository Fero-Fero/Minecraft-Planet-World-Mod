package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.compat.create.CreateTrackGraphSeam;
import com.planetworld.wrap.compat.create.CreateWrapContext;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * Wrap-aware station assembly near the torus seam: bogey scanning and train create
 * must follow wrapped track positions instead of stopping at the world edge.
 */
@Mixin(StationBlockEntity.class)
public abstract class StationBlockEntityMixin {

	@WrapMethod(method = "refreshAssemblyInfo")
	private void planetworld$wrapRefreshAssemblyInfo(Operation<Void> original) {
		Level level = ((BlockEntity) (Object) this).getLevel();
		Level previous = CreateWrapContext.push(level);
		try {
			CreateTrackGraphSeam.stitchAllGraphs(level);
			original.call();
		} finally {
			CreateWrapContext.pop(previous);
		}
	}

	@WrapMethod(method = "assemble")
	private void planetworld$wrapAssemble(UUID playerUUID, Operation<Void> original) {
		Level level = ((BlockEntity) (Object) this).getLevel();
		Level previous = CreateWrapContext.push(level);
		try {
			CreateTrackGraphSeam.stitchAllGraphs(level);
			original.call(playerUUID);
		} finally {
			CreateWrapContext.pop(previous);
		}
	}

	/**
	 * Keep the assembly cursor inside the torus so bogey/track scans continue past the cut
	 * instead of walking Euclidean coordinates like 257, 258, … forever.
	 */
	@Redirect(
			method = "refreshAssemblyInfo",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/BlockPos$MutableBlockPos;move(Lnet/minecraft/core/Direction;)Lnet/minecraft/core/BlockPos$MutableBlockPos;"
			)
	)
	private BlockPos.MutableBlockPos planetworld$wrapAssemblyCursor(BlockPos.MutableBlockPos pos, Direction direction) {
		pos.move(direction);
		Level level = CreateWrapContext.level();
		if (level != null && CreateWrapMath.isWrapped(level)) {
			BlockPos wrapped = CreateWrapMath.wrapPos(level, pos);
			pos.set(wrapped.getX(), wrapped.getY(), wrapped.getZ());
		}
		return pos;
	}

	@Redirect(
			method = {"refreshAssemblyInfo", "assemble"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
			)
	)
	private BlockState planetworld$wrapGetBlockState(Level level, BlockPos pos) {
		return level.getBlockState(CreateWrapMath.wrapPos(level, pos));
	}

	@Redirect(
			method = {"refreshAssemblyInfo", "assemble"},
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"
			)
	)
	private BlockEntity planetworld$wrapGetBlockEntity(Level level, BlockPos pos) {
		return level.getBlockEntity(CreateWrapMath.wrapPos(level, pos));
	}

	@Redirect(
			method = "assemble",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/BlockPos;relative(Lnet/minecraft/core/Direction;I)Lnet/minecraft/core/BlockPos;"
			)
	)
	private BlockPos planetworld$wrapRelative(BlockPos pos, Direction direction, int distance) {
		Level level = CreateWrapContext.level();
		BlockPos moved = pos.relative(direction, distance);
		return CreateWrapMath.wrapPos(level, moved);
	}

	/**
	 * assemble() picks the starting track node via {@code end.subtract(center)}.
	 * Near the seam that must be shortest-path unwrap, or the station never finds a node.
	 */
	@Redirect(
			method = "assemble",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/phys/Vec3;subtract(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
			)
	)
	private Vec3 planetworld$wrapSubtract(Vec3 self, Vec3 other) {
		Level level = CreateWrapContext.level();
		DimensionTransformer t = CreateWrapMath.transformer(level);
		if (!t.isWrapped()) {
			return self.subtract(other);
		}
		return CreateWrapMath.unwrapRelative(t, other, self).subtract(other);
	}
}
