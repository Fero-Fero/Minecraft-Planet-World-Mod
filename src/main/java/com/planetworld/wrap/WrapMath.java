package com.planetworld.wrap;

import com.planetworld.config.PlanetWorldConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Torus wrap helpers for a continuous planet.
 * Players are never teleported — these map coordinates into one circumference
 * for terrain, time, and curvature only.
 * <p>
 * UI {@code planetCircumference} {@code C} is the wrap radius (half-period).
 * Full period {@code P = 2C}. Geographic poles sit at {@code P/4 = C/2};
 * the wrap seam at {@code ±C} is the far equator (opposite face), not a pole.
 */
public final class WrapMath {
    private WrapMath() {
    }

    public static boolean isWrappedDimension(Level level) {
        if (level == null) {
            return false;
        }
        // Defer to the Circumnavigate-style transformer attached to the level (server and client).
        com.planetworld.wrap.core.DimensionTransformer transformer = level.getTransformer();
        if (transformer != null) {
            return transformer.isWrapped();
        }
        return !level.dimensionType().hasFixedTime()
                && level.dimension().location().getPath().equals("overworld");
    }

    public static double wrapX(double x) {
        // `planetCircumference` is treated as *half-period*. Full wrap period is 2x.
        double width = PlanetWorldConfig.planetCircumference() * 2.0;
        double half = width / 2.0;
        x = ((x + half) % width + width) % width - half;
        if (x >= half) {
            x -= width;
        }
        return x;
    }

    public static int wrapChunkX(int chunkX) {
        int width = PlanetWorldConfig.chunkWidth();
        int half = width / 2;
        int wrapped = Math.floorMod(chunkX + half, width) - half;
        if (wrapped >= half) {
            wrapped -= width;
        }
        return wrapped;
    }

    public static int wrapBlockX(int blockX) {
        return (int) Math.floor(wrapX(blockX));
    }

    /**
     * Full torus period in blocks ({@code P = 2C}).
     * UI circumference {@code C} is the half-period / wrap radius (= far-equator distance);
     * geographic poles are at {@code C/2 = P/4}.
     */
    public static double periodBlocks() {
        return PlanetWorldConfig.planetCircumference() * 2.0;
    }

    /** Equator→pole distance {@code P/4 = C/2}. */
    public static double quarterPeriodBlocks() {
        return periodBlocks() * 0.25;
    }

    /** Shortest signed delta on the wrapped circle from {@code fromX} to {@code toX}. */
    public static double shortestDeltaX(double fromX, double toX) {
        return shortestDelta(fromX, toX, periodBlocks());
    }

    /** Shortest signed delta on Z (same period as X for the square torus). */
    public static double shortestDeltaZ(double fromZ, double toZ) {
        return shortestDelta(fromZ, toZ, periodBlocks());
    }

    private static double shortestDelta(double from, double to, double width) {
        double delta = to - from;
        delta = ((delta + width / 2.0) % width + width) % width - width / 2.0;
        return delta;
    }

    public static Vec3 wrapPosition(Vec3 pos) {
        double wx = wrapX(pos.x);
        if (wx == pos.x) {
            return pos;
        }
        return new Vec3(wx, pos.y, pos.z);
    }
}
