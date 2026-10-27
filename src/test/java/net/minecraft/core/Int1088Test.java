package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.HashCommon;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

class Int1088Test {
    static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    static final BigInteger MAX = MIN.negate().subtract(BigInteger.ONE);
    static final BigInteger MODULUS = BigInteger.ONE.shiftLeft(1088);
    static final BigInteger MASK = MODULUS.subtract(BigInteger.ONE);

    static BigInteger integer(Int1088 value) {
        byte[] bytes = new byte[136];
        for (int i = 0; i < bytes.length; ++i) bytes[bytes.length - 1 - i] = (byte) (value.limb(i / 8) >>> ((i % 8) * 8));
        return new BigInteger(bytes);
    }

    static Int1088 value(BigInteger integer) {
        long[] words = new long[17];
        for (int i = 0; i < words.length; ++i) words[i] = integer.shiftRight(i * 64).longValue();
        return Int1088.fromFixedLimbs(words);
    }

    static BigInteger clamp(BigInteger value) {
        return value.max(MIN).min(MAX);
    }

    static BigInteger wrap(BigInteger value) {
        value = value.and(MASK);
        return value.testBit(1087) ? value.subtract(MODULUS) : value;
    }

    static BigInteger floor(BigInteger a, BigInteger b) {
        BigInteger[] qr = a.divideAndRemainder(b);
        return qr[1].signum() != 0 && a.signum() != b.signum() ? qr[0].subtract(BigInteger.ONE) : qr[0];
    }

    static void exact(BigInteger expected, Int1088 actual) {
        assertEquals(expected, integer(actual));
        assertTrue(actual.limbCount() >= 1 && actual.limbCount() <= 17);
        if (actual.limbCount() > 1) assertNotEquals(actual.limb(actual.limbCount() - 2) >> 63, actual.limb(actual.limbCount() - 1));
    }

    static List<BigInteger> boundaries() {
        List<BigInteger> values = new ArrayList<>(List.of(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE.negate(), MIN, MAX));
        for (int bit = 0; bit < 1088; ++bit) {
            if (bit % 64 > 1 && bit % 64 < 62 && bit != 53 && bit != 1024) continue;
            BigInteger power = BigInteger.ONE.shiftLeft(bit);
            for (int offset = -1; offset <= 1; ++offset) {
                BigInteger n = power.add(BigInteger.valueOf(offset));
                if (n.compareTo(MAX) <= 0) values.add(n);
                if (n.negate().compareTo(MIN) >= 0) values.add(n.negate());
            }
        }
        return values;
    }

    static Int1088 random(Random random) {
        long[] words = new long[1 + random.nextInt(17)];
        for (int i = 0; i < words.length; ++i) words[i] = random.nextLong();
        return Int1088.of(words);
    }

