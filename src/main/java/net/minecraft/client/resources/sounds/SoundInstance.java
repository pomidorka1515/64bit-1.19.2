package net.minecraft.client.resources.sounds;

import javax.annotation.Nullable;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SoundInstance {
   ResourceLocation getLocation();

   @Nullable
   WeighedSoundEvents resolve(SoundManager p_119841_);

   Sound getSound();

   SoundSource getSource();

   boolean isLooping();

   boolean isRelative();

   int getDelay();

   float getVolume();

   float getPitch();

   double getX();

   double getY();

   double getZ();

   /**
    * Exact world position when this is a positional sound.  {@code null}
    * denotes a legacy/local source, whose getX/Y/Z values are used only as a
    * listener-space offset when it is not OpenAL-relative.
    */
   @Nullable
   default SectorVec3 getExactPosition() {
      return null;
   }

   SoundInstance.Attenuation getAttenuation();

   default boolean canStartSilent() {
      return false;
   }

   default boolean canPlaySound() {
      return true;
   }

   static RandomSource createUnseededRandom() {
      return RandomSource.create();
   }

   @OnlyIn(Dist.CLIENT)
   public static enum Attenuation {
      NONE,
      LINEAR;
   }
}