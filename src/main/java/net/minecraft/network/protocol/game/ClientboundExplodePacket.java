package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class ClientboundExplodePacket implements Packet<ClientGamePacketListener> {
    private final SectorVec3 position;
    private final float power;
    private final List<BlockPos> toBlow;
    private final float knockbackX;
    private final float knockbackY;
    private final float knockbackZ;

    /** Compatibility constructor for legacy callers with already-lossy doubles. */
    public ClientboundExplodePacket(double x, double y, double z, float power, List<BlockPos> toBlow,
            @Nullable Vec3 knockback) {
        this(SectorVec3.fromApproximate(x, y, z), power, toBlow, knockback);
    }

    public ClientboundExplodePacket(SectorVec3 position, float power, List<BlockPos> toBlow,
            @Nullable Vec3 knockback) {
        this.position = position;
        this.power = power;
        this.toBlow = Lists.newArrayList(toBlow);
        if (knockback != null) {
            this.knockbackX = (float) knockback.x;
            this.knockbackY = (float) knockback.y;
            this.knockbackZ = (float) knockback.z;
        } else {
            this.knockbackX = 0.0F;
            this.knockbackY = 0.0F;
            this.knockbackZ = 0.0F;
        }
    }

    public ClientboundExplodePacket(FriendlyByteBuf buffer) {
        this.position = SectorVec3.fromBlockAndFraction(buffer.readInt128(), buffer.readDouble(), buffer.readDouble(),
                buffer.readInt128(), buffer.readDouble());
        this.power = buffer.readFloat();
        Int128 blockX = this.position.blockX();
        int blockY = Mth.floor(this.position.y());
        Int128 blockZ = this.position.blockZ();
        this.toBlow = buffer.readList(input -> new BlockPos(WorldBounds.addBlockOffset(blockX, (long) input.readByte()), input.readByte() + blockY, WorldBounds.addBlockOffset(blockZ, (long) input.readByte())));
        this.knockbackX = buffer.readFloat();
        this.knockbackY = buffer.readFloat();
        this.knockbackZ = buffer.readFloat();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt128(this.position.blockX());
        buffer.writeDouble(this.position.subX());
        buffer.writeDouble(this.position.y());
        buffer.writeInt128(this.position.blockZ());
        buffer.writeDouble(this.position.subZ());
        buffer.writeFloat(this.power);
        Int128 blockX = this.position.blockX();
        int blockY = Mth.floor(this.position.y());
        Int128 blockZ = this.position.blockZ();
        buffer.writeCollection(this.toBlow, (output, block) -> {
            long deltaX = WorldBounds.signedDifferenceAsLong(block.getX(), blockX);
            int deltaY = block.getY() - blockY;
            long deltaZ = WorldBounds.signedDifferenceAsLong(block.getZ(), blockZ);
            output.writeByte((int) deltaX);
            output.writeByte(deltaY);
            output.writeByte((int) deltaZ);
        });
        buffer.writeFloat(this.knockbackX);
        buffer.writeFloat(this.knockbackY);
        buffer.writeFloat(this.knockbackZ);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleExplosion(this);
    }

    public float getKnockbackX() {
        return this.knockbackX;
    }

    public float getKnockbackY() {
        return this.knockbackY;
    }

    public float getKnockbackZ() {
        return this.knockbackZ;
    }

    /** Exact split-coordinate explosion origin. */
    public SectorVec3 getExactPosition() {
        return this.position;
    }

    /** Lossy compatibility mirror; exact consumers must use {@link #getExactPosition()}. */
    public double getX() {
        return this.position.toApproximateVec3().x;
    }

    /** Legacy approximate coordinate accessor. */
    public double getY() {
        return this.position.y();
    }

    /** Lossy compatibility mirror; exact consumers must use {@link #getExactPosition()}. */
    public double getZ() {
        return this.position.toApproximateVec3().z;
    }

    public float getPower() {
        return this.power;
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }
}
