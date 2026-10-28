package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GenerationContext;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NoiseChunkTest {
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger MAX = MIN.negate().subtract(BigInteger.ONE);
    private static volatile double sink;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void interpolatedPositionsAreReusedOnlyWhileCellAndOffsetStillMatch() {
        for (Int1088 origin : new Int1088[]{Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(160),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX.subtractSaturated(Int1088.fromLong(15))}) {
            NoiseChunk chunk = chunk(origin, origin);
            assertNull(chunk.interpolatedBlockX());
            assertNull(chunk.interpolatedBlockZ());
            chunk.initializeForFirstCellX();
            chunk.advanceCellX(0);
            chunk.selectCellYZ(0, 0);
            Int1088 x = origin.addSaturated(Int1088.fromLong(3));
            Int1088 z = origin.addSaturated(Int1088.fromLong(2));
            chunk.updateForX(x, 0.75);
            chunk.updateForZ(z, 0.5);
            assertSame(x, chunk.interpolatedBlockX());
            assertSame(z, chunk.interpolatedBlockZ());
            chunk.forIndex(0);
            assertNull(chunk.interpolatedBlockX());
            assertNull(chunk.interpolatedBlockZ());
            chunk.updateForX(x, 0.75);
            chunk.updateForZ(z, 0.5);
            chunk.selectCellYZ(0, 1);
            assertNull(chunk.interpolatedBlockZ());
            chunk.stopInterpolation();
        }
    }

    @Test
    void cachedCoordinatesCarryBorrowSaturateAndShrinkWithoutStaleLimbs() throws Exception {
        NoiseChunk chunk = chunk(Int1088.ZERO, Int1088.ZERO);
        var xField = NoiseChunk.class.getDeclaredField("cellStartBlockX");
        var zField = NoiseChunk.class.getDeclaredField("cellStartBlockZ");
        xField.setAccessible(true);
        zField.setAccessible(true);
        for (int bit = 63; bit < 1088; bit += 64) {
            BigInteger seam = BigInteger.ONE.shiftLeft(bit);
            for (BigInteger origin : List.of(seam.subtract(BigInteger.ONE).min(MAX), seam.negate().max(MIN))) {
                xField.set(chunk, value(origin));
                zField.set(chunk, value(origin.negate().min(MAX)));
                for (long offset : new long[] {0, 1, -1, 7, -7, Long.MAX_VALUE, Long.MIN_VALUE, 0}) {
                    chunk.inCellX = offset;
                    chunk.inCellZ = offset;
                    assertAxis(origin.add(BigInteger.valueOf(offset)), chunk, true);
                    assertAxis(origin.negate().min(MAX).add(BigInteger.valueOf(offset)), chunk, false);
                    assertEquals(Blender.empty(), chunk.getBlender());
                }
            }
        }
        // Reusing the buffers for narrow cells must ignore their old upper words.
        xField.set(chunk, Int1088.fromLong(-1));
        zField.set(chunk, Int1088.ZERO);
        chunk.inCellX = 1;
        chunk.inCellZ = -1;
        assertAxis(BigInteger.ZERO, chunk, true);
        assertAxis(BigInteger.ONE.negate(), chunk, false);
    }

    @Test
    void allSamplingEntryPointsExposeExactCoordinatesInEveryPolicy() {
        for (GeneratorMode mode : GeneratorMode.values()) {
            for (CoordinatePrecision precision : CoordinatePrecision.values()) {
                try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                    for (BigInteger origin : List.of(BigInteger.ZERO, BigInteger.ONE.shiftLeft(127),
                            BigInteger.ONE.shiftLeft(1024).negate(), MIN, MAX.subtract(BigInteger.valueOf(15)))) {
                        NoiseChunk chunk = chunk(value(origin), value(origin));
                        chunk.initializeForFirstCellX();
                        chunk.advanceCellX(0);
                        chunk.selectCellYZ(0, 0);
                        double[] samples = new double[chunk.cellWidth * chunk.cellWidth * chunk.cellHeight];
                        DensityFunction checker = new DensityFunction.SimpleFunction() {
                            public double compute(DensityFunction.FunctionContext context) {
                                assertAxis(origin.add(BigInteger.valueOf(chunk.inCellX)), context, true);
                                assertAxis(origin.add(BigInteger.valueOf(chunk.inCellZ)), context, false);
                                assertEquals(chunk.cellStartBlockY + chunk.inCellY, context.blockY());
                                return context.blockY();
                            }
                            public double minValue() { return -Double.MAX_VALUE; }
                            public double maxValue() { return Double.MAX_VALUE; }
                            public KeyDispatchDataCodec<? extends DensityFunction> codec() { return DensityFunctions.Constant.CODEC; }
                        };
                        chunk.fillAllDirectly(samples, checker);
                        for (int index = 0; index < samples.length; ++index) {
                            assertSame(chunk, chunk.forIndex(index));
                            assertEquals(samples[index], checker.compute(chunk));
                        }
                        chunk.updateForX(value(origin.add(BigInteger.valueOf(3))), 0.75);
                        chunk.updateForZ(value(origin.add(BigInteger.valueOf(2))), 0.5);
                        checker.compute(chunk);
                        chunk.stopInterpolation();
                    }
                }
            }
        }
    }

    @Test
    void contextHelpersMatchValuePolicyForBothImplementations() {
        for (GeneratorMode mode : GeneratorMode.values()) {
            for (CoordinatePrecision precision : CoordinatePrecision.values()) {
                try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                    for (Int1088 origin : new Int1088[] {Int1088.fromLong(-17), Int1088.ONE.shiftLeftSaturated(127),
                            Int1088.ONE.shiftLeftSaturated(1024).negate(), Int1088.MIN, Int1088.MAX}) {
                        NoiseChunk chunk = chunk(origin, origin);
                        chunk.initializeForFirstCellX();
                        chunk.advanceCellX(0);
                        chunk.selectCellYZ(0, 0);
                        chunk.updateForX(origin, 0);
                        chunk.updateForZ(origin, 0);
                        for (DensityFunction.FunctionContext context : List.of(chunk, new DensityFunction.SinglePointContext(origin, 0, origin))) {
                            for (boolean x : new boolean[] {true, false}) {
                                assertEquals(origin, ContextCoordinates.copy(context, x));
                                assertEquals(ContextCoordinates.toDouble(origin), ContextCoordinates.toDouble(context, x));
                                assertEquals(ContextCoordinates.scaled(origin, 0.125, -0.75), ContextCoordinates.scaled(context, x, 0.125, -0.75));
                            }
                            assertEquals(0.25, context.getBlender().blendDensity(context, 0.25));
                        }
                        chunk.stopInterpolation();
                    }
                }
            }
        }
    }

    @Test
    void movingCoordinatesCacheMissesAndFlatLookupsDoNotAllocatePerSample() {
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        try (GenerationContext ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, CoordinatePrecision.PRECISE))) {
            for (Int1088 origin : new Int1088[] {Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(127),
                    Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX.subtractSaturated(Int1088.fromLong(15))}) {
                NoiseChunk chunk = chunk(origin, origin);
                chunk.initializeForFirstCellX();
                chunk.advanceCellX(0);
                chunk.selectCellYZ(0, 0);
                NoiseChunk.Cache2D cache = new NoiseChunk.Cache2D(DensityFunctions.constant(0.5));
                DensityFunction flat = chunk.wrap(DensityFunctions.flatCache(DensityFunctions.constant(0.25)));
                for (int i = 0; i < 100_000; ++i) sample(chunk, cache, flat, i);
                long thread = Thread.currentThread().getId();
                long before = bean.getThreadAllocatedBytes(thread);
                for (int i = 0; i < 10_000; ++i) sample(chunk, cache, flat, i);
                long allocated = bean.getThreadAllocatedBytes(thread) - before;
                assertTrue(allocated < 1024, "sample path allocated " + allocated + " bytes at " + origin);
                chunk.stopInterpolation();
            }
        }
    }

    private static void sample(NoiseChunk chunk, DensityFunction cache, DensityFunction flat, int i) {
        chunk.forIndex(i & 63);
        sink = cache.compute(chunk) + flat.compute(chunk) + ContextCoordinates.toDouble(chunk, true)
                + ContextCoordinates.scaled(chunk, false, 0.125, 0.5) + chunk.blockXLimb(16);
    }

    @Test
    void flatCacheUsesFullQuartAddressAndFallsBackOutsideItsWindow() {
        for (Int1088 origin : new Int1088[] {Int1088.ZERO, Int1088.fromLong(-16),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX.subtractSaturated(Int1088.fromLong(15))}) {
            NoiseChunk chunk = chunk(origin, origin);
            NoiseChunk.FlatCache flat = chunk.new FlatCache(DensityFunctions.constant(-1), false);
            for (int x = 0; x < flat.values.length; ++x) {
                for (int z = 0; z < flat.values.length; ++z) flat.values[x][z] = x * 10 + z;
            }
            BigInteger exactOrigin = new BigInteger(origin.toDecimalString());
            for (int dx : new int[] {-4, -1, 0, 1, 3, 4, 15, 16, 19, 20}) {
                for (int dz : new int[] {-4, 0, 3, 16, 20}) {
                    Int1088 x = value(exactOrigin.add(BigInteger.valueOf(dx)));
                    Int1088 z = value(exactOrigin.add(BigInteger.valueOf(dz)));
                    BigInteger qx = new BigInteger(x.toDecimalString()).shiftRight(2).subtract(exactOrigin.shiftRight(2));
                    BigInteger qz = new BigInteger(z.toDecimalString()).shiftRight(2).subtract(exactOrigin.shiftRight(2));
                    boolean inside = qx.signum() >= 0 && qz.signum() >= 0
                            && qx.compareTo(BigInteger.valueOf(flat.values.length)) < 0
                            && qz.compareTo(BigInteger.valueOf(flat.values.length)) < 0;
                    assertEquals(inside ? qx.intValue() * 10 + qz.intValue() : -1,
                            flat.compute(new DensityFunction.SinglePointContext(x, 0, z)));
                }
            }
            // Same low limbs but a different high address must never hit the flat array.
            Int1088 other = origin.equals(Int1088.MIN) ? Int1088.ZERO : Int1088.MIN;
            assertEquals(-1, flat.compute(new DensityFunction.SinglePointContext(other, 0, origin)));
        }
    }

    private static Int1088 value(BigInteger value) {
        return Int1088.parseDecimal(value.max(MIN).min(MAX).toString());
    }

    private static void assertAxis(BigInteger expected, DensityFunction.FunctionContext context, boolean x) {
        expected = expected.max(MIN).min(MAX);
        int length = 17;
        while (length > 1 && expected.shiftRight((length - 1) * 64).longValue()
                == (expected.shiftRight((length - 2) * 64).longValue() >> 63)) --length;
        assertEquals(length, x ? context.blockXLimbCount() : context.blockZLimbCount());
        assertEquals(length == 1, x ? context.blockXFitsLong() : context.blockZFitsLong());
        for (int limb = 0; limb <= 17; ++limb) {
            assertEquals(expected.shiftRight(limb * 64).longValue(), x ? context.blockXLimb(limb) : context.blockZLimb(limb));
        }
    }

    private static NoiseChunk chunk(Int1088 x, Int1088 z) {
        DensityFunction zero = DensityFunctions.zero();
        NoiseRouter router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                zero, zero, zero, zero, zero);
        NoiseGeneratorSettings base = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        NoiseGeneratorSettings settings = new NoiseGeneratorSettings(base.noiseSettings(), base.defaultBlock(),
                base.defaultFluid(), router, base.surfaceRule(), List.of(), 63, false, false, false, false);
        RandomState state = RandomState.create(settings, BuiltinRegistries.NOISE, 42L);
        return new NoiseChunk(4, state, x, z, settings.noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE,
                settings, (bx, y, bz) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
    }
}
