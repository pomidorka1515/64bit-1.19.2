package net.minecraft.server.level;

import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.world.level.ChunkPos;

import java.util.Set;

public final class PlayerMap {
   private final Object2BooleanMap<ServerPlayer> players = new Object2BooleanOpenHashMap<>();

   public Set<ServerPlayer> getPlayers(ChunkPos p_183880_) {
      return this.players.keySet();
   }

   public void addPlayer(ChunkPos p_8253_, ServerPlayer p_8254_, boolean p_8255_) {
      this.players.put(p_8254_, p_8255_);
   }

   public void removePlayer(ChunkPos p_8250_, ServerPlayer p_8251_) {
      this.players.removeBoolean(p_8251_);
   }

   public void ignorePlayer(ServerPlayer p_8257_) {
      this.players.replace(p_8257_, true);
   }

   public void unIgnorePlayer(ServerPlayer p_8259_) {
      this.players.replace(p_8259_, false);
   }

   public boolean ignoredOrUnknown(ServerPlayer p_8261_) {
      return this.players.getOrDefault(p_8261_, true);
   }

   public boolean ignored(ServerPlayer p_8263_) {
      return this.players.getBoolean(p_8263_);
   }

   public void updatePlayer(ChunkPos p_8246_, ChunkPos p_8247_, ServerPlayer p_8248_) {
   }
}