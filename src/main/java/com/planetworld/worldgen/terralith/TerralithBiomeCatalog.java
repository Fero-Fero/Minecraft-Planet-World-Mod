package com.planetworld.worldgen.terralith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;

/**
 * Curated Terralith path → climate band map (from Stardust BiomeTweaker tags) plus
 * registry discovery for unknown {@code terralith:} biomes.
 */
public final class TerralithBiomeCatalog {
	public record Entry(String path, TerralithClimateBand band, boolean fantasy) {
	}

	private static final Map<String, Entry> BY_PATH = buildCatalog();

	private TerralithBiomeCatalog() {
	}

	public static Optional<Entry> lookup(String path) {
		Entry e = BY_PATH.get(normalize(path));
		return Optional.ofNullable(e);
	}

	public static TerralithClimateBand classifyPath(String path) {
		String p = normalize(path);
		Entry known = BY_PATH.get(p);
		if (known != null) {
			return known.band();
		}
		return heuristic(p);
	}

	/**
	 * Resolve holders present in the live biome registry for surface seeding.
	 */
	public static EnumMap<TerralithClimateBand, List<Holder<Biome>>> resolveSurfaceHolders(
			MinecraftServer server, boolean includeFantasy) {
		EnumMap<TerralithClimateBand, List<Holder<Biome>>> out = new EnumMap<>(TerralithClimateBand.class);
		for (TerralithClimateBand b : TerralithClimateBand.values()) {
			out.put(b, new ArrayList<>());
		}
		Registry<Biome> reg = server.registryAccess().registryOrThrow(Registries.BIOME);
		for (Holder.Reference<Biome> holder : reg.holders().toList()) {
			ResourceLocation id = holder.key().location();
			if (!TerralithCompat.NAMESPACE.equals(id.getNamespace())) {
				continue;
			}
			String path = id.getPath();
			TerralithClimateBand band = classifyPath(path);
			if (!band.isSurfaceSeeded()) {
				continue;
			}
			boolean fantasy = band.isFantasy();
			Entry known = BY_PATH.get(normalize(path));
			if (known != null) {
				fantasy = known.fantasy() || fantasy;
			}
			if (fantasy && !includeFantasy) {
				continue;
			}
			if (fantasy) {
				band = TerralithClimateBand.FANTASY;
			}
			out.get(band).add(holder);
		}
		for (TerralithClimateBand b : TerralithClimateBand.values()) {
			out.put(b, Collections.unmodifiableList(out.get(b)));
		}
		return out;
	}

	private static String normalize(String path) {
		return path == null ? "" : path.toLowerCase(Locale.ROOT);
	}

	private static TerralithClimateBand heuristic(String path) {
		if (path.startsWith("cave/") || path.contains("_caves") || path.contains("cave")) {
			return TerralithClimateBand.CAVE;
		}
		if (path.contains("skyland") || path.contains("moonlight") || path.contains("mirage")
				|| path.contains("amethyst") || path.contains("lavender") || path.contains("orchid")) {
			return TerralithClimateBand.FANTASY;
		}
		if (path.contains("beach") || path.contains("cliff") && (path.contains("basalt") || path.contains("granite") || path.contains("white"))
				|| path.contains("warm_river") || path.contains("river")) {
			return TerralithClimateBand.COASTAL;
		}
		if (path.contains("snow") || path.contains("wintry") || path.contains("glacial") || path.contains("frozen")
				|| path.contains("ice_") || path.contains("siberian") || path.contains("gravel_desert")) {
			return TerralithClimateBand.POLAR;
		}
		if (path.contains("desert") || path.contains("sand") || path.contains("mesa") || path.contains("bryce")
				|| path.contains("ancient_sand") || path.contains("arid") || path.contains("warped_mesa")
				|| path.contains("white_mesa") || path.contains("painted_mountain") || path.contains("red_oasis")
				|| path.contains("savanna_badland")) {
			return TerralithClimateBand.ARID;
		}
		if (path.contains("jungle") || path.contains("tropical") || path.contains("volcanic")
				|| path.contains("ashen_savanna")) {
			return TerralithClimateBand.TROPICAL;
		}
		if (path.contains("sakura") || path.contains("blooming") || path.contains("cloud_forest")
				|| path.contains("haze") || path.contains("bamboo")) {
			return TerralithClimateBand.HUMID_SUBTROPICAL;
		}
		if (path.contains("alpine") || path.contains("peak") || path.contains("yosemite") || path.contains("caldera")
				|| path.contains("emerald") || path.contains("rocky_mountain") || path.contains("scarlet")
				|| path.contains("stony_spire") || path.contains("windswept_spire") || path.contains("mountain")) {
			return TerralithClimateBand.ALPINE;
		}
		if (path.contains("taiga") || path.contains("shield") || path.contains("yellowstone")
				|| path.contains("forested_highland") || path.contains("birch_taiga") || path.contains("cold_shrub")) {
			return TerralithClimateBand.BOREAL;
		}
		return TerralithClimateBand.TEMPERATE;
	}

