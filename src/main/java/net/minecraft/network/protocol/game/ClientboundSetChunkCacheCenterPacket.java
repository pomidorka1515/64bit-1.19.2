package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public class ClientboundSetChunkCacheCenterPacket implements Packet<ClientGamePacketListener> {
   private final long x;
   private final long z;

   public ClientboundSetChunkCacheCenterPacket(long p_133086_, long p_133087_) {
      this.x = p_133086_;
      this.z = p_133087_;
   }

   public ClientboundSetChunkCacheCenterPacket(FriendlyByteBuf p_179282_) {
      this.x = p_179282_.readVarLong();
      this.z = p_179282_.readVarLong();
   }

   public void write(FriendlyByteBuf p_133096_) {
      p_133096_.writeVarLong(this.x);
      p_133096_.writeVarLong(this.z);
   }

   public void handle(ClientGamePacketListener p_133093_) {
      p_133093_.handleSetChunkCacheCenter(this);
   }

   public long getX() {
      return this.x;
   }

   public long getZ() {
      return this.z;
   }
}