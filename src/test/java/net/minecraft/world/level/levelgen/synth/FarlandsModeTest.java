package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FarlandsModeTest {
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

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64);
      assertEquals(legacy, FarlandsMode.scaledNoiseCoordinate(coordinate, scale));
      assertFalse(FarlandsMode.isEnabled());
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
      assertEquals(FarlandsMode.Mode.BIT_64, FarlandsMode.fromSerializedName("64bit"));
      assertEquals(FarlandsMode.Mode.OFF, FarlandsMode.fromSerializedName("off"));
      assertEquals(FarlandsMode.Mode.BIT_32, FarlandsMode.fromSerializedName("true"));
      assertEquals(FarlandsMode.Mode.BIT_64, FarlandsMode.fromSerializedName("false"));
      assertEquals(FarlandsMode.Mode.BIT_32, FarlandsMode.fromSerializedName("unknown"));
      assertEquals("off", FarlandsMode.Mode.OFF.serializedName());
   }
}
