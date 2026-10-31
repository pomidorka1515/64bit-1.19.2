package net.minecraft.world.level;

import net.minecraft.core.Int1088;
import net.minecraft.core.Int1088Math;

/**
 * The representable horizontal coordinate limits of the world.
 *
 * <p>A block coordinate is a signed 1088-bit integer. Dividing the two block limits by
 * sixteen gives the only chunk coordinates that can contain a representable
 * block.  Keeping these values in one place is important: an overflowing
 * subtraction must never turn a request at an edge of the world into a request
 * at the opposite edge. Long overloads and {@code _LONG} constants are explicit
 * 64-bit projections for worldgen and local/test callers, not world limits.</p>
 */
public final class WorldBounds {
    public static final Int1088 MIN_BLOCK = Int1088.MIN;
    public static final Int1088 MAX_BLOCK = Int1088.MAX;
    public static final Int1088 MIN_CHUNK = MIN_BLOCK.shiftRight(4);
    public static final Int1088 MAX_CHUNK = MAX_BLOCK.shiftRight(4);

    /** Limits of the explicit 64-bit block/chunk projection. */
    public static final long MIN_BLOCK_LONG = Long.MIN_VALUE;
    public static final long MAX_BLOCK_LONG = Long.MAX_VALUE;
    public static final long MIN_CHUNK_LONG = MIN_BLOCK_LONG >> 4;
    public static final long MAX_CHUNK_LONG = MAX_BLOCK_LONG >> 4;

    private WorldBounds() {
    }

    /** Shared local precision guard; the hard ceiling is {@code (1L << 52) - 1}, not 2^53. */
    public static final long MAX_SAFE_LOCAL_SPAN = 1L << 20;

    /** Exact integer subtraction followed by signed-long saturation; never projects absolute coordinates. */
    public static long signedDifferenceAsLong(Int1088 value, Int1088 origin) {
        return Int1088Math.signedDifferenceAsLong(value, origin);
    }

    public static boolean localSpanExceeds(Int1088 value, Int1088 origin) {
        long difference = signedDifferenceAsLong(value, origin);
        return difference < -MAX_SAFE_LOCAL_SPAN || difference > MAX_SAFE_LOCAL_SPAN;
    }

    /** Local differences are exact; distant hints saturate to signed-long sentinels. */
    public static double signedDifference(Int1088 value, Int1088 origin) {
        return (double) signedDifferenceAsLong(value, origin);
    }

    /** Reports physics-local exactness separately from the approximate distance hint. */
    public static double signedDifference(Int1088 value, Int1088 origin, boolean[] exactOut) {
        exactOut[0] = !localSpanExceeds(value, origin);
        return signedDifference(value, origin);
    }

    public static double signedDifferenceExact(Int1088 value, Int1088 origin) {
        return localSpanExceeds(value, origin) ? Double.NaN : signedDifference(value, origin);
    }

    public static double distance(Int1088 first, Int1088 second) {
        return Math.abs(signedDifference(first, second));
    }

    public static boolean isAscendingBlockRange(Int1088 first, Int1088 last) {
        return first.compareTo(last) <= 0;
    }

    public static boolean isWithinBlockRange(Int1088 coordinate, Int1088 min, Int1088 max) {
        return isAscendingBlockRange(min, max) && coordinate.compareTo(min) >= 0 && coordinate.compareTo(max) <= 0;
    }

    public static boolean canAdvanceBlock(Int1088 coordinate, Int1088 last) {
        return coordinate.compareTo(last) < 0;
    }

    /** Inclusive count, saturated to the signed 1088-bit domain. */
    public static Int1088 inclusiveBlockSpan(Int1088 min, Int1088 max) {
        return isAscendingBlockRange(min, max) ? max.subtractSaturated(min).addSaturated(Int1088.ONE) : Int1088.ZERO;
    }

    /** Approximate count retaining the magnitude even when the signed count saturates. */
    public static double inclusiveBlockSpanDouble(Int1088 min, Int1088 max) {
        if (!isAscendingBlockRange(min, max))
            return 0.0D;
        Int1088 halfDifference = max.shiftRight(1).subtractSaturated(min.shiftRight(1));
        return halfDifference.toDoubleRounded() * 2.0D
                + (max.lowLimb() & 1L) - (min.lowLimb() & 1L) + 1.0D;
    }

    /** Clamped non-negative offset; an unordered range has no local offsets. */
    public static Int1088 blockOffsetInRange(Int1088 coordinate, Int1088 min, Int1088 max) {
        if (!isAscendingBlockRange(min, max) || coordinate.compareTo(min) <= 0)
            return Int1088.ZERO;
        return (coordinate.compareTo(max) >= 0 ? max : coordinate).subtractSaturated(min);
    }

