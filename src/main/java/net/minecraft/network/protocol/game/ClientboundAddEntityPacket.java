package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class ClientboundAddEntityPacket implements Packet<ClientGamePacketListener> {
    private static final double MAGICAL_QUANTIZATION = 8000.0D;
    private static final double LIMIT = 3.9D;
    private final int id;
    private final UUID uuid;
    private final EntityType<?> type;
    private final double y;
    private final SectorVec3 exactPosition;
    private final int xa;
    private final int ya;
    private final int za;
    private final byte xRot;
    private final byte yRot;
    private final byte yHeadRot;
    private final int data;

    public ClientboundAddEntityPacket(LivingEntity pLivingEntity) {
        this(pLivingEntity, 0);
    }

    public ClientboundAddEntityPacket(LivingEntity pLivingEntity, int pData) {
        this(pLivingEntity.getId(), pLivingEntity.getUUID(), pLivingEntity.exactPosition(), pLivingEntity.getXRot(), pLivingEntity.getYRot(),
                pLivingEntity.getType(), pData, pLivingEntity.getDeltaMovement(), (double) pLivingEntity.yHeadRot);
    }

    public ClientboundAddEntityPacket(Entity pEntity) {
        this(pEntity, 0);
    }

    public ClientboundAddEntityPacket(Entity pEntity, int pData) {
        this(pEntity.getId(), pEntity.getUUID(), pEntity.exactPosition(), pEntity.getXRot(), pEntity.getYRot(),
                pEntity.getType(), pData, pEntity.getDeltaMovement(), 0.0D);
    }

    public ClientboundAddEntityPacket(Entity pEntity, int pData, BlockPos pPos) {
        this(pEntity.getId(), pEntity.getUUID(), SectorVec3.fromBlockAndFraction(pPos.getX(), 0.0D, (double) pPos.getY(), pPos.getZ(), 0.0D), pEntity.getXRot(), pEntity.getYRot(),
                pEntity.getType(), pData, pEntity.getDeltaMovement(), 0.0D);
    }

    public ClientboundAddEntityPacket(int pId, UUID pUuid, double pX, double pY, double pZ, float pXRot, float pYRot, EntityType<?> pType, int pData, Vec3 pDeltaMovement, double pYHeadRot) {
        this(pId, pUuid, SectorVec3.fromApproximate(pX, pY, pZ), pXRot, pYRot,
                pType, pData, pDeltaMovement, pYHeadRot);
    }

    public ClientboundAddEntityPacket(int id, UUID uuid, SectorVec3 position, float xRot, float yRot, EntityType<?> type,
            int data, Vec3 velocity, double yHeadRot) {
        this.id = id;
        this.uuid = uuid;
        this.type = type;
        this.exactPosition = position;
        this.y = position.y();
        this.xRot = (byte) Mth.floor(xRot * 256.0F / 360.0F);
        this.yRot = (byte) Mth.floor(yRot * 256.0F / 360.0F);
        this.yHeadRot = (byte) Mth.floor(yHeadRot * 256.0D / 360.0D);
        this.data = data;
        this.xa = (int) (Mth.clamp(velocity.x, -3.9D, 3.9D) * 8000.0D);
        this.ya = (int) (Mth.clamp(velocity.y, -3.9D, 3.9D) * 8000.0D);
        this.za = (int) (Mth.clamp(velocity.z, -3.9D, 3.9D) * 8000.0D);
    }

    public ClientboundAddEntityPacket(FriendlyByteBuf pBuffer) {
        this.id = pBuffer.readVarInt();
        this.uuid = pBuffer.readUUID();
        this.type = pBuffer.readById(Registry.ENTITY_TYPE);
        Int128 blockX = pBuffer.readInt128();
        double subX = pBuffer.readDouble();
        this.y = pBuffer.readDouble();
        Int128 blockZ = pBuffer.readInt128();
        double subZ = pBuffer.readDouble();
        this.exactPosition = SectorVec3.fromBlockAndFraction(blockX, subX, this.y, blockZ, subZ);
        this.xRot = pBuffer.readByte();
        this.yRot = pBuffer.readByte();
        this.yHeadRot = pBuffer.readByte();
        this.data = pBuffer.readVarInt();
        this.xa = pBuffer.readShort();
        this.ya = pBuffer.readShort();
        this.za = pBuffer.readShort();
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeVarInt(this.id);
        pBuffer.writeUUID(this.uuid);
        pBuffer.writeId(Registry.ENTITY_TYPE, this.type);
        SectorVec3 position = this.exactPosition;
        pBuffer.writeInt128(position.blockX());
        pBuffer.writeDouble(position.subX());
        pBuffer.writeDouble(position.y());
        pBuffer.writeInt128(position.blockZ());
        pBuffer.writeDouble(position.subZ());
        pBuffer.writeByte(this.xRot);
        pBuffer.writeByte(this.yRot);
        pBuffer.writeByte(this.yHeadRot);
        pBuffer.writeVarInt(this.data);
        pBuffer.writeShort(this.xa);
        pBuffer.writeShort(this.ya);
        pBuffer.writeShort(this.za);
    }

    public void handle(ClientGamePacketListener pHandler) {
        pHandler.handleAddEntity(this);
    }

    public int getId() {
        return this.id;
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    public SectorVec3 getExactPosition() {
        return this.exactPosition;
    }

    public double getY() {
        return this.y;
    }

    public double getXa() {
        return (double) this.xa / 8000.0D;
    }

    public double getYa() {
        return (double) this.ya / 8000.0D;
    }

    public double getZa() {
        return (double) this.za / 8000.0D;
    }

    public float getXRot() {
        return (float) (this.xRot * 360) / 256.0F;
    }

    public float getYRot() {
        return (float) (this.yRot * 360) / 256.0F;
    }

    public float getYHeadRot() {
        return (float) (this.yHeadRot * 360) / 256.0F;
    }

    public int getData() {
        return this.data;
    }
}
