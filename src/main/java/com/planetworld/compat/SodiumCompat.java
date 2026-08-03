package com.planetworld.compat;

import net.neoforged.fml.ModList;

/**
 * Runtime probes for Sodium / Iris family renderers.
 * <p>
 * Strategy A (see {@code SODIUM_COMPAT_PLAN.md}): Sodium replaces vanilla terrain
 * shaders, so Planet World's horizon curvature degrades gracefully (uniforms stay 0).
 * Wrap, local time, and local weather do not depend on those shaders.
 */
public final class SodiumCompat {
	private static final String[] SODIUM_IDS = {"sodium", "embeddium"};
	private static final String[] IRIS_IDS = {"iris", "oculus"};
	/** Reserved for future SCSS soft-dep (strategy B). */
	private static final String[] SCSS_IDS = {"sodiumcore", "sodium_core_shader_support", "scss"};

	private static volatile boolean curvatureTipShown;

	private SodiumCompat() {
	}

	public static boolean isSodiumLoaded() {
		return any(SODIUM_IDS);
	}

	public static boolean isIrisFamilyLoaded() {
		return any(IRIS_IDS);
	}

	/** Sodium Core Shader Support (or equivalent) — strategy B backend. */
	public static boolean isScssLoaded() {
		return any(SCSS_IDS);
	}

	/**
	 * Sodium (or Embeddium) owns the terrain mesh pipeline; vanilla
	 * {@code rendertype_*} curvature overrides do not run.
	 */
	public static boolean replacesTerrainShaders() {
		return isSodiumLoaded();
	}

	/**
	 * True when Planet World's distance-based Y drop is applied to terrain verts
	 * on the active renderer. Strategy A: only when Sodium is absent.
	 */
	public static boolean isTerrainCurvatureLive() {
		if (!replacesTerrainShaders()) {
			return true;
		}
		// Future: return true when SCSS / Iris gbuffers backend is wired.
		return isScssLoaded();
	}

	/** Vanilla {@code ShaderInstance} curvature uploads should be zero under Sodium without a backend. */
	public static boolean shouldApplyVanillaTerrainCurvature() {
		return isTerrainCurvatureLive();
	}

	/**
	 * Iris/Oculus often replace {@code LevelRenderer.renderSky}; pose-stack tilt is unsafe there.
	 * Local time via {@code getTimeOfDay} still applies.
	 */
	public static boolean shouldApplyVanillaSkyTilt() {
		return !isIrisFamilyLoaded();
	}

	public static boolean shouldShowCurvatureDegradeTip() {
		return replacesTerrainShaders() && !isTerrainCurvatureLive() && !curvatureTipShown;
	}

	public static void markCurvatureTipShown() {
		curvatureTipShown = true;
	}

	public static void resetSessionTips() {
		curvatureTipShown = false;
	}

	private static boolean any(String... ids) {
		ModList list = ModList.get();
		for (String id : ids) {
			if (list.isLoaded(id)) {
				return true;
			}
		}
		return false;
	}
}
