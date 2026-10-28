package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.synth.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DensityTerrainPipelineTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Int1088> addresses() {
        List<Int1088> values = new ArrayList<>(List.of(Int1088.ZERO, Int1088.fromLong(-17), Int1088.MAX, Int1088.MIN));
        for (int bit : new int[] {53, 63, 64, 127, 128, 1024, 1086}) {
            BigInteger n = BigInteger.ONE.shiftLeft(bit);
            for (int sign : new int[] {-1, 1}) {
                values.add(value(n.multiply(BigInteger.valueOf(sign)).add(BigInteger.ONE)));
            }
        }
        return values;
    }

    private static Int1088 value(BigInteger n) { return Int1088.parseDecimal(n.toString()); }
    private static BigInteger integer(Int1088 n) { return new BigInteger(n.toDecimalString()); }
    private static Int1088 offset(Int1088 n, int amount) {
        return value(integer(n).add(BigInteger.valueOf(amount)).max(BigInteger.ONE.shiftLeft(1087).negate())
                .min(BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE)));
    }
    private static long[] limbs(Int1088 n) {
        long[] result = new long[n.limbCount()];
        for (int i = 0; i < result.length; ++i) result[i] = n.limb(i);
        return result;
    }

    @Test void densityEntryPointsKeepExactScalingAndLegacyRouting() {
        NormalNoise noise = NormalNoise.create(new XoroshiroRandomSource(1234), -4, 1.0, 0.5);
        var holder = new DensityFunction.NoiseHolder(Holder.direct(new NormalNoise.NoiseParameters(-4, 1.0, 0.5)), noise);
        for (GeneratorMode mode : GeneratorMode.values()) {
            for (CoordinatePrecision precision : CoordinatePrecision.values()) {
                try (var ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                    for (Int1088 x : addresses()) {
                        Int1088 z = offset(x, -9);
                        long[] xs = limbs(x), zs = limbs(z);
                        var point = new DensityFunction.SinglePointContext(x, -37, z);
                        for (double scale : new double[] {0.25, 0.1, -0.3}) {
                            var plain = new DensityFunctions.Noise(holder, scale, 0.2);
                            var shifted = new DensityFunctions.ShiftedNoise(DensityFunctions.constant(0.125),
                                    DensityFunctions.constant(-0.75), DensityFunctions.constant(-0.375), scale, 0.2, holder);
                            boolean precise = precision == CoordinatePrecision.PRECISE && (!x.fitsLong() || !z.fitsLong()
                                    || PreciseNoiseCoordinate.needsPrecisePath(x.lowLimb()) || PreciseNoiseCoordinate.needsPrecisePath(z.lowLimb()));
                            double dx = x.fitsLong() ? FarlandsMode.scaledNoiseCoordinate(x.lowLimb(), scale) : ContextCoordinates.scaled(x, scale, 0);
                            double dz = z.fitsLong() ? FarlandsMode.scaledNoiseCoordinate(z.lowLimb(), scale) : ContextCoordinates.scaled(z, scale, 0);
                            double expected = precise ? noise.getValueScaled(xs, 0, xs.length, scale, -37, 0.2, zs, 0, zs.length)
                                    : noise.getValue(dx, -37 * 0.2, dz);
                            assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(plain.compute(point)), mode + " " + precision + " " + x);
                            expected = precise ? noise.getValueScaledShifted(xs, 0, xs.length, scale, 0.125, -37 * 0.2 - 0.75, zs, 0, zs.length, -0.375)
                                    : noise.getValue(ContextCoordinates.scaled(x, scale, 0.125), -37 * 0.2 - 0.75, ContextCoordinates.scaled(z, scale, -0.375));
                            assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(shifted.compute(point)));
                            // Wrappers must forward the original full-width context, not projected axes.
                            assertEquals(plain.compute(point), DensityFunctions.add(plain, DensityFunctions.constant(0)).compute(point));
                        }
                    }
                }
            }
        }
    }

    @Test void aquiferUsesFullColumnIdentityAndLocalCenterDistances() throws Exception {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var random = RandomState.create(settings, BuiltinRegistries.NOISE, 0);
        var water = new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState());
        var method = Aquifer.NoiseBasedAquifer.class.getDeclaredMethod("columnCoordinate", DensityFunction.FunctionContext.class, boolean.class);
        method.setAccessible(true);
        for (Int1088 address : addresses()) {
            Int1088 origin = value(integer(address).shiftRight(4).shiftLeft(4));
            Aquifer.FluidPicker picker = (x, y, z) -> water;
            var chunk = new NoiseChunk(4, random, origin, origin, settings.noiseSettings(),
                    DensityFunctions.BeardifierMarker.INSTANCE, settings, picker, Blender.empty());
            var aquifer = new Aquifer.NoiseBasedAquifer(chunk, new ChunkPos(origin.shiftRight(4), origin.shiftRight(4)),
                    random.router(), random.aquiferRandom(), -64, 384, picker);
            for (int local = 0; local < 16; ++local) {
                Int1088 x = offset(origin, local), z = offset(origin, 15 - local);
                var point = new DensityFunction.SinglePointContext(x, 100, z);
                var retainedX = method.invoke(aquifer, point, true);
                var retainedZ = method.invoke(aquifer, point, false);
                assertEquals(x, retainedX);
                assertEquals(z, retainedZ);
                assertSame(retainedX, method.invoke(aquifer, new DensityFunction.SinglePointContext(x.copy(), -50, z.copy()), true));
                assertSame(retainedZ, method.invoke(aquifer, point, false));
                // Repeated nearest-center queries must be stable at seams and both domain edges.
                var first = aquifer.computeSubstance(point, -1);
                boolean schedule = aquifer.shouldScheduleFluidUpdate();
                assertEquals(first, aquifer.computeSubstance(point, -1));
                assertEquals(schedule, aquifer.shouldScheduleFluidUpdate());
                assertNull(aquifer.computeSubstance(point, 1));
                assertFalse(aquifer.shouldScheduleFluidUpdate());
            }
            Int1088 remote = value(integer(origin).xor(BigInteger.ONE.shiftLeft(1024)));
            var point = new DensityFunction.SinglePointContext(remote, 0, origin);
            assertEquals(remote, method.invoke(aquifer, point, true), "Same low bits must not select another column");
        }
    }

    @Test void aquiferNearestCenterAndNoiseCellsMatchIndependentLocalGeometry() throws Exception {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        var random = RandomState.create(settings, BuiltinRegistries.NOISE, 0);
        List<BlockPos> picked = new ArrayList<>();
        List<BlockPos> seeded = new ArrayList<>();
        Aquifer.FluidPicker picker = (x, y, z) -> {
            picked.add(new BlockPos(x, y, z));
            return new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState());
        };
        PositionalRandomFactory factory = new PositionalRandomFactory() {
            public net.minecraft.util.RandomSource at(long x, int y, long z) { throw new AssertionError("Expected wide entry point"); }
            @Override public net.minecraft.util.RandomSource at(Int1088 x, int y, Int1088 z) {
                seeded.add(new BlockPos(x, y, z));
                return new LegacyRandomSource(0);
            }
            public net.minecraft.util.RandomSource fromHashOf(String name) { throw new UnsupportedOperationException(); }
            public void parityConfigString(StringBuilder builder) {}
        };
        var split = Aquifer.NoiseBasedAquifer.class.getDeclaredMethod("singlePointContext", long.class, int.class, long.class, int.class);
        split.setAccessible(true);
        var rng = new LegacyRandomSource(0);
        int jitterX = rng.nextInt(10), jitterY = rng.nextInt(9), jitterZ = rng.nextInt(10);
        for (Int1088 address : addresses()) {
            Int1088 origin = value(integer(address).shiftRight(4).shiftLeft(4));
            var chunk = new NoiseChunk(4, random, origin, origin, settings.noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE,
                    settings, picker, Blender.empty()) {
                @Override public int preliminarySurfaceLevel(Int1088 x, Int1088 z) { return 48; }
            };
            var aquifer = new Aquifer.NoiseBasedAquifer(chunk, new ChunkPos(origin.shiftRight(4), origin.shiftRight(4)),
                    random.router(), factory, -64, 384, picker);
            for (int shift : new int[] {4, 6}) {
                var point = (DensityFunction.SinglePointContext) split.invoke(aquifer, -19L, -2, 31L, shift);
                assertEquals(value(integer(offset(origin, -19)).shiftRight(shift)), point.blockX());
                assertEquals(value(integer(offset(origin, 31)).shiftRight(shift)), point.blockZ());
                assertEquals(-2, point.blockY());
            }
            for (int local = 0; local < 16; ++local) {
                picked.clear();
                seeded.clear();
                double best = Double.POSITIVE_INFINITY;
                int nearestX = 0, nearestY = 0, nearestZ = 0;
                int grid = Math.floorDiv(local - 5, 16);
                for (int gx = grid; gx <= grid + 1; ++gx) {
                    for (int gy = Math.floorDiv(101, 12) - 1; gy <= Math.floorDiv(101, 12) + 1; ++gy) {
                        for (int gz = grid; gz <= grid + 1; ++gz) {
                            int cx = gx * 16 + jitterX, cy = gy * 12 + jitterY, cz = gz * 16 + jitterZ;
                            double distance = (cx - local) * (cx - local) + (cy - 100) * (cy - 100) + (cz - local) * (cz - local);
                            if (best >= distance) {
                                best = distance;
                                nearestX = cx; nearestY = cy; nearestZ = cz;
                            }
                        }
                    }
                }
                var point = new DensityFunction.SinglePointContext(offset(origin, local), 100, offset(origin, local));
                aquifer.computeSubstance(point, -1);
                assertEquals(new BlockPos(point.blockX(), 100, point.blockZ()), picked.get(0));
                // The nearest status is cached after its first query; use a fresh aquifer per local sample.
                assertEquals(new BlockPos(offset(origin, nearestX), nearestY, offset(origin, nearestZ)), picked.get(1));
                assertEquals(12, seeded.size());
                for (BlockPos seed : seeded) {
                    BigInteger base = integer(origin).shiftRight(4);
                    BigInteger min = BigInteger.ONE.shiftLeft(1083).negate(), max = BigInteger.ONE.shiftLeft(1083).subtract(BigInteger.ONE);
                    assertTrue(seed.getX().equals(value(base.add(BigInteger.valueOf(grid)).max(min).min(max)))
                            || seed.getX().equals(value(base.add(BigInteger.valueOf(grid + 1)).max(min).min(max))));
                    assertTrue(seed.getZ().equals(value(base.add(BigInteger.valueOf(grid)).max(min).min(max)))
                            || seed.getZ().equals(value(base.add(BigInteger.valueOf(grid + 1)).max(min).min(max))));
                }
                aquifer = new Aquifer.NoiseBasedAquifer(chunk, new ChunkPos(origin.shiftRight(4), origin.shiftRight(4)),
                        random.router(), factory, -64, 384, picker);
            }
        }
    }

    @Test void surfaceCellCacheUsesFullOriginsAndSaturatedCorners() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var random = RandomState.create(settings.value(), BuiltinRegistries.NOISE, 0);
        var generator = new NoiseBasedChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, BuiltinRegistries.NOISE,
                new FixedBiomeSource(BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS)), settings);
        var height = LevelHeightAccessor.create(-64, 384);
        List<BlockPos> queries = new ArrayList<>();
        var chunk = new NoiseChunk(4, random, 0, 0, settings.value().noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE,
                settings.value(), (x, y, z) -> new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty()) {
            @Override public int preliminarySurfaceLevel(Int1088 x, Int1088 z) {
                queries.add(new BlockPos(x, 0, z));
                return 40;
            }
        };
        var system = new SurfaceSystem(random, Blocks.STONE.defaultBlockState(), 63, random.aquiferRandom()) {
            @Override protected int getSurfaceDepth(Int1088 x, Int1088 z) { return 3; }
        };
        var context = new SurfaceRules.Context(system, random, null, chunk, pos -> BuiltinRegistries.BIOME.getHolderOrThrow(Biomes.PLAINS),
                BuiltinRegistries.BIOME, new WorldGenerationContext(generator, height));
        for (Int1088 x : addresses()) {
            queries.clear();
            Int1088 z = offset(x, -7);
            context.updateXZ(x, z);
            assertEquals(35, context.getMinSurfaceLevel());
            Int1088 ox = value(integer(x).shiftRight(4).shiftLeft(4));
            Int1088 oz = value(integer(z).shiftRight(4).shiftLeft(4));
            assertEquals(List.of(new BlockPos(ox, 0, oz), new BlockPos(offset(ox, 16), 0, oz),
                    new BlockPos(ox, 0, offset(oz, 16)), new BlockPos(offset(ox, 16), 0, offset(oz, 16))), queries);
            context.updateXZ(x.copy(), z.copy());
            assertEquals(35, context.getMinSurfaceLevel());
            assertEquals(4, queries.size(), "Equal full-width cell must reuse corners");
        }
    }

    @Test void surfaceNoiseAndDepthRetainTheirModeSpecificArithmetic() {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        for (GeneratorMode mode : GeneratorMode.values()) {
            for (CoordinatePrecision precision : CoordinatePrecision.values()) {
                try (var ignored = GenerationContext.open(new GeneratorConfig(mode, precision))) {
                    var random = RandomState.create(settings, BuiltinRegistries.NOISE, 1234);
                    var factory = random.aquiferRandom();
                    var system = new SurfaceSystem(random, Blocks.STONE.defaultBlockState(), 63, factory);
                    var primary = random.getOrCreateNoise(Noises.SURFACE);
                    var secondary = random.getOrCreateNoise(Noises.SURFACE_SECONDARY);
                    for (Int1088 x : addresses()) {
                        Int1088 z = offset(x, -7);
                        double first = surfaceSample(primary, x, z, precision);
                        double second = surfaceSample(secondary, x, z, precision);
                        assertEquals(SurfaceArithmetic.depth(first, factory.at(x, 0, z).nextDouble()), system.getSurfaceDepth(x, z));
                        assertEquals(SurfaceArithmetic.noise(second), system.getSurfaceSecondary(x, z));
                    }
                }
            }
        }
    }

    private static double surfaceSample(NormalNoise noise, Int1088 x, Int1088 z, CoordinatePrecision precision) {
        if (precision == CoordinatePrecision.PRECISE && (!x.fitsLong() || !z.fitsLong()
                || PreciseNoiseCoordinate.needsPrecisePath(x.lowLimb()) || PreciseNoiseCoordinate.needsPrecisePath(z.lowLimb()))) {
            return noise.getValueScaled(x, 1, 0, 0, z);
        }
        // The existing surface projection has its own signed-long guard, even in legacy modes.
        double dx = Math.max(-0x1p63, Math.min(Math.nextDown(0x1p63), x.toDoubleRounded()));
        double dz = Math.max(-0x1p63, Math.min(Math.nextDown(0x1p63), z.toDoubleRounded()));
        return noise.getValue(dx, 0, dz);
    }

    @Test void carverStartsSubtractWideChunkOriginsBeforeProjection() throws Exception {
        var type = net.minecraft.world.level.levelgen.carver.WorldCarver.class;
        var start = type.getDeclaredMethod("startCoordinate", ChunkPos.class, ChunkPos.class, int.class, boolean.class);
        var localFrame = type.getDeclaredMethod("usesLocalFrame", ChunkPos.class);
        start.setAccessible(true);
        localFrame.setAccessible(true);
        for (Int1088 address : addresses()) {
            Int1088 targetAxis = value(integer(address).shiftRight(4));
            ChunkPos target = new ChunkPos(targetAxis, targetAxis);
            for (int delta : new int[] {-8, -1, 0, 1, 8}) {
                BigInteger min = BigInteger.ONE.shiftLeft(1083).negate(), max = BigInteger.ONE.shiftLeft(1083).subtract(BigInteger.ONE);
                Int1088 sourceAxis = value(integer(targetAxis).add(BigInteger.valueOf(delta)).max(min).min(max));
                ChunkPos source = new ChunkPos(sourceAxis, sourceAxis);
                for (int block : new int[] {0, 7, 15}) {
                    double expected = (boolean) localFrame.invoke(null, target)
                            ? integer(sourceAxis).subtract(integer(targetAxis)).doubleValue() * 16 + block
                            : integer(sourceAxis).shiftLeft(4).add(BigInteger.valueOf(block)).doubleValue();
                    assertEquals(expected, start.invoke(null, source, target, block, true));
                    assertEquals(expected, start.invoke(null, source, target, block, false));
                }
            }
        }
    }

    @Test void beardifierLocalGeometryIsTranslationInvariantWithoutUpperLimbAliases() {
        for (TerrainAdjustment adjustment : TerrainAdjustment.values()) {
            for (Int1088 address : addresses()) {
                // Leave room on both sides, including at MIN/MAX.
                Int1088 origin = address.equals(Int1088.MIN) ? offset(address, 32) : address.equals(Int1088.MAX) ? offset(address, -32) : address;
                Beardifier translated = beardifier(origin, adjustment);
                Beardifier narrow = beardifier(Int1088.ZERO, adjustment);
                for (int local = -14; local <= 14; ++local) {
                    double expected = narrow.compute(new DensityFunction.SinglePointContext(local, 67, -local));
                    assertEquals(expected, translated.compute(new DensityFunction.SinglePointContext(offset(origin, local), 67, offset(origin, -local))));
                }
                Int1088 remote = value(integer(origin).xor(BigInteger.ONE.shiftLeft(1024)));
                assertEquals(0, translated.compute(new DensityFunction.SinglePointContext(remote, 67, remote)));
            }
        }
    }

    private static Beardifier beardifier(Int1088 origin, TerrainAdjustment adjustment) {
        var pieces = new ObjectArrayList<Beardifier.Rigid>();
        pieces.add(new Beardifier.Rigid(new BoundingBox(offset(origin, -3), 64, offset(origin, -4), offset(origin, 3), 72, offset(origin, 4)), adjustment, 1));
        var junctions = new ObjectArrayList<JigsawJunction>();
        junctions.add(new JigsawJunction(origin, 65, origin, 0, net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID));
        return new Beardifier(pieces.iterator(), junctions.iterator());
    }

    @Test void endIslandTruncationRemaindersAndSizeUseTheWholeCoordinate() {
        boolean previous = EndRingsMode.isEnabled();
        try (var ignored = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, CoordinatePrecision.PRECISE))) {
            EndRingsMode.setEnabled(false);
            var density = new DensityFunctions.EndIslandDensityFunction(1234);
            var rng = new LegacyRandomSource(1234);
            rng.consumeCount(17292);
            var noise = new SimplexNoise(rng);
            for (Int1088 address : addresses()) {
                if (address.fitsLong()) continue;
                Int1088 z = offset(address, -19);
                BigInteger x8 = integer(address).divide(BigInteger.valueOf(8));
                BigInteger z8 = integer(z).divide(BigInteger.valueOf(8));
                BigInteger cx = x8.divide(BigInteger.TWO), cz = z8.divide(BigInteger.TWO);
                int rx = x8.remainder(BigInteger.TWO).intValue(), rz = z8.remainder(BigInteger.TWO).intValue();
                float expected = -100;
                for (int dx = -12; dx <= 12; ++dx) {
                    for (int dz = -12; dz <= 12; ++dz) {
                        BigInteger ax = cx.add(BigInteger.valueOf(dx)), az = cz.add(BigInteger.valueOf(dz));
                        if (ax.multiply(ax).add(az.multiply(az)).compareTo(BigInteger.valueOf(4096)) > 0
                                && noise.getValueAt(value(ax), value(az)) < (double) -0.9F) {
                            float size = (ax.remainder(BigInteger.valueOf(13)).abs().intValue() * 3439
                                    + az.remainder(BigInteger.valueOf(13)).abs().intValue() * 147) % 13 + 9;
                            float fx = rx - dx * 2, fz = rz - dz * 2;
                            expected = Math.max(expected, Mth.clamp(100 - Mth.sqrt(fx * fx + fz * fz) * size, -100, 80));
                        }
                    }
                }
                assertEquals((expected - 8.0) / 128.0, density.compute(new DensityFunction.SinglePointContext(address, 0, z)));
            }
        } finally {
            EndRingsMode.setEnabled(previous);
        }
    }
}
