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
import net.minecraft.core.Int128;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

class WorldBoundsTest {
    private static final BigInteger MIN_128 = BigInteger.ONE.shiftLeft(127).negate();
    private static final BigInteger MAX_128 = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

    private static BigInteger wide(Int128 value) {
        return new BigInteger(value.toString());
    }

    private static BigInteger clamp128(BigInteger value) {
        return value.max(MIN_128).min(MAX_128);
    }

    private static long clampLong(BigInteger value) {
        return value.max(BigInteger.valueOf(Long.MIN_VALUE)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    @Test
    void relativeDifferencesUseTheSpanRatherThanHighHalfEquality() {
        Int128 boundary = Int128.of(123L, 0L);
        Int128 before = boundary.subtract(Int128.ONE);
        assertEquals(1.0D, WorldBounds.signedDifferenceExact(boundary, before));
        assertEquals(-1.0D, WorldBounds.signedDifferenceExact(before, boundary));
        assertEquals(-2.0D, WorldBounds.signedDifference(Int128.of(5L, 3L), Int128.of(5L, 5L)));
        assertEquals(Long.MAX_VALUE, WorldBounds.signedDifferenceAsLong(Int128.of(0L, Long.MIN_VALUE), Int128.ZERO));
        assertEquals(Long.MIN_VALUE, WorldBounds.signedDifferenceAsLong(Int128.MIN, Int128.MAX));
        boolean[] exact = new boolean[1];
        for (long delta : new long[] {-WorldBounds.MAX_SAFE_LOCAL_SPAN - 1L, -WorldBounds.MAX_SAFE_LOCAL_SPAN,
                -1L, 0L, 1L, WorldBounds.MAX_SAFE_LOCAL_SPAN, WorldBounds.MAX_SAFE_LOCAL_SPAN + 1L}) {
            Int128 value = boundary.add(Int128.fromLong(delta));
            assertEquals((double) delta, WorldBounds.signedDifference(value, boundary, exact));
            assertEquals(Math.abs(delta) <= WorldBounds.MAX_SAFE_LOCAL_SPAN, exact[0]);
            assertEquals(!exact[0], Double.isNaN(WorldBounds.signedDifferenceExact(value, boundary)));
        }
        assertTrue(WorldBounds.localSpanExceeds(Int128.of(5L, 0L), Int128.of(5L, 1L << 62)));
    }

    @Test
    void fullWidthRangesAndDifferencesMatchBigInteger() {
        Int128[] edges = {Int128.MIN, Int128.MIN.add(Int128.ONE), Int128.fromLong(Long.MIN_VALUE),
                Int128.fromLong(-1L), Int128.ZERO, Int128.ONE, Int128.fromLong(Long.MAX_VALUE),
                Int128.of(0L, Long.MIN_VALUE), Int128.of(0L, -1L), Int128.of(1L, 0L), Int128.MAX};
        for (Int128 a : edges) {
            for (Int128 b : edges) {
                assertFullWidthRange(a, b);
            }
        }
        Random random = new Random(0x5203L);
        for (int i = 0; i < 1000; ++i) {
            Int128 a = Int128.of(random.nextLong(), random.nextLong());
            assertFullWidthRange(a, Int128.of(random.nextLong(), random.nextLong()));
            assertFullWidthRange(a, Int128.of(a.hi(), random.nextLong()));
            assertFullWidthRange(a, a.addSaturated(Int128.fromLong(random.nextInt(1 << 22) - (1 << 21))));
        }
        assertEquals(0x1p128, WorldBounds.inclusiveBlockSpanDouble(Int128.MIN, Int128.MAX));
        assertEquals(Int128.ZERO, WorldBounds.middleBlockCoordinate(Int128.MIN, Int128.MAX));
        assertEquals(Int128.ZERO, WorldBounds.blockOffsetInRange(Int128.ZERO, Int128.MAX, Int128.MIN));
    }

    private static void assertFullWidthRange(Int128 a, Int128 b) {
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
        assertEquals(clamp128(span).toString(), WorldBounds.inclusiveBlockSpan(a, b).toString());
        assertEquals(span.doubleValue(), WorldBounds.inclusiveBlockSpanDouble(a, b), Math.ulp(span.doubleValue()));
        BigInteger midpoint = first.add(second).add(BigInteger.ONE).shiftRight(1);
        assertEquals(midpoint.toString(), WorldBounds.middleBlockCoordinate(a, b).toString());
        BigInteger offset = ascending ? second.subtract(first) : BigInteger.ZERO;
        assertEquals(clamp128(offset).toString(), WorldBounds.blockOffsetInRange(b, a, b).toString());
        assertEquals(Int128.ZERO, WorldBounds.blockOffsetInRange(a, a, b));
        if (ascending) {
            Int128 middle = WorldBounds.middleBlockCoordinate(a, b);
            assertTrue(WorldBounds.isWithinBlockRange(middle, a, b));
            assertEquals(clamp128(wide(middle).subtract(first)).toString(),
                    WorldBounds.blockOffsetInRange(middle, a, b).toString());
        }
    }

    @Test
    void fullWidthDoubleOffsetsPreserveTheAnchorAndFloorTheDelta() {
        Int128 anchor = Int128.of(123456789L, -19L);
        for (double offset : new double[] {-0x1p100, -0x1p63, -1.1D, -0.5D, 0.0D, 0.5D, 3.9D, 0x1p63, 0x1p100}) {
            BigInteger delta = new java.math.BigDecimal(offset).setScale(0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
            assertEquals(clamp128(wide(anchor).add(delta)).toString(), WorldBounds.addDoubleBlockOffset(anchor, offset).toString());
        }
        for (double offset : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertEquals(anchor, WorldBounds.addDoubleBlockOffset(anchor, offset));
        }
        assertEquals(Int128.MAX, WorldBounds.addDoubleBlockOffset(Int128.MAX, 1.0D));
        assertEquals(Int128.MIN, WorldBounds.addDoubleBlockOffset(Int128.MIN, -0.1D));
    }

    @Test
    void fullWidthArithmeticMatchesBigInteger() {
        Int128[] edges = {Int128.MIN, Int128.MIN.add(Int128.ONE), Int128.fromLong(Long.MIN_VALUE),
                Int128.fromLong(-1L), Int128.ZERO, Int128.ONE, Int128.fromLong(Long.MAX_VALUE),
                Int128.of(1L, 0L), Int128.MAX.subtract(Int128.ONE), Int128.MAX};
        long[] offsets = {Long.MIN_VALUE, Integer.MIN_VALUE, -1L, 0L, 1L, Integer.MAX_VALUE, Long.MAX_VALUE};
        for (Int128 a : edges) {
            for (long offset : offsets) {
                assertFullWidthOffset(a, offset);
            }
            for (Int128 b : edges) {
                assertEquals(clamp128(wide(a).add(wide(b))).toString(), WorldBounds.addBlockOffset(a, b).toString());
                for (long factor : offsets) {
                    for (long otherFactor : offsets) {
                        BigInteger firstProduct = clamp128(wide(a).multiply(BigInteger.valueOf(factor)));
                        BigInteger secondProduct = clamp128(wide(b).multiply(BigInteger.valueOf(otherFactor)));
                        assertEquals(clamp128(firstProduct.subtract(secondProduct)).toString(),
                                WorldBounds.crossComponent(a, factor, b, otherFactor).toString());
                    }
                }
                for (Int128 c : edges) {
                    assertEquals(clamp128(wide(a).add(wide(b)).subtract(wide(c))).toString(),
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
            Int128 a = Int128.of(random.nextLong(), random.nextLong());
            Int128 b = Int128.of(random.nextLong(), random.nextLong());
            Int128 c = Int128.of(random.nextLong(), random.nextLong());
            long factor = random.nextLong();
            long otherFactor = random.nextLong();
            assertFullWidthOffset(a, factor);
            assertEquals(clamp128(wide(a).add(wide(b))).toString(), WorldBounds.addBlockOffset(a, b).toString());
            assertEquals(clamp128(wide(a).add(wide(b)).subtract(wide(c))).toString(),
                    WorldBounds.addSubtract(a, b, c).toString());
            BigInteger firstProduct = clamp128(wide(a).multiply(BigInteger.valueOf(factor)));
            BigInteger secondProduct = clamp128(wide(b).multiply(BigInteger.valueOf(otherFactor)));
            assertEquals(clamp128(firstProduct.subtract(secondProduct)).toString(),
                    WorldBounds.crossComponent(a, factor, b, otherFactor).toString());
        }
        assertEquals(Int128.ZERO, WorldBounds.crossComponent(Int128.MAX, 2L, Int128.MAX, 2L));
        assertEquals(Int128.MAX, WorldBounds.addSubtract(Int128.MAX, Int128.MAX, Int128.MAX));
    }

    private static void assertFullWidthOffset(Int128 coordinate, long offset) {
        BigInteger value = wide(coordinate);
        BigInteger delta = BigInteger.valueOf(offset);
        assertEquals(clamp128(value.add(delta)).toString(), WorldBounds.addBlockOffset(coordinate, offset).toString());
        assertEquals(clamp128(value.subtract(delta)).toString(), WorldBounds.subtractBlockOffset(coordinate, offset).toString());
        assertEquals(clamp128(value.multiply(delta)).toString(), WorldBounds.multiplySaturated(coordinate, offset).toString());
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
        for (double value : new double[] {Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, -0x1p127}) {
            assertEquals(-0x1p127, WorldBounds.clampAbsoluteDouble(value));
        }
        for (double value : new double[] {Double.POSITIVE_INFINITY, Double.MAX_VALUE, 0x1p127}) {
            assertEquals(Math.nextDown(0x1p127), WorldBounds.clampAbsoluteDouble(value));
        }
        for (double value : new double[] {-0x1p100, -0.5D, 0.0D, 0x1p63, 0x1p100, Math.nextDown(0x1p127)}) {
            assertEquals(value, WorldBounds.clampAbsoluteDouble(value));
        }
    }

    @Test
    void fullWidthNoiseUsesOnlyTheLegacyLowWordProjection() {
        for (long high : new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
            for (long low : new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE}) {
                Int128 coordinate = Int128.of(high, low);
                double expected = Math.max(-0x1p63, Math.min(Math.nextDown(0x1p63), (double) low));
                assertEquals(expected, WorldBounds.noiseCoordinate(coordinate));
                for (double scale : new double[] {-Double.MAX_VALUE, -1.0D, 0.0D, 0.5D, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY}) {
                    assertEquals(WorldBounds.scaledNoiseCoordinate(low, scale), WorldBounds.scaledNoiseCoordinate(coordinate, scale));
                }
            }
        }
    }

    @Test
    void fullWidthChunkDomainContainsExactlyTheRepresentableBlocks() {
        assertEquals(Int128.MIN, WorldBounds.MIN_BLOCK);
        assertEquals(Int128.MAX, WorldBounds.MAX_BLOCK);
        assertEquals(Int128.of(Long.MIN_VALUE >> 4, 0L), WorldBounds.MIN_CHUNK);
        assertEquals(Int128.of(Long.MAX_VALUE >> 4, -1L), WorldBounds.MAX_CHUNK);
        assertEquals(Int128.MIN, WorldBounds.chunkToBlock(WorldBounds.MIN_CHUNK));
        assertEquals(Int128.MAX.subtract(Int128.fromLong(15L)), WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.clampChunk(Int128.MIN));
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.clampChunk(Int128.MAX));
        assertEquals(Int128.MIN, WorldBounds.chunkToBlock(Int128.MIN));
        assertEquals(Int128.MAX.subtract(Int128.fromLong(15L)), WorldBounds.chunkToBlock(Int128.MAX));
        assertEquals(Int128.fromLong(-16L), WorldBounds.chunkToBlock(Int128.fromLong(-1L)));
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
        for (Int128 edge : new Int128[] {WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
            assertEquals(edge, WorldBounds.tryAddChunkOffset(edge, 0L));
        }
        assertEquals(WorldBounds.MAX_CHUNK.subtract(Int128.ONE),
                WorldBounds.tryAddChunkOffset(WorldBounds.MAX_CHUNK, -1L));
        assertEquals(WorldBounds.MIN_CHUNK.add(Int128.ONE),
                WorldBounds.tryAddChunkOffset(WorldBounds.MIN_CHUNK, 1L));
        assertNull(WorldBounds.tryAddChunkOffset(Int128.MAX, Long.MIN_VALUE));
        assertNull(WorldBounds.tryAddChunkOffset(Int128.MIN, Long.MAX_VALUE));
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(Int128.MAX, Long.MAX_VALUE));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(Int128.MIN, Long.MIN_VALUE));
    }

    @Test
    void fullWidthConversionsAndOffsetsMatchBigInteger() {
        BigInteger minBlock = BigInteger.ONE.shiftLeft(127).negate();
        BigInteger maxBlock = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);
        BigInteger minChunk = minBlock.shiftRight(4);
        BigInteger maxChunk = maxBlock.shiftRight(4);
        Random random = new Random(0x5201L);
        for (int i = 0; i < 1000; ++i) {
            Int128 coordinate = Int128.of(random.nextLong(), random.nextLong());
            BigInteger value = new BigInteger(coordinate.toString());
            BigInteger clamped = value.max(minChunk).min(maxChunk);
            assertEquals(clamped.toString(), WorldBounds.clampChunk(coordinate).toString());
            assertEquals(clamped.shiftLeft(4).toString(), WorldBounds.chunkToBlock(coordinate).toString());

            Int128 chunk = coordinate.shiftRight(4);
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
            Int128 region = chunk.shiftRight(5);
            long local = chunk.lo() & 31L;
            assertEquals(chunk, WorldBounds.regionToChunk(region, local));
        }
    }

    @Test
    void fullWidthRegionConversionSaturatesWithoutWrapping() {
        assertEquals(Int128.MAX, WorldBounds.regionToChunk(Int128.MAX, Long.MAX_VALUE));
        assertEquals(Int128.MIN, WorldBounds.regionToChunk(Int128.MIN, Long.MIN_VALUE));
        assertEquals(Int128.MAX.subtract(Int128.ONE), WorldBounds.regionToChunk(Int128.MAX, -1L));
        assertEquals(Int128.MIN.add(Int128.ONE), WorldBounds.regionToChunk(Int128.MIN, 1L));
        assertEquals(Int128.fromLong(-1L), WorldBounds.regionToChunk(Int128.fromLong(-1L), 31L));
        assertEquals(WorldBounds.MIN_CHUNK,
                WorldBounds.regionToChunk(WorldBounds.MIN_CHUNK.shiftRight(5), 0L));
        assertEquals(WorldBounds.MAX_CHUNK,
                WorldBounds.regionToChunk(WorldBounds.MAX_CHUNK.shiftRight(5), 31L));
    }

    @Test
    void chunkConstructorsClampToTheFullBlockDomain() {
        assertEquals(WorldBounds.MIN_CHUNK, new ChunkPos(WorldBounds.MIN_CHUNK, Int128.ZERO).x());
        assertEquals(WorldBounds.MAX_CHUNK, new ChunkPos(WorldBounds.MAX_CHUNK, Int128.ZERO).x());
        assertEquals(WorldBounds.MIN_CHUNK, new ChunkPos(WorldBounds.MIN_CHUNK.subtract(Int128.ONE), Int128.ZERO).x());
        assertEquals(WorldBounds.MAX_CHUNK, new ChunkPos(WorldBounds.MAX_CHUNK.add(Int128.ONE), Int128.ZERO).x());
        assertEquals(Int128.fromLong(Long.MAX_VALUE), new ChunkPos(Long.MAX_VALUE, 0L).x());
    }

    @Test
    void offsetsNeverWrapToTheOppositeSide() {
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, 1L));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, -1L));
        assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MAX_CHUNK, 1L));
        assertNull(WorldBounds.tryAddChunkOffset(WorldBounds.MIN_CHUNK, -1L));
        assertEquals(WorldBounds.MAX_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MAX_CHUNK, Long.MAX_VALUE));
        assertEquals(WorldBounds.MIN_CHUNK, WorldBounds.addChunkOffset(WorldBounds.MIN_CHUNK, Long.MIN_VALUE));
        assertEquals(1L, new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK).getChessboardDistance(new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK.subtract(Int128.ONE))));
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
        assertEquals(WorldBounds.MAX_BLOCK.subtract(Int128.fromLong(15)), WorldBounds.chunkToBlock(WorldBounds.MAX_CHUNK));
        assertEquals(WorldBounds.MIN_BLOCK, WorldBounds.chunkToBlock(WorldBounds.MIN_CHUNK));
    }

    @Test
    void sectionNotificationsAtWorldEdgesNeverWrapToTheOppositeSide() {
        List<SectionPos> atMaximum = new ArrayList<>();
        SectionPos.aroundAndAtBlockPos(Int128.MAX, 64, Int128.MAX, atMaximum::add);
        assertFalse(atMaximum.isEmpty());
        assertTrue(atMaximum.stream().allMatch(section -> WorldBounds.isWithinBlockRange(section.x(), WorldBounds.MAX_CHUNK.subtract(Int128.ONE), WorldBounds.MAX_CHUNK)
                && WorldBounds.isWithinBlockRange(section.z(), WorldBounds.MAX_CHUNK.subtract(Int128.ONE), WorldBounds.MAX_CHUNK)));

        List<SectionPos> atMinimum = new ArrayList<>();
        SectionPos.aroundAndAtBlockPos(Int128.MIN, 64, Int128.MIN, atMinimum::add);
        assertFalse(atMinimum.isEmpty());
        assertTrue(atMinimum.stream().allMatch(section -> WorldBounds.isWithinBlockRange(section.x(), WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK.add(Int128.ONE))
                && WorldBounds.isWithinBlockRange(section.z(), WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK.add(Int128.ONE))));
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
        BoundingBox fullWorld = new BoundingBox(Int128.MIN, 0, Int128.MIN, Int128.MAX, 0, Int128.MAX);
        assertEquals(Int128.MAX, fullWorld.getXSpan());
        assertEquals(Int128.MAX, fullWorld.getZSpan());
        assertEquals(Int128.ZERO, fullWorld.getCenter().getX());
        assertEquals(Int128.ZERO, fullWorld.getCenter().getZ());
    }

    @Test
    void boundingBoxesStayOrderedWhenExpandedAtWorldEdges() {
        Int128 nearEdge = Int128.MAX.subtract(Int128.fromLong(2));
        BoundingBox positiveEdge = new BoundingBox(nearEdge, 0, nearEdge, Int128.MAX, 6, Int128.MAX);
        BoundingBox expanded = positiveEdge.inflatedBy(3);
        assertEquals(Int128.MAX.subtract(Int128.fromLong(5)), expanded.minX());
        assertEquals(Int128.MAX, expanded.maxX());
        assertEquals(Int128.fromLong(6), expanded.getXSpan());
        assertEquals(Int128.MAX.subtract(Int128.fromLong(5)), expanded.minZ());
        assertEquals(Int128.MAX, expanded.maxZ());
        assertEquals(Int128.fromLong(6), expanded.getZSpan());
        assertTrue(expanded.isInside(new BlockPos(Int128.MAX, 3, Int128.MAX)));
        // Intersection normalizes reversed endpoints; the full-domain rectangle overlaps.
        assertTrue(expanded.intersects(Int128.MAX, Int128.MAX, Int128.MIN, Int128.MIN));
        assertFalse(expanded.intersects(Int128.MIN, Int128.MIN, Int128.ZERO, Int128.ZERO));
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
        assertEquals(Int128.fromLong(Long.MIN_VALUE), cursor.nextX());
        assertTrue(cursor.advance());
        assertEquals(Int128.fromLong(Long.MIN_VALUE + 1L), cursor.nextX());
    }

    @Test
    void cursorVisitsEdgeBlocksInOrderAndStops() {
        for (Int128 origin : new Int128[] {Int128.MIN, Int128.MAX.subtract(Int128.ONE)}) {
            Cursor3D cursor = new Cursor3D(origin, 4, origin, origin.add(Int128.ONE), 4, origin.add(Int128.ONE));
            for (int z = 0; z < 2; ++z) {
                for (int x = 0; x < 2; ++x) {
                    assertTrue(cursor.advance());
                    assertEquals(origin.add(Int128.fromLong(x)), cursor.nextX());
                    assertEquals(4, cursor.nextY());
                    assertEquals(origin.add(Int128.fromLong(z)), cursor.nextZ());
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
        assertEquals(Int128.MAX, maximum.maxBlockX());
        assertEquals(Int128.MIN, minimum.minBlockZ());
        assertEquals(Int128.MAX, SectionPos.sectionToBlockCoord(WorldBounds.MAX_CHUNK, 16L));
        assertEquals(Int128.MIN, SectionPos.sectionToBlockCoord(WorldBounds.MIN_CHUNK, -1L));
        assertEquals(Int128.fromLong(1L << 40), SectionPos.posToSectionCoord((double) (1L << 44)));
        assertEquals(4L, SectionPos.betweenClosedStream(WorldBounds.MAX_CHUNK.subtract(Int128.ONE), 0, WorldBounds.MAX_CHUNK.subtract(Int128.ONE),
                WorldBounds.MAX_CHUNK, 0, WorldBounds.MAX_CHUNK).count());
        List<SectionPos> sections = SectionPos.betweenClosedStream(WorldBounds.MAX_CHUNK.subtract(Int128.ONE), 0, WorldBounds.MAX_CHUNK.subtract(Int128.ONE),
                WorldBounds.MAX_CHUNK, 0, WorldBounds.MAX_CHUNK).toList();
        assertEquals(4, sections.size());
        assertEquals(maximum.offset(0L, -4, 0L), sections.get(3));
        assertEquals(0L, SectionPos.betweenClosedStream(1L, 0, 0L, 0L, 0, 0L).count());
    }
}
