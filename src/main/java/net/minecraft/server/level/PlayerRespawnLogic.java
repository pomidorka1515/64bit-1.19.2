package net.minecraft.server.level;

import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

public class PlayerRespawnLogic {
    @Nullable
    protected static BlockPos getOverworldRespawnPos(ServerLevel p_183929_, long p_183930_, long p_183931_) {
        return getOverworldRespawnPos(p_183929_, Int1088.fromLong(p_183930_), Int1088.fromLong(p_183931_));
    }

    @Nullable
    protected static BlockPos getOverworldRespawnPos(ServerLevel p_183929_, Int1088 p_183930_, Int1088 p_183931_) {
        boolean flag = p_183929_.dimensionType().hasCeiling();
        LevelChunk levelchunk = p_183929_.getChunk(SectionPos.blockToSectionCoord(p_183930_), SectionPos.blockToSectionCoord(p_183931_));
        int localX = (int) (p_183930_.lowLimb() & 15);
        int localZ = (int) (p_183931_.lowLimb() & 15);
        int i = flag ? p_183929_.getChunkSource().getGenerator().getSpawnHeight(p_183929_) : levelchunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ);
        if (i < p_183929_.getMinBuildHeight()) {
            return null;
        } else {
            int j = levelchunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
            if (j <= i && j > levelchunk.getHeight(Heightmap.Types.OCEAN_FLOOR, localX, localZ)) {
                return null;
            } else {
                BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

                for (int k = i + 1; k >= p_183929_.getMinBuildHeight(); --k) {
                    blockpos$mutableblockpos.set(p_183930_, k, p_183931_);
                    BlockState blockstate = p_183929_.getBlockState(blockpos$mutableblockpos);
                    if (!blockstate.getFluidState().isEmpty()) {
                        break;
                    }

                    if (Block.isFaceFull(blockstate.getCollisionShape(p_183929_, blockpos$mutableblockpos), Direction.UP)) {
                        return blockpos$mutableblockpos.above().immutable();
                    }
                }

                return null;
            }
        }
    }

    @Nullable
    public static BlockPos getSpawnPosInChunk(ServerLevel pLevel, ChunkPos pChunkPos) {
        if (SharedConstants.debugVoidTerrain(pChunkPos)) {
            return null;
        } else {
            for (int x = 0; x < 16; ++x) {
                Int1088 i = WorldBounds.addBlockOffset(pChunkPos.getMinBlockX(), x);
                for (int z = 0; z < 16; ++z) {
                    Int1088 j = WorldBounds.addBlockOffset(pChunkPos.getMinBlockZ(), z);
                    BlockPos blockpos = getOverworldRespawnPos(pLevel, i, j);
                    if (blockpos != null) {
                        return blockpos;
                    }
                }
            }

            return null;
        }
    }
}
