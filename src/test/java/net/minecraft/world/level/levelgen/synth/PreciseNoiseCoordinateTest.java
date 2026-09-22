package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PreciseNoiseCoordinateTest {
   private static final MathContext MC = new MathContext(80);

   @org.junit.jupiter.api.BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @ParameterizedTest
   @MethodSource("coordinates")
   void splitMatchesHighPrecisionProduct(long coordinate, double scale) {
      BigDecimal product = BigDecimal.valueOf(coordinate).multiply(exactDouble(scale), MC);
      BigInteger floor = product.setScale(0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
      BigDecimal fraction = product.subtract(new BigDecimal(floor), MC);
      long expectedLattice = floor.mod(BigInteger.ONE.shiftLeft(64)).longValue();
      assertEquals(expectedLattice, PreciseNoiseCoordinate.scaledLattice(coordinate, scale));
      assertEquals(fraction.doubleValue(), PreciseNoiseCoordinate.scaledFraction(coordinate, scale), "coordinate=" + coordinate + ", scale=" + scale);
      assertTrue(PreciseNoiseCoordinate.scaledFraction(coordinate, scale) >= 0.0D);
      assertTrue(PreciseNoiseCoordinate.scaledFraction(coordinate, scale) < 1.0D);
   }

   @Test
   void randomizedCoordinatesRemainExact() {
      Random random = new Random(0x64f00dL);
      double[] scales = {684.412D, 0.25D, 1.0181268882175227D, 0.2D, 0.75D, 1.28D};
      for (int i = 0; i < 256; ++i) {
         long coordinate = random.nextLong();
         double scale = scales[random.nextInt(scales.length)];
         assertSplit(coordinate, scale);
      }
   }

   @Test
   void roundedNegativeFractionCarriesIntoLattice() {
      // 1 - 2^-54 rounds to 1.0 in a double. The pair must represent this
      // as the next lattice point plus zero, rather than a wrapped fraction.
      double scale = Math.scalb(1.0D, -54);
      assertEquals(0L, PreciseNoiseCoordinate.scaledLattice(-1L, scale));
      assertEquals(0.0D, PreciseNoiseCoordinate.scaledFraction(-1L, scale));
   }

   @Test
   void exactIntegerFractionStaysZeroForNegativeCoordinates() {
      assertEquals(-1L, PreciseNoiseCoordinate.scaledLattice(-4L, 0.25D));
      assertEquals(0.0D, PreciseNoiseCoordinate.scaledFraction(-4L, 0.25D));
      assertEquals(-1L, PreciseNoiseCoordinate.scaledLattice(-1L, 1.0D));
      assertEquals(0.0D, PreciseNoiseCoordinate.scaledFraction(-1L, 1.0D));
   }

   @Test
   void preciseNoiseRemainsFiniteAtLongEdges() {
      NormalNoise noise = NormalNoise.create(new LegacyRandomSource(1234L), 0, 1.0D, 0.5D, 0.25D);
      long[] coordinates = {1L << 62, -(1L << 62), Long.MAX_VALUE, Long.MIN_VALUE};
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      for (long coordinate : coordinates) {
         double value = noise.getValueScaled(coordinate, 0.25D, 0.0D, coordinate);
         assertTrue(Double.isFinite(value));
      }

      double first = noise.getValueScaled((1L << 62), 0.25D, 0.0D, (1L << 62));
      double adjacent = noise.getValueScaled((1L << 62) + 1L, 0.25D, 0.0D, (1L << 62) + 1L);
      assertTrue(Double.isFinite(first));
      assertTrue(Double.isFinite(adjacent));
      assertTrue(Double.doubleToRawLongBits(first) != Double.doubleToRawLongBits(adjacent));
   }

   @org.junit.jupiter.api.Test
   void preciseSmallCoordinatesUseLegacyDoubleParityPath() {
      NormalNoise noise = NormalNoise.create(new LegacyRandomSource(5678L), -2, 1.0D, 0.5D, 0.25D);
      long coordinate = 1L << 52;
      double x = (double)coordinate * 0.25D;
      FarlandsMode.setMode(FarlandsMode.Mode.OFF);
      double off = noise.getValue(x, 0.0D, x);
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      double precise = noise.getValue(x, 0.0D, x);
      assertEquals(Double.doubleToRawLongBits(off), Double.doubleToRawLongBits(precise));
   }

   @Test
   void preciseNoiseRetainsTheZAxisAtLargeCoordinates() {
      NormalNoise noise = NormalNoise.create(new LegacyRandomSource(2468L), -2, 1.0D, 0.5D, 0.25D);
      long x = 1L << 60;
      long z = -(1L << 60);
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      double base = noise.getValueScaled(x, 0.25D, 37, z);
      boolean changesWithZ = false;
      for (long offset = 1L; offset <= 32L; ++offset) {
         if (Double.doubleToRawLongBits(base) != Double.doubleToRawLongBits(noise.getValueScaled(x, 0.25D, 37, z + offset))) {
            changesWithZ = true;
            break;
         }
      }

      assertTrue(changesWithZ, "precise 3D noise must retain Z input");
   }

   private static void assertSplit(long coordinate, double scale) {
      long bits = Double.doubleToRawLongBits(scale);
      long mantissa = bits & 0x000fffffffffffffL;
      int encodedExponent = (int)((bits >>> 52) & 2047L);
      int shift = encodedExponent == 0 ? 1074 : 1075 - encodedExponent;
      if (encodedExponent != 0) mantissa |= 0x0010000000000000L;
      BigInteger numerator = BigInteger.valueOf(coordinate).multiply(BigInteger.valueOf(mantissa));
      BigInteger denominator = BigInteger.ONE.shiftLeft(shift);
      BigInteger[] division = numerator.divideAndRemainder(denominator);
      BigInteger floor = division[0];
      BigInteger remainder = division[1];
      if (remainder.signum() < 0) {
         floor = floor.subtract(BigInteger.ONE);
         remainder = remainder.add(denominator);
      }
      double expectedFraction = new BigDecimal(remainder).divide(new BigDecimal(denominator), 200, java.math.RoundingMode.HALF_EVEN).doubleValue();
      assertEquals(floor.longValue(), PreciseNoiseCoordinate.scaledLattice(coordinate, scale));
      double actualFraction = PreciseNoiseCoordinate.scaledFraction(coordinate, scale);
      assertTrue(Math.abs(expectedFraction - actualFraction) <= Math.ulp(expectedFraction), "coordinate=" + coordinate + ", scale=" + scale + ", expected=" + Double.toHexString(expectedFraction) + ", actual=" + Double.toHexString(actualFraction));
      assertTrue(actualFraction >= 0.0D);
      assertTrue(PreciseNoiseCoordinate.scaledFraction(coordinate, scale) < 1.0D);
   }

   private static BigDecimal exactDouble(double value) {
      long bits = Double.doubleToRawLongBits(value);
      boolean negative = bits < 0L;
      int encodedExponent = (int)((bits >>> 52) & 2047L);
      long mantissa = bits & 0x000fffffffffffffL;
      int exponent;
      if (encodedExponent == 0) {
         exponent = -1074;
      } else {
         mantissa |= 0x0010000000000000L;
         exponent = encodedExponent - 1075;
      }
      BigDecimal result = new BigDecimal(BigInteger.valueOf(mantissa));
      result = exponent >= 0 ? result.multiply(new BigDecimal(BigInteger.ONE.shiftLeft(exponent))) : result.divide(new BigDecimal(BigInteger.ONE.shiftLeft(-exponent)), MC);
      return negative ? result.negate() : result;
   }

   private static Stream<Arguments> coordinates() {
      return Stream.of(
         Arguments.of(0L, 684.412D),
         Arguments.of(1L, 0.25D),
         Arguments.of(-1L, 1.0181268882175227D),
         Arguments.of(9007199254740991L, 684.412D),
         Arguments.of(9007199254740993L, 0.25D),
         Arguments.of(-9007199254740993L, 1.0181268882175227D),
         Arguments.of(Long.MAX_VALUE, 684.412D),
         Arguments.of(Long.MIN_VALUE, 0.25D),
         Arguments.of(Long.MIN_VALUE + 1L, 1.0181268882175227D)
      );
   }
}
