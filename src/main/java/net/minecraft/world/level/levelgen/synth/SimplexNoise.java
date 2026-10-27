package net.minecraft.world.level.levelgen.synth;

import java.util.Locale;
import net.minecraft.core.Int1088;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class SimplexNoise {
    protected static final int[][] GRADIENT = new int[][] {{1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1}, {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}};
    private static final double SQRT_3 = Math.sqrt(3.0D);
    private static final double F2 = 0.5D * (SQRT_3 - 1.0D);
    private static final double G2 = (3.0D - SQRT_3) / 6.0D;
    private final int[] p = new int[512];
    public final double xo;
    public final double yo;
    public final double zo;
    private static final ThreadLocal<Boolean> DEBUG_CAPTURE = ThreadLocal.withInitial(() -> false);
    private static long debugSkewGrid;
    private static double debugCornerDeltaX;
    private static double debugCornerDeltaY;
    private static double debugSimplexOutput;

    public SimplexNoise(RandomSource pRandom) {
        this.xo = pRandom.nextDouble() * 256.0D;
        this.yo = pRandom.nextDouble() * 256.0D;
        this.zo = pRandom.nextDouble() * 256.0D;

        for (int i = 0; i < 256; this.p[i] = i++) {
        }

        for (int l = 0; l < 256; ++l) {
            int j = pRandom.nextInt(256 - l);
            int k = this.p[l];
            this.p[l] = this.p[j + l];
            this.p[j + l] = k;
        }
    }

    private int p(long pIndex) {
        return this.p[(int) (pIndex & 255L)];
    }

    protected static double dot(int[] pGradient, double pX, double pY, double pZ) {
        return (double) pGradient[0] * pX + (double) pGradient[1] * pY + (double) pGradient[2] * pZ;
    }

    private double getCornerNoise3D(int pGradientIndex, double pX, double pY, double pZ, double pOffset) {
        double d1 = pOffset - pX * pX - pY * pY - pZ * pZ;
        double d0;
        if (d1 < 0.0D) {
            d0 = 0.0D;
        } else {
            d1 *= d1;
            d0 = d1 * d1 * dot(GRADIENT[pGradientIndex], pX, pY, pZ);
        }

        return d0;
    }

    public double getValue(double pX, double pY) {
        double d0 = (pX + pY) * F2;
        boolean farlands = FarlandsMode.isEnabled();
        long i = FarlandsMode.floor(pX + d0, farlands);
        long j = FarlandsMode.floor(pY + d0, farlands);
        double d1 = FarlandsMode.floorSum(i, j, farlands) * G2;
        double d2 = (double) i - d1;
        double d3 = (double) j - d1;
        double d4 = pX - d2;
        double d5 = pY - d3;
        return this.getValue2D(i, j, d4, d5);
    }

    /** Samples integer positions without rounding away the skew lattice or local offsets. */
    public double getValueAt(long x, long y) {
        PreciseNoiseCoordinate.Split split = PreciseNoiseCoordinate.threadScratch();
        PreciseNoiseCoordinate.scale(x, F2, split);
        long skew = split.lattice;
        double fraction = split.fraction;
        PreciseNoiseCoordinate.scale(y, F2, split);
        skew += split.lattice;
        fraction += split.fraction;
        long carry = (long) Math.floor(fraction);
        skew += carry;
        fraction -= carry;

        // Both skewed coordinates have the same fraction for integer inputs.
        // Unskew these small fractions, never subtract huge global doubles.
        double delta = fraction * (1.0D - 2.0D * G2);
        return this.getValue2D(x + skew, y + skew, delta, delta);
    }

    private double getValue2D(long i, long j, double d4, double d5) {
        int k;
        int l;
        if (d4 > d5) {
            k = 1;
            l = 0;
        } else {
            k = 0;
            l = 1;
        }

        double d6 = d4 - (double) k + G2;
        double d7 = d5 - (double) l + G2;
        double d8 = d4 - 1.0D + 2.0D * G2;
        double d9 = d5 - 1.0D + 2.0D * G2;
        long i1 = i & 255L;
        long j1 = j & 255L;
        int k1 = this.p(i1 + this.p(j1)) % 12;
        int l1 = this.p(i1 + k + this.p(j1 + l)) % 12;
        int i2 = this.p(i1 + 1 + this.p(j1 + 1)) % 12;
        double d10 = this.getCornerNoise3D(k1, d4, d5, 0.0D, 0.5D);
        double d11 = this.getCornerNoise3D(l1, d6, d7, 0.0D, 0.5D);
        double d12 = this.getCornerNoise3D(i2, d8, d9, 0.0D, 0.5D);
        double d13 = 70.0D * (d10 + d11 + d12);
        if (DEBUG_CAPTURE.get()) {
            debugSkewGrid = i;
            debugCornerDeltaX = d4;
            debugCornerDeltaY = d5;
            debugSimplexOutput = d13;
        }

        return d13;
    }

    public double getValueAt(long xHi, long xLo, long yHi, long yLo) {
        if (WorldgenCoordinate.fitsLong(xHi, xLo) && WorldgenCoordinate.fitsLong(yHi, yLo)) return getValueAt(xLo, yLo);
        PreciseNoiseCoordinate.Split split = PreciseNoiseCoordinate.threadScratch();
        WorldgenCoordinate.scale(xHi, xLo, F2, 0, split);
        long skew = split.lattice;
        double fraction = split.fraction;
        WorldgenCoordinate.scale(yHi, yLo, F2, 0, split);
        skew += split.lattice;
        fraction += split.fraction;
        long carry = (long) Math.floor(fraction);
        skew += carry;
        fraction -= carry;
        double delta = fraction * (1.0D - 2.0D * G2);
        return getValue2D(xLo + skew, yLo + skew, delta, delta);
    }

    public double getValueAt(Int1088 x, Int1088 y) {
        if (x.fitsLong() && y.fitsLong()) return getValueAt(x.lowLimb(), y.lowLimb());
        PreciseNoiseCoordinate.Split split = PreciseNoiseCoordinate.threadScratch();
        WorldgenCoordinate.scale(x, F2, 0, split);
        long skew = split.lattice;
        double fraction = split.fraction;
        WorldgenCoordinate.scale(y, F2, 0, split);
        skew += split.lattice;
        fraction += split.fraction;
        long carry = (long)Math.floor(fraction);
        fraction -= carry;
        double delta = fraction * (1.0D - 2.0D * G2);
        return getValue2D(x.lowLimb() + skew + carry, y.lowLimb() + skew + carry, delta, delta);
    }

    public double getValueAt(long[] x, int xo, int xl, long[] y, int yo, int yl) {
        if (WorldgenCoordinate.fitsLong(x, xo, xl) && WorldgenCoordinate.fitsLong(y, yo, yl)) return getValueAt(x[xo], y[yo]);
        PreciseNoiseCoordinate.Split split = PreciseNoiseCoordinate.threadScratch();
        WorldgenCoordinate.scale(x, xo, xl, F2, 0, split);
        long skew = split.lattice;
        double fraction = split.fraction;
        WorldgenCoordinate.scale(y, yo, yl, F2, 0, split);
        skew += split.lattice;
        fraction += split.fraction;
        long carry = (long)Math.floor(fraction);
        fraction -= carry;
        double delta = fraction * (1.0D - 2.0D * G2);
        return getValue2D(x[xo] + skew + carry, y[yo] + skew + carry, delta, delta);
    }

    public double getValueScaled(long x, long z, double scale, int octave, double offsetX, double offsetZ) {
        return getValueScaled(x >> 63, x, z >> 63, z, scale, octave, offsetX, offsetZ);
    }

    public double getValueScaled(Int1088 x, Int1088 z, double scale, int octave, double offsetX, double offsetZ) {
        if (x.fitsLong() && z.fitsLong()) return getValueScaled(x.lowLimb(), z.lowLimb(), scale, octave, offsetX, offsetZ);
        PreciseNoiseCoordinate.Split split = PreciseNoiseCoordinate.threadScratch();
        WorldgenCoordinate.scale(x, scale, octave, split);
        long lx = split.lattice; double fx = split.fraction + offsetX;
        WorldgenCoordinate.scale(z, scale, octave, split);
        long lz = split.lattice; double fz = split.fraction + offsetZ;
        WorldgenCoordinate.scale(x, scale * F2, octave, split);
        long skew = split.lattice; double sf = split.fraction;
        WorldgenCoordinate.scale(z, scale * F2, octave, split);
        return scaledValue(lx, lz, fx, fz, skew + split.lattice, sf + split.fraction + (offsetX + offsetZ) * F2);
    }

    public double getValueScaled(long[] x, int xo, int xl, long[] z, int zo, int zl, double scale, int octave, double offsetX, double offsetZ) {
        if (WorldgenCoordinate.fitsLong(x, xo, xl) && WorldgenCoordinate.fitsLong(z, zo, zl)) return getValueScaled(x[xo], z[zo], scale, octave, offsetX, offsetZ);
        PreciseNoiseCoordinate.Split split = PreciseNoiseCoordinate.threadScratch();
        WorldgenCoordinate.scale(x, xo, xl, scale, octave, split);
        long lx = split.lattice; double fx = split.fraction + offsetX;
        WorldgenCoordinate.scale(z, zo, zl, scale, octave, split);
        long lz = split.lattice; double fz = split.fraction + offsetZ;
        WorldgenCoordinate.scale(x, xo, xl, scale * F2, octave, split);
        long skew = split.lattice; double sf = split.fraction;
        WorldgenCoordinate.scale(z, zo, zl, scale * F2, octave, split);
        return scaledValue(lx, lz, fx, fz, skew + split.lattice, sf + split.fraction + (offsetX + offsetZ) * F2);
    }

    private double scaledValue(long x, long z, double fx, double fz, long skew, double sf) {
        long ix = (long)Math.floor(fx + sf), iz = (long)Math.floor(fz + sf);
        double rx = fx + sf - ix, rz = fz + sf - iz;
        double unskew = (rx + rz) * G2;
        return getValue2D(x + skew + ix, z + skew + iz, rx - unskew, rz - unskew);
    }

    public double getValueScaled(long xHi, long xLo, long zHi, long zLo, double scale, int octave, double offsetX, double offsetZ) {
        PreciseNoiseCoordinate.Split split = PreciseNoiseCoordinate.threadScratch();
        WorldgenCoordinate.scale(xHi, xLo, scale, octave, split);
        long x = split.lattice;
        double fx = split.fraction + offsetX;
        WorldgenCoordinate.scale(zHi, zLo, scale, octave, split);
        long z = split.lattice;
        double fz = split.fraction + offsetZ;
        WorldgenCoordinate.scale(xHi, xLo, scale * F2, octave, split);
        long skew = split.lattice;
        double sf = split.fraction;
        WorldgenCoordinate.scale(zHi, zLo, scale * F2, octave, split);
        skew += split.lattice;
        sf += split.fraction + (offsetX + offsetZ) * F2;
        long ix = (long) Math.floor(fx + sf);
        long iz = (long) Math.floor(fz + sf);
        double rx = fx + sf - ix;
        double rz = fz + sf - iz;
        double unskew = (rx + rz) * G2;
        return getValue2D(x + skew + ix, z + skew + iz, rx - unskew, rz - unskew);
    }

    public static void beginDebugCapture() {
        DEBUG_CAPTURE.set(true);
    }

    public static void endDebugCapture() {
        DEBUG_CAPTURE.remove();
    }

    public static String getDebugString() {
        return String.format(Locale.ROOT, "Simplex: delta: (%.2f, %.2f) output: %s skew grid: %d",
                debugCornerDeltaX, debugCornerDeltaY, BlendedNoise.formatDebugValue(debugSimplexOutput), debugSkewGrid);
    }

    public static String getDebugString2() {
        return String.format(Locale.ROOT, "Corner Delta Y: %.2f Simplex Output: %s", debugCornerDeltaY, BlendedNoise.formatDebugValue(debugSimplexOutput));
    }

    public double getValue(double pX, double pY, double pZ) {
        double d0 = 0.3333333333333333D;
        double d1 = (pX + pY + pZ) * 0.3333333333333333D;
        boolean farlands = FarlandsMode.isEnabled();
        long i = FarlandsMode.floor(pX + d1, farlands);
        long j = FarlandsMode.floor(pY + d1, farlands);
        long k = FarlandsMode.floor(pZ + d1, farlands);
        double d2 = 0.16666666666666666D;
        double d3 = FarlandsMode.floorSum(i, j, k, farlands) * 0.16666666666666666D;
        double d4 = (double) i - d3;
        double d5 = (double) j - d3;
        double d6 = (double) k - d3;
        double d7 = pX - d4;
        double d8 = pY - d5;
        double d9 = pZ - d6;
        int l;
        int i1;
        int j1;
        int k1;
        int l1;
        int i2;
        if (d7 >= d8) {
            if (d8 >= d9) {
                l = 1;
                i1 = 0;
                j1 = 0;
                k1 = 1;
                l1 = 1;
                i2 = 0;
            } else if (d7 >= d9) {
                l = 1;
                i1 = 0;
                j1 = 0;
                k1 = 1;
                l1 = 0;
                i2 = 1;
            } else {
                l = 0;
                i1 = 0;
                j1 = 1;
                k1 = 1;
                l1 = 0;
                i2 = 1;
            }
        } else if (d8 < d9) {
            l = 0;
            i1 = 0;
            j1 = 1;
            k1 = 0;
            l1 = 1;
            i2 = 1;
        } else if (d7 < d9) {
            l = 0;
            i1 = 1;
            j1 = 0;
            k1 = 0;
            l1 = 1;
            i2 = 1;
        } else {
            l = 0;
            i1 = 1;
            j1 = 0;
            k1 = 1;
            l1 = 1;
            i2 = 0;
        }

        double d10 = d7 - (double) l + 0.16666666666666666D;
        double d11 = d8 - (double) i1 + 0.16666666666666666D;
        double d12 = d9 - (double) j1 + 0.16666666666666666D;
        double d13 = d7 - (double) k1 + 0.3333333333333333D;
        double d14 = d8 - (double) l1 + 0.3333333333333333D;
        double d15 = d9 - (double) i2 + 0.3333333333333333D;
        double d16 = d7 - 1.0D + 0.5D;
        double d17 = d8 - 1.0D + 0.5D;
        double d18 = d9 - 1.0D + 0.5D;
        long j2 = i & 255L;
        long k2 = j & 255L;
        long l2 = k & 255L;
        int i3 = this.p(j2 + this.p(k2 + this.p(l2))) % 12;
        int j3 = this.p(j2 + l + this.p(k2 + i1 + this.p(l2 + j1))) % 12;
        int k3 = this.p(j2 + k1 + this.p(k2 + l1 + this.p(l2 + i2))) % 12;
        int l3 = this.p(j2 + 1 + this.p(k2 + 1 + this.p(l2 + 1))) % 12;
        double d19 = this.getCornerNoise3D(i3, d7, d8, d9, 0.6D);
        double d20 = this.getCornerNoise3D(j3, d10, d11, d12, 0.6D);
        double d21 = this.getCornerNoise3D(k3, d13, d14, d15, 0.6D);
        double d22 = this.getCornerNoise3D(l3, d16, d17, d18, 0.6D);
        return 32.0D * (d19 + d20 + d21 + d22);
    }
}
