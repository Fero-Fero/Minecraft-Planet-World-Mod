package com.planetworld.worldgen.terralith;

/**
 * Earth-band tags for Terralith surface placement on Realism worlds.
 */
public enum TerralithClimateBand {
	/** Far north (−Z): ice, snow, glacial. */
	POLAR(-0.95f, -0.55f, 0.40f),
	/** Cold temperate / taiga belt. */
	BOREAL(-0.75f, -0.20f, 0.45f),
	/** Mid latitudes. */
	TEMPERATE(-0.40f, 0.40f, 0.45f),
	/** Warm humid (bamboo / sakura / lush). */
	HUMID_SUBTROPICAL(0.15f, 0.60f, 0.45f),
	/** Dry belt. */
	ARID(0.10f, 0.55f, 0.40f),
	/** Hot wet south (+Z). */
	TROPICAL(0.40f, 0.95f, 0.45f),
	/** Peaks / massifs — mid lat inland. */
	ALPINE(-0.50f, 0.50f, 0.50f),
	/** Beaches / warm rivers — thinner land. */
	COASTAL(-0.90f, 0.90f, 0.18f),
	/** Underground only — not surface-seeded. */
	CAVE(0.0f, 0.0f, 1.0f),
	/** Fantasy / skylands — optional. */
	FANTASY(-0.50f, 0.50f, 0.45f);

	/** Preferred lat = z / halfPeriod, clamped when placing seeds. */
	public final float latMin;
	public final float latMax;
	/** Minimum landFactor for inland bands; coastal uses as soft floor. */
	public final float minLand;

	TerralithClimateBand(float latMin, float latMax, float minLand) {
		this.latMin = latMin;
		this.latMax = latMax;
		this.minLand = minLand;
	}

	public boolean isSurfaceSeeded() {
		return this != CAVE;
	}

	public boolean isFantasy() {
		return this == FANTASY;
	}
}
