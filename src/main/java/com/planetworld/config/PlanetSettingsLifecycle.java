package com.planetworld.config;

import com.planetworld.PlanetWorld;
import com.planetworld.wrap.storage.TransformerRequests;
import com.planetworld.network.SyncPlanetSettingsPayload;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PlanetSettingsLifecycle {
	private static final SavedData.Factory<PlanetWorldSavedData> FACTORY =
			new SavedData.Factory<>(PlanetWorldSavedData::new, PlanetWorldSavedData::load);

	private PlanetSettingsLifecycle() {
	}

	/**
	 * Activates planet settings early (pending create-world or saved disk data) so
	 * Continents Large Biomes can apply when NoiseBasedChunkGenerator is constructed,
	 * then installs torus wrapping bounds for brand-new worlds.
	 */
	@SubscribeEvent
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		activateSettingsEarly(event.getServer());

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

	/**
	 * Prefer pending create-world settings; otherwise load overworld SavedData from disk
	 * so existing Continents worlds keep Large Biomes on reload.
	 */
	private static void activateSettingsEarly(MinecraftServer server) {
		PlanetSettings pending = PlanetSettingsAccess.getPending();
		if (pending != null) {
			PlanetSettingsAccess.setActive(pending);
			return;
		}
		Path path = server.getWorldPath(LevelResource.ROOT)
				.resolve("data")
				.resolve(PlanetWorldSavedData.ID + ".dat");
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			CompoundTag dataTag = root.contains("data") ? root.getCompound("data") : root;
			PlanetWorldSavedData loaded = PlanetWorldSavedData.load(dataTag, server.registryAccess());
			PlanetSettingsAccess.setActive(loaded.getSettings());
			PlanetWorld.LOGGER.info(
					"Activated planet settings from disk (style={}, circumference={})",
					loaded.getSettings().worldGenStyle(),
					loaded.getSettings().circumference()
			);
		} catch (Exception ex) {
			PlanetWorld.LOGGER.warn("Failed to preload planet settings from {}: {}", path, ex.toString());
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
					"Initialized planet settings for new world: circumference={}",
					data.getSettings().circumference()
			);
		} else {
			PlanetSettingsAccess.clearPending();
		}

		PlanetSettingsAccess.setActive(data.getSettings());
		PlanetWorld.LOGGER.info(
				"Active planet settings: style={}, continental={}, circumference={}",
				data.getSettings().worldGenStyle(),
				data.getSettings().isContinental(),
				data.getSettings().circumference()
		);

	@SubscribeEvent
	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		PacketDistributor.sendToPlayer(player, new SyncPlanetSettingsPayload(PlanetSettingsAccess.get()));
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		// Do not null noiseLevel here — chunk workers may still sample during quit.
		// Session clear happens in onServerStopping.
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		TransformerRequests.clearSessionState();
		PlanetSettingsAccess.clearActive();
		PlanetSettingsAccess.clearPending();
	}
}
