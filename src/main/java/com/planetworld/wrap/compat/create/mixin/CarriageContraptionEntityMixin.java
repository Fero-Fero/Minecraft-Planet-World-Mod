package com.planetworld.wrap.compat.create.mixin;

import com.planetworld.wrap.accessors.WrapsOwnPosition;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Carriage positions come from Create's track graph, which the compat layer already keeps in wrapped
 * space. Declaring the ownership here means core entity wrapping leaves carriages (and their riders)
 * alone without knowing anything about Create.
 */
@Mixin(CarriageContraptionEntity.class)
public abstract class CarriageContraptionEntityMixin implements WrapsOwnPosition {
}
