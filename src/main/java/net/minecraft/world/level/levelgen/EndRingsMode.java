package net.minecraft.world.level.levelgen;

public final class EndRingsMode {
   public static final String LEVEL_DATA_KEY = "EndRingsMode";
   private static volatile boolean enabled = true;

   private EndRingsMode() {
   }

   public static boolean isEnabled() {
      return enabled;
   }

   public static void setEnabled(boolean enabled) {
      EndRingsMode.enabled = enabled;
   }
}
