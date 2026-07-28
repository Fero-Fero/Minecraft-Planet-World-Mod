package com.planetworld.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.neoforged.neoforge.common.MonsterRoomHooks;

/**
 * Force-places a vanilla-style monster-room dungeon. Prepares a solid pocket so
 * placement cannot fail the usual cave-opening checks.
 */
public final class GuaranteedDungeonPlacer {
	private static final EntityType<?>[] FALLBACK_MOBS = {
			EntityType.SKELETON, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.SPIDER
	};

	private GuaranteedDungeonPlacer() {
	}

	public static void tryPlace(WorldGenLevel level, ChunkAccess chunk) {
		// Decoration runs on WorldGenRegion, not ServerLevel.
		if (!(level instanceof ServerLevelAccessor accessor)) {
			return;
		}
		if (!accessor.getLevel().dimension().equals(Level.OVERWORLD)) {
			return;
		}
		long seed = level.getSeed();
		if (!GuaranteedStructures.isDungeonChunk(seed, chunk.getPos().x, chunk.getPos().z)) {
			return;
		}

		int x = chunk.getPos().getMiddleBlockX();
		int z = chunk.getPos().getMiddleBlockZ();
		int surface = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
		int minY = level.getMinBuildHeight() + 8;
		int maxY = Math.max(minY + 4, Math.min(surface - 8, 40));
		if (maxY <= minY) {
			maxY = minY + 8;
		}

		RandomSource random = RandomSource.create(seed ^ chunk.getPos().toLong() ^ 0xD0116E01L);
		int y = minY + random.nextInt(Math.max(1, maxY - minY));
		placeRoom(level, new BlockPos(x, y, z), random);
	}

	private static void placeRoom(WorldGenLevel level, BlockPos origin, RandomSource random) {
		int halfX = 2 + random.nextInt(2);
		int halfZ = 2 + random.nextInt(2);
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState air = Blocks.CAVE_AIR.defaultBlockState();

		// Solid shell so floor/ceiling/wall solidity checks always pass.
		for (int dx = -halfX - 1; dx <= halfX + 1; dx++) {
			for (int dy = -1; dy <= 4; dy++) {
				for (int dz = -halfZ - 1; dz <= halfZ + 1; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					boolean edge = dx == -halfX - 1 || dx == halfX + 1
							|| dy == -1 || dy == 4
							|| dz == -halfZ - 1 || dz == halfZ + 1;
					if (edge) {
						level.setBlock(pos, stone, 2);
					} else {
						level.setBlock(pos, air, 2);
					}
				}
			}
		}

		// One doorway opening (vanilla requires 1–5 openings at floor level).
		Direction door = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos doorPos = origin.relative(door, halfX + 1);
		level.setBlock(doorPos, air, 2);
		level.setBlock(doorPos.above(), air, 2);

		for (int dx = -halfX - 1; dx <= halfX + 1; dx++) {
			for (int dy = 3; dy >= -1; dy--) {
				for (int dz = -halfZ - 1; dz <= halfZ + 1; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					boolean wall = dx == -halfX - 1 || dx == halfX + 1
							|| dy == -1 || dy == 4
							|| dz == -halfZ - 1 || dz == halfZ + 1;
					if (!wall) {
						continue;
					}
					if (pos.equals(doorPos) || pos.equals(doorPos.above())) {
						continue;
					}
					if (dy == -1 && random.nextInt(4) != 0) {
						level.setBlock(pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
					} else {
						level.setBlock(pos, Blocks.COBBLESTONE.defaultBlockState(), 2);
					}
				}
			}
		}

		for (int attempt = 0; attempt < 2; attempt++) {
			for (int tryChest = 0; tryChest < 3; tryChest++) {
				int cx = origin.getX() + random.nextInt(halfX * 2 + 1) - halfX;
				int cz = origin.getZ() + random.nextInt(halfZ * 2 + 1) - halfZ;
				BlockPos chestPos = new BlockPos(cx, origin.getY(), cz);
				if (!level.getBlockState(chestPos).isAir()) {
					continue;
				}
				int solidNeighbors = 0;
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					if (level.getBlockState(chestPos.relative(dir)).isSolid()) {
						solidNeighbors++;
					}
				}
				if (solidNeighbors == 1) {
					level.setBlock(
							chestPos,
							StructurePiece.reorient(level, chestPos, Blocks.CHEST.defaultBlockState()),
							2
					);
					RandomizableContainer.setBlockEntityLootTable(
							level, random, chestPos, BuiltInLootTables.SIMPLE_DUNGEON
					);
					break;
				}
			}
		}

		level.setBlock(origin, Blocks.SPAWNER.defaultBlockState(), 2);
		if (level.getBlockEntity(origin) instanceof SpawnerBlockEntity spawner) {
			EntityType<?> mob;
			try {
				mob = MonsterRoomHooks.getRandomMonsterRoomMob(random);
			} catch (Throwable ignored) {
				mob = FALLBACK_MOBS[random.nextInt(FALLBACK_MOBS.length)];
			}
			spawner.setEntityId(mob, random);
		}
	}
}
