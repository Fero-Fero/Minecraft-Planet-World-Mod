/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.planetworld.wrap.processing;

import net.minecraft.world.phys.EntityHitResult;

public class EntityHitResultWrapped extends EntityHitResult {
	public EntityHitResultWrapped(EntityHitResult result) {
		super(result.getEntity(), result.getEntity().level().getTransformer().Vector3D.wrap(result.getLocation()));
	}
}
