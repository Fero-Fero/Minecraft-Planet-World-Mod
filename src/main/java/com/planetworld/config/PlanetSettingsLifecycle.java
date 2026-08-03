package com.planetworld.config;

import com.planetworld.PlanetWorld;
import com.planetworld.wrap.compat.create.CreateWrapMath;
import com.planetworld.wrap.storage.TransformerRequests;
import com.planetworld.network.SyncPlanetSettingsPayload;
import com.planetworld.wrap.accessors.WorldWrappingSettingsAccessor;
import com.planetworld.wrap.options.WorldWrappingSettings;
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
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Files;
import java.nio.file.Path;

public final class PlanetSettingsLifecycle {
	private static final SavedData.Factory<PlanetWorldSavedData> FACTORY =
			new SavedData.Factory<>(PlanetWorldSavedData::new, PlanetWorldSavedData::load);

	/** Wrap NBT preloaded from {@code planetworld_settings.dat} before levels exist. */
	private static WorldWrappingSettings preloadedWrapBackup;

	private PlanetSettingsLifecycle() {
	}

	/**
	 * Activates planet settings early (pending create-world or saved disk data) so
	 * Continents Large Biomes can apply when NoiseBasedChunkGenerator is constructed,
	 * then installs or restores torus wrapping bounds.
	 */
	@SubscribeEvent
	public static void onServerAboutToStart(ServerAboutToStartEvent event) {
		preloadedWrapBackup = null;
		activateSettingsEarly(event.getServer());

		if (!(event.getServer().getWorldData() instanceof PrimaryLevelData primary)) {
			return;
		}
		WorldWrappingSettingsAccessor accessor = (WorldWrappingSettingsAccessor) primary;
		WorldWrappingSettings existing = accessor.getWorldWrappingSettings();
		if (existing != null) {
			WorldWrappingSettings upgraded = PlanetWrappingBounds.withNetherIfMissing(existing);
			if (upgraded != existing) {
				accessor.setWorldWrappingSettings(upgraded);
				PlanetWorld.LOGGER.info(
						"Added Nether torus wrapping to existing world (End remains unwrapped)"
				);
			}
			return;
		}

		PlanetSettings planet = PlanetSettingsAccess.get();
		if (accessor.planetworld$loadedFromDisk()) {
			if (!hasPersistentPlanetData(event.getServer())) {
				PlanetWorld.LOGGER.warn(
						"Existing world has no Planet World settings on disk — wrapping stays disabled."
				);
				return;
			}
			WorldWrappingSettings restored = preloadedWrapBackup != null
					? PlanetWrappingBounds.withNetherIfMissing(preloadedWrapBackup)
					: PlanetWrappingBounds.create(planet.circumference());
			accessor.setWorldWrappingSettings(restored);
			PlanetWorld.LOGGER.info(
					"Restored torus wrapping from planetworld_settings.dat (circumference={}). "
							+ "level.dat WrappingSettings was missing — usually after opening the world without the mod.",
					planet.circumference()
			);
			return;
		}

		WorldWrappingSettings wrapping = PlanetWrappingBounds.create(planet.circumference());
		accessor.setWorldWrappingSettings(wrapping);
		int halfChunks = Math.max(1, planet.circumference() / 16);
		int netherScale = PlanetWrappingBounds.chooseNetherScale(halfChunks * 2);
		PlanetWorld.LOGGER.info(
				"Installed torus wrapping (Overworld+Nether, not End): circumference={} blocks, netherScale={}",
				planet.circumference(),
				netherScale
		);
	}

	private static boolean hasPersistentPlanetData(MinecraftServer server) {
		Path path = settingsPath(server);
		return Files.isRegularFile(path);
	}

	private static Path settingsPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT)
				.resolve("data")
				.resolve(PlanetWorldSavedData.ID + ".dat");
	}

	/**
	 * Prefer pending create-world settings; otherwise load overworld SavedData from disk
	 * so existing Continents worlds keep Large Biomes on reload and wrap can be restored.
	 */
	private static void activateSettingsEarly(MinecraftServer server) {
		PlanetSettings pending = PlanetSettingsAccess.getPending();
		if (pending != null) {
			PlanetSettingsAccess.setActive(pending);
			return;
		}
		Path path = settingsPath(server);
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			CompoundTag dataTag = root.contains("data") ? root.getCompound("data") : root;
			PlanetWorldSavedData loaded = PlanetWorldSavedData.load(dataTag, server.registryAccess());
			PlanetSettingsAccess.setActive(loaded.getSettings());
			preloadedWrapBackup = loaded.getWrappingBackup();
			PlanetWorld.LOGGER.info(
					"Activated planet settings from disk (style={}, circumference={}, wrapBackup={})",
					loaded.getSettings().worldGenStyle(),
					loaded.getSettings().circumference(),
					preloadedWrapBackup != null
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

		// Keep wrap backup in SavedData so uninstall cannot permanently strip torus bounds.
		if (level.getServer().getWorldData() instanceof PrimaryLevelData primary) {
			WorldWrappingSettings live = ((WorldWrappingSettingsAccessor) primary).getWorldWrappingSettings();
			if (live != null) {
				data.setWrappingBackup(live);
			}
		}

		PlanetWorld.LOGGER.info(
				"Active planet settings: style={}, realism={}, circumference={}",
				data.getSettings().worldGenStyle(),
				data.getSettings().isRealism(),
				data.getSettings().circumference()
		);
	}

	/**
	 * After all dimensions have transformers, delete OOB void shells from region files once.
	 */
	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		for (ServerLevel level : event.getServer().getAllLevels()) {
			OutOfBoundsChunkHealer.purgeEmptyOutOfBounds(level);
		}
		preloadedWrapBackup = null;
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
		// Do not null noiseLevel here — chunk workers may still sample during quit.
		// Session clear happens in onServerStopping.
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		TransformerRequests.clearSessionState();
		CreateWrapMath.clearHealSession();
		PlanetSettingsAccess.clearActive();
		PlanetSettingsAccess.clearPending();
		preloadedWrapBackup = null;
	}

	/**
	 * Worldgen keeps publishing the level it is sampling, and chunks are still being saved and closed
	 * while the server stops, so the clear above can be undone by a task that finishes late. Clearing
	 * again once everything is shut down is what actually stops a closed world from staying reachable.
	 */
	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		TransformerRequests.clearSessionState();
		CreateWrapMath.clearHealSession();
	}
}
