/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.accessors;

import com.planetworld.wrap.core.DimensionTransformer;

public interface TransformerAccessor {
	DimensionTransformer getTransformer();
	void setTransformer(DimensionTransformer transformer);
}
