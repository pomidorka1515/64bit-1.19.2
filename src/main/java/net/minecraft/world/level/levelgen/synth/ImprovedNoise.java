package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import java.util.Locale;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class ImprovedNoise {
    private static final float SHIFT_UP_EPSILON = 1.0E-7F;
    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;
    private static volatile Thread debugCaptureThread;
    private static double debugSamplePoint;
    private static long debugGridIndex;
    private static double debugCellOffset;
    private static double debugSmoothedOffset;

    public ImprovedNoise(RandomSource pRandom) {
        this.xo = pRandom.nextDouble() * 256.0D;
        this.yo = pRandom.nextDouble() * 256.0D;
        this.zo = pRandom.nextDouble() * 256.0D;
        this.p = new byte[256];

        for (int i = 0; i < 256; ++i) {
            this.p[i] = (byte) i;
        }

        for (int k = 0; k < 256; ++k) {
            int j = pRandom.nextInt(256 - k);
            byte b0 = this.p[k];
            this.p[k] = this.p[k + j];
            this.p[k + j] = b0;
        }
    }

    public double noise(double pX, double pY, double pZ) {
        return this.noise(pX, pY, pZ, 0.0D, 0.0D);
    }

    /** @deprecated */
    @Deprecated
    public double noise(double pX, double pY, double pZ, double pYScale, double pYMax) {
        return this.noise(pX, pY, pZ, pYScale, pYMax, FarlandsMode.isEnabled());
    }

    /** Selects the cell-floor arithmetic for legacy BlendedNoise sampling only. */
    double noise(double pX, double pY, double pZ, double pYScale, double pYMax, boolean useLegacy32BitFloor) {
        double d0 = pX + this.xo;
        double d1 = pY + this.yo;
        double d2 = pZ + this.zo;
        long i = FarlandsMode.floor(d0, useLegacy32BitFloor);
        long j = FarlandsMode.floor(d1, useLegacy32BitFloor);
        long k = FarlandsMode.floor(d2, useLegacy32BitFloor);
        double d3 = d0 - (double) i;
        double d4 = d1 - (double) j;
        double d5 = d2 - (double) k;
        if (Thread.currentThread() == debugCaptureThread) {
            debugSamplePoint = d0;
            debugSplitSample = false;
            debugGridIndex = i;
            debugCellOffset = d3;
            debugSmoothedOffset = Mth.smoothstep(d3);
        }

        double d6;
        if (pYScale != 0.0D) {
            double d7;
            if (pYMax >= 0.0D && pYMax < d4) {
                d7 = pYMax;
            } else {
                d7 = d4;
            }

            d6 = (double) Mth.floor(d7 / pYScale + (double) 1.0E-7F) * pYScale;
        } else {
            d6 = 0.0D;
        }

        return this.sampleAndLerp(i, j, k, d3, d4 - d6, d5, d4);
    }

    public double noise(long cellX, double fracX, long cellY, double fracY, long cellZ, double fracZ) {
        return this.noise(cellX, fracX, cellY, fracY, cellZ, fracZ, 0.0D, 0.0D);
    }

    double sampleRouted(PreciseNoiseCoordinate.Split x, PreciseNoiseCoordinate.Split y, PreciseNoiseCoordinate.Split z, double smearScale, double smearOffset) {
        double smear = 0.0D;
        if (smearScale != 0.0D) {
            double input = smearOffset >= 0.0D && smearOffset < y.fraction ? smearOffset : y.fraction;
            smear = (double) Mth.floor(input / smearScale + (double) SHIFT_UP_EPSILON) * smearScale;
        }
        if (Thread.currentThread() == debugCaptureThread) {
            debugSamplePoint = x.fraction;
            debugSplitSample = true;
            debugGridIndex = x.lattice;
            debugCellOffset = x.fraction;
            debugSmoothedOffset = Mth.smoothstep(x.fraction);
        }
        return sampleAndLerp(x.lattice, y.lattice, z.lattice, x.fraction, y.fraction - smear, z.fraction, y.fraction);
    }

    /** Precise split-coordinate variant retaining the legacy vertical smear operation. */
    public double noise(long cellX, double fracX, long cellY, double fracY, long cellZ, double fracZ, double smearScale, double smearOffset) {
        double x = fracX + this.xo;
        double y = fracY + this.yo;
        double z = fracZ + this.zo;
        long xCell = cellX + (long) Math.floor(x);
        long yCell = cellY + (long) Math.floor(y);
        long zCell = cellZ + (long) Math.floor(z);
        double fx = x - Math.floor(x);
        double fy = y - Math.floor(y);
        double fz = z - Math.floor(z);
        if (Thread.currentThread() == debugCaptureThread) {
            debugSamplePoint = fx;
            debugSplitSample = true;
            debugGridIndex = xCell;
            debugCellOffset = fx;
            debugSmoothedOffset = Mth.smoothstep(fx);
        }
        double smear = 0.0D;
        if (smearScale != 0.0D) {
            double smearInput = smearOffset >= 0.0D && smearOffset < fy ? smearOffset : fy;
            smear = (double) Mth.floor(smearInput / smearScale + (double) 1.0E-7F) * smearScale;
        }
        return this.sampleAndLerp(xCell, yCell, zCell, fx, fy - smear, fz, fy);
    }

    public double noiseWithDerivative(double pX, double pY, double pZ, double[] pValues) {
        double d0 = pX + this.xo;
        double d1 = pY + this.yo;
        double d2 = pZ + this.zo;
        boolean farlands = FarlandsMode.isEnabled();
        long i = FarlandsMode.floor(d0, farlands);
        long j = FarlandsMode.floor(d1, farlands);
        long k = FarlandsMode.floor(d2, farlands);
        double d3 = d0 - (double) i;
        double d4 = d1 - (double) j;
        double d5 = d2 - (double) k;
        return this.sampleWithDerivative(i, j, k, d3, d4, d5, pValues);
    }

    public static void beginDebugCapture() {
        debugSplitSample = false;
        debugSamplePoint = Double.NaN;
        debugCellOffset = Double.NaN;
        debugSmoothedOffset = Double.NaN;
        debugGridIndex = 0;
        debugCaptureThread = Thread.currentThread();
    }

    public static void endDebugCapture() {
        debugCaptureThread = null;
    }

    private static boolean debugSplitSample;

    public static String getDebugString() {
        if (Double.isNaN(debugSamplePoint)) return "Not evaluated at this position";
        String gridIndex = BlendedNoise.formatSplitDebugValue(debugGridIndex, 0.0D, 0);
        String sample = debugSplitSample ? BlendedNoise.formatSplitDebugValue(debugGridIndex, debugSamplePoint, 2)
                : BlendedNoise.formatDebugCoordinate(debugSamplePoint, 2);
        return String.format(Locale.ROOT, "Grid Sample point: %s index: %s", sample, gridIndex);
    }

    public static String getDebugString2() {
        return String.format(Locale.ROOT, "Cell Offset raw: %s smoothed: %s", BlendedNoise.formatDebugValue(debugCellOffset), BlendedNoise.formatDebugValue(debugSmoothedOffset));
    }

    private static double gradDot(int pGradIndex, double pXFactor, double pYFactor, double pZFactor) {
        return SimplexNoise.dot(SimplexNoise.GRADIENT[pGradIndex & 15], pXFactor, pYFactor, pZFactor);
    }

    private int p(long pIndex) {
        return this.p[(int) (pIndex & 255L)] & 255;
    }

    private double sampleAndLerp(long pGridX, long pGridY, long pGridZ, double pDeltaX, double pWeirdDeltaY, double pDeltaZ, double pDeltaY) {
        NoiseSampling.observeFractions(pDeltaX, pDeltaY, pDeltaZ);
        int i = this.p(pGridX);
        int j = this.p(pGridX + 1);
        int k = this.p(i + pGridY);
        int l = this.p(i + pGridY + 1);
        int i1 = this.p(j + pGridY);
        int j1 = this.p(j + pGridY + 1);
        double d0 = gradDot(this.p(k + pGridZ), pDeltaX, pWeirdDeltaY, pDeltaZ);
        double d1 = gradDot(this.p(i1 + pGridZ), pDeltaX - 1.0D, pWeirdDeltaY, pDeltaZ);
        double d2 = gradDot(this.p(l + pGridZ), pDeltaX, pWeirdDeltaY - 1.0D, pDeltaZ);
        double d3 = gradDot(this.p(j1 + pGridZ), pDeltaX - 1.0D, pWeirdDeltaY - 1.0D, pDeltaZ);
        double d4 = gradDot(this.p(k + pGridZ + 1), pDeltaX, pWeirdDeltaY, pDeltaZ - 1.0D);
        double d5 = gradDot(this.p(i1 + pGridZ + 1), pDeltaX - 1.0D, pWeirdDeltaY, pDeltaZ - 1.0D);
        double d6 = gradDot(this.p(l + pGridZ + 1), pDeltaX, pWeirdDeltaY - 1.0D, pDeltaZ - 1.0D);
        double d7 = gradDot(this.p(j1 + pGridZ + 1), pDeltaX - 1.0D, pWeirdDeltaY - 1.0D, pDeltaZ - 1.0D);
        double d8 = Mth.smoothstep(pDeltaX);
        double d9 = Mth.smoothstep(pDeltaY);
        double d10 = Mth.smoothstep(pDeltaZ);
        return Mth.lerp3(d8, d9, d10, d0, d1, d2, d3, d4, d5, d6, d7);
    }

    private double sampleWithDerivative(long pGridX, long pGridY, long pGridZ, double pDeltaX, double pDeltaY, double pDeltaZ, double[] pNoiseValues) {
        int i = this.p(pGridX);
        int j = this.p(pGridX + 1);
        int k = this.p(i + pGridY);
        int l = this.p(i + pGridY + 1);
        int i1 = this.p(j + pGridY);
        int j1 = this.p(j + pGridY + 1);
        int k1 = this.p(k + pGridZ);
        int l1 = this.p(i1 + pGridZ);
        int i2 = this.p(l + pGridZ);
        int j2 = this.p(j1 + pGridZ);
        int k2 = this.p(k + pGridZ + 1);
        int l2 = this.p(i1 + pGridZ + 1);
        int i3 = this.p(l + pGridZ + 1);
        int j3 = this.p(j1 + pGridZ + 1);
        int[] aint = SimplexNoise.GRADIENT[k1 & 15];
        int[] aint1 = SimplexNoise.GRADIENT[l1 & 15];
        int[] aint2 = SimplexNoise.GRADIENT[i2 & 15];
        int[] aint3 = SimplexNoise.GRADIENT[j2 & 15];
        int[] aint4 = SimplexNoise.GRADIENT[k2 & 15];
        int[] aint5 = SimplexNoise.GRADIENT[l2 & 15];
        int[] aint6 = SimplexNoise.GRADIENT[i3 & 15];
        int[] aint7 = SimplexNoise.GRADIENT[j3 & 15];
        double d0 = SimplexNoise.dot(aint, pDeltaX, pDeltaY, pDeltaZ);
        double d1 = SimplexNoise.dot(aint1, pDeltaX - 1.0D, pDeltaY, pDeltaZ);
        double d2 = SimplexNoise.dot(aint2, pDeltaX, pDeltaY - 1.0D, pDeltaZ);
        double d3 = SimplexNoise.dot(aint3, pDeltaX - 1.0D, pDeltaY - 1.0D, pDeltaZ);
        double d4 = SimplexNoise.dot(aint4, pDeltaX, pDeltaY, pDeltaZ - 1.0D);
        double d5 = SimplexNoise.dot(aint5, pDeltaX - 1.0D, pDeltaY, pDeltaZ - 1.0D);
        double d6 = SimplexNoise.dot(aint6, pDeltaX, pDeltaY - 1.0D, pDeltaZ - 1.0D);
        double d7 = SimplexNoise.dot(aint7, pDeltaX - 1.0D, pDeltaY - 1.0D, pDeltaZ - 1.0D);
        double d8 = Mth.smoothstep(pDeltaX);
        double d9 = Mth.smoothstep(pDeltaY);
        double d10 = Mth.smoothstep(pDeltaZ);
        double d11 = Mth.lerp3(d8, d9, d10, (double) aint[0], (double) aint1[0], (double) aint2[0], (double) aint3[0], (double) aint4[0], (double) aint5[0], (double) aint6[0], (double) aint7[0]);
        double d12 = Mth.lerp3(d8, d9, d10, (double) aint[1], (double) aint1[1], (double) aint2[1], (double) aint3[1], (double) aint4[1], (double) aint5[1], (double) aint6[1], (double) aint7[1]);
        double d13 = Mth.lerp3(d8, d9, d10, (double) aint[2], (double) aint1[2], (double) aint2[2], (double) aint3[2], (double) aint4[2], (double) aint5[2], (double) aint6[2], (double) aint7[2]);
        double d14 = Mth.lerp2(d9, d10, d1 - d0, d3 - d2, d5 - d4, d7 - d6);
        double d15 = Mth.lerp2(d10, d8, d2 - d0, d6 - d4, d3 - d1, d7 - d5);
        double d16 = Mth.lerp2(d8, d9, d4 - d0, d5 - d1, d6 - d2, d7 - d3);
        double d17 = Mth.smoothstepDerivative(pDeltaX);
        double d18 = Mth.smoothstepDerivative(pDeltaY);
        double d19 = Mth.smoothstepDerivative(pDeltaZ);
        double d20 = d11 + d17 * d14;
        double d21 = d12 + d18 * d15;
        double d22 = d13 + d19 * d16;
        pNoiseValues[0] += d20;
        pNoiseValues[1] += d21;
        pNoiseValues[2] += d22;
        return Mth.lerp3(d8, d9, d10, d0, d1, d2, d3, d4, d5, d6, d7);
    }

    @VisibleForTesting
    public void parityConfigString(StringBuilder p_192824_) {
        NoiseUtils.parityNoiseOctaveConfigString(p_192824_, this.xo, this.yo, this.zo, this.p);
    }
}
