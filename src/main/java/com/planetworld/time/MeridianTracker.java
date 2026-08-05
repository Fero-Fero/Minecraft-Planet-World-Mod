package com.planetworld.time;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Path-dependent meridian unwrap across the torus seam, plus globe latitude.
 * <p>
 * Full wrap period {@code P = 2C} (UI size {@code C}) is one meridian loop:
 * poles at {@code P/4}, far equator at {@code P/2}, home at {@code P}.
 * Sky tip uses unwrapped {@link #tipTurns} (quarter units) so N/S travel rotates
 * continuously. {@link #latitude} is the triangle fold for climate / polar gameplay.
 * Do not shift θ by far-face antipode — that fights continuous tip at ~180°.
 */
public final class MeridianTracker {
	/** Client local-player track when UUID is not yet convenient. */
	public static final UUID CLIENT_OBSERVER = new UUID(0L, 1L);

	private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();

	private MeridianTracker() {
	}

	public static void resetAll() {
		TRACKS.clear();
	}

	public static void reset(UUID id) {
		if (id != null) {
			TRACKS.remove(id);
		}
	}

	public static void resetClient() {
		TRACKS.remove(CLIENT_OBSERVER);
	}

	/** Equator→pole distance: {@code P/4 = C/2}. */
	public static double quarterPeriod(double period) {
		return period * 0.25;
	}

	/** Wrap radius / far-equator distance: {@code P/2 = C}. */
	public static double halfPeriod(double period) {
		return period * 0.5;
	}

	/**
	 * Continuously unwrapped Z for {@code id}. When wrapped Z jumps by ≈±period at the
	 * torus seam (far equator), accumulate an offset so the meridian does not teleport.
	 */
	public static double continuousZ(UUID id, double wrappedZ, double period) {
		if (id == null || !(period > 1.0e-3) || Double.isNaN(wrappedZ)) {
			return wrappedZ;
		}
		Track track = TRACKS.computeIfAbsent(id, ignored -> new Track());
		synchronized (track) {
			track.lastTouchMs = System.currentTimeMillis();
			if (Double.isNaN(track.lastWrappedZ)) {
				track.lastWrappedZ = wrappedZ;
				return wrappedZ + track.unwrapOffset;
			}
			double dz = wrappedZ - track.lastWrappedZ;
			double half = halfPeriod(period);
			if (dz > half) {
				track.unwrapOffset -= period;
			} else if (dz < -half) {
				track.unwrapOffset += period;
			}
			track.lastWrappedZ = wrappedZ;
			return wrappedZ + track.unwrapOffset;
		}
	}

	/**
	 * Unwrapped meridian progress in quarter-period units (…, −1, 0, 1, 2, …).
	 * Quarter = {@code C/2}: pole at 1 ({@code Z=C/2}), far equator at 2 ({@code Z=C}),
	 * home at 4 ({@code Z=2C=P}). Sky tip = {@code +tipTurns × 90°}.
	 * Far-face day uses tip altitude / ~180° tip — not {@code tipTurns × ¼} on θ.
	 */
	public static double tipTurns(double continuousZ, double quarterPeriod) {
		if (!(quarterPeriod > 1.0e-3)) {
			return 0.0;
		}
		return continuousZ / quarterPeriod;
	}

	/** Tip turns from full period: {@code continuousZ / (P/4)}. */
	public static double tipTurnsForPeriod(double continuousZ, double period) {
		return tipTurns(continuousZ, quarterPeriod(period));
	}

	/** Triangle parameter in {@code [0, 4)} — folded geographic phase. */
	public static double meridianPhase(double continuousZ, double quarterPeriod) {
		double t = tipTurns(continuousZ, quarterPeriod) % 4.0;
		if (t < 0.0) {
			t += 4.0;
		}
		return t;
	}

	/**
	 * True on the far face (other side of the planet along the meridian):
	 * south pole → opposite equator → north pole ({@code tipTurns} in {@code (1, 3)}).
	 */
	public static boolean onFarFace(double continuousZ, double quarterPeriod) {
		double t = meridianPhase(continuousZ, quarterPeriod);
		return t > 1.0 && t < 3.0;
	}

	/**
	 * @deprecated Antipode-on-θ rearranges the orbit. Always returns 0.
	 */
	@Deprecated
	public static float antipodeDayOffset(double continuousZ, double quarterPeriod) {
		return 0.0f;
	}

	/**
	 * Latitude in {@code [-1, 1]} along a continuous meridian (triangle fold).
	 * Equator {@code 0}, south pole {@code +1} at {@code tipTurns=1}, north {@code -1};
	 * past a pole eases back toward the far/near equator.
	 *
	 * @param quarterPeriod equator→pole distance ({@code P/4})
	 */
	public static double latitude(double continuousZ, double quarterPeriod) {
		if (!(quarterPeriod > 1.0e-3)) {
			return 0.0;
		}
		return triangleLatitude(continuousZ / quarterPeriod);
	}

	/** Latitude from full wrap period. */
	public static double latitudeForPeriod(double continuousZ, double period) {
		return latitude(continuousZ, quarterPeriod(period));
	}

	/**
	 * Period-4 triangle in tip-turn units: {@code 0 → 1 → 0 → -1 → 0}.
	 */
	public static double triangleLatitude(double tipTurns) {
		double t = tipTurns % 4.0;
		if (t < 0.0) {
			t += 4.0;
		}
		if (t <= 1.0) {
			return t;
		}
		if (t <= 3.0) {
			return 2.0 - t;
		}
		return t - 4.0;
	}

	/** Drop tracks that have not been touched (optional hygiene). */
	public static void pruneStale(long maxIdleMs) {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, Track>> it = TRACKS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Track> e = it.next();
			if (now - e.getValue().lastTouchMs > maxIdleMs) {
				it.remove();
			}
		}
	}

	private static final class Track {
		double unwrapOffset;
		double lastWrappedZ = Double.NaN;
		long lastTouchMs = System.currentTimeMillis();
	}
}
