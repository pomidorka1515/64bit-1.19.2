package net.minecraft.network.protocol.game;

import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.SectorVec3;
import org.apache.commons.lang3.Validate;

/** A positional sound with an exact split-coordinate origin. */
public class ClientboundSoundPacket implements Packet<ClientGamePacketListener> {
    public static final float LOCATION_ACCURACY = 8.0F;
    private final SoundEvent sound;
    private final SoundSource source;
    private final SectorVec3 position;
    private final float volume;
    private final float pitch;
    private final long seed;

    public ClientboundSoundPacket(SoundEvent sound, SoundSource source, SectorVec3 position,
            float volume, float pitch, long seed) {
        this.sound = Validate.notNull(sound, "sound");
        this.source = Validate.notNull(source, "source");
        this.position = Validate.notNull(position, "position");
        this.volume = volume;
        this.pitch = pitch;
        this.seed = seed;
    }

    public ClientboundSoundPacket(FriendlyByteBuf buffer) {
        this.sound = buffer.readById(Registry.SOUND_EVENT);
        this.source = buffer.readEnum(SoundSource.class);
        this.position = buffer.readSectorVec3();
        this.volume = buffer.readFloat();
        this.pitch = buffer.readFloat();
        this.seed = buffer.readLong();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeId(Registry.SOUND_EVENT, this.sound);
        buffer.writeEnum(this.source);
        buffer.writeSectorVec3(this.position);
        buffer.writeFloat(this.volume);
        buffer.writeFloat(this.pitch);
        buffer.writeLong(this.seed);
    }

    public SoundEvent getSound() {
        return this.sound;
    }

    public SoundSource getSource() {
        return this.source;
    }

    public SectorVec3 getExactPosition() {
        return this.position;
    }


    public double getY() {
        return this.position.y();
    }


    public float getVolume() {
        return this.volume;
    }

    public float getPitch() {
        return this.pitch;
    }

    public long getSeed() {
        return this.seed;
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleSoundEvent(this);
    }
}
