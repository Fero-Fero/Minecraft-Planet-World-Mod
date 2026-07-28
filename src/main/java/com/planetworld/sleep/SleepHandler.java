package com.planetworld.sleep;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.time.LocalTime;
import com.planetworld.wrap.WrapMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * Sleeping is gated on local night. When sleep finishes, GlobalServerTime advances
 * so the sleeper's LocalTime reaches dawn — everyone else jumps proportionally.
 */
public final class SleepHandler {
    private SleepHandler() {
    }

    @SubscribeEvent
    public static void onCanSleep(CanPlayerSleepEvent event) {
        if (!PlanetWorldConfig.enableLocalizedTime()) {
            return;
        }
        ServerPlayer player = event.getEntity();
        if (!WrapMath.isWrappedDimension(player.level())) {
            return;
        }

        boolean localDay = LocalTime.isDay(player.level(), player.getX());
        if (localDay) {
            if (event.getProblem() == null
                    || event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
                event.setProblem(Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
            }
        } else if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW) {
            // Global day rejected sleep, but this longitude is night
            event.setProblem(null);
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

        long advance = LocalTime.ticksUntilLocalDawn(level, avgX);
        event.setTimeAddition(level.getDayTime() + advance);
    }
}
