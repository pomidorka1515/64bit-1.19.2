package net.minecraft.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinateHotPathTest {
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger MAX = MIN.negate().subtract(BigInteger.ONE);

    private static List<BigInteger> coordinates() {
        List<BigInteger> values = new ArrayList<>(List.of(MIN, MAX, BigInteger.ZERO,
                BigInteger.ONE, BigInteger.ONE.negate()));
        Random random = new Random(781088);
        for (int bit = 63; bit <= 1087; bit += 64) {
            BigInteger boundary = BigInteger.ONE.shiftLeft(bit);
            for (int delta = -1; delta <= 1; ++delta) {
                BigInteger value = boundary.add(BigInteger.valueOf(delta));
                if (value.compareTo(MAX) <= 0) values.add(value);
                if (value.negate().compareTo(MIN) >= 0) values.add(value.negate());
            }
        }
        for (int width = 1; width <= 1087; width += 7) {
            BigInteger value = new BigInteger(width, random);
            values.add(value);
            values.add(value.negate());
        }
        return values;
    }

    @Test
    void comparisonsAndLocalDifferencesMatchBigIntegerAtEveryWidth() {
        List<BigInteger> values = coordinates();
        Random random = new Random(108872);
        for (BigInteger value : values) {
            for (BigInteger origin : List.of(values.get(random.nextInt(values.size())),
                    value.add(BigInteger.ONE).min(MAX), value.subtract(BigInteger.ONE).max(MIN),
                    value.add(BigInteger.valueOf(Long.MAX_VALUE)).min(MAX),
                    value.add(BigInteger.valueOf(Long.MIN_VALUE)).max(MIN))) {
                Int1088 a = Int1088.parseDecimal(value.toString());
                Int1088 b = Int1088.parseDecimal(origin.toString());
                long expected = value.subtract(origin).max(BigInteger.valueOf(Long.MIN_VALUE))
                        .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
                assertEquals(Integer.signum(value.compareTo(origin)), Integer.signum(a.compareTo(b)));
                assertEquals(expected, Int1088Math.signedDifferenceAsLong(a, b));
                long[] aw = new long[Int1088.LIMBS], bw = new long[Int1088.LIMBS];
                a.copyFixed(aw, 0);
                b.copyFixed(bw, 0);
                assertEquals(expected, Int1088Math.signedDifferenceAsLong(aw, 0, aw.length, bw, 0, bw.length));
            }
        }
    }

    @Test
    void leftShiftsRetainSignAndSaturateOnlyAtDomainBoundary() {
        for (BigInteger value : coordinates()) {
            Int1088 coordinate = Int1088.parseDecimal(value.toString());
            for (int shift : new int[]{0, 1, 4, 63, 64, 65, 511, 1024, 1087, 1088, Integer.MAX_VALUE}) {
                BigInteger expected = shift >= 1088
                        ? (value.signum() == 0 ? BigInteger.ZERO : value.signum() < 0 ? MIN : MAX)
                        : value.shiftLeft(shift).max(MIN).min(MAX);
                assertEquals(expected.toString(), coordinate.shiftLeftSaturated(shift).toString());
            }
        }
    }

    @Test
    void mutableNeighborOffsetsMatchBigIntegerIncludingAliasingAndSaturation() {
        for (BigInteger value : coordinates()) {
            Int1088 coordinate = Int1088.parseDecimal(value.toString());
            BlockPos source = new BlockPos(coordinate, 12, coordinate);
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (long offset : new long[]{0, 1, -1, 16, -16, Long.MIN_VALUE, Long.MAX_VALUE}) {
                BigInteger expected = value.add(BigInteger.valueOf(offset)).max(MIN).min(MAX);
                mutable.setWithOffset(source, offset, 3, offset);
                assertEquals(expected.toString(), mutable.getX().toString());
                assertEquals(expected.toString(), mutable.getZ().toString());
                assertEquals(15, mutable.getY());
                mutable.set(source).setWithOffset(mutable, offset, 3, offset);
                assertEquals(expected.toString(), mutable.getX().toString());
                assertEquals(expected.toString(), mutable.getZ().toString());
            }
        }
    }
}
