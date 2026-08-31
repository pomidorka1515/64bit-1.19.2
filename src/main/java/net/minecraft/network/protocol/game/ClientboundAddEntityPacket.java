package net.minecraft.network.protocol.game;

import java.util.UUID;
import net.minecraft.core.BlockPos;
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
   private final double x;
   private final double y;
   private final double z;
   private final SectorVec3 exactPosition;
   private final int xa;
   private final int ya;
   private final int za;
   private final byte xRot;
   private final byte yRot;
   private final byte yHeadRot;
   private final int data;

   public ClientboundAddEntityPacket(LivingEntity p_237562_) {
      this(p_237562_, 0);
   }

   public ClientboundAddEntityPacket(LivingEntity p_237564_, int p_237565_) {
      this(p_237564_.getId(), p_237564_.getUUID(), p_237564_.exactPosition(), p_237564_.getXRot(), p_237564_.getYRot(),
            p_237564_.getType(), p_237565_, p_237564_.getDeltaMovement(), (double)p_237564_.yHeadRot);
   }

   public ClientboundAddEntityPacket(Entity p_131481_) {
      this(p_131481_, 0);
   }

   public ClientboundAddEntityPacket(Entity p_131483_, int p_131484_) {
      this(p_131483_.getId(), p_131483_.getUUID(), p_131483_.exactPosition(), p_131483_.getXRot(), p_131483_.getYRot(),
            p_131483_.getType(), p_131484_, p_131483_.getDeltaMovement(), 0.0D);
   }

   public ClientboundAddEntityPacket(Entity p_237558_, int p_237559_, BlockPos p_237560_) {
      this(p_237558_.getId(), p_237558_.getUUID(), SectorVec3.fromBlockAndFraction(p_237560_.getX(), 0.0D,
            (double)p_237560_.getY(), p_237560_.getZ(), 0.0D), p_237558_.getXRot(), p_237558_.getYRot(),
            p_237558_.getType(), p_237559_, p_237558_.getDeltaMovement(), 0.0D);
   }

   public ClientboundAddEntityPacket(int p_237546_, UUID p_237547_, double p_237548_, double p_237549_, double p_237550_, float p_237551_, float p_237552_, EntityType<?> p_237553_, int p_237554_, Vec3 p_237555_, double p_237556_) {
      this(p_237546_, p_237547_, SectorVec3.fromApproximate(p_237548_, p_237549_, p_237550_), p_237551_, p_237552_,
            p_237553_, p_237554_, p_237555_, p_237556_);
   }

   public ClientboundAddEntityPacket(int id, UUID uuid, SectorVec3 position, float xRot, float yRot, EntityType<?> type,
                                     int data, Vec3 velocity, double yHeadRot) {
      this.id = id;
      this.uuid = uuid;
      this.type = type;
      this.exactPosition = position;
      Vec3 approximate = position.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.xRot = (byte)Mth.floor(xRot * 256.0F / 360.0F);
      this.yRot = (byte)Mth.floor(yRot * 256.0F / 360.0F);
      this.yHeadRot = (byte)Mth.floor(yHeadRot * 256.0D / 360.0D);
      this.data = data;
      this.xa = (int)(Mth.clamp(velocity.x, -3.9D, 3.9D) * 8000.0D);
      this.ya = (int)(Mth.clamp(velocity.y, -3.9D, 3.9D) * 8000.0D);
      this.za = (int)(Mth.clamp(velocity.z, -3.9D, 3.9D) * 8000.0D);
   }

   public ClientboundAddEntityPacket(FriendlyByteBuf p_178562_) {
      this.id = p_178562_.readVarInt();
      this.uuid = p_178562_.readUUID();
      this.type = p_178562_.readById(Registry.ENTITY_TYPE);
      long blockX = p_178562_.readLong();
      double subX = p_178562_.readDouble();
      this.y = p_178562_.readDouble();
      long blockZ = p_178562_.readLong();
      double subZ = p_178562_.readDouble();
      this.exactPosition = SectorVec3.fromBlockAndFraction(blockX, subX, this.y, blockZ, subZ);
      Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.z = approximate.z;
      this.xRot = p_178562_.readByte();
      this.yRot = p_178562_.readByte();
      this.yHeadRot = p_178562_.readByte();
      this.data = p_178562_.readVarInt();
      this.xa = p_178562_.readShort();
      this.ya = p_178562_.readShort();
      this.za = p_178562_.readShort();
   }

   public void write(FriendlyByteBuf p_131498_) {
      p_131498_.writeVarInt(this.id);
      p_131498_.writeUUID(this.uuid);
      p_131498_.writeId(Registry.ENTITY_TYPE, this.type);
      SectorVec3 position = this.exactPosition;
      p_131498_.writeLong(position.blockX());
      p_131498_.writeDouble(position.subX());
      p_131498_.writeDouble(position.y());
      p_131498_.writeLong(position.blockZ());
      p_131498_.writeDouble(position.subZ());
      p_131498_.writeByte(this.xRot);
      p_131498_.writeByte(this.yRot);
      p_131498_.writeByte(this.yHeadRot);
      p_131498_.writeVarInt(this.data);
      p_131498_.writeShort(this.xa);
      p_131498_.writeShort(this.ya);
      p_131498_.writeShort(this.za);
   }

   public void handle(ClientGamePacketListener p_131495_) {
      p_131495_.handleAddEntity(this);
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

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public double getZ() {
      return this.z;
   }

   public double getXa() {
      return (double)this.xa / 8000.0D;
   }

   public double getYa() {
      return (double)this.ya / 8000.0D;
   }

   public double getZa() {
      return (double)this.za / 8000.0D;
   }

   public float getXRot() {
      return (float)(this.xRot * 360) / 256.0F;
   }

   public float getYRot() {
      return (float)(this.yRot * 360) / 256.0F;
   }

   public float getYHeadRot() {
      return (float)(this.yHeadRot * 360) / 256.0F;
   }

   public int getData() {
      return this.data;
   }
}