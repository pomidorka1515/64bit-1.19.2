package net.minecraft.util;

import net.minecraft.world.phys.Vec3;

public class CubicSampler {
    private static final int GAUSSIAN_SAMPLE_RADIUS = 2;
    private static final int GAUSSIAN_SAMPLE_BREADTH = 6;
    private static final double[] GAUSSIAN_SAMPLE_KERNEL = new double[] {0.0D, 1.0D, 4.0D, 6.0D, 4.0D, 1.0D, 0.0D};

    private CubicSampler() {
    }

    public static Vec3 gaussianSampleVec3(Vec3 p_130039_, CubicSampler.Vec3Fetcher p_130040_) {
        long i = Mth.lfloor(p_130039_.x());
        int j = Mth.floor(p_130039_.y());
        long k = Mth.lfloor(p_130039_.z());
        double d0 = p_130039_.x() - (double) i;
        double d1 = p_130039_.y() - (double) j;
        double d2 = p_130039_.z() - (double) k;
        double d3 = 0.0D;
        double sumX = 0.0D;
        double sumY = 0.0D;
        double sumZ = 0.0D;
        Vec3 customSum = null;

        for (int l = 0; l < 6; ++l) {
            double d4 = Mth.lerp(d0, GAUSSIAN_SAMPLE_KERNEL[l + 1], GAUSSIAN_SAMPLE_KERNEL[l]);
            long i1 = i - 2L + (long) l;

            for (int j1 = 0; j1 < 6; ++j1) {
                double d5 = Mth.lerp(d1, GAUSSIAN_SAMPLE_KERNEL[j1 + 1], GAUSSIAN_SAMPLE_KERNEL[j1]);
                int k1 = j - 2 + j1;

                for (int l1 = 0; l1 < 6; ++l1) {
                    double d6 = Mth.lerp(d2, GAUSSIAN_SAMPLE_KERNEL[l1 + 1], GAUSSIAN_SAMPLE_KERNEL[l1]);
                    long i2 = k - 2L + (long) l1;
                    double d7 = d4 * d5 * d6;
                    d3 += d7;
                    Vec3 sample = p_130040_.fetch(i1, k1, i2);
                    if (sample.getClass() == Vec3.class && customSum == null) {
                        // Match scale followed by add, without two temporary vectors.
                        sumX += sample.x * d7;
                        sumY += sample.y * d7;
                        sumZ += sample.z * d7;
                    } else {
                        // Preserve virtual scale/add behavior for specialized vectors.
                        Vec3 sum = customSum == null ? new Vec3(sumX, sumY, sumZ) : customSum;
                        customSum = sum.add(sample.scale(d7));
                    }
                }
            }
        }

        double scale = 1.0D / d3;
        return customSum == null ? new Vec3(sumX * scale, sumY * scale, sumZ * scale) : customSum.scale(scale);
    }

    @FunctionalInterface
    public interface Vec3Fetcher {
        Vec3 fetch(long p_130042_, int p_130043_, long p_130044_);
    }
}
