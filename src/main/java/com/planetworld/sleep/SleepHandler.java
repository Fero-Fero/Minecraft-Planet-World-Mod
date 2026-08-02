package com.planetworld.sleep;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.time.LocalTime;
import com.planetworld.wrap.WrapMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * On a wrapped planet, beds follow longitude, not the global clock.
 * <p>
 * Sleep is always allowed when the only vanilla objection is “wrong time of day” — somewhere on the
 * torus it is night, and sleeping skips this longitude to the next half-cycle (local day → dusk,
 * local night → dawn). Other players jump by the same global delta.
 */
public final class SleepHandler {
	private SleepHandler() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onCanSleep(CanPlayerSleepEvent event) {
		if (!PlanetWorldConfig.enableLocalizedTime()) {
			return;
		}
		ServerPlayer player = event.getEntity();
		if (!WrapMath.isWrappedDimension(player.level())) {
			return;
		}

		// Global day/night is meaningless on the torus; only keep hard failures (obstructed, monsters, …).
		if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
			event.setProblem(null);
		}
	}

	/**
	 * Vanilla wakes sleepers when {@code Level.isDay()} becomes true. Keep them asleep while we are
	 * skipping from local day to dusk (or holding them through a global-day / local-night mismatch).
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onCanContinueSleeping(CanContinueSleepingEvent event) {
		if (!PlanetWorldConfig.enableLocalizedTime()) {
			return;
		}
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!WrapMath.isWrappedDimension(player.level())) {
			return;
		}
		if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
			event.setContinueSleeping(true);
		}
	}

	@SubscribeEvent
	public static void onSleepFinished(SleepFinishedTimeEvent event) {
		if (!PlanetWorldConfig.enableLocalizedTime()) {
			return;
		}
		if (!(event.getLevel() instanceof ServerLevel level) || !WrapMath.isWrappedDimension(level)) {
			return;
		}

		double avgX = level.players().stream()
				.filter(ServerPlayer::isSleeping)
				.mapToDouble(ServerPlayer::getX)
				.average()
				.orElseGet(() -> level.players().isEmpty() ? 0.0 : level.players().getFirst().getX());

		long advance = LocalTime.ticksUntilLocalSleepTarget(level, avgX);
		// Absolute wake-up dayTime (NeoForge rejects values below the current time).
		event.setTimeAddition(level.getDayTime() + advance);
	}
}