    @Test
    void canonicalConstructionIsDefensiveAndSignExtended() {
        exact(MIN, Int1088.MIN);
        exact(MAX, Int1088.MAX);
        assertEquals(2, value(BigInteger.ONE.shiftLeft(63)).limbCount());
        assertEquals(1, Int1088.fromLong(Long.MIN_VALUE).limbCount());
        assertEquals(3, value(BigInteger.ONE.shiftLeft(127)).limbCount());
        assertEquals(2, value(BigInteger.ONE.shiftLeft(127).negate()).limbCount());
        for (BigInteger n : boundaries()) {
            Int1088 v = value(n);
            exact(n, v);
            assertEquals(n.abs().bitLength(), v.bitLength());
            assertEquals(n.signum(), v.signum());
            assertEquals(n.bitLength() <= 63, v.fitsLong());
            long[] words = new long[21];
            v.copyFixed(words, 2);
            Int1088 copied = Int1088.fromLimbs(words, 2, 17);
            words[2] ^= 123;
            assertEquals(v, copied);
            assertEquals(v, v.copy());
            assertNotSame(v, v.copy());
            assertEquals(n.signum() < 0 ? -1L : 0L, v.limb(16) >> 63);
        }
        long[] source = {Long.MIN_VALUE, 0};
        Int1088 v = Int1088.of(source);
        source[0] = 0;
        exact(BigInteger.ONE.shiftLeft(63), v);
        exact(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE), Int1088.fromUnsignedLong(-1));
        assertEquals(Int1088.ZERO, Int1088.of(0, 0, 0));
        assertEquals(Int1088.fromLong(-1), Int1088.of(-1, -1, -1));
    }

    @Test
    void rejectsInvalidConstructionAndIndices() {
        assertThrows(IllegalArgumentException.class, () -> Int1088.of());
        assertThrows(IllegalArgumentException.class, () -> Int1088.of(new long[18]));
        assertThrows(IllegalArgumentException.class, () -> Int1088.fromFixedLimbs(new long[16]));
        assertThrows(IndexOutOfBoundsException.class, () -> Int1088.fromLimbs(new long[17], 1, 17));
        assertThrows(IndexOutOfBoundsException.class, () -> Int1088.ONE.limb(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> Int1088.ONE.limb(17));
        assertThrows(IndexOutOfBoundsException.class, () -> Int1088.ONE.testBit(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> Int1088.ONE.testBit(1088));
        assertThrows(IndexOutOfBoundsException.class, () -> Int1088.ONE.copyFixed(new long[17], 1));
        assertThrows(IllegalArgumentException.class, () -> Int1088.ONE.shiftRight(-1));
        assertThrows(IllegalArgumentException.class, () -> Int1088.ONE.shiftRightUnsigned(-1));
        assertThrows(IllegalArgumentException.class, () -> Int1088.ONE.shiftLeftSaturated(-1));
        assertThrows(IllegalArgumentException.class, () -> Int1088.ONE.floorDivideSaturated(0));
        assertThrows(IllegalArgumentException.class, () -> Int1088.ONE.remainder(-1));
        assertThrows(ArithmeticException.class, () -> Int1088.ONE.floorDivideSaturated(Int1088.ZERO));
    }

    @Test
    void boundaryArithmeticCarriesBorrowsShiftsAndSaturation() {
        int[] shifts = {0, 1, 63, 64, 65, 127, 128, 1023, 1024, 1087, 1088, Integer.MAX_VALUE};
        long[] factors = {0, 1, -1, 2, -2, Long.MIN_VALUE, Long.MAX_VALUE, (1L << 53) - 1};
        for (BigInteger a : boundaries()) {
            Int1088 v = value(a);
            for (long b : new long[]{-1, 1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                exact(clamp(a.add(BigInteger.valueOf(b))), v.addSaturated(Int1088.fromLong(b)));
                exact(clamp(a.subtract(BigInteger.valueOf(b))), v.subtractSaturated(Int1088.fromLong(b)));
            }
            exact(clamp(a.negate()), v.negate());
            exact(clamp(a.abs()), v.absSaturated());
            exact(BigInteger.ZERO, v.subtractSaturated(v));
            for (int shift : shifts) {
                exact(a.shiftRight(shift), v.shiftRight(shift));
                BigInteger left = shift >= 1088 ? a.signum() == 0 ? BigInteger.ZERO : a.signum() < 0 ? MIN : MAX : clamp(a.shiftLeft(shift));
                exact(left, v.shiftLeftSaturated(shift));
                if (shift != 0) exact(a.and(MASK).shiftRight(shift), v.shiftRightUnsigned(shift));
            }
            for (long factor : factors) exact(clamp(a.multiply(BigInteger.valueOf(factor))), v.multiplySaturated(factor));
            exact(clamp(a.multiply(BigInteger.valueOf(13))), v.multiplySaturated(13));
        }
        assertEquals(Int1088.MAX, Int1088.MIN.negate());
        assertEquals(Int1088.MIN, Int1088.MIN.subtractSaturated(Int1088.ONE));
        assertEquals(Int1088.MAX, Int1088.MAX.addSaturated(Int1088.ONE));
        assertEquals(Int1088.MIN, Int1088.MIN.addSaturated(Int1088.MIN));
        assertEquals(Int1088.MAX, Int1088.MAX.subtractSaturated(Int1088.MIN));
        assertEquals(Int1088.ONE, Int1088.ONE.shiftRightUnsigned(0));
    }

    @Test
    void decimalAndHexRoundTripAndRejectMalformedText() {
        for (BigInteger n : boundaries()) {
            Int1088 v = value(n);
            assertEquals(n.toString(), v.toDecimalString());
            assertEquals(n.toString(), v.toString());
            assertEquals(v, Int1088.parseDecimal(n.toString()));
            String hex = String.format(Locale.ROOT, "%0272x", n.and(MASK));
            assertEquals(hex, v.toHex());
            assertEquals(v, Int1088.parseHex(hex));
            assertEquals(v, Int1088.parseHex(hex.toUpperCase(Locale.ROOT)));
        }
        assertEquals(Int1088.ZERO, Int1088.parseDecimal("-0"));
        assertEquals(Int1088.ONE, Int1088.parseDecimal("+" + "0".repeat(2000) + "1"));
        for (String invalid : new String[]{null, "", "+", "-", " 1", "1 ", "1.0", "1e2", "1_0", "1-2", "--1", "１２", MAX.add(BigInteger.ONE).toString(), MIN.subtract(BigInteger.ONE).toString(), "9".repeat(5000)}) {
            assertThrows(NumberFormatException.class, () -> Int1088.parseDecimal(invalid));
        }
        for (String invalid : new String[]{null, "", "0".repeat(271), "0".repeat(273), "g" + "0".repeat(271), "０".repeat(272)}) {
            assertThrows(NumberFormatException.class, () -> Int1088.parseHex(invalid));
        }
    }

    @Test
    void orderingAndFullWidthHashesMatchSpecifiedFold() {
        List<BigInteger> sorted = boundaries().stream().distinct().sorted().toList();
        HashSet<Long> hashes = new HashSet<>();
        HashSet<Integer> foldedHashes = new HashSet<>();
        Int1088 previous = null;
        for (BigInteger n : sorted) {
            Int1088 v = value(n);
            if (previous != null) assertTrue(previous.compareTo(v) < 0);
            previous = v;
            long h = 0xcbf29ce484222325L;
            for (int i = 0; i < 17; ++i) {
                long mixed = HashCommon.murmurHash3(n.shiftRight(i * 64).longValue() + 0x9e3779b97f4a7c15L * (i + 1));
                h = (h ^ mixed) * 0x100000001b3L;
            }
            h = HashCommon.murmurHash3(h);
            assertEquals(h, v.hash());
            assertEquals(Long.hashCode(h), v.hashCode());
            assertEquals(Long.hashCode(h), v.hashXZ());
            assertTrue(hashes.add(v.hash()), "boundary hash collision: " + n);
            assertTrue(foldedHashes.add(v.hashXZ()), "boundary folded hash collision: " + n);
            assertEquals(v.hashCode(), v.copy().hashCode());
            assertNotEquals(v, new Object());
            assertNotEquals(v, null);
        }
    }

    @Test
    void smallAndGeneralDivisionUseMathematicalFloor() {
        for (BigInteger a : boundaries()) {
            Int1088 v = value(a);
            for (int divisor = 1; divisor <= 256; ++divisor) {
                BigInteger d = BigInteger.valueOf(divisor);
                exact(floor(a, d), v.floorDivideSaturated(divisor));
                assertEquals(a.remainder(d).intValue(), v.remainder(divisor));
            }
            for (BigInteger b : List.of(MIN, MAX, BigInteger.ONE, BigInteger.ONE.negate(), BigInteger.valueOf(13), BigInteger.valueOf(-13), BigInteger.valueOf(Integer.MAX_VALUE))) {
                exact(clamp(floor(a, b)), v.floorDivideSaturated(value(b)));
            }
        }
    }

    static double finiteDouble(BigInteger n) {
        double d = n.doubleValue();
        return Double.isInfinite(d) ? Math.copySign(Double.MAX_VALUE, d) : d;
    }

    static float finiteFloat(BigInteger n) {
        float f = n.floatValue();
        return Float.isInfinite(f) ? Math.copySign(Float.MAX_VALUE, f) : f;
    }

    @Test
    void doubleFloorHandlesEveryExponentAndSpecialValue() {
        assertEquals(Int1088.ZERO, Int1088.fromDoubleFloorSaturated(Double.NaN));
        assertEquals(Int1088.MAX, Int1088.fromDoubleFloorSaturated(Double.POSITIVE_INFINITY));
        assertEquals(Int1088.MIN, Int1088.fromDoubleFloorSaturated(Double.NEGATIVE_INFINITY));
        List<Double> values = new ArrayList<>(List.of(0.0, -0.0, 0.5, -0.5, 1.5, -1.5, Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE));
        for (int exponent = -1074; exponent <= 1023; ++exponent) {
            double d = Math.scalb(1.0, exponent);
            for (double x : new double[]{Math.nextDown(d), d, Math.nextUp(d)}) {
                values.add(x);
                values.add(-x);
            }
        }
        Random random = new Random(0x1088d0b1L);
        System.out.println("Int1088 double seed: 0x1088d0b1");
        for (int i = 0; i < 10000; ++i) {
            double d = Double.longBitsToDouble(random.nextLong());
            if (Double.isFinite(d)) values.add(d);
        }
        for (double d : values) {
            BigInteger n = new BigDecimal(d).setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
            Int1088 v = Int1088.fromDoubleFloorSaturated(d);
            exact(n, v);
            assertEquals(finiteDouble(n), v.toDoubleRounded());
            assertEquals(finiteDouble(n), v.toDoubleSaturated());
        }
    }

    @Test
    void roundingTiesAndStickyBitsMatchIndependentOracle() {
        for (int precision : new int[]{24, 53}) {
            for (int shift = 1; shift <= 1087 - precision; ++shift) {
                for (int low = 0; low <= 1; ++low) {
                    BigInteger retained = BigInteger.ONE.shiftLeft(precision - 1).add(BigInteger.valueOf(low));
                    BigInteger tie = retained.shiftLeft(shift).add(BigInteger.ONE.shiftLeft(shift - 1));
                    for (int offset = -1; offset <= 1; ++offset) {
                        BigInteger a = tie.add(BigInteger.valueOf(offset));
                        for (BigInteger n : List.of(a, a.negate())) {
                            Int1088 v = value(n);
                            assertEquals(finiteDouble(n), v.toDoubleRounded());
                            assertEquals(finiteFloat(n), v.toFloatRounded());
                        }
                    }
                }
            }
        }
        for (BigInteger n : boundaries()) {
            assertEquals(finiteDouble(n), value(n).toDoubleRounded());
            assertEquals(finiteFloat(n), value(n).toFloatRounded());
        }
    }

    @Test
    void tenThousandSeededArithmeticProperties() {
        Random random = new Random(0x1088a11L);
        System.out.println("Int1088 arithmetic seed: 0x1088a11");
        for (int i = 0; i < 10000; ++i) {
            Int1088 a = random(random), b = random(random);
            BigInteger x = integer(a), y = integer(b);
            exact(clamp(x.add(y)), a.addSaturated(b));
            exact(clamp(x.subtract(y)), a.subtractSaturated(b));
            exact(wrap(x.add(y)), a.add(b));
            exact(wrap(x.subtract(y)), a.subtract(b));
            exact(clamp(x.negate()), a.negate());
            exact(x.and(y), a.and(b));
            exact(x.or(y), a.or(b));
            exact(x.xor(y), a.xor(b));
            exact(x.not(), a.not());
            int bit = random.nextInt(1088);
            assertEquals(x.testBit(bit), a.testBit(bit));
            int shift = random.nextInt(1200);
            exact(x.shiftRight(shift), a.shiftRight(shift));
            exact(clamp(x.shiftLeft(shift)), a.shiftLeftSaturated(shift));
            if (shift != 0) exact(x.and(MASK).shiftRight(shift), a.shiftRightUnsigned(shift));
            long factor = random.nextLong();
            exact(clamp(x.multiply(BigInteger.valueOf(factor))), a.multiplySaturated(factor));
            int divisor = 1 + random.nextInt(Integer.MAX_VALUE);
            exact(floor(x, BigInteger.valueOf(divisor)), a.floorDivideSaturated(divisor));
            assertEquals(x.remainder(BigInteger.valueOf(divisor)).intValue(), a.remainder(divisor));
            if (y.signum() != 0) exact(clamp(floor(x, y)), a.floorDivideSaturated(b));
            assertEquals(Integer.signum(x.compareTo(y)), Integer.signum(a.compareTo(b)));
            assertEquals(x.toString(), a.toDecimalString());
            assertEquals(a, Int1088.parseDecimal(x.toString()));
            assertEquals(a, Int1088.parseHex(a.toHex()));
            assertEquals(finiteDouble(x), a.toDoubleRounded());
            assertEquals(finiteFloat(x), a.toFloatRounded());
        }
    }
}
