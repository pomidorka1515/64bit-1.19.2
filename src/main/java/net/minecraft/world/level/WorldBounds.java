package net.minecraft.world.level;

import net.minecraft.core.Int128;

/**
 * The representable horizontal coordinate limits of the world.
 *
 * <p>A block coordinate is a signed long.  Dividing the two block limits by
 * sixteen gives the only chunk coordinates that can contain a representable
 * block.  Keeping these values in one place is important: an overflowing
 * {@code long} subtraction must never turn a request at an edge of the world
 * into a request at the opposite edge. The additive {@code Int128} overloads
 * and {@code _128} constants provide the full-width domain during migration.</p>
 */
public final class WorldBounds {
    public static final long MIN_BLOCK = Long.MIN_VALUE;
    public static final long MAX_BLOCK = Long.MAX_VALUE;
    public static final long MIN_CHUNK = MIN_BLOCK >> 4;
    public static final long MAX_CHUNK = MAX_BLOCK >> 4;

    public static final Int128 MIN_BLOCK_128 = Int128.MIN;
    public static final Int128 MAX_BLOCK_128 = Int128.MAX;
    public static final Int128 MIN_CHUNK_128 = MIN_BLOCK_128.shiftRight(4);
    public static final Int128 MAX_CHUNK_128 = MAX_BLOCK_128.shiftRight(4);

    private WorldBounds() {
    }

    public static Int128 clampChunk(Int128 chunk) {
        if (chunk.compareTo(MIN_CHUNK_128) < 0)
            return MIN_CHUNK_128;
        if (chunk.compareTo(MAX_CHUNK_128) > 0)
            return MAX_CHUNK_128;
        return chunk;
    }

    /** Converts a chunk coordinate to its first block without overflowing. */
    public static Int128 chunkToBlock(Int128 chunk) {
        return clampChunk(chunk).shiftLeftSaturated(4);
    }

    /** Adds a chunk offset, saturating to the representable chunk interval. */
    public static Int128 addChunkOffset(Int128 chunk, long offset) {
        return clampChunk(chunk.addSaturated(Int128.fromLong(offset)));
    }

    /** Returns null when the offset would leave the representable chunk range. */
    public static Int128 tryAddChunkOffset(Int128 chunk, long offset) {
        Int128 result = chunk.addSaturated(Int128.fromLong(offset));
        return result.compareTo(MIN_CHUNK_128) < 0 || result.compareTo(MAX_CHUNK_128) > 0 ? null : result;
    }

    /** Converts a region and local chunk offset, saturating each operation to 128 bits. */
    public static Int128 regionToChunk(Int128 region, long localChunk) {
        return region.shiftLeftSaturated(5).addSaturated(Int128.fromLong(localChunk));
    }

    /** Adds a block offset, saturating only at the signed 128-bit edges. */
    public static Int128 addBlockOffset(Int128 block, long offset) {
        return addBlockOffset(block, Int128.fromLong(offset));
    }

    public static Int128 addBlockOffset(Int128 block, int offset) {
        return addBlockOffset(block, (long) offset);
    }

    public static Int128 addBlockOffset(Int128 block, Int128 offset) {
        return block.addSaturated(offset);
    }

    public static Int128 subtractBlockOffset(Int128 block, long offset) {
        return block.subtractSaturated(Int128.fromLong(offset));
    }

    public static Int128 subtractBlockOffset(Int128 block, int offset) {
        return subtractBlockOffset(block, (long) offset);
    }

    public static Int128 multiplySaturated(Int128 coordinate, long factor) {
        return coordinate.multiplySaturated(factor);
    }

    public static Int128 multiplySaturated(Int128 coordinate, int factor) {
        return multiplySaturated(coordinate, (long) factor);
    }

    /** Saturates each product and then their difference in the horizontal domain. */
    public static Int128 crossComponent(Int128 a, long b, Int128 c, long d) {
        return a.multiplySaturated(b).subtractSaturated(c.multiplySaturated(d));
    }

    /** Adds two coordinates and subtracts a third, clamping only the final result. */
    public static Int128 addSubtract(Int128 first, Int128 second, Int128 subtrahend) {
        // Same-sign subtraction cannot overflow; do it first to preserve cancellation.
        if ((first.hi() < 0L) == (subtrahend.hi() < 0L))
            return first.subtract(subtrahend).addSaturated(second);
        return first.addSaturated(second).subtractSaturated(subtrahend);
    }

    /** Low-64 projection for the deferred world-generation seam. */
    public static double noiseCoordinate(Int128 coordinate) {
        return noiseCoordinate(coordinate.toLongLow());
    }

    public static double scaledNoiseCoordinate(Int128 coordinate, double scale) {
        return scaledNoiseCoordinate(coordinate.toLongLow(), scale);
    }

