package com.planetworld.time;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.season.SeasonAuthority;
import com.planetworld.wrap.WrapMath;
import com.planetworld.wrap.core.DimensionTransformer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Spatial time / sky authority for a globe-like wrapped Overworld:
 * <ul>
 *   <li>Longitude (X): east/west time zones on the fixed solar ring.</li>
 *   <li>Meridian (Z): continuous viewpoint tip (full rotations around the globe).</li>
 *   <li>Far-face day: tip ~180° + altitude proxy — never a hard +½ day on θ.</li>
 *   <li>Orbital obliquity: fixed ~{@link #ORBITAL_OBLIQUITY_DEGREES}° lean of the shared ring.</li>
 * </ul>
 * Planet-fixed sun stays on one ring from shared {@code dayTime}. Walking only changes
 * the observer frame (tip + meridian longitude) — never a hard antipode jump.
 * <p>
 * Chunk sky-light is still global, so sun-burn and monster spawn must use these helpers
 * instead of {@code Level.isDay()} / {@code LightLayer.SKY} alone.
 */
public final class LocalTime {
	public static final long DAY_LENGTH = 24000L;

	/** Light level vanilla requires for crops and saplings to advance. */
	private static final int GROWTH_LIGHT_LEVEL = 9;

	/**
	 * Tip per meridian half-period (equator→pole). South (+Z) raises tip so the path
	 * lowers toward the north.
	 */
	public static final float POLE_TIP_DEGREES = 90.0f;

	/**
	 * Observer-longitude advance per tip turn (fraction of a day). Reserved for experiments;
	 * sky θ uses X longitude only — far-face day is continuous tip + altitude, not +½ on θ.
	 */
	public static final float MERIDIAN_LONGITUDE_PER_TURN = 0.25f;

	/**
	 * Fixed lean of the shared sun/moon orbital plane (degrees), separate from geographic tip.
	 */
	public static final float ORBITAL_OBLIQUITY_DEGREES = 5.0f;

	/** @deprecated use {@link #POLE_TIP_DEGREES}. */
	@Deprecated
	public static final float POLE_TIP_BASE_DEGREES = POLE_TIP_DEGREES;

	/** @deprecated seasonal pole swing removed; obliquity is fixed. */
	@Deprecated
	public static final float POLE_TIP_SEASON_SWING = 0.0f;

	/** @deprecated use {@link #POLE_TIP_DEGREES}. */
	@Deprecated
	public static final float LATITUDE_TILT_DEGREES = POLE_TIP_DEGREES;

	/** Blend gameplay day/night from longitude toward seasonal polar day above this |lat|. */
	private static final float POLAR_EXPOSURE_START = 0.55f;
	/** Stronger day/night length dilation above this |lat|. */
	private static final float POLAR_DILATION_START = 0.70f;

	private LocalTime() {
	}

	public static long globalTime(Level level) {
		return level.getDayTime();
	}

	/**
	 * Longitude-only local ticks (east/west time zones). Prefer
	 * {@link #observerLocalTimeTicks(Level, double, double)} when meridian matters.
	 */
	public static long localTime(Level level, double x) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return globalTime(level);
		}
		double fullWidth = periodBlocksX(level);
		double wrappedX = wrapX(level, x);
		double normalized = wrappedX / fullWidth; // −0.5 .. 0.5
		long offset = Math.round(normalized * DAY_LENGTH);
		return Math.floorMod(globalTime(level) + offset, DAY_LENGTH);
	}

	public static long localTime(Entity entity) {
		return observerLocalTimeTicks(entity);
	}

	/**
	 * Observer solar ticks: world clock + X longitude only.
	 * Meridian does not shift θ — far-face opposite day comes from continuous sky tip
	 * (~180° at {@code Z=C}) and {@link #sunExposureAt} altitude, not a +½ day on the beads.
	 * {@code continuousZ} is kept for call-site compatibility.
	 */
	public static long observerLocalTimeTicks(Level level, double x, double continuousZ) {
		return localTime(level, x);
	}

	public static long observerLocalTimeTicks(Entity entity) {
		return observerLocalTimeTicks(entity.level(), entity.getX(), continuousZFor(entity));
	}

	/** Celestial angle 0..1 for an observer at longitude {@code x} and continuous meridian Z. */
	public static float observerCelestialAngle(Level level, double x, double continuousZ) {
		return celestialAngleFromTicks(observerLocalTimeTicks(level, x, continuousZ));
	}

	public static float observerCelestialAngle(Entity entity) {
		return observerCelestialAngle(entity.level(), entity.getX(), continuousZFor(entity));
	}

	/**
	 * How “equatorial” day/night still applies (1 at equator → 0 at poles).
	 * Poles use seasonal elevation instead of locking the sun underfoot/overhead.
	 */
	public static float latitudeDayFactor(Level level, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 1.0f;
		}
		double lat = SeasonAuthority.latitude(level, z);
		return Mth.clamp((float) Math.cos(lat * Math.PI * 0.5), 0.0f, 1.0f);
	}

	/** Prefer the live dimension transformer period so C=256 worlds don't use a stale config width. */
	private static double periodBlocksX(Level level) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.X.domainLength;
		}
		return PlanetWorldConfig.planetCircumference() * 2.0;
	}

	static double periodBlocksZ(Level level) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.Z.domainLength;
		}
		return com.planetworld.worldgen.ContinentalClimate.periodBlocks();
	}

	private static double wrapX(Level level, double x) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.X.wrap(x);
		}
		return WrapMath.wrapX(x);
	}

	static double wrapZ(Level level, double z) {
		DimensionTransformer t = level.getTransformer();
		if (t != null && t.isWrapped()) {
			return t.Coord.Z.wrap(z);
		}
		return wrapToSignedHalf(z, periodBlocksZ(level));
	}

	private static double wrapToSignedHalf(double value, double width) {
		if (!(width > 1.0e-3)) {
			return value;
		}
		double half = width * 0.5;
		value = ((value + half) % width + width) % width - half;
		if (value >= half) {
			value -= width;
		}
		return value;
	}

	/**
	 * Update meridian unwrap for {@code entity} and return continuous Z.
	 */
	public static double continuousZFor(Entity entity) {
		Level level = entity.level();
		double period = periodBlocksZ(level);
		double wrapped = wrapZ(level, entity.getZ());
		return MeridianTracker.continuousZ(entity.getUUID(), wrapped, period);
	}

	public static boolean isDay(Level level, double x, double z) {
		return sunExposure(level, x, z) > 0.18f;
	}

	public static boolean isDay(Entity entity) {
		return sunExposure(entity) > 0.18f;
	}

	public static boolean isDay(Level level, double x) {
		return isDay(level, x, 0.0);
	}

	public static boolean isNight(Level level, double x, double z) {
		return !isDay(level, x, z);
	}

	public static boolean isNight(Level level, double x) {
		return isNight(level, x, 0.0);
	}

	/** True when sunlight is strong enough for undead to burn. */
	public static boolean isSunBurnTime(Level level, double x, double z) {
		return isDay(level, x, z);
	}

	public static boolean isSunBurnTime(Entity entity) {
		return isDay(entity);
	}

	public static boolean isSunBurnTime(Level level, double x) {
		return isSunBurnTime(level, x, 0.0);
	}

	/**
	 * 0..1 sun strength for an entity (longitude day curve × latitude altitude + polar rules).
	 */
	public static float sunExposure(Entity entity) {
		Level level = entity.level();
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return equatorialSunBrightness(level, entity.getX(), entity.getZ());
		}
		double continuousZ = continuousZFor(entity);
		return sunExposureAt(level, entity.getX(), continuousZ);
	}

	/**
	 * 0..1 sun strength at block coordinates.
	 * <p>
	 * Without an entity track uses wrapped Z as continuous Z. Prefer
	 * {@link #sunExposure(Entity)} for movers so polar tip stays continuous.
	 */
	public static float sunExposure(Level level, double x, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return equatorialSunBrightness(level, x, z);
		}
		double continuousZ = wrapZ(level, z);
		return sunExposureAt(level, x, continuousZ);
	}

	/**
	 * Day/night from longitude day curve × continuous tip altitude.
	 * {@code cos(tipTurns·π/2)} is signed: 1 at near equator, 0 at poles, −1 at far equator
	 * (opposite day) — without shifting orbital phase θ.
	 */
	public static float sunExposureAt(Level level, double x, double continuousZ) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return brightnessFromTicks(localTime(level, x));
		}
		double period = periodBlocksZ(level);
		double quarter = MeridianTracker.quarterPeriod(period);
		double tipTurns = MeridianTracker.tipTurns(continuousZ, quarter);
		float dayCurve = brightnessFromTicks(localTime(level, x));
		float spin = dayCurve * 2.0f - 1.0f;
		float altitude = (float) Math.cos(tipTurns * Math.PI * 0.5);
		float equatorial = 0.5f + 0.5f * spin * altitude;

		double lat = MeridianTracker.latitude(continuousZ, quarter);
		float absLat = (float) Math.abs(lat);
		float warmth = SeasonAuthority.warmthAtLatitude(level, lat);

		float dilateBlend = Mth.clamp((absLat - POLAR_DILATION_START) / (1.0f - POLAR_DILATION_START), 0.0f, 1.0f);
		if (dilateBlend > 0.0f) {
			equatorial = dilateDayNight(equatorial, warmth, dilateBlend);
		}

		float polarBlend = Mth.clamp((absLat - POLAR_EXPOSURE_START) / (1.0f - POLAR_EXPOSURE_START), 0.0f, 1.0f);
		if (polarBlend <= 0.0f) {
			return equatorial;
		}
		float polar = Mth.clamp(0.5f + warmth * 0.65f, 0.0f, 1.0f);
		return Mth.lerp(polarBlend, equatorial, polar);
	}

	/**
	 * Stretch local winter nights / summer days near the poles without stopping the orbit.
	 * Winter ({@code warmth < 0}): darken more of the day curve. Summer: brighten more.
	 */
	private static float dilateDayNight(float brightness, float warmth, float blend) {
		float strength = blend * Math.abs(warmth);
		if (strength < 0.01f) {
			return brightness;
		}
		double exponent = 1.0 + strength * 1.6;
		if (warmth < 0.0f) {
			return (float) Math.pow(brightness, exponent);
		}
		return 1.0f - (float) Math.pow(1.0 - brightness, exponent);
	}

	private static float equatorialSunBrightness(Level level, double x, double z) {
		return brightnessFromTicks(localTime(level, x));
	}

	private static float brightnessFromTicks(long ticks) {
		float angle = celestialAngleFromTicks(ticks);
		return (float) Mth.clamp(Math.cos(angle * Math.PI * 2.0) * 2.0 + 0.5, 0.0, 1.0);
	}

	public static float sunExposure(Level level, double x) {
		return sunExposure(level, x, 0.0);
	}

	/**
	 * Monster surface/cave darkness using local day at {@code pos} rather than global sky light.
	 */
	public static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
		Level world = level.getLevel();
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(world)) {
			return net.minecraft.world.entity.monster.Monster.isDarkEnoughToSpawn(level, pos, random);
		}

		boolean localDay = isDay(world, pos.getX(), pos.getZ());
		boolean openSky = level.canSeeSky(pos);

		if (localDay && openSky) {
			return false;
		}

		DimensionType dimension = level.dimensionType();
		int blockLightLimit = dimension.monsterSpawnBlockLightLimit();
		if (blockLightLimit < 15 && level.getBrightness(LightLayer.BLOCK, pos) > blockLightLimit) {
			return false;
		}

		int brightness = spawnBrightness(level, pos, localDay, openSky);
		return brightness <= dimension.monsterSpawnLightTest().sample(random);
	}

	private static int spawnBrightness(ServerLevelAccessor level, BlockPos pos, boolean localDay, boolean openSky) {
		if (!localDay && openSky) {
			int block = level.getBrightness(LightLayer.BLOCK, pos);
			return level.getLevel().isThundering() ? Math.max(block, 10) : block;
		}
		return level.getLevel().isThundering()
				? level.getMaxLocalRawBrightness(pos, 10)
				: level.getMaxLocalRawBrightness(pos);
	}

	public static long ticksUntilLocalDawn(Level level, double sleeperX) {
		long local = localTime(level, sleeperX);
		return Math.floorMod(-local, DAY_LENGTH);
	}

	public static long ticksUntilLocalDusk(Level level, double sleeperX) {
		long local = localTime(level, sleeperX);
		if (local <= 12000L) {
			return 12000L - local;
		}
		return DAY_LENGTH - local + 12000L;
	}

	public static long ticksUntilLocalSleepTarget(Level level, double sleeperX) {
		return isDay(level, sleeperX, 0.0)
				? ticksUntilLocalDusk(level, sleeperX)
				: ticksUntilLocalDawn(level, sleeperX);
	}

	public static boolean holdsBackSunlitGrowth(Level level, BlockPos lightPos) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return false;
		}
		if (isDay(level, lightPos.getX(), lightPos.getZ())) {
			return false;
		}
		return level.getBrightness(LightLayer.BLOCK, lightPos) < GROWTH_LIGHT_LEVEL;
	}

	/**
	 * Sky / ambient lighting clock (0..1) — not for sun/moon disc spin.
	 * <p>
	 * Uses the smooth X-local solar phase (vanilla dawn/dusk), then adds a tip-derived
	 * half-day shift so the far equator is opposite day. Prefer
	 * {@link #skyLightingCelestialAngleAt} for client sky brightness so mid-meridian
	 * spots where the tipped sun is already up are not left in artificial dusk.
	 */
	public static float lightingCelestialAngleAt(Level level, double x, double continuousZ) {
		float phase = celestialAngle(level, x);
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return phase;
		}
		double period = periodBlocksZ(level);
		double quarter = MeridianTracker.quarterPeriod(period);
		double tipTurns = MeridianTracker.tipTurns(continuousZ, quarter);
		// altitude +1 near equator, 0 at poles, −1 far equator → shift 0 … 0.25 … 0.5 day
		float altitude = (float) Math.cos(tipTurns * Math.PI * 0.5);
		float shift = 0.25f * (1.0f - altitude);
		float lighting = phase + shift;
		lighting -= (float) Math.floor(lighting);
		return lighting;
	}

	/**
	 * Map sun strength to a celestial angle whose vanilla {@code cos(θ·2π)} day factor
	 * matches {@code exposure}. Used when tip-altitude says the sun is up more strongly
	 * than the phase+shift clock (mid-meridian / far-face mismatch).
	 */
	public static float lightingCelestialAngleFromExposure(float exposure) {
		float c = Mth.clamp(exposure * 2.0f - 1.0f, -1.0f, 1.0f);
		return (float) (Math.acos(c) / (Math.PI * 2.0));
	}

	private static float dayFactorFromAngle(float celestialAngle) {
		return Mth.clamp((float) (Math.cos(celestialAngle * Math.PI * 2.0) * 2.0 + 0.5), 0.0f, 1.0f);
	}

	/**
	 * Client sky clock: keep phase+shift for smooth dawn/dusk, but brighten to match
	 * {@link #sunExposureAt} when the tipped sun is already above the horizon.
	 */
	public static float skyLightingCelestialAngleAt(Level level, double x, double continuousZ) {
		float phaseLighting = lightingCelestialAngleAt(level, x, continuousZ);
		float phaseDay = dayFactorFromAngle(phaseLighting);
		float sunDay = sunExposureAt(level, x, continuousZ);
		if (sunDay > phaseDay + 0.02f) {
			return lightingCelestialAngleFromExposure(sunDay);
		}
		return phaseLighting;
	}

	public static float skyLightingCelestialAngle(Entity entity) {
		return skyLightingCelestialAngleAt(entity.level(), entity.getX(), continuousZFor(entity));
	}

	/** Day factor 0..1 matching {@link #skyLightingCelestialAngleAt}. */
	public static float skyDayFactorAt(Level level, double x, double continuousZ) {
		return Math.max(dayFactorFromAngle(lightingCelestialAngleAt(level, x, continuousZ)), sunExposureAt(level, x, continuousZ));
	}

	public static float skyDayFactor(Entity entity) {
		return skyDayFactorAt(entity.level(), entity.getX(), continuousZFor(entity));
	}

	/** Lighting clock at wrapped block Z (no path unwrap). Prefer entity continuous Z for movers. */
	public static float lightingCelestialAngle(Level level, double x, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return celestialAngle(level, x);
		}
		return lightingCelestialAngleAt(level, x, wrapZ(level, z));
	}

	public static float lightingCelestialAngle(Entity entity) {
		return lightingCelestialAngleAt(entity.level(), entity.getX(), continuousZFor(entity));
	}

	/** Shared world celestial angle 0..1 from {@code dayTime} (no longitude offset). */
	public static float worldCelestialAngle(Level level) {
		return celestialAngleFromTicks(Math.floorMod(globalTime(level), DAY_LENGTH));
	}

	/**
	 * Celestial angle 0..1 for sky rendering at longitude {@code x} (near-face / no meridian).
	 */
	public static float celestialAngle(Level level, double x) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return worldCelestialAngle(level);
		}
		return celestialAngleFromTicks(localTime(level, x));
	}

	/**
	 * Observer celestial angle including meridian longitude from wrapped {@code z}
	 * (no path unwrap). Movers should use {@link #observerCelestialAngle(Entity)}.
	 */
	public static float celestialAngle(Level level, double x, double z) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return worldCelestialAngle(level);
		}
		return observerCelestialAngle(level, x, wrapZ(level, z));
	}

	/**
	 * Sky tip from unwrapped meridian progress (quarter-period units).
	 * Advances continuously so N/S travel does not pendulum-fold the path:
	 * {@code 0 → 90° → 180° → 270° → 360°} over a full meridian loop.
	 * <ul>
	 *   <li>{@code tipTurns = 0} ({@code Z=0}): near equator</li>
	 *   <li>{@code 1} ({@code Z=C/2}): south pole — ring on horizon</li>
	 *   <li>{@code 2} ({@code Z=C}): far equator — tip ~180° (other face)</li>
	 *   <li>{@code 3} ({@code Z=3C/2}): north pole</li>
	 *   <li>{@code 4} ({@code Z=2C=P}): home</li>
	 * </ul>
	 * Day phase uses meridian-longitude separately ({@code tipTurns × ¼} via
	 * {@link #observerLocalTimeTicks}). Do not also fold tip back to 0 at the far
	 * equator — that reintroduces the pendulum and wrong lowering direction.
	 *
	 * @param quarterPeriod equator→pole distance ({@code P/4})
	 */
	public static float celestialTiltDegrees(Level level, double continuousZ, double quarterPeriod) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 0.0f;
		}
		float observerTip = (float) (MeridianTracker.tipTurns(continuousZ, quarterPeriod) * POLE_TIP_DEGREES);
		return observerTip + ORBITAL_OBLIQUITY_DEGREES;
	}

	/**
	 * @deprecated Folded latitude tips bounce 0→90→0 (pendulum). Use
	 * {@link #celestialTiltDegrees(Level, double, double)} with continuous Z.
	 */
	@Deprecated
	public static float celestialTiltDegrees(Level level, double latitude) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level)) {
			return 0.0f;
		}
		double lat = Mth.clamp(latitude, -1.0, 1.0);
		return (float) (lat * POLE_TIP_DEGREES) + ORBITAL_OBLIQUITY_DEGREES;
	}

	/**
	 * @deprecated Prefer {@link #celestialTiltDegrees(Level, double, double)}.
	 */
	@Deprecated
	public static float celestialTiltDegreesFromWrappedZ(Level level, double z) {
		return celestialTiltDegrees(level, SeasonAuthority.latitude(level, z));
	}

	private static float celestialAngleFromTicks(long t) {
		double d = (t / (double) DAY_LENGTH) - 0.25;
		if (d < 0) {
			d += 1.0;
		}
		double smoothed = 1.0 - (Math.cos(d * Math.PI) + 1.0) / 2.0;
		return (float) (d * 2.0 + smoothed) / 3.0f;
	}
}
