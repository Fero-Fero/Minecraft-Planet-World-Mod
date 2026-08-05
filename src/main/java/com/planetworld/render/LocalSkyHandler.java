package com.planetworld.render;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.time.LocalTime;
import com.planetworld.time.MeridianTracker;
import com.planetworld.wrap.WrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client sky: longitude + continuous meridian observer frame on a fixed orbital ring.
 * Folded latitude tips the path (horizon at poles, overhead at both equators).
 * Meridian-longitude ({@code tipTurns × ¼}) makes {@code Z = C} opposite day to home.
 */
@OnlyIn(Dist.CLIENT)
public final class LocalSkyHandler {
	/** Vanilla sun/moon discs are oversized for a globe view; half size by default. */
	public static final float CELESTIAL_DISC_SCALE = 0.5f;

	private LocalSkyHandler() {
	}

	/**
	 * Observer day-cycle angle — world time ± X longitude ± continuous meridian longitude
	 * ({@code tipTurns × ¼} so {@code Z = C} is opposite day to {@code Z = 0}).
	 */
	public static float localCelestialAngle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return 0f;
		}
		if (mc.player == null
				|| !PlanetWorldConfig.enableLocalizedTime()
				|| !WrapMath.isWrappedDimension(mc.level)) {
			return LocalTime.worldCelestialAngle(mc.level);
		}
		return LocalTime.observerCelestialAngle(mc.level, mc.player.getX(), continuousZ());
	}

	public static float localCelestialTiltDegrees() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return 0f;
		}
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(mc.level)) {
			return 0f;
		}
		DimensionTransformer t = mc.level.getTransformer();
		double period = (t != null && t.isWrapped())
				? t.Coord.Z.domainLength
				: com.planetworld.worldgen.ContinentalClimate.periodBlocks();
		return LocalTime.celestialTiltDegrees(mc.level, continuousZ(), MeridianTracker.quarterPeriod(period));
	}

	/** Continuous meridian Z for the local player (updates unwrap state). */
	public static double continuousZ() {
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
		return MeridianTracker.continuousZ(mc.player.getUUID(), wrappedZ, period);
	}

	/**
	 * Folded geographic latitude for fog / climate (triangle). Sky tip uses
	 * {@link #continuousZ()} via {@link LocalTime#celestialTiltDegrees(Level, double, double)}.
	 */
	public static double continuousLatitude() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return 0.0;
		}
		DimensionTransformer t = mc.level.getTransformer();
		double period = (t != null && t.isWrapped())
				? t.Coord.Z.domainLength
				: com.planetworld.worldgen.ContinentalClimate.periodBlocks();
		return MeridianTracker.latitudeForPeriod(continuousZ(), period);
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