	private static Map<String, Entry> buildCatalog() {
		Map<String, Entry> m = new LinkedHashMap<>();
		// Fantasy / rare magical — optional surface seeds
		put(m, "alpha_islands", TerralithClimateBand.FANTASY, true);
		put(m, "alpha_islands_winter", TerralithClimateBand.FANTASY, true);
		put(m, "amethyst_canyon", TerralithClimateBand.FANTASY, true);
		put(m, "amethyst_rainforest", TerralithClimateBand.FANTASY, true);
		put(m, "lavender_forest", TerralithClimateBand.FANTASY, true);
		put(m, "lavender_valley", TerralithClimateBand.FANTASY, true);
		put(m, "mirage_isles", TerralithClimateBand.FANTASY, true);
		put(m, "moonlight_grove", TerralithClimateBand.FANTASY, true);
		put(m, "moonlight_valley", TerralithClimateBand.FANTASY, true);
		put(m, "orchid_swamp", TerralithClimateBand.FANTASY, true);
		put(m, "skylands", TerralithClimateBand.FANTASY, true);
		put(m, "skylands_autumn", TerralithClimateBand.FANTASY, true);
		put(m, "skylands_spring", TerralithClimateBand.FANTASY, true);
		put(m, "skylands_summer", TerralithClimateBand.FANTASY, true);
		put(m, "skylands_winter", TerralithClimateBand.FANTASY, true);

		// Polar
		put(m, "alpine_grove", TerralithClimateBand.POLAR, false);
		put(m, "alpine_grove_highlands", TerralithClimateBand.POLAR, false);
		put(m, "caldera", TerralithClimateBand.POLAR, false);
		put(m, "cold_shrubland", TerralithClimateBand.POLAR, false);
		put(m, "emerald_peaks", TerralithClimateBand.POLAR, false);
		put(m, "forested_highlands", TerralithClimateBand.POLAR, false);
		put(m, "frozen_cliffs", TerralithClimateBand.POLAR, false);
		put(m, "glacial_chasm", TerralithClimateBand.POLAR, false);
		put(m, "gravel_desert", TerralithClimateBand.POLAR, false);
		put(m, "ice_marsh", TerralithClimateBand.POLAR, false);
		put(m, "rocky_mountains", TerralithClimateBand.POLAR, false);
		put(m, "rocky_shrubland", TerralithClimateBand.POLAR, false);
		put(m, "scarlet_mountains", TerralithClimateBand.POLAR, false);
		put(m, "siberian_grove", TerralithClimateBand.POLAR, false);
		put(m, "siberian_taiga", TerralithClimateBand.POLAR, false);
		put(m, "snowy_badlands", TerralithClimateBand.POLAR, false);
		put(m, "snowy_maple_forest", TerralithClimateBand.POLAR, false);
		put(m, "snowy_shield", TerralithClimateBand.POLAR, false);
		put(m, "wintry_forest", TerralithClimateBand.POLAR, false);
		put(m, "wintry_lowlands", TerralithClimateBand.POLAR, false);
		put(m, "yosemite_lowlands", TerralithClimateBand.POLAR, false);

		// Boreal
		put(m, "birch_taiga", TerralithClimateBand.BOREAL, false);
		put(m, "cloud_forest", TerralithClimateBand.BOREAL, false);
		put(m, "highlands", TerralithClimateBand.BOREAL, false);
		put(m, "lush_valley", TerralithClimateBand.BOREAL, false);
		put(m, "mountain_steppe", TerralithClimateBand.BOREAL, false);
		put(m, "shield", TerralithClimateBand.BOREAL, false);
		put(m, "shield_clearing", TerralithClimateBand.BOREAL, false);
		put(m, "yellowstone", TerralithClimateBand.BOREAL, false);
		put(m, "yosemite_cliffs", TerralithClimateBand.BOREAL, false);

		// Temperate
		put(m, "blooming_plateau", TerralithClimateBand.TEMPERATE, false);
		put(m, "blooming_valley", TerralithClimateBand.TEMPERATE, false);
		put(m, "haze_mountain", TerralithClimateBand.TEMPERATE, false);
		put(m, "sakura_grove", TerralithClimateBand.HUMID_SUBTROPICAL, false);
		put(m, "sakura_valley", TerralithClimateBand.HUMID_SUBTROPICAL, false);
		put(m, "steppe", TerralithClimateBand.TEMPERATE, false);
		put(m, "temperate_highlands", TerralithClimateBand.TEMPERATE, false);
		put(m, "valley_clearing", TerralithClimateBand.TEMPERATE, false);

		// Arid
		put(m, "ancient_sands", TerralithClimateBand.ARID, false);
		put(m, "arid_highlands", TerralithClimateBand.ARID, false);
		put(m, "ashen_savanna", TerralithClimateBand.ARID, false);
		put(m, "brushland", TerralithClimateBand.ARID, false);
		put(m, "bryce_canyon", TerralithClimateBand.ARID, false);
		put(m, "desert_canyon", TerralithClimateBand.ARID, false);
		put(m, "desert_oasis", TerralithClimateBand.ARID, false);
		put(m, "desert_spires", TerralithClimateBand.ARID, false);
		put(m, "fractured_savanna", TerralithClimateBand.ARID, false);
		put(m, "hot_shrubland", TerralithClimateBand.ARID, false);
		put(m, "painted_mountains", TerralithClimateBand.ARID, false);
		put(m, "red_oasis", TerralithClimateBand.ARID, false);
		put(m, "sandstone_valley", TerralithClimateBand.ARID, false);
		put(m, "savanna_badlands", TerralithClimateBand.ARID, false);
		put(m, "savanna_slopes", TerralithClimateBand.ARID, false);
		put(m, "shrubland", TerralithClimateBand.ARID, false);
		put(m, "warped_mesa", TerralithClimateBand.ARID, false);
		put(m, "white_mesa", TerralithClimateBand.ARID, false);

		// Tropical
		put(m, "jungle_mountains", TerralithClimateBand.TROPICAL, false);
		put(m, "rocky_jungle", TerralithClimateBand.TROPICAL, false);
		put(m, "tropical_jungle", TerralithClimateBand.TROPICAL, false);
		put(m, "volcanic_crater", TerralithClimateBand.TROPICAL, false);
		put(m, "volcanic_peaks", TerralithClimateBand.TROPICAL, false);

		// Alpine (peaks / massifs)
		put(m, "stony_spires", TerralithClimateBand.ALPINE, false);
		put(m, "windswept_spires", TerralithClimateBand.ALPINE, false);

		// Coastal
		put(m, "basalt_cliffs", TerralithClimateBand.COASTAL, false);
		put(m, "granite_cliffs", TerralithClimateBand.COASTAL, false);
		put(m, "gravel_beach", TerralithClimateBand.COASTAL, false);
		put(m, "warm_river", TerralithClimateBand.COASTAL, false);
		put(m, "white_cliffs", TerralithClimateBand.COASTAL, false);

		// Caves — not surface-seeded
		putCave(m, "cave/andesite_caves");
		putCave(m, "cave/desert_caves");
		putCave(m, "cave/diorite_caves");
		putCave(m, "cave/frostfire_caves");
		putCave(m, "cave/fungal_caves");
		putCave(m, "cave/granite_caves");
		putCave(m, "cave/ice_caves");
		putCave(m, "cave/infested_caves");
		putCave(m, "cave/mantle_caves");
		putCave(m, "cave/thermal_caves");
		putCave(m, "cave/tuff_caves");

		return Collections.unmodifiableMap(m);
	}

	private static void put(Map<String, Entry> m, String path, TerralithClimateBand band, boolean fantasy) {
		m.put(path, new Entry(path, band, fantasy));
	}

	private static void putCave(Map<String, Entry> m, String path) {
		put(m, path, TerralithClimateBand.CAVE, false);
	}
}
