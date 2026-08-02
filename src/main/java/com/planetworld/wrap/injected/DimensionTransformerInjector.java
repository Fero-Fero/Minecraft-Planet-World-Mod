/* SPDX-License-Identifier: AGPL-3.0-only */

package com.planetworld.wrap.injected;

import com.planetworld.wrap.core.DimensionTransformer;

public interface DimensionTransformerInjector {
	default DimensionTransformer getTransformer() {
		return null;
	}

	default void setTransformer(DimensionTransformer transformer) {

	}
}
