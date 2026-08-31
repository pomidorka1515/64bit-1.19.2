package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.SectorVec3;

public class ServerboundMoveVehiclePacket implements Packet<ServerGamePacketListener> {
   private final double x;
   private final double y;
   private final double z;
   private final SectorVec3 exactPosition;
   private final float yRot;
   private final float xRot;

   public ServerboundMoveVehiclePacket(Entity p_134192_) {
      this.exactPosition = p_134192_.sectorPosition();
      net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.yRot = p_134192_.getYRot();
      this.xRot = p_134192_.getXRot();
   }

   public ServerboundMoveVehiclePacket(FriendlyByteBuf p_179700_) {
      this.exactPosition = SectorVec3.fromBlockAndFraction(p_179700_.readLong(), p_179700_.readDouble(),
            p_179700_.readDouble(), p_179700_.readLong(), p_179700_.readDouble());
      net.minecraft.world.phys.Vec3 approximate = this.exactPosition.toApproximateVec3();
      this.x = approximate.x;
      this.y = approximate.y;
      this.z = approximate.z;
      this.yRot = p_179700_.readFloat();
      this.xRot = p_179700_.readFloat();
   }

   public void write(FriendlyByteBuf p_134201_) {
      p_134201_.writeLong(this.exactPosition.blockX());
      p_134201_.writeDouble(this.exactPosition.subX());
      p_134201_.writeDouble(this.exactPosition.y());
      p_134201_.writeLong(this.exactPosition.blockZ());
      p_134201_.writeDouble(this.exactPosition.subZ());
      p_134201_.writeFloat(this.yRot);
      p_134201_.writeFloat(this.xRot);
   }

   public void handle(ServerGamePacketListener p_134198_) {
      p_134198_.handleMoveVehicle(this);
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

   public float getYRot() {
      return this.yRot;
   }

   public float getXRot() {
      return this.xRot;
   }
}