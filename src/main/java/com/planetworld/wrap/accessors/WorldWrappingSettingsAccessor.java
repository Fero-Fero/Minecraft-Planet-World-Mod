/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.accessors;

import com.planetworld.wrap.options.WorldWrappingSettings;

public interface WorldWrappingSettingsAccessor {
	void setWorldWrappingSettings(WorldWrappingSettings settings);
	WorldWrappingSettings getWorldWrappingSettings();

	/** True when this level data was parsed from an existing level.dat (Planet World addition). */
	default boolean planetworld$loadedFromDisk() {
		return false;
	}

	default void planetworld$markLoadedFromDisk() {
	}
}