    /** Upper midpoint without overflowing either a sum or a difference. */
    public static Int1088 middleBlockCoordinate(Int1088 min, Int1088 max) {
        Int1088 halves = min.shiftRight(1).add(max.shiftRight(1));
        return halves.add(Int1088.fromLong((min.lowLimb() & 1L) | (max.lowLimb() & 1L)));
    }

    /** Floors the offset independently, preserving every bit of the absolute coordinate. */
    public static Int1088 addDoubleBlockOffset(Int1088 block, double offset) {
        if (!Double.isFinite(offset) || offset == 0.0D)
            return block;
        return block.addSaturated(Int1088.fromDoubleFloorSaturated(offset));
    }

    public static Int1088 clampChunk(Int1088 chunk) {
        if (chunk.compareTo(MIN_CHUNK) < 0)
            return MIN_CHUNK;
        if (chunk.compareTo(MAX_CHUNK) > 0)
            return MAX_CHUNK;
        return chunk;
    }

    /** Converts a chunk coordinate to its first block without overflowing. */
    public static Int1088 chunkToBlock(Int1088 chunk) {
        return clampChunk(chunk).shiftLeftSaturated(4);
    }

    /** Adds a chunk offset, saturating to the representable chunk interval. */
    public static Int1088 addChunkOffset(Int1088 chunk, long offset) {
        return clampChunk(chunk.addSaturated(offset));
    }

    /** Returns null when the offset would leave the representable chunk range. */
    public static Int1088 tryAddChunkOffset(Int1088 chunk, long offset) {
        Int1088 result = chunk.addSaturated(offset);
        return result.compareTo(MIN_CHUNK) < 0 || result.compareTo(MAX_CHUNK) > 0 ? null : result;
    }

    /** Converts a region and local chunk offset, saturating each operation to 1088 bits. */
    public static Int1088 regionToChunk(Int1088 region, long localChunk) {
        return clampChunk(region.shiftLeftSaturated(5).addSaturated(Int1088.fromLong(localChunk)));
    }

    /** Adds a block offset, saturating only at the signed 1088-bit edges. */
    public static Int1088 addBlockOffset(Int1088 block, long offset) {
        return block.addSaturated(offset);
    }

    public static Int1088 addBlockOffset(Int1088 block, int offset) {
        return addBlockOffset(block, (long) offset);
    }

    public static Int1088 addBlockOffset(Int1088 block, Int1088 offset) {
        return block.addSaturated(offset);
    }

    public static Int1088 subtractBlockOffset(Int1088 block, long offset) {
        return block.subtractSaturated(Int1088.fromLong(offset));
    }

    public static Int1088 subtractBlockOffset(Int1088 block, int offset) {
        return subtractBlockOffset(block, (long) offset);
    }

    public static Int1088 multiplySaturated(Int1088 coordinate, long factor) {
        return coordinate.multiplySaturated(factor);
    }

    public static Int1088 multiplySaturated(Int1088 coordinate, int factor) {
        return multiplySaturated(coordinate, (long) factor);
    }

    /** Saturates each product and then their difference in the horizontal domain. */
    public static Int1088 crossComponent(Int1088 a, long b, Int1088 c, long d) {
        return a.multiplySaturated(b).subtractSaturated(c.multiplySaturated(d));
    }

    /** Adds two coordinates and subtracts a third, clamping only the final result. */
    public static Int1088 addSubtract(Int1088 first, Int1088 second, Int1088 subtrahend) {
        // Same-sign subtraction cannot overflow; do it first to preserve cancellation.
        if ((first.signum() < 0) == (subtrahend.signum() < 0))
            return first.subtract(subtrahend).addSaturated(second);
        if ((second.signum() < 0) == (subtrahend.signum() < 0))
            return second.subtract(subtrahend).addSaturated(first);
        return first.subtractSaturated(subtrahend).addSaturated(second);
    }

    /** Full signed coordinate, rounded only at the floating-point sampling boundary. */
    public static double noiseCoordinate(Int1088 coordinate) {
        return coordinate.toDoubleRounded();
    }

    /** Full-width scaling with the existing finite-coordinate safeguards. */
    public static double scaledNoiseCoordinate(Int1088 coordinate, double scale) {
        return coordinate.toDoubleRounded() * scale;
    }

    public static long clampChunk(long chunk) {
        return Math.max(MIN_CHUNK_LONG, Math.min(MAX_CHUNK_LONG, chunk));
    }

    /** Converts a chunk coordinate to its first block without overflowing. */
    public static long chunkToBlock(long chunk) {
        return clampChunk(chunk) << 4;
    }

