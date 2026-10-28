package net.minecraft.core;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SectionContainmentTest {
    @Test
    void containmentNeverMaterializesMutableCoordinates() {
        Int1088 coordinate = Int1088.of(-1, 7);
        BlockPos.MutableBlockPos block = new BlockPos.MutableBlockPos(coordinate, 64, coordinate) {
            @Override public Int1088 getX() { throw new AssertionError("materialized X"); }
            @Override public Int1088 getZ() { throw new AssertionError("materialized Z"); }
        };
        assertTrue(SectionPos.of(coordinate.shiftRight(4), 4, coordinate.shiftRight(4)).contains(block));
    }

    @Test
    void primitiveContainmentMatchesShiftedIdentityAtEveryWidth() {
        Random random = new Random(71088);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int length = 1; length <= Int1088.LIMBS; ++length) {
            for (int iteration = 0; iteration < 40; ++iteration) {
                long[] words = new long[length];
                for (int i = 0; i < length; ++i) words[i] = random.nextLong();
                Int1088 x = Int1088.of(words);
                Int1088 z = x.negate();
                BlockPos block = new BlockPos(x, random.nextInt(), z);
                SectionPos section = SectionPos.of(block);
                mutable.set(block);
                assertTrue(section.contains(block));
                assertTrue(section.contains(mutable));
                assertFalse(section.offset(1, 0, 0).contains(mutable));
                mutable.setY(block.getY() ^ 16);
                assertFalse(section.contains(mutable));
                mutable.set(block).setX(x.xor(Int1088.ONE.shiftLeftSaturated(1024)));
                assertEquals(section.getX().equals(mutable.getX().shiftRight(4)),
                        SectionPos.matchesBlockCoordinate(section.getX(), mutable, true));
            }
        }
        for (Int1088 edge : new Int1088[]{Int1088.MIN, Int1088.MAX}) {
            mutable.set(edge, -1, edge);
            assertTrue(SectionPos.of(mutable).contains(mutable));
        }
    }

    @Test
    void fixedCopiesOverwriteSignExtensionWithoutTouchingAdjacentWords() {
        long[] destination = new long[Int1088.LIMBS + 2];
        java.util.Arrays.fill(destination, 73);
        for (Int1088 value : new Int1088[]{Int1088.MIN, Int1088.MAX, Int1088.of(0, -7), Int1088.ONE, Int1088.fromLong(-1)}) {
            value.copyFixed(destination, 1);
            assertEquals(73, destination[0]);
            assertEquals(73, destination[destination.length - 1]);
            for (int i = 0; i < Int1088.LIMBS; ++i) assertEquals(value.limb(i), destination[i + 1]);
        }
    }
}
