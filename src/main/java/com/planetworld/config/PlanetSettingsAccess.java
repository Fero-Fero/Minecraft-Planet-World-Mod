package com.planetworld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.planetworld.PlanetWorld;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves active planet settings: synced/loaded world data, then pending create-world choices, then config defaults.
 * Pending choices are also written to disk so they survive the create-world handoff.
 */
public final class PlanetSettingsAccess {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PENDING_FILE = FMLPaths.GAMEDIR.get().resolve("planetworld-pending.json");

    private static volatile PlanetSettings active;
    private static volatile PlanetSettings pending;

    private PlanetSettingsAccess() {
    }

    public static PlanetSettings get() {
        PlanetSettings current = active;
        if (current != null) {
            return current;
        }
        PlanetSettings create = pending != null ? pending : readPendingFile();
        if (create != null) {
            return create;
        }
        return PlanetSettings.defaults();
    }

    public static void setActive(@Nullable PlanetSettings settings) {
        active = settings;
    }

    public static void clearActive() {
        active = null;
    }

    public static void setPending(@Nullable PlanetSettings settings) {
        pending = settings;
        if (settings != null) {
            writePendingFile(settings);
        } else {
            deletePendingFile();
        }
    }

    @Nullable
    public static PlanetSettings getPending() {
        if (pending != null) {
            return pending;
        }
        return readPendingFile();
    }

    public static void clearPending() {
        pending = null;
        deletePendingFile();
    }

    /** Consume pending settings for a brand-new world, or fall back to defaults. */
    public static PlanetSettings takePendingOrDefaults() {
        PlanetSettings create = pending != null ? pending : readPendingFile();
        pending = null;
        deletePendingFile();
        return create != null ? create : PlanetSettings.defaults();
    }

    private static void writePendingFile(PlanetSettings settings) {
        try {
            Files.createDirectories(PENDING_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(PENDING_FILE)) {
                GSON.toJson(settings, writer);
            }
        } catch (IOException ex) {
            PlanetWorld.LOGGER.warn("Failed to write pending planet settings: {}", ex.toString());
        }
    }

    @Nullable
    private static PlanetSettings readPendingFile() {
        if (!Files.isRegularFile(PENDING_FILE)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(PENDING_FILE)) {
            return GSON.fromJson(reader, PlanetSettings.class);
        } catch (Exception ex) {
            PlanetWorld.LOGGER.warn("Failed to read pending planet settings: {}", ex.toString());
            return null;
        }
    }

    private static void deletePendingFile() {
        try {
            Files.deleteIfExists(PENDING_FILE);
        } catch (IOException ignored) {
        }
    }
}
