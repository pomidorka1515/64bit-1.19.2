package net.minecraft.core;

import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotPositionTest {
    @Test
    void primitiveOffsetsMatchSaturatingValueArithmetic() {
        Int1088[] edges = {Int1088.MIN, Int1088.MAX, Int1088.ZERO, Int1088.of(-1L, 17L),
                Int1088.of(0L, -18L), Int1088.fromLong(Long.MIN_VALUE), Int1088.fromLong(Long.MAX_VALUE)};
        long[] offsets = {Long.MIN_VALUE, Long.MAX_VALUE, -17L, -1L, 0L, 1L, 17L};
        for (Int1088 x : edges) {
            for (Int1088 z : edges) {
                for (long delta : offsets) {
                    BlockPos pos = new BlockPos(x, 64, z);
                    assertEquals(new BlockPos(x.addSaturated(Int1088.fromLong(delta)), 67,
                            z.addSaturated(Int1088.fromLong(delta))), pos.offset(delta, 3, delta));
                    for (Direction direction : Direction.values()) {
                        assertEquals(new BlockPos(x.addSaturated(Int1088.fromLong(direction.getStepX())),
                                64 + direction.getStepY(), z.addSaturated(Int1088.fromLong(direction.getStepZ()))),
                                pos.relative(direction));
                    }
                }
            }
        }
    }

    @Test
    void primitiveSumMatchesValueArithmeticForFullWidthOperands() {
        Random random = new Random(0x128L);
        for (int i = 0; i < 10000; ++i) {
            Int1088 a = Int1088.of(random.nextLong(), random.nextLong());
            Int1088 b = Int1088.of(random.nextLong(), random.nextLong());
            Int1088 expected = a.addSaturated(b);
            BlockPos pos = new BlockPos(a, 12, b);
            BlockPos offset = new BlockPos(b, 3, a);
            BlockPos sum = new BlockPos(expected, 15, expected);
            assertEquals(sum, pos.offset(offset));
            assertEquals(sum, pos.mutable().move(offset));
            BlockPos.MutableBlockPos cursor = pos.mutable();
            assertEquals(sum, cursor.setWithOffset(offset, cursor));
            cursor.set(pos);
            assertEquals(new BlockPos(a.addSaturated(a), 24, b.addSaturated(b)), cursor.move(cursor));
        }
    }

    @Test
    void copiesHashesOrderingAndChunkConversionRetainAllLimbs() {
        Int1088[] coordinates = {Int1088.MIN, Int1088.MAX, Int1088.ZERO, Int1088.fromLong(-1L),
                Int1088.of(-1L, 17L), Int1088.of(0L, -18L)};
        for (Int1088 x : coordinates) {
            for (Int1088 z : coordinates) {
                BlockPos pos = new BlockPos(x, 64, z);
                assertEquals((64 + z.hashXZ() * 31) * 31 + x.hashXZ(), pos.hashCode());
                assertEquals(pos, new BlockPos(pos));
                assertEquals(pos, pos.mutable().immutable());
                BlockPos.MutableBlockPos mutable = pos.mutable();
                BlockPos copy = mutable.immutable();
                mutable.move(Direction.EAST);
                assertEquals(pos, copy);
                assertEquals(new BlockPos(x, 12, z), pos.atY(12));
                assertEquals(new ChunkPos(x.shiftRight(4), z.shiftRight(4)), new ChunkPos(pos));
                ChunkPos chunk = new ChunkPos(pos);
                assertEquals(new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ()), chunk.getWorldPosition());
                for (Int1088 otherX : coordinates) {
                    BlockPos other = new BlockPos(otherX, 64, z);
                    assertEquals(Integer.signum(x.compareTo(otherX)), Integer.signum(pos.compareTo(other)));
                    assertEquals(Integer.signum(z.compareTo(otherX)),
                            Integer.signum(pos.compareTo(new BlockPos(x, 64, otherX))));
                }
                assertTrue(pos.compareTo(pos.above()) < 0);
            }
        }
    }
}
