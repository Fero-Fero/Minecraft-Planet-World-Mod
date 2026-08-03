package com.planetworld.worldgen;

import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.wrap.storage.TransformerRequests;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Continents ≥2048: force a baseline of structures inside the wrap and paint
 * matching biome patches so they can actually generate.
 */
public final class GuaranteedStructures {
	public static final int WOODLAND_MANSION_SALT = 10387319;
	public static final int VILLAGE_SALT = 10387312;
	public static final int PILLAGER_OUTPOST_SALT = 165745296;
	public static final int DESERT_PYRAMID_SALT = 14357617;
	public static final int JUNGLE_PYRAMID_SALT = 14357619;
	public static final int OCEAN_MONUMENT_SALT = 10387313;
	/** Shared fortress + bastion structure-set salt. */
	public static final int NETHER_COMPLEXES_SALT = 30084232;

	private static final int BIOME_PATCH_RADIUS = 96;
	/** Monument biome check samples ~29 blocks; keep a generous deep-ocean disk. */
	private static final int MONUMENT_BIOME_RADIUS = 64;
	private static final int OUTPOST_COUNT = 5;
	private static final int DESERT_TEMPLE_COUNT = 3;
	private static final int JUNGLE_TEMPLE_COUNT = 3;
	private static final int OCEAN_MONUMENT_COUNT = 3;
	private static final int DUNGEON_COUNT = 10;
	private static final int NETHER_FORTRESS_COUNT = 3;
	private static final int NETHER_BASTION_COUNT = 4;

	private static final Object LOCK = new Object();
	private static volatile AnchorCache cache;

	private GuaranteedStructures() {
	}

	public static void clearCache() {
		cache = null;
	}

