package net.minecraft.core;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Int1088WorldgenArithmeticTest {
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger MAX = MIN.negate().subtract(BigInteger.ONE);

    @Test
    void smallOffsetsAndShiftsMatchBigIntegerAcrossEveryWidth() {
        Random random = new Random(1088512);
        for (int width = 65; width <= 1088; ++width) {
            BigInteger value = new BigInteger(width - 1, random).setBit(width - 2);
            if (random.nextBoolean()) value = value.negate();
            Int1088 coordinate = Int1088.parseDecimal(value.toString());
            for (int divisor : new int[]{1, 13, 37, Integer.MAX_VALUE}) {
                assertEquals(value.remainder(BigInteger.valueOf(divisor)).intValue(), coordinate.remainder(divisor));
            }
            for (long offset : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, random.nextLong()}) {
                BigInteger expected = value.add(BigInteger.valueOf(offset)).max(MIN).min(MAX);
                assertEquals(expected.toString(), coordinate.addSaturated(Int1088.fromLong(offset)).toString());
                assertEquals(expected.toString(), Int1088.fromLong(offset).addSaturated(coordinate).toString());
            }
            for (int shift : new int[]{0, 1, 4, 63, 64, 65, 1087, 1088, 1200, random.nextInt(1200)}) {
                assertEquals(value.shiftRight(shift).toString(), coordinate.shiftRight(shift).toString());
            }
        }
    }

    @Test
    void carriesBorrowsAndSaturationAtLimbBoundaries() {
        for (int bit = 127; bit <= 1087; bit += 64) {
            for (BigInteger value : new BigInteger[]{BigInteger.ONE.shiftLeft(bit).subtract(BigInteger.ONE),
                    BigInteger.ONE.shiftLeft(bit).negate()}) {
                Int1088 coordinate = Int1088.parseDecimal(value.toString());
                for (long offset : new long[]{1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                    assertEquals(value.add(BigInteger.valueOf(offset)).max(MIN).min(MAX).toString(),
                            coordinate.addSaturated(Int1088.fromLong(offset)).toString());
                }
            }
        }
    }

    @Test
    void cachedHashesMatchPrimitiveViewsAndMutablePositions() {
        for (Int1088 x : new Int1088[]{Int1088.ZERO, Int1088.fromLong(-53),
                Int1088.ONE.shiftLeftSaturated(127), Int1088.ONE.shiftLeftSaturated(512), Int1088.MIN, Int1088.MAX}) {
            long[] words = new long[Int1088.LIMBS];
            x.copyFixed(words, 0);
            long expected = Int1088Math.hash(words, 0, words.length);
            assertEquals(expected, x.hash());
            assertEquals(expected, x.hash());
            BlockPos immutable = new BlockPos(x, 64, Int1088.ONE);
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(x, 64, Int1088.ONE);
            assertEquals(immutable.hashCode(), mutable.hashCode());
            mutable.move(Direction.EAST);
            assertEquals(immutable.east().hashCode(), mutable.hashCode());
            mutable.setX(Int1088.ZERO);
            assertEquals(new BlockPos(0, 64, 1).hashCode(), mutable.hashCode());
        }
    }
}
