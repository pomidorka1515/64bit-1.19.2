package net.minecraft.client.renderer.chunk;

import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RenderChunkSnapshotTest {
    @Test
    void sectionArrayPreservesEmptySlotsAndIndependentPaletteSnapshots() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        PalettedContainer<BlockState> air = states(Blocks.AIR.defaultBlockState());
        PalettedContainer<BlockState> stone = states(Blocks.STONE.defaultBlockState());
        LevelChunkSection[] sections = {
                new LevelChunkSection(-4, air, null),
                new LevelChunkSection(-3, stone, null)};
        PalettedContainer<BlockState>[] snapshot = RenderChunk.copySections(sections);
        assertEquals(2, snapshot.length);
        assertNull(snapshot[0]);
        assertNotSame(stone, snapshot[1]);
        sections[0].setBlockState(0, 0, 0, Blocks.STONE.defaultBlockState());
        sections[1].setBlockState(0, 0, 0, Blocks.DIRT.defaultBlockState());
        assertNull(snapshot[0]);
        assertSame(Blocks.STONE.defaultBlockState(), snapshot[1].get(0, 0, 0));
        snapshot[1].set(1, 0, 0, Blocks.GRASS_BLOCK.defaultBlockState());
        assertSame(Blocks.STONE.defaultBlockState(), stone.get(1, 0, 0));
        assertEquals(0, RenderChunk.copySections(new LevelChunkSection[0]).length);
    }

    @Test
    void capturedMinimumSectionMatchesHeightAccessorForAllVerticalRanges() {
        Random random = new Random(1488);
        for (int minY : new int[]{-64, 0, 64, -2048, 2032}) {
            LevelHeightAccessor height = LevelHeightAccessor.create(minY, 384);
            int minSection = height.getMinSection();
            for (int y = minY - 17; y <= minY + 401; ++y) {
                assertEquals(height.getSectionIndex(y), RenderChunk.sectionIndex(y, minSection));
            }
            for (int i = 0; i < 10000; ++i) {
                int y = random.nextInt();
                assertEquals(height.getSectionIndex(y), RenderChunk.sectionIndex(y, minSection));
            }
            for (int y : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertEquals(height.getSectionIndex(y), RenderChunk.sectionIndex(y, minSection));
            }
        }
    }

    private static PalettedContainer<BlockState> states(BlockState state) {
        return new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, state, PalettedContainer.Strategy.SECTION_STATES);
    }
}
