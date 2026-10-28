package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChunkLocalPositionLookupTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void protoChunkUsesLocalBitsWithoutMaterializingMutableCoordinates() {
        Int1088 wide = Int1088.ONE.shiftLeftSaturated(512);
        ProtoChunk chunk = new ProtoChunk(new ChunkPos(wide.shiftRight(4), wide.negate().shiftRight(4)),
                UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), BuiltinRegistries.BIOME, null);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos() {
            @Override public Int1088 getX() { throw new AssertionError("materialized X"); }
            @Override public Int1088 getZ() { throw new AssertionError("materialized Z"); }
        };
        pos.set(wide.addSaturated(Int1088.fromLong(15)), 20, wide.negate().addSaturated(Int1088.fromLong(3)));
        chunk.setBlockState(pos, Blocks.WATER.defaultBlockState(), false);
        assertSame(Blocks.WATER.defaultBlockState(), chunk.getBlockState(pos));
        assertEquals(Blocks.WATER.defaultBlockState().getFluidState(), chunk.getFluidState(pos));
        pos.set(wide.addSaturated(Int1088.fromLong(14)), 20, wide.negate().addSaturated(Int1088.fromLong(3)));
        assertSame(Blocks.AIR.defaultBlockState(), chunk.getBlockState(pos));
    }
}
