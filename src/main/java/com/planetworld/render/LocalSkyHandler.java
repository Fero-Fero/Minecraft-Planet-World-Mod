package com.planetworld.render;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.time.LocalTime;
import com.planetworld.wrap.WrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client sky: world-fixed sun/moon clock (same for every player) plus continuous
 * meridian tip so polar wrap crossings do not reverse the sky or horizon tint.
 */
@OnlyIn(Dist.CLIENT)
public final class LocalSkyHandler {
	/** Vanilla sun/moon discs are oversized for a globe view; half size by default. */
	public static final float CELESTIAL_DISC_SCALE = 0.5f;

	private LocalSkyHandler() {
	}

	/**
	 * Global day-cycle angle for sun/moon rendering — identical for all players.
	 * Longitude-local time still drives gameplay ({@link LocalTime#isDay}, crops, spawn).
	 */
	public static float localCelestialAngle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 0f;
		}
		return LocalTime.worldCelestialAngle(mc.level);
	}

	public static float localCelestialTiltDegrees() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return 0f;
		}
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(mc.level)) {
			return 0f;
		}
		return LocalTime.celestialTiltDegrees(mc.level, continuousLatitude());
	}

	/**
	 * Continuous meridian latitude for sky tip + fog (does not flip at the polar wrap seam).
	 */
	public static double continuousLatitude() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return 0.0;
		}
		DimensionTransformer t = mc.level.getTransformer();
		double period;
		double wrappedZ = mc.player.getZ();
		if (t != null && t.isWrapped()) {
			period = t.Coord.Z.domainLength;
			wrappedZ = t.Coord.Z.wrap(wrappedZ);
		} else {
			period = com.planetworld.worldgen.ContinentalClimate.periodBlocks();
		}
		double half = period * 0.5;
		double continuousZ = ContinuousMeridian.continuousZ(wrappedZ, period);
		return ContinuousMeridian.latitude(continuousZ, half);
	}

	/** Sun disc scale: 50% of vanilla, then ±8% by northern calendar season. */
	public static float seasonSunScale() {
		Minecraft mc = Minecraft.getInstance();
		float season = 1.0f;
		if (mc.level != null
				&& PlanetWorldConfig.enableLocalizedTime()
				&& WrapMath.isWrappedDimension(mc.level)) {
			season = 1.0f + SeasonAuthority.northernWarmth(mc.level) * 0.08f;
		}
		return CELESTIAL_DISC_SCALE * season;
	}

	/** Moon disc scale: 50% of vanilla (no seasonal size change). */
	public static float moonDiscScale() {
		return CELESTIAL_DISC_SCALE;
	}

	/** @deprecated use {@link #seasonSunScale()} */
	@Deprecated
	public static float localSunScale() {
		return seasonSunScale();
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
		boolean night = angle > 0.25f && angle < 0.75f;
		if (night) {
			event.setRed(event.getRed() * 0.35f);
			event.setGreen(event.getGreen() * 0.35f);
			event.setBlue(event.getBlue() * 0.45f);
		}
		// Continuous latitude — wrapped Z would flip south↔north season at the pole seam
		// and flash night fog into golden summer evening.
		float warmth = SeasonAuthority.warmthAtLatitude(mc.level, continuousLatitude());
		if (warmth < -0.15f) {
			float t = Math.min(1f, -warmth);
			event.setRed(event.getRed() * (1f - 0.12f * t) + 0.05f * t);
			event.setGreen(event.getGreen() * (1f - 0.08f * t) + 0.06f * t);
			event.setBlue(event.getBlue() * (1f - 0.02f * t) + 0.10f * t);
		} else if (warmth > 0.15f) {
			float t = Math.min(1f, warmth);
			event.setRed(event.getRed() * (1f - 0.05f * t) + 0.12f * t);
			event.setGreen(event.getGreen() * (1f - 0.04f * t) + 0.08f * t);
			event.setBlue(event.getBlue() * (1f - 0.10f * t));
		}
	}

	@SubscribeEvent
	public static void onFogDensity(ViewportEvent.RenderFog event) {
		if (!PlanetWorldConfig.enableLocalizedTime()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !WrapMath.isWrappedDimension(mc.level)) {
			return;
		}
		double lat = continuousLatitude();
		float warmth = SeasonAuthority.warmthAtLatitude(mc.level, lat);
		double absLat = Math.abs(lat);
		float pullIn = 0f;
		if (warmth < 0f) {
			pullIn += -warmth * 0.12f;
		}
		if (absLat > 0.7 && warmth < 0f) {
			pullIn += 0.18f;
		}
		if (pullIn <= 0.01f) {
			return;
		}
		float far = event.getFarPlaneDistance();
		event.setFarPlaneDistance(Math.max(24f, far * (1f - pullIn)));
	}
}
