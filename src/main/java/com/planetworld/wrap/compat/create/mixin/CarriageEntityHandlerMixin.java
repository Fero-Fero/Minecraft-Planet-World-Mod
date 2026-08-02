package com.planetworld.wrap.compat.create.mixin;

import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.simibubi.create.content.trains.entity.CarriageEntityHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create's chunk-active checks must use wrapped coordinates so seam lookahead / entity
 * validation never wait on out-of-torus cells that PlanetWorld will not generate.
 */
@Mixin(CarriageEntityHandler.class)
public abstract class CarriageEntityHandlerMixin {

	@Inject(method = "isActiveChunk", at = @At("HEAD"), cancellable = true)
	private static void planetworld$wrapIsActiveChunk(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (!CreateWrapMath.isWrapped(level)) {
			return;
		}
		BlockPos wrapped = CreateWrapMath.wrapPos(level, pos);
		cir.setReturnValue(serverLevel.isPositionEntityTicking(wrapped));
	}
}
