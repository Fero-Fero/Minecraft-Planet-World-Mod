package com.planetworld.debug;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Applies extra {@code dayTime} advancement when a debug time-speed multiplier is active.
 */
public final class TimeSpeedHandler {
	private TimeSpeedHandler() {
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		Level level = event.getLevel();
		if (level.isClientSide() || !(level instanceof ServerLevel server)) {
			return;
		}
		if (!level.dimension().equals(Level.OVERWORLD)) {
			return;
		}
		double extra = CelestialDebugState.accumulateTimeSpeed(level);
		if (extra <= 0.0) {
			return;
		}
		server.setDayTime(server.getDayTime() + (long) extra);
	}
}
