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
    WeighedSoundEvents resolve(SoundManager pManager);

    Sound getSound();

    SoundSource getSource();

    boolean isLooping();

    boolean isRelative();

    int getDelay();

    float getVolume();

    float getPitch();

    double getY();

    /**
    * Exact world position when this is a positional sound.  {@code null}
     * denotes a missing or malformed source. Relative sources use positions
     * relative to the zero origin, rather than the world listener.
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
