package com.planetworld.wrap.compat.create.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.wrap.compat.create.CreateTrackGraphSeam;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.simibubi.create.content.trains.GlobalRailwayManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Heals seam gaps in railway graphs that were saved before this compat layer existed, so existing
 * networks keep working without replacing every rail.
 * <p>
 * New track is stitched while Create discovers it, so this runs once per dimension per session
 * rather than polling the whole network.
 */
@Mixin(GlobalRailwayManager.class)
public abstract class GlobalRailwayManagerMixin {

	@WrapMethod(method = "tick")
	private void planetworld$healSeamsOnce(Level level, Operation<Void> original) {
		original.call(level);
		if (level == null || level.isClientSide || !CreateWrapMath.isWrapped(level)) {
			return;
		}
		if (CreateWrapMath.consumeSeamHeal(level.dimension())) {
			CreateTrackGraphSeam.stitchAllGraphs(level);
		}
	}
}
