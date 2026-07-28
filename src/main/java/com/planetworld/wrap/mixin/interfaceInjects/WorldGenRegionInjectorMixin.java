/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.mixin.interfaceInjects;

import com.planetworld.wrap.core.DimensionTransformer;
import com.planetworld.wrap.injected.DimensionTransformerInjector;
import net.minecraft.server.level.WorldGenRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WorldGenRegion.class)
public class WorldGenRegionInjectorMixin implements DimensionTransformerInjector {
	@Unique
	private DimensionTransformer transformer = null;

	@Override
	public DimensionTransformer getTransformer() {
		return transformer;
	}

	@Override
	public void setTransformer(DimensionTransformer transformer) {
		this.transformer = transformer;
	}
}
