package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;

class DensityArrayScratchTest {
    private static final DensityFunction.ContextProvider CONTEXT = new DensityFunction.ContextProvider() {
        public DensityFunction.FunctionContext forIndex(int index) {
            return new DensityFunction.SinglePointContext(Int1088.ZERO, index, Int1088.ZERO);
        }
        public void fillAllDirectly(double[] values, DensityFunction function) {
            for (int i = 0; i < values.length; ++i) values[i] = function.compute(forIndex(i));
        }
    };

    private static DensityFunction linear(double offset) {
        return new DensityFunction.SimpleFunction() {
            public double compute(DensityFunction.FunctionContext context) { return context.blockY() + offset; }
            public double minValue() { return -1000; }
            public double maxValue() { return 1000; }
            public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void nestedAddFillsMatchScalarEvaluationAcrossReusedSizes() {
        DensityFunction function = DensityFunctions.add(linear(1),
                DensityFunctions.add(linear(2), DensityFunctions.add(linear(3), linear(4))));
        for (int length : new int[] {33, 128, 33, 0, 128, 4097, 33}) {
            double[] values = new double[length];
            function.fillArray(values, CONTEXT);
            for (int i = 0; i < length; ++i) {
                assertEquals(function.compute(CONTEXT.forIndex(i)), values[i]);
            }
        }
    }

    @Test
    void checkedOutBuffersCannotAliasAndAreReusable() {
        double[] outer = DensityArrayScratch.acquire(33);
        double[] inner = DensityArrayScratch.acquire(33);
        assertNotSame(outer, inner);
        DensityArrayScratch.release(inner);
        assertSame(inner, DensityArrayScratch.acquire(33));
        DensityArrayScratch.release(inner);
        DensityArrayScratch.release(outer);
    }

    @Test
    void failedNestedFillReturnsScratchWithoutContaminatingLaterFills() {
        DensityFunction failing = new DensityFunction.SimpleFunction() {
            public double compute(DensityFunction.FunctionContext context) {
                throw new IllegalStateException("deliberate sample failure");
            }
            public double minValue() { return -1000; }
            public double maxValue() { return 1000; }
            public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
                throw new UnsupportedOperationException();
            }
        };
        double[] scratch = DensityArrayScratch.acquire(19);
        java.util.Arrays.fill(scratch, Double.NaN);
        DensityArrayScratch.release(scratch);
        var broken = DensityFunctions.add(linear(1), DensityFunctions.add(linear(2), failing));
        assertThrows(IllegalStateException.class, () -> broken.fillArray(new double[19], CONTEXT));
        double[] reused = DensityArrayScratch.acquire(19);
        assertSame(scratch, reused);
        DensityArrayScratch.release(reused);
        var healthy = DensityFunctions.add(linear(1), linear(2));
        double[] values = new double[19];
        healthy.fillArray(values, CONTEXT);
        for (int i = 0; i < values.length; ++i) assertEquals(2 * i + 3, values[i]);
    }
}
