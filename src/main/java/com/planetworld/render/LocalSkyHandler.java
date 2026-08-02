package com.planetworld.render;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.time.LocalTime;
import com.planetworld.wrap.WrapMath;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Drives client sky / lighting perception from the player's local time mapping.
 */
@OnlyIn(Dist.CLIENT)
public final class LocalSkyHandler {
    private LocalSkyHandler() {
    }

    public static float localCelestialAngle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return 0f;
        }
        if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(mc.level)) {
            return mc.level.getTimeOfDay(1.0f);
        }
        return LocalTime.celestialAngle(mc.level, mc.player.getX());
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!PlanetWorldConfig.enableLocalizedTime()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !WrapMath.isWrappedDimension(mc.level)) {
            return;
        }
        float angle = localCelestialAngle();
        // Darken fog slightly at local night
        boolean night = angle > 0.25f && angle < 0.75f;
        if (night) {
            event.setRed(event.getRed() * 0.35f);
            event.setGreen(event.getGreen() * 0.35f);
            event.setBlue(event.getBlue() * 0.45f);
        }
    }
}
