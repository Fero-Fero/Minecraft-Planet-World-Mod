/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Static holder used by the world-wrapping core ported from Circumnavigate
 * (https://github.com/FamroFexl/Circumnavigate) by Famro Fexl, AGPL-3.0.
 */
public class Circumnavigate {
	public static final String MOD_ID = "planetworld";
	public static final Logger LOGGER = LogManager.getLogger("PlanetWorld/Wrap");

	public static final boolean DEV_MODE = System.getProperty("env", "").equals("dev");
}
