package net.minecraft.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockPosTest {
    @Test
    void originOffsetTraversalAndStreamsAgreeWithIndependentCoordinates() {
        for (Int1088 first : new Int1088[] {Int1088.MIN, Int1088.fromLong(-1),
                Int1088.fromLong(Long.MAX_VALUE), Int1088.ONE.shiftLeftSaturated(1024).subtractSaturated(Int1088.ONE),
                Int1088.MAX.subtractSaturated(Int1088.ONE)}) {
            BigInteger origin = new BigInteger(first.toString());
            Int1088 last = Int1088.parseDecimal(origin.add(BigInteger.ONE).toString());
            List<BlockPos> expected = new ArrayList<>();
            for (int z = 0; z < 2; ++z) for (int y = 0; y < 2; ++y) for (int x = 0; x < 2; ++x) {
                expected.add(new BlockPos(Int1088.parseDecimal(origin.add(BigInteger.valueOf(x)).toString()), y - 1,
                        Int1088.parseDecimal(origin.add(BigInteger.valueOf(z)).toString())));
            }
            List<BlockPos> actual = new ArrayList<>();
            BlockPos.betweenClosed(first, -1, first, last, 0, last).forEach(pos -> actual.add(pos.immutable()));
            assertEquals(expected, actual);
            BoundingBox box = new BoundingBox(first, -1, first, last, 0, last);
            assertEquals(expected, BlockPos.betweenClosedStream(box).map(BlockPos::immutable).toList());
            List<BlockPos> corners = new ArrayList<>();
            box.forAllCorners(pos -> corners.add(pos.immutable()));
            assertEquals(8, new java.util.HashSet<>(corners).size());
            assertTrue(corners.stream().allMatch(box::isInside));
        }
    }

    @Test
    void randomTraversalReusesCursorAndSamplesUnsignedWidthsWithoutAliasing() {
        for (Int1088[] bounds : new Int1088[][] {{Int1088.MIN, Int1088.MAX}, {Int1088.MAX, Int1088.MAX},
                {Int1088.MIN, Int1088.MIN.addSaturated(Int1088.fromLong(15))},
                {Int1088.MAX.subtractSaturated(Int1088.fromLong(15)), Int1088.MAX},
                {Int1088.ONE.shiftLeftSaturated(1024).subtractSaturated(Int1088.ONE), Int1088.ONE.shiftLeftSaturated(1024).addSaturated(Int1088.ONE)}}) {
            var positions = BlockPos.randomBetweenClosed(RandomSource.create(0x108843), 1000,
                    bounds[0], Integer.MIN_VALUE, bounds[0], bounds[1], Integer.MAX_VALUE, bounds[1]);
            BlockPos previous = null;
            RandomSource oracleRandom = RandomSource.create(0x108843);
            int count = 0;
            BigInteger min = new BigInteger(bounds[0].toString()), max = new BigInteger(bounds[1].toString());
            for (BlockPos pos : positions) {
                if (previous != null) assertSame(previous, pos);
                BigInteger x = new BigInteger(pos.getX().toString()), z = new BigInteger(pos.getZ().toString());
                assertEquals(randomCoordinate(oracleRandom, min, max), x);
                assertEquals(randomCoordinate(oracleRandom, BigInteger.valueOf(Integer.MIN_VALUE),
                        BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact(), pos.getY());
                assertEquals(randomCoordinate(oracleRandom, min, max), z);
                assertTrue(x.compareTo(min) >= 0 && x.compareTo(max) <= 0);
                assertTrue(z.compareTo(min) >= 0 && z.compareTo(max) <= 0);
                previous = pos;
                ++count;
            }
            assertEquals(1000, count);
        }
        assertFalse(BlockPos.randomBetweenClosed(RandomSource.create(1), 1, 1, 0, 0, 0, 0, 0).iterator().hasNext());
        assertFalse(BlockPos.randomBetweenClosed(RandomSource.create(1), 0, 0, 0, 0, 0, 0, 0).iterator().hasNext());
    }

    @Test
    void streamSizeHintsSaturateWithoutClaimingExactSizes() {
        var sections = SectionPos.betweenClosedStream(WorldBounds.MIN_CHUNK, Integer.MIN_VALUE, WorldBounds.MIN_CHUNK,
                WorldBounds.MAX_CHUNK, Integer.MAX_VALUE, WorldBounds.MAX_CHUNK).spliterator();
        assertEquals(Long.MAX_VALUE, sections.estimateSize());
        assertFalse(sections.hasCharacteristics(Spliterator.SIZED));
        var chunks = ChunkPos.rangeClosed(new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK),
                new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MIN_CHUNK)).spliterator();
        assertEquals(Long.MAX_VALUE, chunks.estimateSize());
        assertFalse(chunks.hasCharacteristics(Spliterator.SIZED));
        List<SectionPos> prefix = SectionPos.betweenClosedStream(WorldBounds.MAX_CHUNK.subtractSaturated(Int1088.ONE), 0,
                WorldBounds.MAX_CHUNK, WorldBounds.MAX_CHUNK, 1, WorldBounds.MAX_CHUNK).toList();
        assertEquals(4, prefix.size());
        assertEquals(WorldBounds.MAX_CHUNK, prefix.get(3).x());
        assertEquals(1, prefix.get(3).y());
        assertEquals(27, SectionPos.betweenClosedStream(-1, -1, -1, 1, 1, 1).count());
    }

    private static BigInteger randomCoordinate(RandomSource random, BigInteger min, BigInteger max) {
        BigInteger width = max.subtract(min);
        int top = Math.max(0, (width.bitLength() - 1) / 64);
        int bits = width.bitLength() - top * 64;
        BigInteger sample;
        do {
            sample = BigInteger.ZERO;
            for (int i = 0; i <= top; ++i) {
                long word = random.nextLong();
                if (i == top && bits < 64) word = bits == 0 ? 0 : word >>> (64 - bits);
                BigInteger unsigned = BigInteger.valueOf(word & Long.MAX_VALUE);
                if (word < 0) unsigned = unsigned.setBit(63);
                sample = sample.or(unsigned.shiftLeft(i * 64));
            }
        } while (sample.compareTo(width) > 0);
        return min.add(sample);
    }
}
