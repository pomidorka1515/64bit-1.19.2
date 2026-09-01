package net.minecraft.world.level.levelgen.synth;

import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldBounds;

/** Controls which legacy Far Lands arithmetic and coordinate handling noise uses. */
public final class FarlandsMode {
   public static final String LEVEL_DATA_KEY = "FarlandsMode";
   private static volatile Mode mode = Mode.BIT_32;

   private FarlandsMode() {
   }

   public static Mode getMode() {
      return mode;
   }

   /** Returns whether the original 32-bit Far Lands arithmetic is active globally. */
   public static boolean isEnabled() {
      return mode == Mode.BIT_32;
   }

   /** Returns whether BlendedNoise should retain its original 32-bit arithmetic. */
   public static boolean usesLegacyBlendedNoise() {
      return mode == Mode.BIT_32 || mode == Mode.BIT_32_HYBRID;
   }

   /** Returns whether the no-Far-Lands coordinate clamp is active. */
   public static boolean isOff() {
      return mode == Mode.OFF;
   }

   /**
    * Retained for old level.dat values: true selects the original 32-bit route
    * and false selects the original 64-bit route.
    */
   public static void setEnabled(boolean enabled) {
      setMode(enabled ? Mode.BIT_32 : Mode.BIT_64);
   }

   public static void setMode(Mode mode) {
      FarlandsMode.mode = mode == null ? Mode.BIT_32 : mode;
   }

   public static Mode fromSerializedName(String name) {
      if (name != null) {
         for(Mode mode : Mode.values()) {
            if (mode.serializedName.equals(name)) {
               return mode;
            }
         }
      }

      // The previous boolean representation can be read without changing its
      // established meaning for worlds saved before the three-way mode existed.
      if ("true".equals(name)) {
         return Mode.BIT_32;
      } else {
         return "false".equals(name) ? Mode.BIT_64 : Mode.BIT_32;
      }
   }

   /**
    * Applies the newer finite-coordinate patch only to the explicit off mode.
    * The 32-bit and 64-bit routes deliberately retain their old expressions.
    */
   public static double scaledNoiseCoordinate(long coordinate, double scale) {
      return isOff() ? WorldBounds.scaledNoiseCoordinate(coordinate, scale) : (double)coordinate * scale;
   }

   /** Applies the shifted-noise portion of the patch only to the off mode. */
   public static double shiftedNoiseCoordinate(long coordinate, double scale, double shift) {
      return isOff() ? WorldBounds.clampAbsoluteDouble(WorldBounds.scaledNoiseCoordinate(coordinate, scale) + shift) : (double)coordinate * scale + shift;
   }

   static long floor(double value, boolean farlands) {
      return farlands ? (long)Mth.floor(value) : Mth.lfloor(value);
   }

   static double floorSum(long first, long second, boolean farlands) {
      return farlands ? (double)((int)first + (int)second) : (double)(first + second);
   }

   static double floorSum(long first, long second, long third, boolean farlands) {
      return farlands ? (double)((int)first + (int)second + (int)third) : (double)(first + second + third);
   }

   public enum Mode {
      BIT_32("32bit", "32-bit"),
      BIT_32_HYBRID("32bit-hybrid", "32-bit (hybrid)"),
      BIT_64("64bit", "64-bit"),
      OFF("off", "64-bit (no farlands)");

      private final String serializedName;
      private final String generatorDescription;

      Mode(String serializedName, String generatorDescription) {
         this.serializedName = serializedName;
         this.generatorDescription = generatorDescription;
      }

      public String serializedName() {
         return this.serializedName;
      }

      public String generatorDescription() {
         return this.generatorDescription;
      }
   }
}
