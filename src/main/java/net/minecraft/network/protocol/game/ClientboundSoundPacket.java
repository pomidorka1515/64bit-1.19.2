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

   /** Compatibility entry point for callers that have only a legacy double position. */
   public ClientboundSoundPacket(SoundEvent sound, SoundSource source, double x, double y, double z,
                                 float volume, float pitch, long seed) {
      this(sound, source, SectorVec3.fromApproximate(x, y, z), volume, pitch, seed);
   }

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
      this.position = SectorVec3.fromBlockAndFraction(buffer.readLong(), buffer.readDouble(), buffer.readDouble(),
            buffer.readLong(), buffer.readDouble());
      this.volume = buffer.readFloat();
      this.pitch = buffer.readFloat();
      this.seed = buffer.readLong();
   }

   public void write(FriendlyByteBuf buffer) {
      buffer.writeId(Registry.SOUND_EVENT, this.sound);
      buffer.writeEnum(this.source);
      buffer.writeLong(this.position.blockX());
      buffer.writeDouble(this.position.subX());
      buffer.writeDouble(this.position.y());
      buffer.writeLong(this.position.blockZ());
      buffer.writeDouble(this.position.subZ());
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

   /** Lossy compatibility mirror; exact consumers must use {@link #getExactPosition()}. */
   public double getX() {
      return (double)this.position.blockX() + this.position.subX();
   }

   public double getY() {
      return this.position.y();
   }

   /** Lossy compatibility mirror; exact consumers must use {@link #getExactPosition()}. */
   public double getZ() {
      return (double)this.position.blockZ() + this.position.subZ();
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

   public void handle(ClientGamePacketListener p_133454_) {
      p_133454_.handleSoundEvent(this);
   }
}