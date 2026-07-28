package com.planetworld.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Client-side curvature illusion.
 * Applies a distance-based vertical drop to the projection/model stack so far
 * terrain appears to roll over the horizon (Animal Crossing style).
 * Physical hitboxes remain flat.
 */
@OnlyIn(Dist.CLIENT)
public final class CurvatureRenderer {
    private CurvatureRenderer() {
    }

    /** Y drop for a world-space horizontal distance from the camera. */
    public static float curvatureDrop(double horizontalDistance) {
        float intensity = PlanetWorldConfig.curvatureIntensity();
        // Half-period circumference: full wrap period = 2*C ⇒ sphere R = period/(2π) = C/π
        double radius = Math.max(1.0, PlanetWorldConfig.planetCircumference() / Math.PI);
        // Approximate sphere: drop ≈ d² / (2R), scaled by auto intensity (C/360)
        double drop = (horizontalDistance * horizontalDistance) / (2.0 * radius);
        return (float) (-drop * intensity);
    }

    public static float curvatureDropFromCamera(double worldX, double worldZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.player == null) {
            return 0f;
        }
        var camera = mc.gameRenderer.getMainCamera();
        // Torus: shortest distance on both axes (period = 2 * circumference).
        double dx = WrapMath.shortestDeltaX(camera.getPosition().x, worldX);
        double dz = WrapMath.shortestDeltaZ(camera.getPosition().z, worldZ);
        return curvatureDrop(Math.sqrt(dx * dx + dz * dz));
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (!PlanetWorldConfig.enableCurvatureShader()) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !WrapMath.isWrappedDimension(mc.level)) {
            return;
        }
        // Expose uniforms for any core/iris shaders that bind to these names
        RenderSystem.setShaderGameTime(mc.level.getGameTime(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        // Hook retained for future horizon fog / angle bias adjustments.
    }

    /**
     * Builds a subtle shear/curve bias matrix for custom render paths.
     * Full terrain remapping is applied via {@code LevelRendererMixin} + shader uniforms.
     */
    public static Matrix4f curvatureBiasMatrix(float intensity) {
        Matrix4f m = new Matrix4f().identity();
        // Mild vertical squash that increases perceived horizon drop without breaking depth.
        float scale = 1.0f - Math.min(0.08f, intensity * 0.02f);
        m.scale(1f, scale, 1f);
        return m;
    }
}
