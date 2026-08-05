package com.planetworld.worldgen.terralith;

/**
 * Climate bands for Terralith surface seeds. Latitudes are absolute triangle latitude
 * (|φ| in {@code [0,1]}, poles at {@code |Z| = P/4}): both poles cold, equators tropical.
 */
public enum TerralithClimateBand {
	/** Both poles (|lat| high). */
	POLAR(0.62f, 0.96f, 0.28f),
	/** Cold temperate / taiga. */
	BOREAL(0.38f, 0.72f, 0.45f),
	/** Mid latitudes. */
	TEMPERATE(0.12f, 0.48f, 0.45f),
	/** Warm humid near subtropics. */
	HUMID_SUBTROPICAL(0.08f, 0.36f, 0.45f),
	/** Horse-latitude arid belts. */
	ARID(0.26f, 0.55f, 0.40f),
	/** Equatorial tropics. */
	TROPICAL(0.00f, 0.30f, 0.45f),
	/** Peaks / massifs — mid abs-lat inland. */
	ALPINE(0.15f, 0.60f, 0.50f),
	/** Beaches / warm rivers — thinner land. */
	COASTAL(0.00f, 0.90f, 0.18f),
	/** Underground only — not surface-seeded. */
	CAVE(0.0f, 0.0f, 1.0f),
	/** Fantasy / skylands — optional. */
	FANTASY(0.00f, 0.55f, 0.45f);

	/** Absolute triangle-latitude band; Z ≈ sign × absLat × (P/4). */
	public final float absLatMin;
	public final float absLatMax;
	/** Minimum landFactor for inland bands; coastal uses as soft floor. */
	public final float minLand;

	TerralithClimateBand(float absLatMin, float absLatMax, float minLand) {
		this.absLatMin = absLatMin;
		this.absLatMax = absLatMax;
		this.minLand = minLand;
	}

	public boolean isSurfaceSeeded() {
		return this != CAVE;
	}

	public boolean isFantasy() {
		return this == FANTASY;
	}
}
