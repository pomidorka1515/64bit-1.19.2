package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class VoidStartPlatformFeature extends Feature<NoneFeatureConfiguration> {
    private static final BlockPos PLATFORM_OFFSET = new BlockPos(8, 3, 8);
    private static final ChunkPos PLATFORM_ORIGIN_CHUNK = new ChunkPos(PLATFORM_OFFSET);
    private static final int PLATFORM_RADIUS = 16;
    private static final int PLATFORM_RADIUS_CHUNKS = 1;

    public VoidStartPlatformFeature(Codec<NoneFeatureConfiguration> pCodec) {
        super(pCodec);
    }

    private static double checkerboardDistance(net.minecraft.core.Int1088 pFirstX, net.minecraft.core.Int1088 pFirstZ, net.minecraft.core.Int1088 pSecondX, net.minecraft.core.Int1088 pSecondZ) {
        return Math.max(WorldBounds.distance(pFirstX, pSecondX), WorldBounds.distance(pFirstZ, pSecondZ));
    }

    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> pContext) {
        WorldGenLevel worldgenlevel = pContext.level();
        ChunkPos chunkpos = new ChunkPos(pContext.origin());
        if (checkerboardDistance(chunkpos.x(), chunkpos.z(), PLATFORM_ORIGIN_CHUNK.x(), PLATFORM_ORIGIN_CHUNK.z()) > 1) {
            return true;
        } else {
            BlockPos blockpos = PLATFORM_OFFSET.atY(pContext.origin().getY() + PLATFORM_OFFSET.getY());
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            net.minecraft.core.Int1088 i = chunkpos.getMinBlockZ();
            net.minecraft.core.Int1088 j = chunkpos.getMinBlockX();
            for (int k = 0; k < 16; ++k) {
                net.minecraft.core.Int1088 l = WorldBounds.addBlockOffset(i, k);
                for (int i1 = 0; i1 < 16; ++i1) {
                    net.minecraft.core.Int1088 j1 = WorldBounds.addBlockOffset(j, i1);
                    if (checkerboardDistance(blockpos.getX(), blockpos.getZ(), j1, l) <= 16.0D) {
                        blockpos$mutableblockpos.set(j1, blockpos.getY(), l);
                        if (blockpos$mutableblockpos.equals(blockpos)) {
                            worldgenlevel.setBlock(blockpos$mutableblockpos, Blocks.COBBLESTONE.defaultBlockState(), 2);
                        } else {
                            worldgenlevel.setBlock(blockpos$mutableblockpos, Blocks.STONE.defaultBlockState(), 2);
                        }
                    }
                }
            }

            return true;
        }
    }
}
