package net.minecraft.client.resources.sounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EntityBoundSoundInstance extends AbstractTickableSoundInstance {
    private final Entity entity;

    public EntityBoundSoundInstance(SoundEvent pSoundEvent, SoundSource pSource, float pVolume, float pPitch, Entity pEntity, long pSeed) {
        super(pSoundEvent, pSource, RandomSource.create(pSeed));
        this.volume = pVolume;
        this.pitch = pPitch;
        this.entity = pEntity;
        this.updatePosition();
    }

    public boolean canPlaySound() {
        return !this.entity.isSilent();
    }

    public void tick() {
        if (this.entity.isRemoved()) {
            this.stop();
        } else {
            this.updatePosition();
        }
    }

    private void updatePosition() {
        if (this.entity.hasSectorPosition()) {
            this.setExactPosition(this.entity.sectorPosition());
        } else {
            this.setPosition(this.entity.getX(), this.entity.getY(), this.entity.getZ());
        }
    }
}
