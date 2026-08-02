package com.planetworld.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.planetworld.config.PlanetWorldConfig;
import com.planetworld.time.LocalTime;
import com.planetworld.wrap.WrapMath;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Hostile natural spawn darkness follows local time at the spawn column.
 * Global sky light would otherwise spawn mobs on the sunny side at global midnight and
 * block them on the midnight side at global noon.
 */
@Mixin(Monster.class)
public abstract class MonsterMixin {
	@WrapMethod(method = "isDarkEnoughToSpawn")
	private static boolean planetworld$localDarkEnough(
			ServerLevelAccessor level,
			BlockPos pos,
			RandomSource random,
			Operation<Boolean> original
	) {
		if (!PlanetWorldConfig.enableLocalizedTime() || !WrapMath.isWrappedDimension(level.getLevel())) {
			return original.call(level, pos, random);
		}
		return LocalTime.isDarkEnoughToSpawn(level, pos, random);
	}
}
