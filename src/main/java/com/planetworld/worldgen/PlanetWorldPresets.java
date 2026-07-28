package com.planetworld.worldgen;

import com.planetworld.PlanetWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

public final class PlanetWorldPresets {
    public static final ResourceKey<WorldPreset> WRAPPED = ResourceKey.create(
            Registries.WORLD_PRESET,
            ResourceLocation.fromNamespaceAndPath(PlanetWorld.MOD_ID, "wrapped")
    );

    private PlanetWorldPresets() {
    }
}
