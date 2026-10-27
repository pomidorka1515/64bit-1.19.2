package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class WorldBoundsTest {
    private static volatile long productSink;
    private static final BigInteger MIN_1088 = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger MAX_1088 = BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE);

    private static BigInteger wide(Int1088 value) {
        return new BigInteger(value.toString());
    }

    private static BigInteger clamp1088(BigInteger value) {
        return value.max(MIN_1088).min(MAX_1088);
    }

    private static Int1088 randomCoordinate(Random random) {
        long[] limbs = new long[Int1088.LIMBS];
        for (int i = 0; i < limbs.length; ++i) limbs[i] = random.nextLong();
        return Int1088.fromFixedLimbs(limbs);
    }

    private static long clampLong(BigInteger value) {
        return value.max(BigInteger.valueOf(Long.MIN_VALUE)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    @Test
    void relativeDifferencesUseTheSpanRatherThanLimbEquality() {
        Int1088 boundary = Int1088.of(0L, 123L);
        Int1088 before = boundary.subtract(Int1088.ONE);
        assertEquals(1.0D, WorldBounds.signedDifferenceExact(boundary, before));
        assertEquals(-1.0D, WorldBounds.signedDifferenceExact(before, boundary));
        assertEquals(-2.0D, WorldBounds.signedDifference(Int1088.of(3L, 5L), Int1088.of(5L, 5L)));
        assertEquals(Long.MAX_VALUE, WorldBounds.signedDifferenceAsLong(Int1088.of(Long.MIN_VALUE, 0L), Int1088.ZERO));
        assertEquals(Long.MIN_VALUE, WorldBounds.signedDifferenceAsLong(Int1088.MIN, Int1088.MAX));
        boolean[] exact = new boolean[1];
        for (long delta : new long[] {-WorldBounds.MAX_SAFE_LOCAL_SPAN - 1L, -WorldBounds.MAX_SAFE_LOCAL_SPAN,
                -1L, 0L, 1L, WorldBounds.MAX_SAFE_LOCAL_SPAN, WorldBounds.MAX_SAFE_LOCAL_SPAN + 1L}) {
            Int1088 value = boundary.add(Int1088.fromLong(delta));
            assertEquals((double) delta, WorldBounds.signedDifference(value, boundary, exact));
            assertEquals(Math.abs(delta) <= WorldBounds.MAX_SAFE_LOCAL_SPAN, exact[0]);
            assertEquals(!exact[0], Double.isNaN(WorldBounds.signedDifferenceExact(value, boundary)));
        }
        assertTrue(WorldBounds.localSpanExceeds(Int1088.of(0L, 5L), Int1088.of(1L << 62, 5L)));
    }

    @Test
    void fullWidthRangesAndDifferencesMatchBigInteger() {
        Int1088[] edges = {Int1088.MIN, Int1088.MIN.add(Int1088.ONE), Int1088.fromLong(Long.MIN_VALUE),
                Int1088.fromLong(-1L), Int1088.ZERO, Int1088.ONE, Int1088.fromLong(Long.MAX_VALUE),
                Int1088.of(Long.MIN_VALUE, 0L), Int1088.of(-1L, 0L), Int1088.of(0L, 1L), Int1088.MAX};
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            for (int sign : new int[] {-1, 1}) {
                BigInteger boundary = BigInteger.ONE.shiftLeft(64 * limb).multiply(BigInteger.valueOf(sign));
                assertFullWidthRange(Int1088.parseDecimal(boundary.subtract(BigInteger.ONE).toString()),
                        Int1088.parseDecimal(boundary.add(BigInteger.ONE).toString()));
            }
        }
        for (Int1088 a : edges) {
            for (Int1088 b : edges) {
                assertFullWidthRange(a, b);
            }
        }
        Random random = new Random(0x5203L);
        for (int i = 0; i < 1000; ++i) {
            Int1088 a = randomCoordinate(random);
            assertFullWidthRange(a, randomCoordinate(random));
            long[] nearby = new long[Int1088.LIMBS];
            a.copyFixed(nearby, 0);
            nearby[0] = random.nextLong();
            assertFullWidthRange(a, Int1088.fromFixedLimbs(nearby));
            assertFullWidthRange(a, a.addSaturated(Int1088.fromLong(random.nextInt(1 << 22) - (1 << 21))));
        }
        assertEquals(Double.POSITIVE_INFINITY, WorldBounds.inclusiveBlockSpanDouble(Int1088.MIN, Int1088.MAX));
        assertEquals(Int1088.ZERO, WorldBounds.middleBlockCoordinate(Int1088.MIN, Int1088.MAX));
        assertEquals(Int1088.ZERO, WorldBounds.blockOffsetInRange(Int1088.ZERO, Int1088.MAX, Int1088.MIN));
    }

    private static void assertFullWidthRange(Int1088 a, Int1088 b) {
        BigInteger first = wide(a);
        BigInteger second = wide(b);
        BigInteger difference = first.subtract(second);
        long clamped = clampLong(difference);
        assertEquals(clamped, WorldBounds.signedDifferenceAsLong(a, b));
        assertEquals((double) clamped, WorldBounds.signedDifference(a, b));
        assertEquals(Math.abs((double) clamped), WorldBounds.distance(a, b));
        boolean ascending = first.compareTo(second) <= 0;
        assertEquals(ascending, WorldBounds.isAscendingBlockRange(a, b));
        assertEquals(first.compareTo(second) < 0, WorldBounds.canAdvanceBlock(a, b));
        assertEquals(ascending, WorldBounds.isWithinBlockRange(a, a, b));
        assertEquals(ascending, WorldBounds.isWithinBlockRange(b, a, b));
        BigInteger span = ascending ? second.subtract(first).add(BigInteger.ONE) : BigInteger.ZERO;
        assertEquals(clamp1088(span).toString(), WorldBounds.inclusiveBlockSpan(a, b).toString());
        assertEquals(span.doubleValue(), WorldBounds.inclusiveBlockSpanDouble(a, b), Math.ulp(span.doubleValue()));
        BigInteger midpoint = first.add(second).add(BigInteger.ONE).shiftRight(1);
        assertEquals(midpoint.toString(), WorldBounds.middleBlockCoordinate(a, b).toString());
        BigInteger offset = ascending ? second.subtract(first) : BigInteger.ZERO;
        assertEquals(clamp1088(offset).toString(), WorldBounds.blockOffsetInRange(b, a, b).toString());
        assertEquals(Int1088.ZERO, WorldBounds.blockOffsetInRange(a, a, b));
        if (ascending) {
            Int1088 middle = WorldBounds.middleBlockCoordinate(a, b);
            assertTrue(WorldBounds.isWithinBlockRange(middle, a, b));
            assertEquals(clamp1088(wide(middle).subtract(first)).toString(),
                    WorldBounds.blockOffsetInRange(middle, a, b).toString());
        }
    }

    @Test
    void fullWidthDoubleOffsetsPreserveTheAnchorAndFloorTheDelta() {
        Int1088 anchor = Int1088.of(-19L, 123456789L);
        for (double offset : new double[] {-0x1p100, -0x1p63, -1.1D, -0.5D, 0.0D, 0.5D, 3.9D, 0x1p63, 0x1p100}) {
            BigInteger delta = new java.math.BigDecimal(offset).setScale(0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
            assertEquals(clamp1088(wide(anchor).add(delta)).toString(), WorldBounds.addDoubleBlockOffset(anchor, offset).toString());
        }
        for (double offset : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertEquals(anchor, WorldBounds.addDoubleBlockOffset(anchor, offset));
        }
        assertEquals(Int1088.MAX, WorldBounds.addDoubleBlockOffset(Int1088.MAX, 1.0D));
        assertEquals(Int1088.MIN, WorldBounds.addDoubleBlockOffset(Int1088.MIN, -0.1D));
    }

    @Test
    void fullWidthArithmeticMatchesBigInteger() {
        Int1088[] edges = {Int1088.MIN, Int1088.MIN.add(Int1088.ONE), Int1088.fromLong(Long.MIN_VALUE),
                Int1088.fromLong(-1L), Int1088.ZERO, Int1088.ONE, Int1088.fromLong(Long.MAX_VALUE),
                Int1088.of(0L, 1L), Int1088.MAX.subtract(Int1088.ONE), Int1088.MAX};
        long[] offsets = {Long.MIN_VALUE, Integer.MIN_VALUE, -1L, 0L, 1L, Integer.MAX_VALUE, Long.MAX_VALUE};
        for (Int1088 a : edges) {
            for (long offset : offsets) {
                assertFullWidthOffset(a, offset);
            }
            for (Int1088 b : edges) {
                assertEquals(clamp1088(wide(a).add(wide(b))).toString(), WorldBounds.addBlockOffset(a, b).toString());
                for (long factor : offsets) {
                    for (long otherFactor : offsets) {
                        BigInteger firstProduct = clamp1088(wide(a).multiply(BigInteger.valueOf(factor)));
                        BigInteger secondProduct = clamp1088(wide(b).multiply(BigInteger.valueOf(otherFactor)));
                        assertEquals(clamp1088(firstProduct.subtract(secondProduct)).toString(),
                                WorldBounds.crossComponent(a, factor, b, otherFactor).toString());
                    }
                }
                for (Int1088 c : edges) {
                    assertEquals(clamp1088(wide(a).add(wide(b)).subtract(wide(c))).toString(),
                            WorldBounds.addSubtract(a, b, c).toString());
                }
            }
            for (int offset : new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
                assertEquals(WorldBounds.addBlockOffset(a, (long) offset), WorldBounds.addBlockOffset(a, offset));
                assertEquals(WorldBounds.subtractBlockOffset(a, (long) offset), WorldBounds.subtractBlockOffset(a, offset));
                assertEquals(WorldBounds.multiplySaturated(a, (long) offset), WorldBounds.multiplySaturated(a, offset));
            }
        }
        Random random = new Random(0x5202L);
        for (int i = 0; i < 1000; ++i) {
            Int1088 a = randomCoordinate(random);
            Int1088 b = randomCoordinate(random);
            Int1088 c = randomCoordinate(random);
            long factor = random.nextLong();
            long otherFactor = random.nextLong();
            assertFullWidthOffset(a, factor);
            assertEquals(clamp1088(wide(a).add(wide(b))).toString(), WorldBounds.addBlockOffset(a, b).toString());
            assertEquals(clamp1088(wide(a).add(wide(b)).subtract(wide(c))).toString(),
                    WorldBounds.addSubtract(a, b, c).toString());
            BigInteger firstProduct = clamp1088(wide(a).multiply(BigInteger.valueOf(factor)));
            BigInteger secondProduct = clamp1088(wide(b).multiply(BigInteger.valueOf(otherFactor)));
            assertEquals(clamp1088(firstProduct.subtract(secondProduct)).toString(),
                    WorldBounds.crossComponent(a, factor, b, otherFactor).toString());
        }
        assertEquals(Int1088.ZERO, WorldBounds.crossComponent(Int1088.MAX, 2L, Int1088.MAX, 2L));
        assertEquals(Int1088.MAX, WorldBounds.addSubtract(Int1088.MAX, Int1088.MAX, Int1088.MAX));
    }

    private static void assertFullWidthOffset(Int1088 coordinate, long offset) {
        BigInteger value = wide(coordinate);
        BigInteger delta = BigInteger.valueOf(offset);
        assertEquals(clamp1088(value.add(delta)).toString(), WorldBounds.addBlockOffset(coordinate, offset).toString());
        assertEquals(clamp1088(value.subtract(delta)).toString(), WorldBounds.subtractBlockOffset(coordinate, offset).toString());
        assertEquals(clamp1088(value.multiply(delta)).toString(), WorldBounds.multiplySaturated(coordinate, offset).toString());
    }

    @Test
    void overflowingProductsDoNotAllocateExceptions() {
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 20; ++i) productSink = overflowingProducts();
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        productSink = overflowingProducts();
        long bytes = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(bytes < 1024L, "overflow products allocated " + bytes + " bytes");
    }

    private static long overflowingProducts() {
        long sum = 0L;
        for (int i = 0; i < 100000; ++i) {
            long value = Long.MAX_VALUE - i;
            sum += WorldBounds.multiplySaturated(value, value);
            sum += WorldBounds.multiplySaturated(-value, value);
        }
        return sum;
    }

    @Test
    void saturatedLongProductsMatchBigInteger() {
        long[] edges = {Long.MIN_VALUE, Long.MIN_VALUE + 1L, -3037000500L, -3037000499L,
                -1L, 0L, 1L, 3037000499L, 3037000500L, Long.MAX_VALUE - 1L, Long.MAX_VALUE};
        for (long a : edges) {
            for (long b : edges) {
                assertEquals(clampLong(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))),
                        WorldBounds.multiplySaturated(a, b));
            }
        }
        Random random = new Random(0x5204L);
        for (int i = 0; i < 10000; ++i) {
            long a = random.nextLong();
            long b = random.nextLong();
            assertEquals(clampLong(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))),
                    WorldBounds.multiplySaturated(a, b));
        }
    }

    @Test
    void legacyCompoundArithmeticPreservesCancellation() {
        long[] edges = {Long.MIN_VALUE, Long.MIN_VALUE + 1L, -1L, 0L, 1L, Long.MAX_VALUE - 1L, Long.MAX_VALUE};
        for (long a : edges) {
            for (long b : edges) {
                for (long c : edges) {
                    assertEquals(clampLong(BigInteger.valueOf(a).add(BigInteger.valueOf(b)).subtract(BigInteger.valueOf(c))),
                            WorldBounds.addSubtract(a, b, c));
                    for (long d : edges) {
                        assertEquals(clampLong(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b))
                                        .subtract(BigInteger.valueOf(c).multiply(BigInteger.valueOf(d)))),
                                WorldBounds.crossComponent(a, b, c, d));
                    }
                }
            }
        }
    }

    @Test
    void absoluteDoubleIngressUsesFullWidthBounds() {
        assertEquals(0.0D, WorldBounds.clampAbsoluteDouble(Double.NaN));
        assertEquals(-Double.MAX_VALUE, WorldBounds.clampAbsoluteDouble(Double.NEGATIVE_INFINITY));
        assertEquals(Double.MAX_VALUE, WorldBounds.clampAbsoluteDouble(Double.POSITIVE_INFINITY));
        for (double value : new double[] {-Double.MAX_VALUE, -0x1p127, -0x1p100, -0.5D, 0.0D, 0x1p63, 0x1p100, 0x1p127, Double.MAX_VALUE}) {
            assertEquals(value, WorldBounds.clampAbsoluteDouble(value));
        }
    }

    @Test
    void fullWidthNoisePreservesTheFullSignedMagnitude() {
        for (long high : new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
            for (long low : new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
                Int1088 coordinate = Int1088.of(low, high);
                double magnitude = new java.math.BigInteger(coordinate.toString()).doubleValue();
                assertEquals(magnitude, WorldBounds.noiseCoordinate(coordinate));
                for (double scale : new double[] {-Double.MAX_VALUE, -1.0D, 0.0D, 0.5D, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY}) {
                    double product = magnitude * scale;
                    assertEquals(product, WorldBounds.scaledNoiseCoordinate(coordinate, scale));
                }
            }
        }
    }

    @Test
    void fullWidthChunkDomainContainsExactlyTheRepresentableBlocks() {
        assertEquals(Int1088.MIN, WorldBounds.MIN_BLOCK);
        assertEquals(Int1088.MAX, WorldBounds.MAX_BLOCK);
        assertEquals(Int1088.MIN.shiftRight(4), WorldBounds.MIN_CHUNK);
        assertEquals(Int1088.MAX.shiftRight(4), WorldBounds.MAX_CHUNK);
        assertEquals(Int1088.MIN, WorldBounds.chunkToBlock(WorldBounds.MIN_CHUNK));
        assertEquals(Int1088.MAX.subtract(Int1088.fromLong(15L)), WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.clampChunk(Int1088.MIN));
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.clampChunk(Int1088.MAX));
        assertEquals(Int1088.MIN, WorldBounds.chunkToBlock(Int1088.MIN));
        assertEquals(Int1088.MAX.subtract(Int1088.fromLong(15L)), WorldBounds.chunkToBlock(Int1088.MAX));
        assertEquals(Int1088.fromLong(-16L), WorldBounds.chunkToBlock(Int1088.fromLong(-1L)));
    }

    @Test
    void fullWidthChunkOffsetsSaturateOrRejectAtTheEdges() {
        for (long offset : new long[] {1L, Long.MAX_VALUE}) {
            assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, offset));
            assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MAX_CHUNK, offset));
        }
        for (long offset : new long[] {-1L, Long.MIN_VALUE}) {
            assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, offset));
            assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MIN_CHUNK, offset));
        }
        for (Int1088 edge : new Int1088[] {WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
            assertEquals(edge, WorldBounds.tryAddChunkOffset(edge, 0L));
        }
        assertEquals(WorldBounds.MAX_CHUNK.subtract(Int1088.ONE),
                WorldBounds.tryAddChunkOffset(WorldBounds.MAX_CHUNK, -1L));
        assertEquals(WorldBounds.MIN_CHUNK.add(Int1088.ONE),
                WorldBounds.tryAddChunkOffset(WorldBounds.MIN_CHUNK, 1L));
        assertNull(WorldBounds.tryAddChunkOffset(Int1088.MAX, Long.MIN_VALUE));
        assertNull(WorldBounds.tryAddChunkOffset(Int1088.MIN, Long.MAX_VALUE));
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(Int1088.MAX, Long.MAX_VALUE));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(Int1088.MIN, Long.MIN_VALUE));
    }

    @Test
    void fullWidthConversionsAndOffsetsMatchBigInteger() {
        BigInteger minBlock = BigInteger.ONE.shiftLeft(1087).negate();
        BigInteger maxBlock = BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE);
        BigInteger minChunk = minBlock.shiftRight(4);
        BigInteger maxChunk = maxBlock.shiftRight(4);
        Random random = new Random(0x5201L);
        for (int i = 0; i < 1000; ++i) {
            Int1088 coordinate = randomCoordinate(random);
            BigInteger value = new BigInteger(coordinate.toString());
            BigInteger clamped = value.max(minChunk).min(maxChunk);
            assertEquals(clamped.toString(), WorldBounds.clampChunk(coordinate).toString());
            assertEquals(clamped.shiftLeft(4).toString(), WorldBounds.chunkToBlock(coordinate).toString());

            Int1088 chunk = coordinate.shiftRight(4);
            assertEquals(value.shiftRight(4).toString(), chunk.toString());
            long offset = random.nextLong();
            BigInteger sum = value.shiftRight(4).add(BigInteger.valueOf(offset));
            assertEquals(sum.max(minChunk).min(maxChunk).toString(), WorldBounds.addChunkOffset(chunk, offset).toString());
            if (sum.compareTo(minChunk) < 0 || sum.compareTo(maxChunk) > 0) {
                assertNull(WorldBounds.tryAddChunkOffset(chunk, offset));
            } else {
                assertEquals(sum.toString(), WorldBounds.tryAddChunkOffset(chunk, offset).toString());
            }

            // Legal region coordinates round-trip their chunk and local index exactly.
            Int1088 region = chunk.shiftRight(5);
            long local = chunk.lowLimb() & 31L;
            assertEquals(chunk, WorldBounds.regionToChunk(region, local));
        }
    }

    @Test
    void fullWidthRegionConversionSaturatesWithoutWrapping() {
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.regionToChunk(Int1088.MAX, Long.MAX_VALUE));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.regionToChunk(Int1088.MIN, Long.MIN_VALUE));
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.regionToChunk(Int1088.MAX, -1L));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.regionToChunk(Int1088.MIN, 1L));
        assertEquals(Int1088.fromLong(-1L), WorldBounds.regionToChunk(Int1088.fromLong(-1L), 31L));
        assertEquals(WorldBounds.MIN_CHUNK,
                WorldBounds.regionToChunk(WorldBounds.MIN_CHUNK.shiftRight(5), 0L));
        assertEquals(WorldBounds.MAX_CHUNK,
                WorldBounds.regionToChunk(WorldBounds.MAX_CHUNK.shiftRight(5), 31L));
    }

    @Test
    void chunkConstructorsClampToTheFullBlockDomain() {
        assertEquals(WorldBounds.MIN_CHUNK, new ChunkPos(WorldBounds.MIN_CHUNK, Int1088.ZERO).x());
        assertEquals(WorldBounds.MAX_CHUNK, new ChunkPos(WorldBounds.MAX_CHUNK, Int1088.ZERO).x());
        assertEquals(WorldBounds.MIN_CHUNK, new ChunkPos(WorldBounds.MIN_CHUNK.subtract(Int1088.ONE), Int1088.ZERO).x());
        assertEquals(WorldBounds.MAX_CHUNK, new ChunkPos(WorldBounds.MAX_CHUNK.add(Int1088.ONE), Int1088.ZERO).x());
        assertEquals(Int1088.fromLong(Long.MAX_VALUE), new ChunkPos(Long.MAX_VALUE, 0L).x());
    }

    @Test
    void offsetsNeverWrapToTheOppositeSide() {
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, 1L));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, -1L));
        assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MAX_CHUNK, 1L));
        assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MIN_CHUNK, -1L));
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, Long.MAX_VALUE));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, Long.MIN_VALUE));
        assertEquals(1L, new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK).getChessboardDistance(new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK.subtract(Int1088.ONE))));
    }

    @Test
    void forwardRangeHelpersStopAtBothWorldEdges() {
        assertTrue(WorldBounds.isAscendingBlockRange(Long.MAX_VALUE - 1L, Long.MAX_VALUE));
        assertTrue(WorldBounds.canAdvanceBlock(Long.MAX_VALUE - 1L, Long.MAX_VALUE));
        assertFalse(WorldBounds.canAdvanceBlock(Long.MAX_VALUE, Long.MAX_VALUE));
        assertFalse(WorldBounds.isAscendingBlockRange(Long.MAX_VALUE, Long.MIN_VALUE));
        assertTrue(WorldBounds.isAscendingIntRange(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        assertTrue(WorldBounds.canAdvanceInt(Integer.MAX_VALUE - 1, Integer.MAX_VALUE));
        assertFalse(WorldBounds.canAdvanceInt(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(WorldBounds.isAscendingIntRange(Integer.MAX_VALUE, Integer.MIN_VALUE));
    }

    @Test
    void blockOffsetsNeverWrapToTheOppositeSide() {
        assertEquals(WorldBounds.MAX_BLOCK, WorldBounds.addBlockOffset(WorldBounds.MAX_BLOCK, 1L));
        assertEquals(WorldBounds.MIN_BLOCK, WorldBounds.addBlockOffset(WorldBounds.MIN_BLOCK, -1L));
        assertEquals(WorldBounds.MIN_BLOCK, WorldBounds.subtractBlockOffset(WorldBounds.MIN_BLOCK, 1L));
        assertEquals(0L, WorldBounds.subtractBlockOffset(Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(WorldBounds.MAX_BLOCK, WorldBounds.subtractBlockOffset(WorldBounds.MAX_BLOCK, Long.MIN_VALUE));
        assertEquals(0L, WorldBounds.middleBlockCoordinate(Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, WorldBounds.signedDifferenceAsLong(Long.MAX_VALUE, Long.MIN_VALUE));
        assertEquals(Long.MIN_VALUE, WorldBounds.signedDifferenceAsLong(Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, WorldBounds.signedDifferenceAsLong(0L, Long.MIN_VALUE));
        assertEquals(Long.MIN_VALUE, WorldBounds.signedDifferenceAsLong(-1L, Long.MAX_VALUE));
        assertEquals(1L, WorldBounds.middleBlockCoordinate(0L, 1L));
        assertEquals(-1L, WorldBounds.middleBlockCoordinate(-2L, -1L));
    }

    @Test
    void doubleBlockOffsetsKeepTheIntegerCoordinate() {
        long huge = 52999999999999981L;
        assertEquals(huge, WorldBounds.addDoubleBlockOffset(huge, 0.5D));
        assertEquals(huge, WorldBounds.addDoubleBlockOffset(huge, 0.0D));
        assertEquals(huge + 3L, WorldBounds.addDoubleBlockOffset(huge, 3.9D));
        assertEquals(huge - 2L, WorldBounds.addDoubleBlockOffset(huge, -1.1D));
        assertEquals(huge, WorldBounds.addDoubleBlockOffset(huge, Double.NaN));
        assertEquals(huge, WorldBounds.addDoubleBlockOffset(huge, Double.POSITIVE_INFINITY));
    }

    @Test
    void surfaceArithmeticIsFiniteAndSaturating() {
        assertEquals(Integer.MAX_VALUE, WorldBounds.addSaturated(Integer.MAX_VALUE, 1));
        assertEquals(Integer.MIN_VALUE, WorldBounds.addSaturated(Integer.MIN_VALUE, -1));
        assertEquals(Integer.MAX_VALUE, WorldBounds.multiplySaturated(Integer.MAX_VALUE, 2));
        assertEquals(Integer.MIN_VALUE, WorldBounds.multiplySaturated(Integer.MIN_VALUE, 2));
        assertEquals(-1.0D, WorldBounds.clampNoise(Double.NEGATIVE_INFINITY));
        assertEquals(0.0D, WorldBounds.clampNoise(Double.NaN));
        assertEquals(1.0D, WorldBounds.clampNoise(Double.POSITIVE_INFINITY));
        assertEquals(WorldBounds.MAX_BLOCK.subtract(Int1088.fromLong(15)), WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK));
        assertEquals(WorldBounds.MIN_BLOCK, WorldBounds.chunkToBlock(WorldBounds.MIN_CHUNK));
    }

    @Test
    void sectionNotificationsAtWorldEdgesNeverWrapToTheOppositeSide() {
        List<SectionPos> atMaximum = new ArrayList<>();
        SectionPos.aroundAndAtBlockPos(Int1088.MAX, 64, Int1088.MAX, atMaximum::add);
        assertFalse(atMaximum.isEmpty());
        assertTrue(atMaximum.stream().allMatch(section -> WorldBounds.isWithinBlockRange(section.x(), WorldBounds.MAX_CHUNK.subtract(Int1088.ONE), WorldBounds.MAX_CHUNK)
                && WorldBounds.isWithinBlockRange(section.z(), WorldBounds.MAX_CHUNK.subtract(Int1088.ONE), WorldBounds.MAX_CHUNK)));

        List<SectionPos> atMinimum = new ArrayList<>();
        SectionPos.aroundAndAtBlockPos(Int1088.MIN, 64, Int1088.MIN, atMinimum::add);
        assertFalse(atMinimum.isEmpty());
        assertTrue(atMinimum.stream().allMatch(section -> WorldBounds.isWithinBlockRange(section.x(), WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK.add(Int1088.ONE))
                && WorldBounds.isWithinBlockRange(section.z(), WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK.add(Int1088.ONE))));
    }

    @Test
    void sectionRangesAtWorldEdgesRemainInsideTheChunkDomain() {
        assertTrue(SectionPos.cube(SectionPos.of(WorldBounds.MAX_CHUNK, 0, WorldBounds.MAX_CHUNK), 2)
                        .allMatch(section -> WorldBounds.isValidChunk(section.x(), section.z())));
        assertTrue(SectionPos.aroundChunk(new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK), 2, 0, 1)
                        .allMatch(section -> WorldBounds.isValidChunk(section.x(), section.z())));
    }

    @Test
    void projectedLongChunkMembershipDoesNotWrapItsUpperBound() {
        long firstBlock = WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK_LONG);
        assertTrue(WorldBounds.isWithinChunk(firstBlock, firstBlock));
        assertTrue(WorldBounds.isWithinChunk(Long.MAX_VALUE, firstBlock));
        assertFalse(WorldBounds.isWithinChunk(Long.MIN_VALUE, firstBlock));
    }

    @Test
    void blockRangesAtWorldEdgesReachTheirEndpointThenStop() {
        List<BlockPos> atMaximum = new ArrayList<>();
        BlockPos.betweenClosed(Long.MAX_VALUE - 1L, 0, Long.MAX_VALUE - 1L, Long.MAX_VALUE, 0, Long.MAX_VALUE).forEach(atMaximum::add);
        assertEquals(4, atMaximum.size());
        assertEquals(new BlockPos(Long.MAX_VALUE, 0, Long.MAX_VALUE), atMaximum.get(3));

        List<BlockPos> atMinimum = new ArrayList<>();
        BlockPos.betweenClosed(Long.MIN_VALUE, 0, Long.MIN_VALUE, Long.MIN_VALUE + 1L, 0, Long.MIN_VALUE + 1L).forEach(atMinimum::add);
        assertEquals(4, atMinimum.size());
        assertEquals(new BlockPos(Long.MIN_VALUE + 1L, 0, Long.MIN_VALUE + 1L), atMinimum.get(3));

        assertFalse(BlockPos.betweenClosed(Long.MAX_VALUE, 0, 0L, Long.MIN_VALUE, 0, 0L).iterator().hasNext());
    }

    @Test
    void boundingBoxSpansAndCentersRemainValidAtWorldEdges() {
        BoundingBox fullWorld = new BoundingBox(Int1088.MIN, 0, Int1088.MIN, Int1088.MAX, 0, Int1088.MAX);
        assertEquals(Int1088.MAX, fullWorld.getXSpan());
        assertEquals(Int1088.MAX, fullWorld.getZSpan());
        assertEquals(Int1088.ZERO, fullWorld.getCenter().getX());
        assertEquals(Int1088.ZERO, fullWorld.getCenter().getZ());
    }

    @Test
    void boundingBoxesStayOrderedWhenExpandedAtWorldEdges() {
        Int1088 nearEdge = Int1088.MAX.subtract(Int1088.fromLong(2));
        BoundingBox positiveEdge = new BoundingBox(nearEdge, 0, nearEdge, Int1088.MAX, 6, Int1088.MAX);
        BoundingBox expanded = positiveEdge.inflatedBy(3);
        assertEquals(Int1088.MAX.subtract(Int1088.fromLong(5)), expanded.minX());
        assertEquals(Int1088.MAX, expanded.maxX());
        assertEquals(Int1088.fromLong(6), expanded.getXSpan());
        assertEquals(Int1088.MAX.subtract(Int1088.fromLong(5)), expanded.minZ());
        assertEquals(Int1088.MAX, expanded.maxZ());
        assertEquals(Int1088.fromLong(6), expanded.getZSpan());
        assertTrue(expanded.isInside(new BlockPos(Int1088.MAX, 3, Int1088.MAX)));
        // Intersection normalizes reversed endpoints; the full-domain rectangle overlaps.
        assertTrue(expanded.intersects(Int1088.MAX, Int1088.MAX, Int1088.MIN, Int1088.MIN));
        assertFalse(expanded.intersects(Int1088.MIN, Int1088.MIN, Int1088.ZERO, Int1088.ZERO));
    }

    @Test
    void blockRangeHelpersSaturateRatherThanWrap() {
        assertEquals(3L, WorldBounds.inclusiveBlockSpan(Long.MAX_VALUE - 2L, Long.MAX_VALUE));
        assertEquals(0L, WorldBounds.inclusiveBlockSpan(Long.MAX_VALUE, Long.MIN_VALUE));
        assertEquals(Long.MAX_VALUE, WorldBounds.inclusiveBlockSpan(Long.MIN_VALUE, Long.MAX_VALUE));
        assertEquals(2L, WorldBounds.blockOffsetInRange(Long.MAX_VALUE, Long.MAX_VALUE - 2L, Long.MAX_VALUE));
        assertTrue(WorldBounds.isPositiveIntSpan(1L));
        assertTrue(WorldBounds.isPositiveIntSpan(Integer.MAX_VALUE));
        assertFalse(WorldBounds.isPositiveIntSpan(0L));
        assertFalse(WorldBounds.isPositiveIntSpan((long) Integer.MAX_VALUE + 1L));
        assertTrue(WorldBounds.isVoxelShapeSize(0, 0, 0));
        assertTrue(WorldBounds.isVoxelShapeSize(3, 7, 7));
        assertFalse(WorldBounds.isVoxelShapeSize(Integer.MAX_VALUE, 2, 1));
    }

    @Test
    void cursorTraversesOverflowingRangesLazily() {
        Cursor3D cursor = new Cursor3D(Long.MIN_VALUE, 0, 0L, Long.MAX_VALUE, 0, 0L);
        assertTrue(cursor.advance());
        assertEquals(Int1088.fromLong(Long.MIN_VALUE), cursor.nextX());
        assertTrue(cursor.advance());
        assertEquals(Int1088.fromLong(Long.MIN_VALUE + 1L), cursor.nextX());
    }

    @Test
    void cursorVisitsEdgeBlocksInOrderAndStops() {
        for (Int1088 origin : new Int1088[] {Int1088.MIN, Int1088.MAX.subtract(Int1088.ONE)}) {
            Cursor3D cursor = new Cursor3D(origin, 4, origin, origin.add(Int1088.ONE), 4, origin.add(Int1088.ONE));
            for (int z = 0; z < 2; ++z) {
                for (int x = 0; x < 2; ++x) {
                    assertTrue(cursor.advance());
                    assertEquals(origin.add(Int1088.fromLong(x)), cursor.nextX());
                    assertEquals(4, cursor.nextY());
                    assertEquals(origin.add(Int1088.fromLong(z)), cursor.nextZ());
                    assertEquals(Cursor3D.TYPE_CORNER, cursor.getNextType());
                }
            }
            assertFalse(cursor.advance());
            assertFalse(cursor.advance());
        }
    }

    @Test
    void sectionOffsetsAndConversionsStayAtWorldEdges() {
        SectionPos maximum = SectionPos.of(WorldBounds.MAX_CHUNK, 4, WorldBounds.MAX_CHUNK);
        SectionPos minimum = SectionPos.of(WorldBounds.MIN_CHUNK, 4, WorldBounds.MIN_CHUNK);
        assertEquals(WorldBounds.MAX_CHUNK, maximum.offset(1L, 0, 1L).x());
        assertEquals(WorldBounds.MIN_CHUNK, minimum.offset(-1L, 0, -1L).z());
        assertEquals(Int1088.MAX, maximum.maxBlockX());
        assertEquals(Int1088.MIN, minimum.minBlockZ());
        assertEquals(Int1088.MAX, SectionPos.sectionToBlockCoord(WorldBounds.MAX_CHUNK, 16L));
        assertEquals(Int1088.MIN, SectionPos.sectionToBlockCoord(WorldBounds.MIN_CHUNK, -1L));
        assertEquals(Int1088.fromLong(1L << 40), SectionPos.posToSectionCoord((double) (1L << 44)));
        assertEquals(4L, SectionPos.betweenClosedStream(WorldBounds.MAX_CHUNK.subtract(Int1088.ONE), 0, WorldBounds.MAX_CHUNK.subtract(Int1088.ONE),
                WorldBounds.MAX_CHUNK, 0, WorldBounds.MAX_CHUNK).count());
        List<SectionPos> sections = SectionPos.betweenClosedStream(WorldBounds.MAX_CHUNK.subtract(Int1088.ONE), 0, WorldBounds.MAX_CHUNK.subtract(Int1088.ONE),
                WorldBounds.MAX_CHUNK, 0, WorldBounds.MAX_CHUNK).toList();
        assertEquals(4, sections.size());
        assertEquals(maximum.offset(0L, -4, 0L), sections.get(3));
        assertEquals(0L, SectionPos.betweenClosedStream(1L, 0, 0L, 0L, 0, 0L).count());
    }
}
