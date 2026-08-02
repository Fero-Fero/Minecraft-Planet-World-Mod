/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.accessors;

/**
 * Marker for entities that keep their own horizontal position wrap-correct.
 * <p>
 * Core wrapping skips {@code setPosRaw} wrapping for these so two owners cannot fight over the
 * same coordinate each tick. Compat layers implement this via mixin on the entity class, which
 * keeps core entity code free of any dependency on optional mods.
 */
public interface WrapsOwnPosition {
}
