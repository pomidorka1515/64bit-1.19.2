package net.minecraft.core;

import static net.minecraft.core.Int1088Test.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int1088MathTest {
    private static final long SENTINEL = 0x123456789abcdefL;

    private static long[] view(Int1088 value, int offset, boolean redundant) {
        long[] out = new long[24];
        Arrays.fill(out, SENTINEL);
        int len = redundant ? 17 : value.limbCount();
        for (int i = 0; i < len; ++i) out[offset + i] = value.limb(i);
        return out;
    }

    private static void output(BigInteger expected, long[] out, int offset, int length) {
        exact(expected, Int1088.fromLimbs(out, offset, length));
        assertEquals(length, Int1088Math.canonicalLength(out, offset, length));
        assertEquals(SENTINEL, out[offset - 1]);
        assertEquals(SENTINEL, out[offset + 17]);
        Arrays.fill(out, SENTINEL);
    }

    @Test
    void tenThousandPrimitiveViewsMatchIndependentOracle() {
        Random random = new Random(0x1088b0ffL);
        System.out.println("Int1088Math view seed: 0x1088b0ff");
        long[] out = new long[24];
        for (int iteration = 0; iteration < 10000; ++iteration) {
            Int1088 a = random(random), b = random(random);
            BigInteger x = integer(a), y = integer(b);
            boolean redundant = (iteration & 1) == 0;
            int alen = redundant ? 17 : a.limbCount();
            int blen = redundant ? 17 : b.limbCount();
            long[] av = view(a, 2, redundant), bv = view(b, 3, redundant);
            long[] originalA = av.clone(), originalB = bv.clone();
            Arrays.fill(out, SENTINEL);
            output(wrap(x.add(y)), out, 4, Int1088Math.add(av, 2, alen, bv, 3, blen, out, 4));
            output(clamp(x.add(y)), out, 4, Int1088Math.addSaturated(av, 2, alen, bv, 3, blen, out, 4));
            output(wrap(x.subtract(y)), out, 4, Int1088Math.subtract(av, 2, alen, bv, 3, blen, out, 4));
            output(clamp(x.subtract(y)), out, 4, Int1088Math.subtractSaturated(av, 2, alen, bv, 3, blen, out, 4));
            long factor = random.nextLong();
            output(wrap(x.add(BigInteger.valueOf(factor))), out, 4, Int1088Math.addLong(av, 2, alen, factor, out, 4));
            output(clamp(x.multiply(BigInteger.valueOf(factor))), out, 4, Int1088Math.multiplySaturated(av, 2, alen, factor, out, 4));
            int shift = random.nextInt(1200);
            output(x.shiftRight(shift), out, 4, Int1088Math.shiftRight(av, 2, alen, shift, out, 4));
            output(clamp(x.shiftLeft(shift)), out, 4, Int1088Math.shiftLeftSaturated(av, 2, alen, shift, out, 4));
            int divisor = 1 + random.nextInt(Integer.MAX_VALUE);
            output(floor(x, BigInteger.valueOf(divisor)), out, 4, Int1088Math.floorDivide(av, 2, alen, divisor, out, 4));
            assertEquals(x.remainder(BigInteger.valueOf(divisor)).intValue(), Int1088Math.remainder(av, 2, alen, divisor));
            assertEquals(a.fitsLong(), Int1088Math.fitsLong(av, 2, alen));
            assertEquals(a.limbCount(), Int1088Math.canonicalLength(av, 2, alen));
            assertEquals(Integer.signum(x.compareTo(y)), Integer.signum(Int1088Math.compare(av, 2, alen, bv, 3, blen)));
            assertEquals(a.hash(), Int1088Math.hash(av, 2, alen));
            assertEquals(a.hashXZ(), Int1088Math.hashXZ(av, 2, alen));
            assertEquals(finiteDouble(x), Int1088Math.toDoubleRounded(av, 2, alen));
            assertEquals(finiteDouble(x), Int1088Math.toDoubleSaturated(av, 2, alen));
            assertArrayEquals(originalA, av);
            assertArrayEquals(originalB, bv);
            int inPlaceLength = Int1088Math.shiftRight(av, 2, alen, shift, av, 2);
            exact(x.shiftRight(shift), Int1088.fromLimbs(av, 2, inPlaceLength));
            assertEquals(SENTINEL, av[1]);
            assertEquals(SENTINEL, av[19]);

            // Unsigned product is allowed to grow into an eighteenth word; no sign extension.
            long[] unsigned = new long[17];
            for (int i = 0; i < 17; ++i) unsigned[i] = a.limb(i);
            int length = Int1088Math.multiplyUnsigned(unsigned, 0, 17, factor, out, 4);
            BigInteger actual = BigInteger.ZERO;
            for (int i = length - 1; i >= 0; --i) actual = actual.shiftLeft(64).add(new BigInteger(Long.toUnsignedString(out[4 + i])));
            BigInteger expected = x.and(MASK).multiply(new BigInteger(Long.toUnsignedString(factor)));
            assertEquals(expected, actual);
            assertTrue(length == 1 || out[4 + length - 1] != 0);
            assertEquals(SENTINEL, out[3]);
            assertEquals(SENTINEL, out[22]);
        }
    }

    @Test
    void boundaryViewsExerciseEveryCarryBorrowAndInPlaceShift() {
        long[] out = new long[24];
        for (BigInteger n : boundaries()) {
            Int1088 a = value(n);
            for (boolean redundant : new boolean[]{false, true}) {
                int len = redundant ? 17 : a.limbCount();
                long[] av = view(a, 2, redundant);
                for (int bits : new int[]{0, 1, 63, 64, 65, 127, 1023, 1087, 1088, Integer.MAX_VALUE}) {
                    long[] inPlace = av.clone();
                    int resultLength = Int1088Math.shiftRight(inPlace, 2, len, bits, inPlace, 2);
                    exact(n.shiftRight(bits), Int1088.fromLimbs(inPlace, 2, resultLength));
                }
                for (long b : new long[]{-1, 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                    long[] bv = {b};
                    Arrays.fill(out, SENTINEL);
                    output(clamp(n.add(BigInteger.valueOf(b))), out, 4, Int1088Math.addSaturated(av, 2, len, bv, 0, 1, out, 4));
                    output(clamp(n.subtract(BigInteger.valueOf(b))), out, 4, Int1088Math.subtractSaturated(av, 2, len, bv, 0, 1, out, 4));
                    output(clamp(n.multiply(BigInteger.valueOf(b))), out, 4, Int1088Math.multiplySaturated(av, 2, len, b, out, 4));
                }
            }
        }
    }

    @Test
    void primitiveDoubleFloorUsesOffsetsAndLeavesUnusedWordsAlone() {
        Random random = new Random(0x1088f100L);
        System.out.println("Int1088Math double seed: 0x1088f100");
        long[] out = new long[24];
        for (int i = 0; i < 10000; ++i) {
            double d = Double.longBitsToDouble(random.nextLong());
            BigInteger expected = Double.isNaN(d) ? BigInteger.ZERO : Double.isInfinite(d) ? d < 0 ? MIN : MAX
                    : new BigDecimal(d).setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
            Arrays.fill(out, SENTINEL);
            output(expected, out, 4, Int1088Math.fromDoubleFloor(d, out, 4));
        }
    }

    @Test
    void invalidPrimitiveArgumentsAreRejected() {
        long[] a = {1}, out = new long[20];
        assertThrows(IllegalArgumentException.class, () -> Int1088Math.canonicalLength(a, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> Int1088Math.canonicalLength(a, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Int1088Math.shiftRight(a, 0, 1, -1, out, 0));
        assertThrows(IllegalArgumentException.class, () -> Int1088Math.shiftLeftSaturated(a, 0, 1, -1, out, 0));
        assertThrows(IllegalArgumentException.class, () -> Int1088Math.floorDivide(a, 0, 1, 0, out, 0));
        assertThrows(IllegalArgumentException.class, () -> Int1088Math.remainder(a, 0, 1, -1));
    }
}
