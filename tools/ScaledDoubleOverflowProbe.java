import java.math.BigDecimal;
import java.math.BigInteger;

/** Exact scaled-integer conversion references, not measured sampler/world failures.
 * Run: java tools/ScaledDoubleOverflowProbe.java
 */
public final class ScaledDoubleOverflowProbe {
    public static void main(String[] args) {
        BigInteger threshold = BigInteger.ONE.shiftLeft(1024).subtract(BigInteger.ONE.shiftLeft(970));
        check("jagged X/Z", threshold, 1500, 1);
        check("ore_vein_a/b X/Y/Z", threshold, 4, 1);
        check("noodle_ridge_a/b X/Y/Z (stored binary64 scale)", threshold,
                BigInteger.valueOf(6004799503160661L), BigInteger.ONE.shiftLeft(51));
        check("spaghetti modulator/thickness/rarity X/Z", threshold, 2, 1);
        check("ore_veininess X/Y/Z", threshold, 3, 2);
        check("cave_entrance X/Z", threshold, 3, 4);
        check("zero-shift climate X/Z", threshold, 1, 4);
        if (!new BigDecimal(2.6666666666666665D).equals(
                new BigDecimal(BigInteger.valueOf(6004799503160661L))
                        .divide(new BigDecimal(BigInteger.ONE.shiftLeft(51))))) {
            throw new AssertionError("noodle stored scale ratio");
        }
    }

    private static void check(String label, BigInteger threshold, int numerator, int denominator) {
        check(label, threshold, BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    private static void check(String label, BigInteger threshold, BigInteger numerator, BigInteger denominator) {
        BigInteger divisor = numerator;
        BigInteger n = threshold.multiply(denominator)
                .add(divisor).subtract(BigInteger.ONE).divide(divisor);
        for (int sign : new int[]{1, -1}) {
            BigInteger signed = n.multiply(BigInteger.valueOf(sign));
            double at = scaled(signed, numerator, denominator);
            double inward = scaled(signed.subtract(BigInteger.valueOf(sign)), numerator, denominator);
            if (at != (sign > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY)
                    || !Double.isFinite(inward)) throw new AssertionError(label + " sign=" + sign);
        }
        int shift = Math.max(0, n.bitLength() - 53);
        double log2 = shift + Math.log(n.shiftRight(shift).doubleValue()) / Math.log(2);
        System.out.printf(java.util.Locale.ROOT, "%s: N=%s log2=%.6f (both signs, inward neighbors verified)%n", label, n, log2);
    }

    private static double scaled(BigInteger n, BigInteger numerator, BigInteger denominator) {
        // Scale exactly BEFORE the single binary64 rounding. Converting n first
        // and then multiplying a double is a different operation and threshold.
        return new BigDecimal(n.multiply(numerator))
                .divide(new BigDecimal(denominator)).doubleValue();
    }
}
