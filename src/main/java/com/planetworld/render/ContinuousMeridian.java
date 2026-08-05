package com.planetworld.render;

import com.planetworld.time.MeridianTracker;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client facade over {@link MeridianTracker} for the local player.
 * Prefer {@link MeridianTracker} directly for new code (common / server).
 */
@OnlyIn(Dist.CLIENT)
public final class ContinuousMeridian {
	private ContinuousMeridian() {
	}

	public static void reset() {
		MeridianTracker.resetClient();
	}

	public static double continuousZ(double wrappedZ, double period) {
		return MeridianTracker.continuousZ(MeridianTracker.CLIENT_OBSERVER, wrappedZ, period);
	}

	public static double latitude(double continuousZ, double quarterPeriod) {
		return MeridianTracker.latitude(continuousZ, quarterPeriod);
	}

	static double triangleLatitude(double x) {
		return MeridianTracker.triangleLatitude(x);
	}
}
