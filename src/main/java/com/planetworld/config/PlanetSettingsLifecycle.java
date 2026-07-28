package com.planetworld.config;

import com.planetworld.PlanetWorld;
import com.planetworld.network.SyncPlanetSettingsPayload;
import com.planetworld.wrap.storage.TransformerRequests;
import com.planetworld.worldgen.StructureCoverage;
import com.planetworld.wrap.accessors.WorldWrappingSettingsAccessor;
import com.planetworld.wrap.options.DimensionWrappingSettings;
import com.planetworld.wrap.options.WorldWrappingSettings;
import com.planetworld.wrap.options.WrappingOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PlanetSettingsLifecycle {
    private static final SavedData.Factory<PlanetWorldSavedData> FACTORY =
            new SavedData.Factory<>(PlanetWorldSavedData::new, PlanetWorldSavedData::load);

    private PlanetSettingsLifecycle() {
    }

    /**
     * Activates planet settings early (pending or disk) so complete-coverage structure clamps
     * see the correct style when generators are constructed, then installs wrap bounds
     * for brand-new worlds.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        activateSettingsEarly(server);

        if (!(server.getWorldData() instanceof PrimaryLevelData primary)) {
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
        int halfChunks = Math.max(1, planet.circumference() / 16);
        DimensionWrappingSettings overworld = new DimensionWrappingSettings(
                -halfChunks, halfChunks, -halfChunks, halfChunks,
                DimensionWrappingSettings.Axis.X, 0, true);
        accessor.setWorldWrappingSettings(new WorldWrappingSettings(
                new WrappingOptions(1), Map.of(Level.OVERWORLD, overworld)));
        PlanetWorld.LOGGER.info(
                "Installed torus wrapping bounds for new world: circumference={} blocks ({} chunks per axis), style={}",
                planet.circumference(), halfChunks * 2, planet.worldGenStyle());
    }

    private static void activateSettingsEarly(MinecraftServer server) {
        PlanetSettings pending = PlanetSettingsAccess.getPending();
        if (pending != null) {
            PlanetSettingsAccess.setActive(pending);
            return;
        }
        PlanetSettings fromDisk = tryReadSavedSettings(server);
        if (fromDisk != null) {
            PlanetSettingsAccess.setActive(fromDisk);
        }
    }

    private static PlanetSettings tryReadSavedSettings(MinecraftServer server) {
        Path dataFile = server.getWorldPath(LevelResource.ROOT)
                .resolve("data")
                .resolve(PlanetWorldSavedData.ID + ".dat");
        if (!Files.isRegularFile(dataFile)) {
            return null;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
            CompoundTag data = root.contains("data") ? root.getCompound("data") : root;
            return PlanetWorldSavedData.load(data, server.registryAccess()).getSettings();
        } catch (IOException | RuntimeException ex) {
            PlanetWorld.LOGGER.warn("Could not preload planet settings from {}: {}", dataFile, ex.toString());
            return null;
        }
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
                    "Initialized planet settings for new world: circumference={}, style={}",
                    data.getSettings().circumference(),
                    data.getSettings().worldGenStyle()
            );
        } else {
            PlanetSettingsAccess.clearPending();
        }

        PlanetSettingsAccess.setActive(data.getSettings());
        StructureCoverage.verifyCompleteCoverage(level);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new SyncPlanetSettingsPayload(PlanetSettingsAccess.get()));
    }


    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && TransformerRequests.noiseLevel == level) {
            TransformerRequests.noiseLevel = null;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TransformerRequests.clearSessionState();
        PlanetSettingsAccess.clearActive();
        PlanetSettingsAccess.clearPending();
    }
}

