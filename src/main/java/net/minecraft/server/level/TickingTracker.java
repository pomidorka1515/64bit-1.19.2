package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkPosMap;

public class TickingTracker extends ChunkTracker {
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    protected final Object2ByteMap<ChunkPos> chunks = new Object2ByteOpenHashMap<>();
    private final ChunkPosMap<SortedArraySet<Ticket<?>>> tickets = new ChunkPosMap<>();

    public TickingTracker() {
        super(34, 16, 256);
        this.chunks.defaultReturnValue((byte) 33);
    }

    private SortedArraySet<Ticket<?>> getTickets(ChunkPos pChunkPos) {
        SortedArraySet<Ticket<?>> cached = this.tickets.get(pChunkPos.x(), pChunkPos.z());
        if (cached != null) {
            return cached;
        }
        return this.tickets.computeIfAbsent(pChunkPos, (p_184180_) -> {
            return SortedArraySet.create(4);
        });
    }

    private int getTicketLevelAt(SortedArraySet<Ticket<?>> p_184160_) {
        return p_184160_.isEmpty() ? 34 : p_184160_.first().getTicketLevel();
    }

    public void addTicket(ChunkPos pChunkPos, Ticket<?> pTicket) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(pChunkPos);
        int i = this.getTicketLevelAt(sortedarrayset);
        sortedarrayset.add(pTicket);
        if (pTicket.getTicketLevel() < i) {
            this.update(pChunkPos, pTicket.getTicketLevel(), true);
        }
    }

    public void removeTicket(ChunkPos pChunkPos, Ticket<?> pTicket) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(pChunkPos);
        sortedarrayset.remove(pTicket);
        if (sortedarrayset.isEmpty()) {
            this.tickets.remove(pChunkPos);
        }

        this.update(pChunkPos, this.getTicketLevelAt(sortedarrayset), false);
    }

    public <T> void addTicket(TicketType<T> pType, ChunkPos pChunkPos, int pTicketLevel, T pKey) {
        this.addTicket(pChunkPos, new Ticket<>(pType, pTicketLevel, pKey));
    }

    public <T> void removeTicket(TicketType<T> pType, ChunkPos pChunkPos, int pTicketLevel, T pKey) {
        Ticket<T> ticket = new Ticket<>(pType, pTicketLevel, pKey);
        this.removeTicket(pChunkPos, ticket);
    }

    public void replacePlayerTicketsLevel(int p_184147_) {
        List<Pair<Ticket<ChunkPos>, ChunkPos>> list = new ArrayList<>();

        for (Entry<ChunkPos, SortedArraySet<Ticket<?>>> entry : this.tickets.entrySet()) {
            for (Ticket<?> ticket : entry.getValue()) {
                if (ticket.getType() == TicketType.PLAYER) {
                    list.add(Pair.of((Ticket<ChunkPos>) ticket, entry.getKey()));
                }
            }
        }

        for (Pair<Ticket<ChunkPos>, ChunkPos> pair : list) {
            Ticket<ChunkPos> ticket1 = pair.getFirst();
            ChunkPos chunkpos = pair.getSecond();
            this.removeTicket(chunkpos, ticket1);
            TicketType<ChunkPos> tickettype = ticket1.getType();
            this.addTicket(tickettype, chunkpos, p_184147_, chunkpos);
        }
    }

    protected int getLevelFromSource(ChunkPos pPos) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.tickets.get(pPos.x(), pPos.z());
        return sortedarrayset != null && !sortedarrayset.isEmpty() ? sortedarrayset.first().getTicketLevel() : Integer.MAX_VALUE;
    }

    protected int getLevel(ChunkPos pChunkPos) {
        return this.chunks.getByte(pChunkPos);
    }

    protected void setLevel(ChunkPos pChunkPos, int pLevel) {
        if (pLevel > 33) {
            this.chunks.removeByte(pChunkPos);
        } else {
            this.chunks.put(pChunkPos, (byte) pLevel);
        }
    }

    public void runAllUpdates() {
        this.runUpdates(Integer.MAX_VALUE);
    }

    public String getTicketDebugString(ChunkPos p_184176_) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.tickets.get(p_184176_.x(), p_184176_.z());
        return sortedarrayset != null && !sortedarrayset.isEmpty() ? sortedarrayset.first().toString() : "no_ticket";
    }
}
