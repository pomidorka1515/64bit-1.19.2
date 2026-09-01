package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FarlandsModeTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @AfterEach
   void restoreDefaultMode() {
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
   }

   @Test
   void offIsTheOnlyModeThatUsesTheNoFarlandsCoordinatePatch() {
      long coordinate = Long.MAX_VALUE;
      double scale = 684.412D;
      double legacy = (double)coordinate * scale;

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
      assertEquals(legacy, FarlandsMode.scaledNoiseCoordinate(coordinate, scale));
      assertTrue(FarlandsMode.isEnabled());

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_32_HYBRID);
      assertEquals(legacy, FarlandsMode.scaledNoiseCoordinate(coordinate, scale));
      assertFalse(FarlandsMode.isEnabled());
      assertTrue(FarlandsMode.usesLegacyBlendedNoise());
      assertFalse(FarlandsMode.isOff());

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64);
      assertEquals(legacy, FarlandsMode.scaledNoiseCoordinate(coordinate, scale));
      assertFalse(FarlandsMode.isEnabled());
      assertFalse(FarlandsMode.usesLegacyBlendedNoise());
      assertFalse(FarlandsMode.isOff());

      FarlandsMode.setMode(FarlandsMode.Mode.OFF);
      assertEquals(WorldBounds.scaledNoiseCoordinate(coordinate, scale), FarlandsMode.scaledNoiseCoordinate(coordinate, scale));
      assertFalse(FarlandsMode.isEnabled());
      assertTrue(FarlandsMode.isOff());
      assertEquals("64-bit (no farlands)", FarlandsMode.getMode().generatorDescription());
   }

   @Test
   void serializedModesPreserveOldBooleanWorlds() {
      assertEquals(FarlandsMode.Mode.BIT_32, FarlandsMode.fromSerializedName("32bit"));
      assertEquals(FarlandsMode.Mode.BIT_32_HYBRID, FarlandsMode.fromSerializedName("32bit-hybrid"));
      assertEquals(FarlandsMode.Mode.BIT_64, FarlandsMode.fromSerializedName("64bit"));
      assertEquals(FarlandsMode.Mode.OFF, FarlandsMode.fromSerializedName("off"));
      assertEquals(FarlandsMode.Mode.BIT_32, FarlandsMode.fromSerializedName("true"));
      assertEquals(FarlandsMode.Mode.BIT_64, FarlandsMode.fromSerializedName("false"));
      assertEquals(FarlandsMode.Mode.BIT_32, FarlandsMode.fromSerializedName("unknown"));
      assertEquals("32bit-hybrid", FarlandsMode.Mode.BIT_32_HYBRID.serializedName());
      assertEquals("off", FarlandsMode.Mode.OFF.serializedName());
   }

   @Test
   void hybridUsesLegacyArithmeticOnlyForBlendedNoise() {
      double coordinate = 16_000_000_000.0D;
      ImprovedNoise improvednoise = new ImprovedNoise(new LegacyRandomSource(1234L));
      BlendedNoise blendednoise = new BlendedNoise(new LegacyRandomSource(1234L), 0.25D, 0.125D, 80.0D, 160.0D, 8.0D);
      DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext((long)coordinate, 0, 0L);

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_32_HYBRID);
      assertFalse(FarlandsMode.isEnabled());
      assertTrue(FarlandsMode.usesLegacyBlendedNoise());
      ImprovedNoise.beginDebugCapture();
      double hybridOrdinaryNoise;

      try {
         hybridOrdinaryNoise = improvednoise.noise(coordinate, 0.0D, 0.0D);
      } finally {
         ImprovedNoise.endDebugCapture();
      }

      assertUnitCellOffset(ImprovedNoise.getDebugString2());
      assertTrue(Double.isFinite(hybridOrdinaryNoise));
      double hybridBlendedNoise = blendednoise.compute(context);

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
      assertTrue(FarlandsMode.isEnabled());
      assertTrue(FarlandsMode.usesLegacyBlendedNoise());
      double legacyBlendedNoise = blendednoise.compute(context);

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64);
      assertFalse(FarlandsMode.usesLegacyBlendedNoise());
      double sixtyFourBitOrdinaryNoise = improvednoise.noise(coordinate, 0.0D, 0.0D);
      double sixtyFourBitBlendedNoise = blendednoise.compute(context);

      assertEquals(sixtyFourBitOrdinaryNoise, hybridOrdinaryNoise);
      assertEquals(legacyBlendedNoise, hybridBlendedNoise);
      assertTrue(Double.isFinite(hybridBlendedNoise));
      assertTrue(Double.isFinite(sixtyFourBitBlendedNoise));
      assertNotEquals(sixtyFourBitBlendedNoise, hybridBlendedNoise);
   }

   private static void assertUnitCellOffset(String debugString) {
      String[] astring = debugString.split(" ");
      double cellOffset = Double.parseDouble(astring[2]);
      double smoothedOffset = Double.parseDouble(astring[5]);
      assertTrue(cellOffset >= 0.0D && cellOffset <= 1.0D);
      assertTrue(smoothedOffset >= 0.0D && smoothedOffset <= 1.0D);
   }
}
