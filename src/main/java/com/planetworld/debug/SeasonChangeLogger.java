package com.planetworld.debug;

import com.planetworld.season.SeasonAuthority;
import com.planetworld.wrap.WrapMath;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Broadcasts in-game chat when the northern season quarter changes (debug command).
 */
public final class SeasonChangeLogger {
	private SeasonChangeLogger() {
	}

	@SubscribeEvent
	public static void onLevelTick(LevelTickEvent.Post event) {
		Level level = event.getLevel();
		if (level.isClientSide() || !(level instanceof ServerLevel server)) {
			return;
		}
		if (!WrapMath.isWrappedDimension(level)) {
			return;
		}
		if (!CelestialDebugState.isSeasonLogging(level)) {
			return;
		}
		if (server.getGameTime() % 20 != 0) {
			return;
		}

		float progress = SeasonAuthority.northernSeasonProgress(level);
		SeasonQuarter current = SeasonQuarter.fromNorthernProgress(progress);
		SeasonQuarter last = CelestialDebugState.lastLoggedQuarter(level);
		if (current == last) {
			return;
		}
		CelestialDebugState.setLastLoggedQuarter(level, current);

		Component message = Component.translatable(
				"planetworld.command.season.changed",
				current.displayName(),
				String.format("%.3f", progress),
				String.format("%.2f", SeasonAuthority.northernWarmth(level))
		);
		for (ServerPlayer player : server.players()) {
			player.sendSystemMessage(message);
		}
	}
}