    public static long clampChunk(long chunk) {
        return Math.max(MIN_CHUNK, Math.min(MAX_CHUNK, chunk));
    }

    /** Converts a chunk coordinate to its first block without overflowing. */
    public static long chunkToBlock(long chunk) {
        return clampChunk(chunk) << 4;
    }

    /** Converts an X/Z block coordinate to a finite noise input. */
    public static double noiseCoordinate(long coordinate) {
        return clampProjectedNoiseCoordinate((double) coordinate);
    }

    /** Scales an X/Z coordinate without allowing infinity into a noise sampler. */
    public static double scaledNoiseCoordinate(long coordinate, double scale) {
        double result = (double) coordinate * scale;
        if (Double.isNaN(result))
            return 0.0D;
        if (!Double.isFinite(result))
            return result < 0.0D ? -0x1.0p63 : Math.nextDown(0x1.0p63);
        return clampProjectedNoiseCoordinate(result);
    }

    private static double clampProjectedNoiseCoordinate(double coordinate) {
        return Math.max(-0x1.0p63, Math.min(Math.nextDown(0x1.0p63), coordinate));
    }

    /** Clamps an inclusive Y coordinate to a build-height interval. */
    public static int clampBuildHeight(int coordinate, int minY, int maxYExclusive) {
        if (maxYExclusive <= minY)
            return minY;
        return Math.max(minY, Math.min(maxYExclusive - 1, coordinate));
    }

    /** Keeps a surface depth inside the generated column's finite range. */
    public static int clampSurfaceDepth(int depth, int generationDepth) {
        int limit = Math.max(0, generationDepth);
        return Math.max(-limit, Math.min(limit, depth));
    }

    /** Saturating addition for values used by vertical rule expressions. */
    public static int addSaturated(int first, int second) {
        long result = (long) first + second;
        return result < Integer.MIN_VALUE ? Integer.MIN_VALUE : result > Integer.MAX_VALUE ? Integer.MAX_VALUE
                                                                                           : (int) result;
    }

    /** Saturating multiplication for values used by vertical rule expressions. */
    public static int multiplySaturated(int first, int second) {
        long result = (long) first * second;
        return result < Integer.MIN_VALUE ? Integer.MIN_VALUE : result > Integer.MAX_VALUE ? Integer.MAX_VALUE
                                                                                           : (int) result;
    }

