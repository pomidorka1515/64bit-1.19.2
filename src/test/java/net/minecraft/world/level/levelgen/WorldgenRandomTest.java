package net.minecraft.world.level.levelgen;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static net.minecraft.world.level.levelgen.WorldgenCoordinateInt1088Test.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldgenRandomTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void wideSeedMethodsPreserveFormulasAndSubsequentStreams() {
        for (BigInteger x : coordinates()) {
            BigInteger z = x.negate().max(MIN).min(MAX).add(BigInteger.valueOf(19)).min(MAX);
            for (long worldSeed : new long[]{0, -1, Long.MIN_VALUE, 123456789}) {
                long sx = fold(x), sz = fold(z);
                Random oracle = new Random(worldSeed);
                long decoration = sx * (oracle.nextLong() | 1L) + sz * (oracle.nextLong() | 1L) ^ worldSeed;
                WorldgenRandom actual = new WorldgenRandom(new LegacyRandomSource(0));
                assertEquals(decoration, actual.setDecorationSeed(worldSeed, value(x), value(z)));
                assertStream(new Random(decoration), actual);

                oracle.setSeed(worldSeed);
                long feature = sx * oracle.nextLong() ^ sz * oracle.nextLong() ^ worldSeed;
                actual.setLargeFeatureSeed(worldSeed, value(x), value(z));
                assertStream(new Random(feature), actual);

                long salt = 10387320;
                actual.setLargeFeatureWithSalt(worldSeed, value(x), value(z), salt);
                assertStream(new Random(sx * 341873128712L + sz * 132897987541L + worldSeed + salt), actual);
            }
        }
    }

    @Test
    void positionalFactoriesFoldExactBlockCoordinatesForBothAlgorithms() {
        for (WorldgenRandom.Algorithm algorithm : WorldgenRandom.Algorithm.values()) {
            PositionalRandomFactory factory = algorithm.newInstance(12345).forkPositional();
            for (BigInteger x : coordinates()) {
                BigInteger z = x.negate().max(MIN).min(MAX);
                for (int y : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
                    RandomSource expected = factory.at(fold(x), y, fold(z));
                    RandomSource wide = factory.at(value(x), y, value(z));
                    RandomSource block = factory.at(new BlockPos(value(x), y, value(z)));
                    for (int i = 0; i < 8; i++) {
                        long next = expected.nextLong();
                        assertEquals(next, wide.nextLong());
                        assertEquals(next, block.nextLong());
                    }
                }
            }
        }
    }

    @Test
    void randomSpreadUsesWideFloorRegionsAndSaturatedOrigins() {
        for (int spacing : new int[]{3, 32, 34, 4096}) {
            for (RandomSpreadType spread : RandomSpreadType.values()) {
                int separation = spacing / 3;
                RandomSpreadStructurePlacement placement = new RandomSpreadStructurePlacement(spacing, separation, spread, 1234);
                for (BigInteger x : coordinates()) {
                    BigInteger z = x.negate().max(MIN).min(MAX);
                    BigInteger rx = floor(x, spacing), rz = floor(z, spacing);
                    Random oracle = new Random(fold(rx) * 341873128712L + fold(rz) * 132897987541L + 42 + 1234);
                    int dx = offset(oracle, spacing - separation, spread);
                    int dz = offset(oracle, spacing - separation, spread);
                    BigInteger expectedX = clamp(clamp(rx.multiply(BigInteger.valueOf(spacing))).add(BigInteger.valueOf(dx)));
                    BigInteger expectedZ = clamp(clamp(rz.multiply(BigInteger.valueOf(spacing))).add(BigInteger.valueOf(dz)));
                    ChunkPos actual = placement.getPotentialStructureChunk(42, value(x), value(z));
                    // ChunkPos retains the pre-existing block-derived chunk domain.
                    assertEquals(clampChunk(expectedX).toString(), actual.x().toDecimalString());
                    assertEquals(clampChunk(expectedZ).toString(), actual.z().toDecimalString());
                    assertEquals(actual, placement.getPotentialStructureChunk(42, value(x), value(z)));
                }
            }
        }
    }

    private static int offset(Random random, int bound, RandomSpreadType spread) {
        return spread == RandomSpreadType.LINEAR ? random.nextInt(bound)
                : (random.nextInt(bound) + random.nextInt(bound)) / 2;
    }

    private static BigInteger floor(BigInteger n, int divisor) {
        BigInteger[] qr = n.divideAndRemainder(BigInteger.valueOf(divisor));
        return qr[1].signum() < 0 ? qr[0].subtract(BigInteger.ONE) : qr[0];
    }

    private static BigInteger clamp(BigInteger n) {
        return n.max(MIN).min(MAX);
    }

    private static BigInteger clampChunk(BigInteger n) {
        return n.max(MIN.shiftRight(4)).min(MAX.shiftRight(4));
    }

    private static void assertStream(Random expected, RandomSource actual) {
        for (int i = 0; i < 8; i++) assertEquals(expected.nextLong(), actual.nextLong());
    }

    private static List<BigInteger> coordinates() {
        List<BigInteger> values = new ArrayList<>(List.of(MIN, MAX, MIN.shiftRight(4), MAX.shiftRight(4),
                BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE.negate()));
        for (int bit : new int[]{53, 63, 64, 127, 128, 512, 1024, 1086}) {
            for (int sign : new int[]{-1, 1}) {
                for (int delta = -1; delta <= 1; delta++) {
                    values.add(BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign)).add(BigInteger.valueOf(delta)));
                }
            }
        }
        return values;
    }
}
