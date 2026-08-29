package net.minecraft.world.level;

/**
 * The representable horizontal coordinate limits of the long-coordinate world.
 *
 * <p>A block coordinate is a signed long.  Dividing the two block limits by
 * sixteen gives the only chunk coordinates that can contain a representable
 * block.  Keeping these values in one place is important: an overflowing
 * {@code long} subtraction must never turn a request at an edge of the world
 * into a request at the opposite edge.</p>
 */
public final class WorldBounds {
   public static final long MIN_BLOCK = Long.MIN_VALUE;
   public static final long MAX_BLOCK = Long.MAX_VALUE;
   public static final long MIN_CHUNK = MIN_BLOCK >> 4;
   public static final long MAX_CHUNK = MAX_BLOCK >> 4;

   private WorldBounds() {
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
      return clampAbsoluteDouble((double)coordinate);
   }

   /** Scales an X/Z coordinate without allowing infinity into a noise sampler. */
   public static double scaledNoiseCoordinate(long coordinate, double scale) {
      double result = (double)coordinate * scale;
      if (Double.isNaN(result)) return 0.0D;
      if (!Double.isFinite(result)) return result < 0.0D ? -0x1.0p63 : Math.nextDown(0x1.0p63);
      return clampAbsoluteDouble(result);
   }

   /** Clamps an inclusive Y coordinate to a build-height interval. */
   public static int clampBuildHeight(int coordinate, int minY, int maxYExclusive) {
      if (maxYExclusive <= minY) return minY;
      return Math.max(minY, Math.min(maxYExclusive - 1, coordinate));
   }

   /** Keeps a surface depth inside the generated column's finite range. */
   public static int clampSurfaceDepth(int depth, int generationDepth) {
      int limit = Math.max(0, generationDepth);
      return Math.max(-limit, Math.min(limit, depth));
   }

   /** Saturating addition for values used by vertical rule expressions. */
   public static int addSaturated(int first, int second) {
      long result = (long)first + second;
      return result < Integer.MIN_VALUE ? Integer.MIN_VALUE : result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)result;
   }

   /** Saturating multiplication for values used by vertical rule expressions. */
   public static int multiplySaturated(int first, int second) {
      long result = (long)first * second;
      return result < Integer.MIN_VALUE ? Integer.MIN_VALUE : result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)result;
   }

   /**
    * Noise is expected to be normalized.  Far-away coordinates can make the
    * legacy noise implementation produce very large values, so do not allow
    * those values to enter surface arithmetic.
    */
   public static double clampNoise(double value) {
      if (Double.isNaN(value)) return 0.0D;
      if (value <= -1.0D) return -1.0D;
      if (value >= 1.0D) return 1.0D;
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
      if (offset > 0L && value > Long.MAX_VALUE - offset) return Long.MAX_VALUE;
      if (offset < 0L && value < Long.MIN_VALUE - offset) return Long.MIN_VALUE;
      if (offset == Long.MIN_VALUE) return Long.MIN_VALUE;
      return value + offset;
   }

   public static boolean isValidChunk(long x, long z) {
      return isValidChunkCoordinate(x) && isValidChunkCoordinate(z);
   }

   public static boolean isValidChunkCoordinate(long chunk) {
      return chunk >= MIN_CHUNK && chunk <= MAX_CHUNK;
   }

   /** Adds a block offset without wrapping at a signed-long edge. */
   public static long addBlockOffset(long block, long offset) {
      if (offset > 0L && block > MAX_BLOCK - offset) return MAX_BLOCK;
      if (offset < 0L) {
         if (offset == Long.MIN_VALUE) return block < 0L ? MIN_BLOCK : MIN_BLOCK + block;
         if (block < MIN_BLOCK - offset) return MIN_BLOCK;
      }
      return block + offset;
   }

   /** Adds a signed block offset while preserving the legal interval. */
   public static long addBlockOffset(long block, int offset) {
      return addBlockOffset(block, (long)offset);
   }

   /** Subtracts a block offset without wrapping at a signed-long edge. */
   public static long subtractBlockOffset(long block, long offset) {
      if (offset == Long.MIN_VALUE) return block < 0L ? MAX_BLOCK + block + 1L : MAX_BLOCK;
      return addBlockOffset(block, -offset);
   }

   /** Adds a small chunk offset without wrapping at a long edge. */
   public static long addChunkOffset(long chunk, long offset) {
      if (offset > 0L && chunk > MAX_CHUNK - offset) return MAX_CHUNK;
      if (offset < 0L) {
         if (offset == Long.MIN_VALUE) return chunk < 0L ? MIN_CHUNK : MIN_CHUNK + chunk;
         if (chunk < MIN_CHUNK - offset) return MIN_CHUNK;
      }
      return chunk + offset;
   }

   /** Returns null when the offset would leave the representable chunk range. */
   public static Long tryAddChunkOffset(long chunk, long offset) {
      if (offset > 0L && chunk > MAX_CHUNK - offset) return null;
      if (offset < 0L) {
         if (offset == Long.MIN_VALUE) return null;
         if (chunk < MIN_CHUNK - offset) return null;
      }
      long result = chunk + offset;
      return isValidChunkCoordinate(result) ? result : null;
   }

   /** Overflow-safe absolute difference, saturated rather than wrapped. */
   public static double distance(long first, long second) {
      if (first == second) return 0.0D;
      if (first >= 0L && second < 0L) return (double)first + -(double)second;
      if (second >= 0L && first < 0L) return (double)second + -(double)first;
      return first >= second ? (double)(first - second) : (double)(second - first);
   }

   /** Returns whether two coordinates are no farther apart than radius. */
   public static boolean within(long coordinate, long center, long radius) {
      return radius >= 0L && distance(coordinate, center) <= (double)radius;
   }

   /** Returns value - origin without allowing signed-long subtraction to wrap. */
   public static double signedDifference(long value, long origin) {
      if (value >= 0L && origin < 0L) return (double)value - (double)origin;
      if (value < 0L && origin >= 0L) return (double)value - (double)origin;
      return (double)(value - origin);
   }

   /** Returns the upper center of an inclusive block-coordinate interval. */
   public static long middleBlockCoordinate(long min, long max) {
      if (min < 0L && max > 0L) {
         long sum = min + max;
         return sum >= 0L ? sum / 2L + sum % 2L : sum / 2L;
      }

      long difference = max - min;
      return min + difference / 2L + difference % 2L;
   }

   /** Keeps legacy entity Y ingress inside the range supported by BlockPos/AABB physics. */
   public static double clampVerticalDouble(double coordinate) {
      if (Double.isNaN(coordinate)) return 0.0D;
      if (coordinate == Double.NEGATIVE_INFINITY || coordinate < -20_000_000.0D) return -20_000_000.0D;
      if (coordinate == Double.POSITIVE_INFINITY || coordinate > 20_000_000.0D) return 20_000_000.0D;
      return coordinate;
   }

   /** Clamps an ordinary absolute double to the representable block interval. */
   public static double clampAbsoluteDouble(double coordinate) {
      if (Double.isNaN(coordinate)) return 0.0D;
      if (coordinate == Double.NEGATIVE_INFINITY || coordinate < (double)MIN_BLOCK) return (double)MIN_BLOCK;
      // 2^63 itself has no valid floor block.  The next representable double
      // below it is still inside the last block and is the correct safe edge.
      double upperExclusive = 0x1.0p63;
      if (coordinate == Double.POSITIVE_INFINITY || coordinate >= upperExclusive) return Math.nextDown(upperExclusive);
      return coordinate;
   }
}
