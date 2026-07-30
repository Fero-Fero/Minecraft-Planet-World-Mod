package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Station / signal / observer targets are stored as a relative offset from the block entity.
 * When that sum lands past a bound (or the relative was computed the long way), wrap so
 * {@code hasValidTrack} / assemble / overlay still see the real track block.
 */
@Mixin(TrackTargetingBehaviour.class)
public abstract class TrackTargetingBehaviourMixin {

	@WrapMethod(method = "getGlobalPosition")
	private BlockPos planetworld$wrapGlobalPosition(Operation<BlockPos> original) {
		BlockPos pos = original.call();
		Level level = ((BlockEntityBehaviour) (Object) this).getWorld();
		return CreateWrapMath.wrapPos(level, pos);
	}
}
