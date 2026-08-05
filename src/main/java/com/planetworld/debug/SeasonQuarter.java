package com.planetworld.debug;

import com.planetworld.season.SeasonAuthority;
import net.minecraft.network.chat.Component;

/**
 * Northern-calendar season quarters for debug logging and skip commands.
 */
public enum SeasonQuarter {
	SPRING(0.125f, "planetworld.command.season.spring"),
	SUMMER(SeasonAuthority.MIDSUMMER_PROGRESS, "planetworld.command.season.summer"),
	AUTUMN(0.625f, "planetworld.command.season.autumn"),
	WINTER(0.875f, "planetworld.command.season.winter");

	private final float northernProgress;
	private final String translationKey;

	SeasonQuarter(float northernProgress, String translationKey) {
		this.northernProgress = northernProgress;
		this.translationKey = translationKey;
	}

	public float northernProgress() {
		return northernProgress;
	}

	public Component displayName() {
		return Component.translatable(translationKey);
	}

	public static SeasonQuarter fromNorthernProgress(float progress) {
		float p = progress - (float) Math.floor(progress);
		if (p < 0.25f) {
			return SPRING;
		}
		if (p < 0.5f) {
			return SUMMER;
		}
		if (p < 0.75f) {
			return AUTUMN;
		}
		return WINTER;
	}
}
