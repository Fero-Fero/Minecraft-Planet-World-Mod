package com.planetworld.config;

import com.planetworld.PlanetWorld;
import com.planetworld.wrap.options.WorldWrappingSettings;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Per-world planet settings in {@code data/planetworld_settings.dat}.
 * <p>
 * Also stores a backup of {@link WorldWrappingSettings}. Vanilla rebuilds {@code level.dat}
 * without the mod and drops the {@code WrappingSettings} key; this file is ignored by vanilla
 * and survives uninstall/reinstall so torus bounds can be restored.
 */
public class PlanetWorldSavedData extends SavedData {
	public static final String ID = PlanetWorld.MOD_ID + "_settings";

	private PlanetSettings settings;
	@Nullable
	private WorldWrappingSettings wrappingBackup;

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

	@Nullable
	public WorldWrappingSettings getWrappingBackup() {
		return wrappingBackup;
	}

	public void setWrappingBackup(@Nullable WorldWrappingSettings wrapping) {
		this.wrappingBackup = wrapping;
		this.setDirty();
	}

	public static PlanetWorldSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
		WorldGenStyle style = tag.contains("worldGenStyle")
				? WorldGenStyle.fromName(tag.getString("worldGenStyle"))
				: WorldGenStyle.NORMAL;
		PlanetWorldSavedData data = new PlanetWorldSavedData(new PlanetSettings(
				tag.getInt("circumference"),
				tag.getFloat("curvatureIntensity"),
				tag.getBoolean("localizedTime"),
				tag.getBoolean("localizedWeather"),
				tag.getBoolean("entityWrap"),
				!tag.contains("curvatureShader") || tag.getBoolean("curvatureShader"),
				style
		));
		OutOfBoundsChunkHealer.readWrapBackup(tag).ifPresent(wrap -> data.wrappingBackup = wrap);
		return data;
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
		OutOfBoundsChunkHealer.writeWrapBackup(tag, wrappingBackup);
		return tag;
	}
}