	public static boolean isForcedChunk(int salt, long worldSeed, int chunkX, int chunkZ) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return false;
		}
		Set<Long> set = layout(worldSeed).bySalt.get(salt);
		return set != null && set.contains(ChunkPos.asLong(chunkX, chunkZ));
	}

	/** @deprecated use {@link #isForcedChunk} */
	@Deprecated
	public static boolean isForcedMansionChunk(int salt, long worldSeed, int chunkX, int chunkZ) {
		return isForcedChunk(salt, worldSeed, chunkX, chunkZ);
	}

	@Nullable
	public static ChunkPos strongholdChunk(long worldSeed) {
		return layout(worldSeed).stronghold;
	}

	public static boolean isDungeonChunk(long worldSeed, int chunkX, int chunkZ) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return false;
		}
		return layout(worldSeed).dungeonChunks.contains(ChunkPos.asLong(chunkX, chunkZ));
	}

	public enum NetherComplex {
		FORTRESS,
		BASTION
	}

	/**
	 * Which nether complex must generate at this forced chunk (fortress vs bastion share one salt).
	 */
	@Nullable
	public static NetherComplex netherComplexAt(long worldSeed, int chunkX, int chunkZ) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return null;
		}
		AnchorCache layout = layout(worldSeed);
		long key = ChunkPos.asLong(chunkX, chunkZ);
		if (layout.fortressChunks.contains(key)) {
			return NetherComplex.FORTRESS;
		}
		if (layout.bastionChunks.contains(key)) {
			return NetherComplex.BASTION;
		}
		return null;
	}

	@Nullable
	public static Holder<Biome> biomeOverride(double blockX, double blockZ, ServerLevel level) {
		if (!ContinentalClimate.shouldScaleStructures()) {
			return null;
		}
		AnchorCache layout = layout(level.getSeed());
		double period = ContinentalClimate.periodBlocks();
		double half = period * 0.5;
		double x = ContinentalClimate.wrapToSignedHalf(blockX, period, half);
		double z = ContinentalClimate.wrapToSignedHalf(blockZ, period, half);

		Holder<Biome> best = null;
		double bestDist = Double.MAX_VALUE;
		for (BiomeAnchor anchor : layout.biomeAnchors) {
			double dx = Math.abs(x - anchor.x);
			double dz = Math.abs(z - anchor.z);
			dx = Math.min(dx, period - dx);
			dz = Math.min(dz, period - dz);
			double distSq = dx * dx + dz * dz;
			double r = anchor.radius;
			if (distSq <= r * r && distSq < bestDist) {
				bestDist = distSq;
				best = anchor.biome;
			}
		}
		return best;
	}

	private static AnchorCache layout(long worldSeed) {
		AnchorCache local = cache;
		if (local != null && local.seed == worldSeed) {
			if (!local.biomeAnchors.isEmpty() || biomeLookup() == null) {
				return local;
			}
		}
		synchronized (LOCK) {
			local = cache;
			if (local != null && local.seed == worldSeed) {
				if (!local.biomeAnchors.isEmpty() || biomeLookup() == null) {
					return local;
				}
			}
			local = build(worldSeed);
			cache = local;
			return local;
		}
	}

	private static AnchorCache build(long worldSeed) {
		double period = ContinentalClimate.periodBlocks();
		double half = period * 0.5;
		int chunkWidth = Math.max(16, PlanetWorldConfig.chunkWidth());
		HolderLookup.RegistryLookup<Biome> biomes = biomeLookup();

		Map<Integer, Set<Long>> bySalt = new HashMap<>();
		List<BiomeAnchor> biomeAnchors = new ArrayList<>();
		Set<Long> usedChunks = new HashSet<>();

		double[] dark = OverworldBiomeSeedPlacer.ensureDarkForestOnLand(worldSeed);
		ChunkPos mansion = toChunk(dark[0], dark[1]);
		addForced(bySalt, WOODLAND_MANSION_SALT, mansion, usedChunks);
		addBiome(biomeAnchors, biomes, Biomes.DARK_FOREST, dark[0], dark[1], BIOME_PATCH_RADIUS);

		addVillage(worldSeed, 0, Biomes.PLAINS, 0.0, 0.18, bySalt, biomeAnchors, biomes, usedChunks, period, half);
		addVillage(worldSeed, 1, Biomes.DESERT, 0.32, 0.52, bySalt, biomeAnchors, biomes, usedChunks, period, half);
		addVillage(worldSeed, 2, Biomes.SAVANNA, 0.22, 0.42, bySalt, biomeAnchors, biomes, usedChunks, period, half);
		addVillage(worldSeed, 3, Biomes.SNOWY_PLAINS, 0.68, 0.92, bySalt, biomeAnchors, biomes, usedChunks, period, half);
		addVillage(worldSeed, 4, Biomes.TAIGA, 0.42, 0.70, bySalt, biomeAnchors, biomes, usedChunks, period, half);

		for (int i = 0; i < OUTPOST_COUNT; i++) {
			double[] pos = findLandAbs(worldSeed, 0x0A070000L + i * 17L, 0.0, 0.35, period, half, usedChunks);
			ChunkPos chunk = toChunk(pos[0], pos[1]);
			addForced(bySalt, PILLAGER_OUTPOST_SALT, chunk, usedChunks);
			addBiome(biomeAnchors, biomes, Biomes.PLAINS, pos[0], pos[1], BIOME_PATCH_RADIUS);
		}

		for (int i = 0; i < DESERT_TEMPLE_COUNT; i++) {
			double[] pos = findLandAbs(worldSeed, 0xDE5E0000L + i * 19L, 0.28, 0.55, period, half, usedChunks);
			ChunkPos chunk = toChunk(pos[0], pos[1]);
			addForced(bySalt, DESERT_PYRAMID_SALT, chunk, usedChunks);
			addBiome(biomeAnchors, biomes, Biomes.DESERT, pos[0], pos[1], BIOME_PATCH_RADIUS);
		}

		for (int i = 0; i < JUNGLE_TEMPLE_COUNT; i++) {
			double[] pos = findLandAbs(worldSeed, 0x1061E000L + i * 23L, 0.0, 0.28, period, half, usedChunks);
			ChunkPos chunk = toChunk(pos[0], pos[1]);
			addForced(bySalt, JUNGLE_PYRAMID_SALT, chunk, usedChunks);
			addBiome(biomeAnchors, biomes, Biomes.JUNGLE, pos[0], pos[1], BIOME_PATCH_RADIUS);
		}

		for (int i = 0; i < OCEAN_MONUMENT_COUNT; i++) {
			double[] pos = findOceanAbs(worldSeed, 0x40CEA000L + i * 29L, 0.0, 0.40, period, half, usedChunks);
			ChunkPos chunk = toChunk(pos[0], pos[1]);
			addForced(bySalt, OCEAN_MONUMENT_SALT, chunk, usedChunks);
			addBiome(biomeAnchors, biomes, Biomes.DEEP_OCEAN, pos[0], pos[1], MONUMENT_BIOME_RADIUS);
		}

		Set<Long> dungeonChunks = new HashSet<>();
		for (int i = 0; i < DUNGEON_COUNT; i++) {
			double[] pos = findLandAbs(worldSeed, 0xD0116E00L + i * 37L, 0.0, 0.55, period, half, usedChunks);
			ChunkPos chunk = toChunk(pos[0], pos[1]);
			usedChunks.add(chunk.toLong());
			dungeonChunks.add(chunk.toLong());
		}

		// Land + plains biome so End Remastered / vanilla eyes can locate a real stronghold.
		double[] sh = findLandAbs(worldSeed, 0x5700A60DL, 0.0, 0.28, period, half, usedChunks);
		ChunkPos stronghold = toChunk(sh[0], sh[1]);
		usedChunks.add(stronghold.toLong());
		addBiome(biomeAnchors, biomes, Biomes.PLAINS, sh[0], sh[1], BIOME_PATCH_RADIUS);

		Set<Long> fortressChunks = new HashSet<>();
		Set<Long> bastionChunks = new HashSet<>();
		Set<Long> usedNether = new HashSet<>();
		int owFull = Math.max(16, PlanetWorldConfig.chunkWidth());
		int netherScale = com.planetworld.config.PlanetWrappingBounds.chooseNetherScale(owFull);
		int netherHalf = Math.max(2, owFull / (2 * netherScale));

		for (int i = 0; i < NETHER_FORTRESS_COUNT; i++) {
			ChunkPos chunk = findNetherChunk(worldSeed, 0xF0270000L + i * 41L, netherHalf, usedNether);
			addForced(bySalt, NETHER_COMPLEXES_SALT, chunk, usedNether);
			fortressChunks.add(chunk.toLong());
		}
		for (int i = 0; i < NETHER_BASTION_COUNT; i++) {
			ChunkPos chunk = findNetherChunk(worldSeed, 0xBA571000L + i * 43L, netherHalf, usedNether);
			addForced(bySalt, NETHER_COMPLEXES_SALT, chunk, usedNether);
			bastionChunks.add(chunk.toLong());
		}

		return new AnchorCache(
				worldSeed, bySalt, biomeAnchors, stronghold, dungeonChunks, fortressChunks, bastionChunks, chunkWidth
		);
	}

	private static void addVillage(
			long worldSeed,
			int index,
			ResourceKey<Biome> biome,
			double absLatMin,
			double absLatMax,
			Map<Integer, Set<Long>> bySalt,
			List<BiomeAnchor> biomeAnchors,
			@Nullable HolderLookup.RegistryLookup<Biome> biomes,
			Set<Long> usedChunks,
			double period,
			double half
	) {
		double[] pos = findLandAbs(worldSeed, 0xA111A6E0L + index * 31L, absLatMin, absLatMax, period, half, usedChunks);
		ChunkPos chunk = toChunk(pos[0], pos[1]);
		addForced(bySalt, VILLAGE_SALT, chunk, usedChunks);
		addBiome(biomeAnchors, biomes, biome, pos[0], pos[1], BIOME_PATCH_RADIUS);
	}

	private static void addForced(Map<Integer, Set<Long>> bySalt, int salt, ChunkPos chunk, Set<Long> used) {
		bySalt.computeIfAbsent(salt, s -> new HashSet<>()).add(chunk.toLong());
		used.add(chunk.toLong());
	}

	private static void addBiome(
			List<BiomeAnchor> anchors,
			@Nullable HolderLookup.RegistryLookup<Biome> biomes,
			ResourceKey<Biome> key,
			double x,
			double z,
			int radius
	) {
		if (biomes == null) {
			return;
		}
		biomes.get(key).ifPresent(h -> anchors.add(new BiomeAnchor(x, z, radius, h)));
	}

	private static double[] findLand(
			long worldSeed,
			long salt,
			double latMin,
			double latMax,
			double period,
			double half,
			Set<Long> usedChunks
	) {
		double[] best = new double[]{0.0, latMin * half};
		float bestLand = -1.0f;
		for (int attempt = 0; attempt < 64; attempt++) {
			long h = mix(worldSeed, salt, attempt);
			double cx = ((h >>> 9) & 0xFFFF) / 65535.0 * period - half;
			double t = ((h >>> 25) & 0xFFFF) / 65535.0;
			double lat = latMin + t * (latMax - latMin);
			double cz = lat * half;
			cx = ContinentalClimate.wrapToSignedHalf(cx, period, half);
			cz = ContinentalClimate.wrapToSignedHalf(cz, period, half);
			ChunkPos chunk = toChunk(cx, cz);
			if (usedChunks.contains(chunk.toLong())) {
				continue;
			}
			float land = ContinentalLandmask.landFactor(cx, cz, worldSeed);
			if (land > bestLand) {
				bestLand = land;
				best = new double[]{cx, cz};
				if (land >= 0.55f) {
					return best;
				}
			}
		}
		return best;
	}

	/** Sample land in absolute-latitude bands, randomly on either hemisphere. */
	private static double[] findLandAbs(
			long worldSeed,
			long salt,
			double absLatMin,
			double absLatMax,
			double period,
			double half,
			Set<Long> usedChunks
	) {
		double[] best = new double[]{0.0, absLatMin * half};
		float bestLand = -1.0f;
		for (int attempt = 0; attempt < 64; attempt++) {
			long h = mix(worldSeed, salt, attempt);
			double cx = ((h >>> 9) & 0xFFFF) / 65535.0 * period - half;
			double t = ((h >>> 25) & 0xFFFF) / 65535.0;
			double absLat = absLatMin + t * (absLatMax - absLatMin);
			double sign = ((h >>> 7) & 1L) == 0L ? 1.0 : -1.0;
			double cz = sign * absLat * half;
			cx = ContinentalClimate.wrapToSignedHalf(cx, period, half);
			cz = ContinentalClimate.wrapToSignedHalf(cz, period, half);
			ChunkPos chunk = toChunk(cx, cz);
			if (usedChunks.contains(chunk.toLong())) {
				continue;
			}
			float land = ContinentalLandmask.landFactor(cx, cz, worldSeed);
			if (land > bestLand) {
				bestLand = land;
				best = new double[]{cx, cz};
				if (land >= 0.55f) {
					return best;
				}
			}
		}
		return best;
	}

	private static double[] findOcean(
			long worldSeed,
			long salt,
			double latMin,
			double latMax,
			double period,
			double half,
			Set<Long> usedChunks
	) {
		double[] best = new double[]{0.0, latMin * half};
		float bestOcean = 2.0f;
		for (int attempt = 0; attempt < 64; attempt++) {
			long h = mix(worldSeed, salt, attempt);
			double cx = ((h >>> 9) & 0xFFFF) / 65535.0 * period - half;
			double t = ((h >>> 25) & 0xFFFF) / 65535.0;
			double lat = latMin + t * (latMax - latMin);
			double cz = lat * half;
			cx = ContinentalClimate.wrapToSignedHalf(cx, period, half);
			cz = ContinentalClimate.wrapToSignedHalf(cz, period, half);
			ChunkPos chunk = toChunk(cx, cz);
			if (usedChunks.contains(chunk.toLong())) {
				continue;
			}
			float land = ContinentalLandmask.landFactor(cx, cz, worldSeed);
			if (land < bestOcean) {
				bestOcean = land;
				best = new double[]{cx, cz};
				if (land <= 0.12f) {
					return best;
				}
			}
		}
		return best;
	}

	private static double[] findOceanAbs(
			long worldSeed,
			long salt,
			double absLatMin,
			double absLatMax,
			double period,
			double half,
			Set<Long> usedChunks
	) {
		double[] best = new double[]{0.0, absLatMin * half};
		float bestOcean = 2.0f;
		for (int attempt = 0; attempt < 64; attempt++) {
			long h = mix(worldSeed, salt, attempt);
			double cx = ((h >>> 9) & 0xFFFF) / 65535.0 * period - half;
			double t = ((h >>> 25) & 0xFFFF) / 65535.0;
			double absLat = absLatMin + t * (absLatMax - absLatMin);
			double sign = ((h >>> 7) & 1L) == 0L ? 1.0 : -1.0;
			double cz = sign * absLat * half;
			cx = ContinentalClimate.wrapToSignedHalf(cx, period, half);
			cz = ContinentalClimate.wrapToSignedHalf(cz, period, half);
			ChunkPos chunk = toChunk(cx, cz);
			if (usedChunks.contains(chunk.toLong())) {
				continue;
			}
			float land = ContinentalLandmask.landFactor(cx, cz, worldSeed);
			if (land < bestOcean) {
				bestOcean = land;
				best = new double[]{cx, cz};
				if (land <= 0.12f) {
					return best;
				}
			}
		}
		return best;
	}

	private static ChunkPos findNetherChunk(long worldSeed, long salt, int netherHalf, Set<Long> usedChunks) {
		int span = Math.max(2, netherHalf * 2);
		for (int attempt = 0; attempt < 64; attempt++) {
			long h = mix(worldSeed, salt, attempt);
			int cx = (int) ((((h >>> 9) & 0xFFFF) / 65535.0) * span) - netherHalf;
			int cz = (int) ((((h >>> 25) & 0xFFFF) / 65535.0) * span) - netherHalf;
			cx = Math.max(-netherHalf, Math.min(netherHalf - 1, cx));
			cz = Math.max(-netherHalf, Math.min(netherHalf - 1, cz));
			ChunkPos chunk = new ChunkPos(cx, cz);
			if (!usedChunks.contains(chunk.toLong())) {
				return chunk;
			}
		}
		return new ChunkPos(0, 0);
	}

	private static ChunkPos toChunk(double blockX, double blockZ) {
		return new ChunkPos(
				SectionPos.blockToSectionCoord((int) Math.floor(blockX)),
				SectionPos.blockToSectionCoord((int) Math.floor(blockZ))
		);
	}

	private static long mix(long a, long b, long c) {
		long x = a ^ (b + c * 0x9E3779B97F4A7C15L);
		x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
		x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
		return x ^ (x >>> 31);
	}

	@Nullable
	private static HolderLookup.RegistryLookup<Biome> biomeLookup() {
		ServerLevel level = TransformerRequests.noiseLevel;
		if (level == null) {
			return null;
		}
		return level.registryAccess().lookupOrThrow(Registries.BIOME);
	}

	private record BiomeAnchor(double x, double z, int radius, Holder<Biome> biome) {
	}

	private record AnchorCache(
			long seed,
			Map<Integer, Set<Long>> bySalt,
			List<BiomeAnchor> biomeAnchors,
			ChunkPos stronghold,
			Set<Long> dungeonChunks,
			Set<Long> fortressChunks,
			Set<Long> bastionChunks,
			int chunkWidth
	) {
	}
}
