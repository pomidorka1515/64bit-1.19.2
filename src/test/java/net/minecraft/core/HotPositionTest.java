package net.minecraft.core;

import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotPositionTest {
    @Test
    void primitiveOffsetsMatchSaturatingValueArithmetic() {
        Int128[] edges = {Int128.MIN, Int128.MAX, Int128.ZERO, Int128.of(17L, -1L),
                Int128.of(-18L, 0L), Int128.fromLong(Long.MIN_VALUE), Int128.fromLong(Long.MAX_VALUE)};
        long[] offsets = {Long.MIN_VALUE, Long.MAX_VALUE, -17L, -1L, 0L, 1L, 17L};
        for (Int128 x : edges) {
            for (Int128 z : edges) {
                for (long delta : offsets) {
                    BlockPos pos = new BlockPos(x, 64, z);
                    assertEquals(new BlockPos(x.addSaturated(Int128.fromLong(delta)), 67,
                            z.addSaturated(Int128.fromLong(delta))), pos.offset(delta, 3, delta));
                    for (Direction direction : Direction.values()) {
                        assertEquals(new BlockPos(x.addSaturated(Int128.fromLong(direction.getStepX())),
                                64 + direction.getStepY(), z.addSaturated(Int128.fromLong(direction.getStepZ()))),
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
            Int128 a = Int128.of(random.nextLong(), random.nextLong());
            Int128 b = Int128.of(random.nextLong(), random.nextLong());
            Int128 expected = a.addSaturated(b);
            assertEquals(expected.hi(), Int128Math.addSaturatedHi(a.hi(), a.lo(), b.hi(), b.lo()));
            assertEquals(expected.lo(), Int128Math.addSaturatedLo(a.hi(), a.lo(), b.hi(), b.lo()));
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
        Int128[] coordinates = {Int128.MIN, Int128.MAX, Int128.ZERO, Int128.fromLong(-1L),
                Int128.of(17L, -1L), Int128.of(-18L, 0L)};
        for (Int128 x : coordinates) {
            for (Int128 z : coordinates) {
                BlockPos pos = new BlockPos(x, 64, z);
                assertEquals((64 + z.hashCode() * 31) * 31 + x.hashCode(), pos.hashCode());
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
                for (Int128 otherX : coordinates) {
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
