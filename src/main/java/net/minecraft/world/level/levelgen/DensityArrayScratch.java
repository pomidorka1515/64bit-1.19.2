package net.minecraft.world.level.levelgen;

import java.util.ArrayList;

/** Bounded per-worker scratch pool. Checked-out arrays are never shared by nested fills. */
final class DensityArrayScratch {
    private static final ThreadLocal<ArrayList<double[]>> FREE = ThreadLocal.withInitial(ArrayList::new);

    private DensityArrayScratch() {}

    static double[] acquire(int length) {
        ArrayList<double[]> free = FREE.get();
        for (int i = free.size() - 1; i >= 0; --i) {
            if (free.get(i).length == length) return free.remove(i);
        }
        return new double[length];
    }

    static void release(double[] array) {
        // Fills overwrite every element; retaining large/unusual requests is not worthwhile.
        ArrayList<double[]> free = FREE.get();
        if (array.length <= 4096 && free.size() < 16) free.add(array);
    }
}
