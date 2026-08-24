package net.minecraft.network.protocol.game;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;

public class ClientboundForgetLevelChunkPacket implements Packet<ClientGamePacketListener> {
   private final long x;
   private final long z;

   public ClientboundForgetLevelChunkPacket(long p_132141_, long p_132142_) {
      this.x = p_132141_;
      this.z = p_132142_;
   }

   public ClientboundForgetLevelChunkPacket(FriendlyByteBuf p_178858_) {
      this.x = p_178858_.readLong();
      this.z = p_178858_.readLong();
   }

   public void write(FriendlyByteBuf p_132151_) {
      p_132151_.writeLong(this.x);
      p_132151_.writeLong(this.z);
   }

   public void handle(ClientGamePacketListener p_132148_) {
      p_132148_.handleForgetLevelChunk(this);
   }

   public long getX() {
      return this.x;
   }

   public long getZ() {
      return this.z;
   }
}