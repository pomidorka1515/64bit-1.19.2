package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

public class TickingTracker extends ChunkTracker {
   private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
   protected final Object2ByteMap<ChunkPos> chunks = new Object2ByteOpenHashMap<>();
   private final Object2ObjectOpenHashMap<ChunkPos, SortedArraySet<Ticket<?>>> tickets = new Object2ObjectOpenHashMap<>();

   public TickingTracker() {
      super(34, 16, 256);
      this.chunks.defaultReturnValue((byte)33);
   }

   private SortedArraySet<Ticket<?>> getTickets(ChunkPos p_184178_) {
      return this.tickets.computeIfAbsent(p_184178_, (p_184180_) -> {
         return SortedArraySet.create(4);
      });
   }

   private int getTicketLevelAt(SortedArraySet<Ticket<?>> p_184160_) {
      return p_184160_.isEmpty() ? 34 : p_184160_.first().getTicketLevel();
   }

   public void addTicket(ChunkPos p_184152_, Ticket<?> p_184153_) {
      SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(p_184152_);
      int i = this.getTicketLevelAt(sortedarrayset);
      sortedarrayset.add(p_184153_);
      if (p_184153_.getTicketLevel() < i) {
         this.update(p_184152_, p_184153_.getTicketLevel(), true);
      }

   }

   public void removeTicket(ChunkPos p_184166_, Ticket<?> p_184167_) {
      SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(p_184166_);
      sortedarrayset.remove(p_184167_);
      if (sortedarrayset.isEmpty()) {
         this.tickets.remove(p_184166_);
      }

      this.update(p_184166_, this.getTicketLevelAt(sortedarrayset), false);
   }

   public <T> void addTicket(TicketType<T> p_184155_, ChunkPos p_184156_, int p_184157_, T p_184158_) {
      this.addTicket(p_184156_, new Ticket<>(p_184155_, p_184157_, p_184158_));
   }

   public <T> void removeTicket(TicketType<T> p_184169_, ChunkPos p_184170_, int p_184171_, T p_184172_) {
      Ticket<T> ticket = new Ticket<>(p_184169_, p_184171_, p_184172_);
      this.removeTicket(p_184170_, ticket);
   }

   public void replacePlayerTicketsLevel(int p_184147_) {
      List<Pair<Ticket<ChunkPos>, ChunkPos>> list = new ArrayList<>();

      for(Entry<ChunkPos, SortedArraySet<Ticket<?>>> entry : this.tickets.entrySet()) {
         for(Ticket<?> ticket : entry.getValue()) {
            if (ticket.getType() == TicketType.PLAYER) {
               list.add(Pair.of((Ticket<ChunkPos>)ticket, entry.getKey()));
            }
         }
      }

      for(Pair<Ticket<ChunkPos>, ChunkPos> pair : list) {
         Ticket<ChunkPos> ticket1 = pair.getFirst();
         ChunkPos chunkpos =  pair.getSecond();
         this.removeTicket(chunkpos, ticket1);
         TicketType<ChunkPos> tickettype = ticket1.getType();
         this.addTicket(tickettype, chunkpos, p_184147_, chunkpos);
      }

   }

   protected int getLevelFromSource(ChunkPos p_184164_) {
      SortedArraySet<Ticket<?>> sortedarrayset = this.tickets.get(p_184164_);
      return sortedarrayset != null && !sortedarrayset.isEmpty() ? sortedarrayset.first().getTicketLevel() : Integer.MAX_VALUE;
   }

   protected int getLevel(ChunkPos p_184174_) {
      return this.chunks.getByte(p_184174_);
   }

   protected void setLevel(ChunkPos p_184149_, int p_184150_) {
      if (p_184150_ > 33) {
         this.chunks.removeByte(p_184149_);
      } else {
         this.chunks.put(p_184149_, (byte)p_184150_);
      }

   }

   public void runAllUpdates() {
      this.runUpdates(Integer.MAX_VALUE);
   }

   public String getTicketDebugString(ChunkPos p_184176_) {
      SortedArraySet<Ticket<?>> sortedarrayset = this.tickets.get(p_184176_);
      return sortedarrayset != null && !sortedarrayset.isEmpty() ? sortedarrayset.first().toString() : "no_ticket";
   }

}
