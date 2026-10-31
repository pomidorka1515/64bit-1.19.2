package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.math.BigInteger;
import net.minecraft.core.Int1088;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class ViewAreaTest {
    @Test
    void mutableWindowLookupDoesNotMaterializeCoordinates() {
        Int1088 first = Int1088.ONE.shiftLeftSaturated(1024).negate();
        ViewArea.AxisIndex axis = new ViewArea.AxisIndex(first, 33);
        Int1088 block = axis.blocks[16].addSaturated(15);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(block, 64, block) {
            @Override public Int1088 getX() { throw new AssertionError("Mutable X materialized"); }
            @Override public Int1088 getZ() { throw new AssertionError("Mutable Z materialized"); }
        };
        assertEquals(16, axis.blockOffset(pos, true));
        assertEquals(16, axis.blockOffset(pos, false));
    }

    @Test
    void windowIndexMatchesFullRemaindersAndRejectsDistantAliases() {
        for (Int1088 center : new Int1088[] {Int1088.ZERO, Int1088.fromLong(-1),
                Int1088.of(-1, 7), Int1088.of(0, -7), WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK,
                Int1088.ONE.shiftLeftSaturated(1024)}) {
            for (int size : new int[] {1, 3, 33, 65}) {
                Int1088 first = ViewArea.firstChunkInView(center, size);
                ViewArea.AxisIndex axis = new ViewArea.AxisIndex(first, size);
                for (int i = 0; i < size; ++i) {
                    assertEquals(ViewArea.floorMod(axis.sections[i], size), axis.slot(i));
                    assertEquals(axis.slot(i), axis.sectionSlot(axis.sections[i]));
                    for (int local : new int[] {0, 15}) {
                        Int1088 block = WorldBounds.addBlockOffset(axis.blocks[i], local);
                        BlockPos pos = new BlockPos(block, 10, block);
                        assertEquals(i, axis.blockOffset(pos, true));
                        assertEquals(i, axis.blockOffset(pos.mutable(), false));
                    }
                }
                Int1088 middle = axis.blocks[size / 2];
                Int1088 alias = middle.add(Int1088.ONE.shiftLeftSaturated(128));
                if (!alias.equals(middle))
                    assertEquals(-1, axis.blockOffset(new BlockPos(alias, 0, alias), true));
                assertEquals(ViewArea.floorMod(alias, size), axis.sectionSlot(alias));
                if (!first.equals(WorldBounds.MIN_CHUNK)) {
                    Int1088 before = WorldBounds.chunkToBlock(first.subtract(Int1088.ONE));
                    assertEquals(-1, axis.blockOffset(new BlockPos(before, 0, before), true));
                }
            }
        }
    }

    @Test
    void everyUpperLimbKeepsContiguousWindowsAndExactPeriodicSlots() {
        for (int bit = 64; bit <= 1024; bit += 64) {
            for (int sign : new int[] {-1, 1}) {
                BigInteger exact = BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign))
                        .subtract(BigInteger.ONE);
                Int1088 center = Int1088.parseDecimal(exact.toString());
                assertContiguousWindow(center, 33,
                        Int1088.parseDecimal(exact.subtract(BigInteger.valueOf(16)).toString()));
                for (int period : new int[] {3, 33, 65})
                    assertEquals(exact.mod(BigInteger.valueOf(period)).intValue(), ViewArea.floorMod(center, period));
            }
        }
    }

    @Test
    void smallInvalidationsVisitOnlyTheirExactSections() {
        for (Int1088 first : new Int1088[] {Int1088.fromLong(-1), Int1088.of(-1, 7), Int1088.of(-1, -7),
                WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK.subtract(Int1088.ONE)}) {
            Set<String> visited = new HashSet<>();
            assertTrue(ViewArea.forEachDirtySection(first, -1, first, first.add(Int1088.ONE), 0,
                    first.add(Int1088.ONE), 15000, (x, y, z) -> {
                        assertTrue(x.equals(first) || x.equals(first.add(Int1088.ONE)));
                        assertTrue(z.equals(first) || z.equals(first.add(Int1088.ONE)));
                        assertTrue(y == -1 || y == 0);
                        assertTrue(visited.add(x + ":" + y + ":" + z));
                    }));
            assertEquals(8, visited.size());
        }
    }

    @Test
    void largeInvalidationsFallBackBeforeVisitingAnySections() {
        ViewArea.DirtySectionConsumer unexpected = (x, y, z) -> {
            throw new AssertionError("Oversized range must use the bounded scan");
        };
        assertFalse(ViewArea.forEachDirtySection(WorldBounds.MIN_CHUNK, -4, WorldBounds.MIN_CHUNK,
                WorldBounds.MAX_CHUNK, 19, WorldBounds.MAX_CHUNK, 15000, unexpected));
        assertFalse(ViewArea.forEachDirtySection(Int1088.ZERO, 0, Int1088.ZERO,
                Int1088.fromLong(99), 0, Int1088.fromLong(99), 9999, unexpected));
        assertTrue(ViewArea.forEachDirtySection(Int1088.ZERO, 1, Int1088.ZERO,
                Int1088.ZERO, 0, Int1088.ZERO, 0, unexpected));
    }

    @Test
    void exactLookupBudgetIsAcceptedWithoutOverflow() {
        int[] visits = {0};
        assertTrue(ViewArea.forEachDirtySection(Int1088.ZERO, -4, Int1088.ZERO,
                Int1088.ONE, 19, Int1088.ONE, 96, (x, y, z) -> ++visits[0]));
        assertEquals(96, visits[0]);
    }

    @Test
    void paddedBlockUpdatesIncludeBoundaryNeighborsWithoutWorldEdgeWraparound() {
        for (Int1088 block : new Int1088[] {Int1088.ZERO, Int1088.fromLong(-1), Int1088.fromLong(15),
                Int1088.of(0, 7), WorldBounds.MIN_BLOCK, WorldBounds.MAX_BLOCK}) {
            Int1088 min = WorldBounds.addBlockOffset(block, -1).shiftRight(4);
            Int1088 max = WorldBounds.addBlockOffset(block, 1).shiftRight(4);
            Set<Int1088> visited = new HashSet<>();
            assertTrue(ViewArea.forEachDirtySection(min, 0, min, max, 0, max, 8, (x, y, z) -> {
                assertTrue(WorldBounds.isValidChunk(x, z));
                visited.add(x);
            }));
            assertTrue(visited.contains(block.shiftRight(4)));
            assertEquals(block.equals(WorldBounds.MIN_BLOCK) || block.equals(WorldBounds.MAX_BLOCK) ? 1 : 2,
                    visited.size());
        }
    }

    @Test
    void positiveWorldEdgeUsesAUniqueLegalRenderWindow() {
        assertContiguousWindow(WorldBounds.MAX_CHUNK, 33, WorldBounds.MAX_CHUNK.subtract(Int1088.fromLong(32)));
    }

    @Test
    void aNearPositiveWorldEdgeShiftsTheFullWindowInsideTheWorld() {
        assertContiguousWindow(WorldBounds.MAX_CHUNK.subtract(Int1088.ONE), 33, WorldBounds.MAX_CHUNK.subtract(Int1088.fromLong(32)));
    }

    @Test
    void negativeWorldEdgeUsesAUniqueLegalRenderWindow() {
        assertContiguousWindow(WorldBounds.MIN_CHUNK, 33, WorldBounds.MIN_CHUNK);
    }

    @Test
    void highHalvesAndLowWordSeamsKeepUniqueGridSlots() {
        for (Int1088 center : new Int1088[] {Int1088.of(-8, 7), Int1088.of(8, -7), Int1088.fromLong(-1)}) {
            assertContiguousWindow(center, 33, center.subtract(Int1088.fromLong(16)));
        }
    }

    @Test
    void periodicIndicesUseTheFullSignedCoordinate() {
        for (Int1088 value : new Int1088[] {Int1088.MIN, Int1088.MAX, Int1088.of(0, 1), Int1088.of(0, -1), Int1088.ONE.shiftLeftSaturated(1024), Int1088.fromLong(-1)}) {
            BigInteger exact = new BigInteger(value.toDecimalString());
            for (int period : new int[] {1, 3, 33, 65, 24576})
                assertEquals(exact.mod(BigInteger.valueOf(period)).intValue(), ViewArea.floorMod(value, period));
        }
    }

    private static void assertContiguousWindow(Int1088 center, int width, Int1088 expectedFirst) {
        Int1088 first = ViewArea.firstChunkInView(center, width);
        assertEquals(expectedFirst, first);

        Set<Int1088> chunks = new HashSet<>();
        Set<Integer> slots = new HashSet<>();
        for (int offset = 0; offset < width; ++offset) {
            Int1088 chunk = first.add(Int1088.fromLong(offset));
            assertTrue(chunk.compareTo(WorldBounds.MIN_CHUNK) >= 0);
            assertTrue(chunk.compareTo(WorldBounds.MAX_CHUNK) <= 0);
            chunks.add(chunk);
            slots.add(ViewArea.floorMod(chunk, width));
        }

        assertEquals(width, chunks.size());
        assertEquals(width, slots.size());
        assertEquals(expectedFirst.add(Int1088.fromLong(width - 1L)), first.add(Int1088.fromLong(width - 1L)));
    }
}
