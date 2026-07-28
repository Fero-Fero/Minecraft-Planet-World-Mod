package com.planetworld.worldgen;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete-coverage helpers: force one patch per biome on the torus so every
 * overworld biome appears at least once inside the wrap period.
 */
public final class BiomeStructureCoverage {
    private static final Map<BiomeSource, List<Holder<Biome>>> BIOME_CACHE = new IdentityHashMap<>();

    private BiomeStructureCoverage() {
    }

    public static boolean isActive() {
        return PlanetWorldConfig.isCompleteCoverage();
    }

    /**
     * Returns a forced biome when {@code (blockX, blockZ)} sits in a reserved
     * coverage patch; otherwise {@code null} so vanilla multi-noise applies.
     */
    public static Holder<Biome> forcedBiomeAt(int blockX, int blockZ, BiomeSource source) {
        if (!isActive() || source == null) {
            return null;
        }
        List<Holder<Biome>> biomes = biomesOf(source);
        int n = biomes.size();
        if (n == 0) {
            return null;
        }

        int period = Math.max(16, (int) Math.round(WrapMath.periodBlocks()));
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n)));
        int rows = Math.max(1, (int) Math.ceil(n / (double) cols));
        int cellW = Math.max(16, period / cols);
        int cellH = Math.max(16, period / rows);

        int wx = Math.floorMod(blockX, period);
        int wz = Math.floorMod(blockZ, period);
        int col = Math.min(cols - 1, wx / cellW);
        int row = Math.min(rows - 1, wz / cellH);
        int idx = row * cols + col;
        if (idx < 0 || idx >= n) {
            return null;
        }

        int localX = wx - col * cellW;
        int localZ = wz - row * cellH;
        int margin = Math.max(2, Math.min(cellW, cellH) / 5);
        if (localX < margin || localZ < margin || localX >= cellW - margin || localZ >= cellH - margin) {
            return null;
        }
        return biomes.get(idx);
    }

    private static List<Holder<Biome>> biomesOf(BiomeSource source) {
        synchronized (BIOME_CACHE) {
            List<Holder<Biome>> cached = BIOME_CACHE.get(source);
            if (cached != null) {
                return cached;
            }
            List<Holder<Biome>> list = new ArrayList<>();
            source.possibleBiomes().forEach(list::add);
            BIOME_CACHE.put(source, List.copyOf(list));
            return BIOME_CACHE.get(source);
        }
    }

    public static void clearCache() {
        synchronized (BIOME_CACHE) {
            BIOME_CACHE.clear();
        }
    }
}
