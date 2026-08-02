package com.planetworld.render;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Physically realistic horizon curvature for a sphere with
 * {@code R = circumference / pi} (UI circumference is the half-period).
 * Drop is {@code d^2 / (2R)} with no artificial intensity boost.
 * Uniforms are only non-zero while the world is being drawn (not GUI/hotbar).
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

    public static double planetRadiusBlocks() {
        return Math.max(1.0, PlanetWorldConfig.planetCircumference() / Math.PI);
    }

    /** Shader factor so {@code drop = dist2 * factor} equals {@code d^2 / (2R)}. */
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
        if (!PlanetWorldConfig.enableCurvatureShader()) {
            return 0f;
        }
        double radius = planetRadiusBlocks();
        return (float) (-(horizontalDistance * horizontalDistance) / (2.0 * radius));
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

    /** Percent drop relative to distance at 64 blocks (for customize UI). */
    public static float physicalCurvePercentAt(int circumference, double distance) {
        double radius = Math.max(1.0, circumference / Math.PI);
        if (distance <= 0.0) {
            return 0f;
        }
        double drop = (distance * distance) / (2.0 * radius);
        return (float) (100.0 * drop / distance);
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
    }
}
