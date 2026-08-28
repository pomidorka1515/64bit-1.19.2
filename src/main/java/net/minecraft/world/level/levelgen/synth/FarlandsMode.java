package net.minecraft.world.level.levelgen.synth;

import net.minecraft.util.Mth;

/** Controls whether noise uses the intentionally broken Far Lands arithmetic. */
public final class FarlandsMode {
   public static final String LEVEL_DATA_KEY = "FarlandsMode";
   private static volatile boolean enabled = true;

   private FarlandsMode() {
   }

   public static boolean isEnabled() {
      return enabled;
   }

   public static void setEnabled(boolean enabled) {
      FarlandsMode.enabled = enabled;
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
}