    /** Saturating multiplication in the horizontal coordinate domain. */
    public static long multiplySaturated(long first, long second) {
        try {
            return Math.multiplyExact(first, second);
        } catch (ArithmeticException overflow) {
            return (first ^ second) < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
    }

    /** Computes a determinant before clamping, preserving cancellation. */
    public static long crossComponent(long a, long b, long c, long d) {
        return clampToLong(crossComponent(Int128.fromLong(a), b, Int128.fromLong(c), d));
    }

    /** Adds two coordinates and subtracts a third, clamping only the final result. */
    public static long addSubtract(long first, long second, long subtrahend) {
        return clampToLong(addSubtract(Int128.fromLong(first), Int128.fromLong(second), Int128.fromLong(subtrahend)));
    }

    private static long clampToLong(Int128 value) {
        if (value.compareTo(Int128.fromLong(Long.MIN_VALUE)) < 0)
            return Long.MIN_VALUE;
        if (value.compareTo(Int128.fromLong(Long.MAX_VALUE)) > 0)
            return Long.MAX_VALUE;
        return value.toLongLow();
    }

    /**
    * Noise is expected to be normalized.  Far-away coordinates can make the
    * legacy noise implementation produce very large values, so do not allow
    * those values to enter surface arithmetic.
    */
    public static double clampNoise(double value) {
        if (Double.isNaN(value))
            return 0.0D;
        if (value <= -1.0D)
            return -1.0D;
        if (value >= 1.0D)
            return 1.0D;
        return value;
    }

    /** True for a block coordinate that is representable in the world model. */
    public static boolean isValidBlock(long coordinate) {
        return coordinate >= MIN_BLOCK && coordinate <= MAX_BLOCK;
    }

    /** Both signed-long horizontal block coordinates are representable. */
    public static boolean isValidBlock(long x, long z) {
        return isValidBlock(x) && isValidBlock(z);
    }

    /** Converts a region coordinate and local chunk offset without wrapping. */
    public static long regionToChunk(long region, long localChunk) {
        long base;
        if (region > Long.MAX_VALUE / 32L) {
            base = Long.MAX_VALUE;
        } else if (region < Long.MIN_VALUE / 32L) {
            base = Long.MIN_VALUE;
        } else {
            base = region * 32L;
        }
        return addSaturatedLong(base, localChunk);
    }

    private static long addSaturatedLong(long value, long offset) {
        if (offset > 0L && value > Long.MAX_VALUE - offset)
            return Long.MAX_VALUE;
        if (offset < 0L && value < Long.MIN_VALUE - offset)
            return Long.MIN_VALUE;
        if (offset == Long.MIN_VALUE)
            return Long.MIN_VALUE;
        return value + offset;
    }

    public static boolean isValidChunk(long x, long z) {
        return isValidChunkCoordinate(x) && isValidChunkCoordinate(z);
    }

    public static boolean isValidChunkCoordinate(long chunk) {
        return chunk >= MIN_CHUNK && chunk <= MAX_CHUNK;
    }

    /** Returns the lower of two horizontal block coordinates. */
    public static long minBlockCoordinate(long first, long second) {
        return Math.min(first, second);
    }

    /** Returns the higher of two horizontal block coordinates. */
    public static long maxBlockCoordinate(long first, long second) {
        return Math.max(first, second);
    }

    /** Returns whether an inclusive horizontal range is ordered for forward iteration. */
    public static boolean isAscendingBlockRange(long first, long last) {
        return first <= last;
    }

    /** Returns whether two ordered inclusive horizontal ranges overlap. */
    public static boolean blockRangesOverlap(long firstMin, long firstMax, long secondMin, long secondMax) {
        return isAscendingBlockRange(firstMin, firstMax) && isAscendingBlockRange(secondMin, secondMax) && firstMax >= secondMin && firstMin <= secondMax;
    }

    /** Returns whether a horizontal coordinate belongs to an ordered inclusive range. */
    public static boolean isWithinBlockRange(long coordinate, long min, long max) {
        return isAscendingBlockRange(min, max) && coordinate >= min && coordinate <= max;
    }

    /**
    * Returns the number of coordinates in an ordered inclusive horizontal
    * range.  Ranges too wide for a long are saturated instead of wrapping.
    */
    public static long inclusiveBlockSpan(long min, long max) {
        if (!isAscendingBlockRange(min, max))
            return 0L;
        long distance = signedDifferenceAsLong(max, min);
        return distance == MAX_BLOCK ? MAX_BLOCK : distance + 1L;
    }

    /**
    * Returns a signed horizontal difference as a long, saturating a difference
    * outside the signed-long domain instead of wrapping it to the other edge.
    */
    public static long signedDifferenceAsLong(long value, long origin) {
        double difference = signedDifference(value, origin);
        if (difference >= (double) MAX_BLOCK)
            return MAX_BLOCK;
        if (difference <= (double) MIN_BLOCK)
            return MIN_BLOCK;
        return (long) difference;
    }

    /**
    * Returns the non-negative local offset of a coordinate inside an ordered
    * range.  Callers must verify containment first; an out-of-range coordinate
    * is clamped to the nearest representable local offset.
    */
    public static long blockOffsetInRange(long coordinate, long min, long max) {
        if (coordinate <= min)
            return 0L;
        if (coordinate >= max)
            return inclusiveBlockSpan(min, max) - 1L;
        return signedDifferenceAsLong(coordinate, min);
    }

    /** Returns whether a positive horizontal span fits an int-indexed voxel shape. */
    public static boolean isPositiveIntSpan(long span) {
        return span > 0L && span <= Integer.MAX_VALUE;
    }

    /** Returns whether three non-negative voxel dimensions fit the backing bit set. */
    public static boolean isVoxelShapeSize(int xSpan, int ySpan, int zSpan) {
        return xSpan >= 0 && ySpan >= 0 && zSpan >= 0 && (long) xSpan * (long) ySpan * (long) zSpan <= Integer.MAX_VALUE;
    }

    /**
    * Returns whether a forward block-coordinate iterator can move once more
    * without crossing its inclusive endpoint.  Callers must advance using
    * {@link #addBlockOffset(long, long)} so the addition itself cannot wrap.
    */
    public static boolean canAdvanceBlock(long coordinate, long last) {
        return coordinate < last;
    }

    /** Returns whether an inclusive integer range is ordered for forward iteration. */
    public static boolean isAscendingIntRange(int first, int last) {
        return first <= last;
    }

    /** Returns whether a forward integer iterator can move once more. */
    public static boolean canAdvanceInt(int coordinate, int last) {
        return coordinate < last;
    }

    /**
    * Adds a double offset to a block coordinate without rounding the coordinate
    * itself through {@code double}.  Tree limbs and similar local steps keep a
    * small fractional offset; adding that offset to a 64-bit coordinate in
    * double arithmetic can move the block by several units of last place and
    * make a vertical log look like an X/Z step.
    */
    public static long addDoubleBlockOffset(long block, double offset) {
        if (offset == 0.0D || !Double.isFinite(offset))
            return block;
        long whole = (long) offset;
        if (offset < (double) whole && whole != Long.MIN_VALUE) {
            --whole;
        }

        return addBlockOffset(block, whole);
    }

    /** Adds a block offset without wrapping at a signed-long edge. */
    public static long addBlockOffset(long block, long offset) {
        if (offset > 0L && block > MAX_BLOCK - offset)
            return MAX_BLOCK;
        if (offset < 0L) {
            if (offset == Long.MIN_VALUE)
                return block < 0L ? MIN_BLOCK : MIN_BLOCK + block;
            if (block < MIN_BLOCK - offset)
                return MIN_BLOCK;
        }
        return block + offset;
    }

    /** Adds a signed block offset while preserving the legal interval. */
    public static long addBlockOffset(long block, int offset) {
        return addBlockOffset(block, (long) offset);
    }

    /**
    * Returns whether a block coordinate belongs to the 16-block chunk beginning
    * at {@code chunkMinBlock}.  The upper edge is calculated saturating so the
    * last chunk can be checked without making {@code min + 16} wrap negative.
    */
    public static boolean isWithinChunk(long block, long chunkMinBlock) {
        return block >= chunkMinBlock && block <= addBlockOffset(chunkMinBlock, 15L);
    }

    /** Subtracts a block offset without wrapping at a signed-long edge. */
    public static long subtractBlockOffset(long block, long offset) {
        if (offset == Long.MIN_VALUE)
            return block < 0L ? MAX_BLOCK + block + 1L : MAX_BLOCK;
        return addBlockOffset(block, -offset);
    }

    /** Adds a small chunk offset without wrapping at a long edge. */
    public static long addChunkOffset(long chunk, long offset) {
        if (offset > 0L && chunk > MAX_CHUNK - offset)
            return MAX_CHUNK;
        if (offset < 0L) {
            if (offset == Long.MIN_VALUE)
                return chunk < 0L ? MIN_CHUNK : MIN_CHUNK + chunk;
            if (chunk < MIN_CHUNK - offset)
                return MIN_CHUNK;
        }
        return chunk + offset;
    }

    /** Returns null when the offset would leave the representable chunk range. */
    public static Long tryAddChunkOffset(long chunk, long offset) {
        if (offset > 0L && chunk > MAX_CHUNK - offset)
            return null;
        if (offset < 0L) {
            if (offset == Long.MIN_VALUE)
                return null;
            if (chunk < MIN_CHUNK - offset)
                return null;
        }
        long result = chunk + offset;
        return isValidChunkCoordinate(result) ? result : null;
    }

    /** Overflow-safe absolute difference, saturated rather than wrapped. */
    public static double distance(long first, long second) {
        if (first == second)
            return 0.0D;
        if (first >= 0L && second < 0L)
            return (double) first + -(double) second;
        if (second >= 0L && first < 0L)
            return (double) second + -(double) first;
        return first >= second ? (double) (first - second) : (double) (second - first);
    }

    /** Returns whether two coordinates are no farther apart than radius. */
    public static boolean within(long coordinate, long center, long radius) {
        return radius >= 0L && distance(coordinate, center) <= (double) radius;
    }

    /** Returns value - origin without allowing signed-long subtraction to wrap. */
    public static double signedDifference(long value, long origin) {
        if (value >= 0L && origin < 0L)
            return (double) value - (double) origin;
        if (value < 0L && origin >= 0L)
            return (double) value - (double) origin;
        return (double) (value - origin);
    }

    /** Returns the upper center of an inclusive block-coordinate interval. */
    public static long middleBlockCoordinate(long min, long max) {
        if (min < 0L && max >= 0L) {
            long sum = min + max;
            return sum >= 0L ? sum / 2L + sum % 2L : sum / 2L;
        }

        long difference = max - min;
        return min + difference / 2L + difference % 2L;
    }

    /** Keeps legacy entity Y ingress inside the range supported by BlockPos/AABB physics. */
    public static double clampVerticalDouble(double coordinate) {
        if (Double.isNaN(coordinate))
            return 0.0D;
        if (coordinate == Double.NEGATIVE_INFINITY || coordinate < -20_000_000.0D)
            return -20_000_000.0D;
        if (coordinate == Double.POSITIVE_INFINITY || coordinate > 20_000_000.0D)
            return 20_000_000.0D;
        return coordinate;
    }

    /** Clamps an ordinary absolute double to the representable block interval. */
    public static double clampAbsoluteDouble(double coordinate) {
        if (Double.isNaN(coordinate))
            return 0.0D;
        if (coordinate == Double.NEGATIVE_INFINITY || coordinate < -0x1.0p127)
            return -0x1.0p127;
        // 2^127 itself has no valid floor block. The next representable double
        // below it has a representable floor block and is the correct safe edge.
        double upperExclusive = 0x1.0p127;
        if (coordinate == Double.POSITIVE_INFINITY || coordinate >= upperExclusive)
            return Math.nextDown(upperExclusive);
        return coordinate;
    }
}