    /** Converts an already low-64-projected X/Z block coordinate to a finite noise input. */
    public static double noiseCoordinate(long coordinate) {
        return clampProjectedNoiseCoordinate((double) coordinate);
    }

    /** Scales an already low-64-projected X/Z coordinate without allowing infinity into a noise sampler. */
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
        long result = first * second;
        // Overflow is routine for broken climate samples. Do not allocate an
        // exception (and capture a stack trace) for every biome-distance axis.
        return Math.multiplyHigh(first, second) == (result >> 63) ? result
                : (first ^ second) < 0L ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    /** Computes a determinant before clamping, preserving cancellation. */
    public static long crossComponent(long a, long b, long c, long d) {
        return clampToLong(crossComponent(Int1088.fromLong(a), b, Int1088.fromLong(c), d));
    }

    /** Adds two coordinates and subtracts a third, clamping only the final result. */
    public static long addSubtract(long first, long second, long subtrahend) {
        return clampToLong(addSubtract(Int1088.fromLong(first), Int1088.fromLong(second), Int1088.fromLong(subtrahend)));
    }

    private static long clampToLong(Int1088 value) {
        if (value.compareTo(Int1088.fromLong(Long.MIN_VALUE)) < 0)
            return Long.MIN_VALUE;
        if (value.compareTo(Int1088.fromLong(Long.MAX_VALUE)) > 0)
            return Long.MAX_VALUE;
        return value.lowLimb();
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

    /** Always true; not a security, availability, or movement-validation check. */
    public static boolean isValidBlock(Int1088 coordinate) {
        return true;
    }

    /** Always true; not a security, availability, or movement-validation check. */
    public static boolean isValidBlock(Int1088 x, Int1088 z) {
        return true;
    }

    /** Always true for the long projection; not a security check. */
    public static boolean isValidBlock(long coordinate) {
        return true;
    }

    /** Always true for the long projection; not a security check. */
    public static boolean isValidBlock(long x, long z) {
        return true;
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

    /** Always true; not a security, availability, or movement-validation check. */
    public static boolean isValidChunk(Int1088 x, Int1088 z) {
        return true;
    }

    /** Always true for the long projection; not a security check. */
    public static boolean isValidChunk(long x, long z) {
        return true;
    }

    /** Checks only the 64-bit projection's chunk interval, not world validity or security. */
    public static boolean isValidChunkCoordinate(long chunk) {
        return chunk >= MIN_CHUNK_LONG && chunk <= MAX_CHUNK_LONG;
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
        return distance == MAX_BLOCK_LONG ? MAX_BLOCK_LONG : distance + 1L;
    }

    /**
    * Returns a signed horizontal difference as a long, saturating a difference
    * outside the signed-long domain instead of wrapping it to the other edge.
    */
    public static long signedDifferenceAsLong(long value, long origin) {
        double difference = signedDifference(value, origin);
        if (difference >= (double) MAX_BLOCK_LONG)
            return MAX_BLOCK_LONG;
        if (difference <= (double) MIN_BLOCK_LONG)
            return MIN_BLOCK_LONG;
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
        if (offset > 0L && block > MAX_BLOCK_LONG - offset)
            return MAX_BLOCK_LONG;
        if (offset < 0L) {
            if (offset == Long.MIN_VALUE)
                return block < 0L ? MIN_BLOCK_LONG : MIN_BLOCK_LONG + block;
            if (block < MIN_BLOCK_LONG - offset)
                return MIN_BLOCK_LONG;
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
            return block < 0L ? MAX_BLOCK_LONG + block + 1L : MAX_BLOCK_LONG;
        return addBlockOffset(block, -offset);
    }

    /** Adds a small chunk offset without wrapping at a long edge. */
    public static long addChunkOffset(long chunk, long offset) {
        if (offset > 0L && chunk > MAX_CHUNK_LONG - offset)
            return MAX_CHUNK_LONG;
        if (offset < 0L) {
            if (offset == Long.MIN_VALUE)
                return chunk < 0L ? MIN_CHUNK_LONG : MIN_CHUNK_LONG + chunk;
            if (chunk < MIN_CHUNK_LONG - offset)
                return MIN_CHUNK_LONG;
        }
        return chunk + offset;
    }

    /** Returns null when the offset would leave the representable chunk range. */
    public static Long tryAddChunkOffset(long chunk, long offset) {
        if (offset > 0L && chunk > MAX_CHUNK_LONG - offset)
            return null;
        if (offset < 0L) {
            if (offset == Long.MIN_VALUE)
                return null;
            if (chunk < MIN_CHUNK_LONG - offset)
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
        return coordinate == Double.NEGATIVE_INFINITY ? -Double.MAX_VALUE
                : coordinate == Double.POSITIVE_INFINITY ? Double.MAX_VALUE : coordinate;
    }
}
