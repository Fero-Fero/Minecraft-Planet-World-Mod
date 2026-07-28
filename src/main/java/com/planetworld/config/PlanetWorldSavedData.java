package com.planetworld.config;

import com.planetworld.PlanetWorld;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

public class PlanetWorldSavedData extends SavedData {
    public static final String ID = PlanetWorld.MOD_ID + "_settings";

    private PlanetSettings settings;

    public PlanetWorldSavedData() {
        this(PlanetSettings.defaults());
    }

    public PlanetWorldSavedData(PlanetSettings settings) {
        this.settings = settings;
    }

    public PlanetSettings getSettings() {
        return settings;
    }

    public void setSettings(PlanetSettings settings) {
        this.settings = settings;
        this.setDirty();
    }

    public static PlanetWorldSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WorldGenStyle style = tag.contains("worldGenStyle")
                ? WorldGenStyle.fromName(tag.getString("worldGenStyle"))
                : WorldGenStyle.NORMAL;
        return new PlanetWorldSavedData(new PlanetSettings(
                tag.getInt("circumference"),
                tag.getFloat("curvatureIntensity"),
                tag.getBoolean("localizedTime"),
                tag.getBoolean("localizedWeather"),
                tag.getBoolean("entityWrap"),
                !tag.contains("curvatureShader") || tag.getBoolean("curvatureShader"),
                style
        ));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("circumference", settings.circumference());
        tag.putFloat("curvatureIntensity", settings.curvatureIntensity());
        tag.putBoolean("localizedTime", settings.localizedTime());
        tag.putBoolean("localizedWeather", settings.localizedWeather());
        tag.putBoolean("entityWrap", settings.entityWrap());
        tag.putBoolean("curvatureShader", settings.curvatureShader());
        tag.putString("worldGenStyle", settings.worldGenStyle().name());
        return tag;
    }
}
