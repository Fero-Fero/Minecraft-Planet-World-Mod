package com.planetworld.mixin;

import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomSpreadStructurePlacement.class)
public interface RandomSpreadStructurePlacementAccessor {
	@Accessor("spacing")
	int planetworld$rawSpacing();

	@Accessor("separation")
	int planetworld$rawSeparation();
}
