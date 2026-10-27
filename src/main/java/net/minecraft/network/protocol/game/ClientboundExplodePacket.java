package net.minecraft.network.protocol.game;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
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
        this.position = buffer.readSectorVec3();
        this.power = buffer.readFloat();
        this.toBlow = buffer.readList(FriendlyByteBuf::readBlockPos);
        this.knockbackX = buffer.readFloat();
        this.knockbackY = buffer.readFloat();
        this.knockbackZ = buffer.readFloat();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeSectorVec3(this.position);
        buffer.writeFloat(this.power);
        buffer.writeCollection(this.toBlow, FriendlyByteBuf::writeBlockPos);
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

    /** Legacy approximate coordinate accessor. */
    public double getY() {
        return this.position.y();
    }

    /** Lossy compatibility mirror; exact consumers must use {@link #getExactPosition()}. */

    public float getPower() {
        return this.power;
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }
}
