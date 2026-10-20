package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

/** A custom positional sound with an exact split-coordinate origin. */
public class ClientboundCustomSoundPacket implements Packet<ClientGamePacketListener> {
    public static final float LOCATION_ACCURACY = 8.0F;
    private final ResourceLocation name;
    private final SoundSource source;
    private final SectorVec3 position;
    private final float volume;
    private final float pitch;
    private final long seed;

    /** Compatibility constructor for callers with an already-lossy approximate position. */
    public ClientboundCustomSoundPacket(ResourceLocation name, SoundSource source, Vec3 position,
            float volume, float pitch, long seed) {
        this(name, source, SectorVec3.fromApproximate(position.x, position.y, position.z), volume, pitch, seed);
    }

    public ClientboundCustomSoundPacket(ResourceLocation name, SoundSource source, SectorVec3 position,
            float volume, float pitch, long seed) {
        this.name = name;
        this.source = source;
        this.position = position;
        this.volume = volume;
        this.pitch = pitch;
        this.seed = seed;
    }

    public ClientboundCustomSoundPacket(FriendlyByteBuf buffer) {
        this.name = buffer.readResourceLocation();
        this.source = buffer.readEnum(SoundSource.class);
        this.position = SectorVec3.fromBlockAndFraction(buffer.readInt128(), buffer.readDouble(), buffer.readDouble(),
                buffer.readInt128(), buffer.readDouble());
        this.volume = buffer.readFloat();
        this.pitch = buffer.readFloat();
        this.seed = buffer.readLong();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.name);
        buffer.writeEnum(this.source);
        buffer.writeInt128(this.position.blockX());
        buffer.writeDouble(this.position.subX());
        buffer.writeDouble(this.position.y());
        buffer.writeInt128(this.position.blockZ());
        buffer.writeDouble(this.position.subZ());
        buffer.writeFloat(this.volume);
        buffer.writeFloat(this.pitch);
        buffer.writeLong(this.seed);
    }

    public ResourceLocation getName() {
        return this.name;
    }

    public SoundSource getSource() {
        return this.source;
    }

    public SectorVec3 getExactPosition() {
        return this.position;
    }

    /** Lossy compatibility mirror; exact consumers must use {@link #getExactPosition()}. */
    public double getX() {
        return this.position.toApproximateVec3().x;
    }

    public double getY() {
        return this.position.y();
    }

    /** Lossy compatibility mirror; exact consumers must use {@link #getExactPosition()}. */
    public double getZ() {
        return this.position.toApproximateVec3().z;
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
        pHandler.handleCustomSoundEvent(this);
    }
}
