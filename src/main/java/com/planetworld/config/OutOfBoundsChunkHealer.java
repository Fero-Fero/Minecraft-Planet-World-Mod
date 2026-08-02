package com.planetworld.config;

import com.planetworld.PlanetWorld;
import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.options.WorldWrappingSettings;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes empty out-of-bounds region chunks left when wrap cancelled generation but still
 * promoted shells to disk. Those shells become permanent voids if the world is opened without
 * Planet World (and stay on disk after reinstall until purged).
 */
public final class OutOfBoundsChunkHealer {
	private static final Pattern REGION_FILE = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");

	private OutOfBoundsChunkHealer() {
	}

	public static void purgeEmptyOutOfBounds(ServerLevel level) {
		DimensionTransformer transformer = level.getTransformer();
		if (transformer == null || !transformer.isWrapped()) {
			return;
		}

		Path regionDir = regionDirectory(level);
		if (regionDir == null || !Files.isDirectory(regionDir)) {
			return;
		}

		int purged = 0;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "r.*.*.mca")) {
			for (Path regionPath : stream) {
				Matcher matcher = REGION_FILE.matcher(regionPath.getFileName().toString());
				if (!matcher.matches()) {
					continue;
				}
				int regionX = Integer.parseInt(matcher.group(1));
				int regionZ = Integer.parseInt(matcher.group(2));
				purged += purgeRegion(level, transformer, regionPath, regionX, regionZ);
			}
		} catch (IOException ex) {
			PlanetWorld.LOGGER.warn("Failed to scan region files for OOB void purge: {}", ex.toString());
			return;
		}

		if (purged > 0) {
			PlanetWorld.LOGGER.info(
					"Purged {} out-of-bounds empty chunk slot(s) in {} (void shells from wrap cancel / modless exploration)",
					purged,
					level.dimension().location()
			);
		}
	}

	private static Path regionDirectory(ServerLevel level) {
		Path root = level.getServer().getWorldPath(LevelResource.ROOT);
		var dim = level.dimension();
		if (dim.equals(net.minecraft.world.level.Level.OVERWORLD)) {
			return root.resolve("region");
		}
		if (dim.equals(net.minecraft.world.level.Level.NETHER)) {
			return root.resolve("DIM-1").resolve("region");
		}
		if (dim.equals(net.minecraft.world.level.Level.END)) {
			return root.resolve("DIM1").resolve("region");
		}
		return root.resolve("dimensions")
				.resolve(dim.location().getNamespace())
				.resolve(dim.location().getPath())
				.resolve("region");
	}

	private static int purgeRegion(
			ServerLevel level,
			DimensionTransformer transformer,
			Path regionPath,
			int regionX,
			int regionZ
	) {
		int purged = 0;
		RegionStorageInfo info = new RegionStorageInfo(
				level.dimension().location().toString(),
				level.dimension(),
				"chunk"
		);
		try (RegionFile regionFile = new RegionFile(info, regionPath, regionPath.getParent(), true)) {
			for (int localX = 0; localX < 32; localX++) {
				for (int localZ = 0; localZ < 32; localZ++) {
					ChunkPos pos = new ChunkPos((regionX << 5) + localX, (regionZ << 5) + localZ);
					if (!transformer.Chunk.isOver(pos)) {
						continue;
					}
					if (!regionFile.hasChunk(pos)) {
						continue;
					}
					regionFile.clear(pos);
					purged++;
				}
			}
		} catch (IOException ex) {
			PlanetWorld.LOGGER.warn("Failed to purge OOB chunks in {}: {}", regionPath, ex.toString());
		}
		return purged;
	}

	public static Optional<WorldWrappingSettings> readWrapBackup(CompoundTag tag) {
		if (!tag.contains("WrappingSettings")) {
			return Optional.empty();
		}
		return WorldWrappingSettings.CODEC
				.parse(NbtOps.INSTANCE, tag.get("WrappingSettings"))
				.result();
	}

	public static void writeWrapBackup(CompoundTag tag, WorldWrappingSettings settings) {
		if (settings == null) {
			return;
		}
		WorldWrappingSettings.CODEC.encodeStart(NbtOps.INSTANCE, settings).result().ifPresent(encoded ->
				tag.put("WrappingSettings", encoded)
		);
	}
}
