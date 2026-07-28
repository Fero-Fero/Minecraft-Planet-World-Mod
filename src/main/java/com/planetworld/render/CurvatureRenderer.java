package com.planetworld.render;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Physically realistic horizon curvature for a sphere whose equatorial
 * circumference matches the planet half-period UI value ({@code R = C / pi}).
 * Drop is {@code d^2 / (2R)} with no artificial intensity boost — large worlds
 * look nearly flat, small worlds curve more.
 */
@OnlyIn(Dist.CLIENT)
public final class CurvatureRenderer {
    private static boolean levelRendering;

    private CurvatureRenderer() {
    }

    public static void beginLevelRender() {
        levelRendering = true;
    }

    public static void endLevelRender() {
        levelRendering = false;
    }

    public static boolean isActive() {
        if (!levelRendering || !PlanetWorldConfig.enableCurvatureShader()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && WrapMath.isWrappedDimension(mc.level);
    }

    /** Sphere radius in blocks: {@code circumference / pi}. */
    public static double planetRadiusBlocks() {
        return Math.max(1.0, PlanetWorldConfig.planetCircumference() / Math.PI);
    }

    /**
     * Shader uniform factor so {@code drop = (dx^2 + dz^2) * factor} equals {@code d^2 / (2R)}.
     */
    public static float shaderCurvatureFactor() {
        if (!isActive()) {
            return 0f;
        }
        return (float) (1.0 / (2.0 * planetRadiusBlocks()));
    }

    public static float shaderWrapHalf() {
        if (!isActive()) {
            return 0f;
        }
        return (float) (WrapMath.periodBlocks() * 0.5);
    }

    public static float curvatureDrop(double horizontalDistance) {
        return (float) (-horizontalDistance * horizontalDistance * shaderCurvatureFactor());
    }

    public static float curvatureDropFromCamera(double worldX, double worldZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.player == null) {
            return 0f;
        }
        var camera = mc.gameRenderer.getMainCamera();
        double dx = WrapMath.shortestDeltaX(camera.getPosition().x, worldX);
        double dz = WrapMath.shortestDeltaZ(camera.getPosition().z, worldZ);
        return curvatureDrop(Math.sqrt(dx * dx + dz * dz));
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
    }
}
