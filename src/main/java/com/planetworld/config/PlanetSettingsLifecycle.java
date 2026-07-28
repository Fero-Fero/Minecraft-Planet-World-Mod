package com.planetworld.config;

import com.planetworld.PlanetWorld;
import com.planetworld.network.SyncPlanetSettingsPayload;
import com.planetworld.wrap.accessors.WorldWrappingSettingsAccessor;
import com.planetworld.wrap.options.DimensionWrappingSettings;
import com.planetworld.wrap.options.WorldWrappingSettings;
import com.planetworld.wrap.options.WrappingOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

public final class PlanetSettingsLifecycle {
    private static final SavedData.Factory<PlanetWorldSavedData> FACTORY =
            new SavedData.Factory<>(PlanetWorldSavedData::new, PlanetWorldSavedData::load);

    private PlanetSettingsLifecycle() {
    }

    /**
     * Installs the torus wrapping bounds on the level data before any ServerLevel is
     * constructed, so the ported Circumnavigate core picks them up at level init.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!(event.getServer().getWorldData() instanceof PrimaryLevelData primary)) {
            return;
        }
        WorldWrappingSettingsAccessor accessor = (WorldWrappingSettingsAccessor) primary;
        if (accessor.getWorldWrappingSettings() != null) {
            return; // Existing wrapped world; bounds already loaded from level.dat.
        }
        if (accessor.planetworld$loadedFromDisk()) {
            PlanetWorld.LOGGER.warn(
                    "Existing world has no wrapping bounds in level.dat — world wrapping stays disabled for it.");
            return;
        }

        PlanetSettings planet = PlanetSettingsAccess.get();
        // Full torus width in blocks is 2*circumference, so:
        // fullChunks = (2*circumference)/16 = circumference/8
        // halfChunks = fullChunks/2 = circumference/16
        int halfChunks = Math.max(1, planet.circumference() / 16);
        DimensionWrappingSettings overworld = new DimensionWrappingSettings(
                -halfChunks, halfChunks, -halfChunks, halfChunks,
                DimensionWrappingSettings.Axis.X, 0, true);
        accessor.setWorldWrappingSettings(new WorldWrappingSettings(
                new WrappingOptions(1), Map.of(Level.OVERWORLD, overworld)));
        PlanetWorld.LOGGER.info(
                "Installed torus wrapping bounds for new world: circumference={} blocks ({} chunks per axis)",
                planet.circumference(), halfChunks * 2);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        DimensionDataStorage storage = level.getDataStorage();
        PlanetWorldSavedData data = storage.get(FACTORY, PlanetWorldSavedData.ID);
        if (data == null) {
            data = new PlanetWorldSavedData(PlanetSettingsAccess.takePendingOrDefaults());
            storage.set(PlanetWorldSavedData.ID, data);
            data.setDirty();
            PlanetWorld.LOGGER.info(
                    "Initialized planet settings for new world: circumference={}",
                    data.getSettings().circumference()
            );
        } else {
            PlanetSettingsAccess.clearPending();
        }

        PlanetSettingsAccess.setActive(data.getSettings());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new SyncPlanetSettingsPayload(PlanetSettingsAccess.get()));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PlanetSettingsAccess.clearActive();
        PlanetSettingsAccess.clearPending();
    }
}
