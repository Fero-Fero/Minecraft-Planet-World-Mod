package com.planetworld.time;

import com.planetworld.config.PlanetWorldConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Keeps a lightweight server-side pulse so localized time consumers stay in sync.
 * Heavy lifting is done via mixins that query {@link LocalTime} per entity/block.
 */
public final class LocalTimeHandler {
    private LocalTimeHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!PlanetWorldConfig.enableLocalizedTime() || event.getLevel().isClientSide()) {
            return;
        }
        // Intentionally lightweight — mixins read LocalTime on demand.
    }
}
