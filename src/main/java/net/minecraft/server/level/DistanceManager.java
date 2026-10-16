package net.minecraft.server.level;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.util.SortedArraySet;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

public abstract class DistanceManager {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final int ENTITY_TICKING_RANGE = 2;
    static final int PLAYER_TICKET_LEVEL = 33 + ChunkStatus.getDistance(ChunkStatus.FULL) - 2;
    private static final int INITIAL_TICKET_LIST_CAPACITY = 4;
    private static final int ENTITY_TICKING_LEVEL_THRESHOLD = 32;
    private static final int BLOCK_TICKING_LEVEL_THRESHOLD = 33;
    final Object2ObjectMap<ChunkPos, ObjectSet<ServerPlayer>> playersPerChunk = new Object2ObjectOpenHashMap<>();
    final Object2ObjectOpenHashMap<ChunkPos, SortedArraySet<Ticket<?>>> tickets = new Object2ObjectOpenHashMap<>();
    private final DistanceManager.ChunkTicketTracker ticketTracker = new DistanceManager.ChunkTicketTracker();
    private final DistanceManager.FixedPlayerDistanceChunkTracker naturalSpawnChunkCounter = new DistanceManager.FixedPlayerDistanceChunkTracker(8);
    private final TickingTracker tickingTicketsTracker = new TickingTracker();
    private final DistanceManager.PlayerTicketTracker playerTicketManager = new DistanceManager.PlayerTicketTracker(33);
    final Set<ChunkHolder> chunksToUpdateFutures = Sets.newHashSet();
    final ChunkTaskPriorityQueueSorter ticketThrottler;
    final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> ticketThrottlerInput;
    final ProcessorHandle<ChunkTaskPriorityQueueSorter.Release> ticketThrottlerReleaser;
    final ObjectSet<ChunkPos> ticketsToRelease = new ObjectOpenHashSet<>();
    final Executor mainThreadExecutor;
    private long ticketTickCounter;
    private int simulationDistance = 10;

    protected DistanceManager(Executor pDispatcher, Executor pMainThreadExecutor) {
        ProcessorHandle<Runnable> processorhandle = ProcessorHandle.of("player ticket throttler", pMainThreadExecutor::execute);
        ChunkTaskPriorityQueueSorter chunktaskpriorityqueuesorter = new ChunkTaskPriorityQueueSorter(ImmutableList.of(processorhandle), pDispatcher, 4);
        this.ticketThrottler = chunktaskpriorityqueuesorter;
        this.ticketThrottlerInput = chunktaskpriorityqueuesorter.getProcessor(processorhandle, true);
        this.ticketThrottlerReleaser = chunktaskpriorityqueuesorter.getReleaseProcessor(processorhandle);
        this.mainThreadExecutor = pMainThreadExecutor;
    }

    protected void purgeStaleTickets() {
        ++this.ticketTickCounter;

        // Movement updates the player-distance graph before this pass.  Reconcile
        // the actual ticket map as well: PLAYER tickets are intentionally
        // non-expiring, so a lost release message otherwise pins a ChunkHolder
        // forever.
        this.playerTicketManager.runAllUpdates();
        ObjectIterator<Entry<ChunkPos, SortedArraySet<Ticket<?>>>> objectiterator = this.tickets.entrySet().iterator();

        while (objectiterator.hasNext()) {
            Entry<ChunkPos, SortedArraySet<Ticket<?>>> entry = objectiterator.next();
            Iterator<Ticket<?>> iterator = entry.getValue().iterator();
            boolean flag = false;

            while (iterator.hasNext()) {
                Ticket<?> ticket = iterator.next();
                boolean stalePlayerTicket = ticket.getType() == TicketType.PLAYER
                        && !this.playerTicketManager.hasTicketForChunk(entry.getKey());
                if (stalePlayerTicket || ticket.timedOut(this.ticketTickCounter)) {
                    iterator.remove();
                    flag = true;
                    this.tickingTicketsTracker.removeTicket(entry.getKey(), ticket);
                    if (stalePlayerTicket) {
                        this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {}, entry.getKey(), true));
                    }
                }
            }

            if (flag) {
                this.ticketTracker.update(entry.getKey(), getTicketLevelAt(entry.getValue()), false);
            }

            if (entry.getValue().isEmpty()) {
                objectiterator.remove();
            }
        }
    }

    private static int getTicketLevelAt(SortedArraySet<Ticket<?>> p_140798_) {
        return !p_140798_.isEmpty() ? p_140798_.first().getTicketLevel() : ChunkMap.MAX_CHUNK_DISTANCE + 1;
    }

    protected abstract boolean isChunkToRemove(ChunkPos pChunkPos);

    @Nullable
    protected abstract ChunkHolder getChunk(ChunkPos pChunkPos);

    @Nullable
    protected abstract ChunkHolder updateChunkScheduling(ChunkPos pChunkPos, int pNewLevel, @Nullable ChunkHolder pHolder, int pOldLevel);

    public boolean runAllUpdates(ChunkMap pChunkManager) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        this.tickingTicketsTracker.runAllUpdates();
        this.playerTicketManager.runAllUpdates();
        int i = Integer.MAX_VALUE - this.ticketTracker.runDistanceUpdates(Integer.MAX_VALUE);
        boolean flag = i != 0;
        if (flag) {
        }

        if (!this.chunksToUpdateFutures.isEmpty()) {
            this.chunksToUpdateFutures.forEach((p_183908_) -> {
                p_183908_.updateFutures(pChunkManager, this.mainThreadExecutor);
            });
            this.chunksToUpdateFutures.clear();
            return true;
        } else {
            if (!this.ticketsToRelease.isEmpty()) {
                ObjectIterator<ChunkPos> longiterator = this.ticketsToRelease.iterator();

                while (longiterator.hasNext()) {
                    ChunkPos j = longiterator.next();
                    if (this.getTickets(j).stream().anyMatch((p_183910_) -> {
                            return p_183910_.getType() == TicketType.PLAYER;
                        })) {
                        ChunkHolder chunkholder = pChunkManager.getUpdatingChunkIfPresent(j);
                        if (chunkholder == null) {
                            throw new IllegalStateException();
                        }

                        CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> completablefuture = chunkholder.getEntityTickingChunkFuture();
                        completablefuture.thenAccept((p_183905_) -> {
                            this.mainThreadExecutor.execute(() -> {
                                this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {}, j, false));
                            });
                        });
                    }
                }

                this.ticketsToRelease.clear();
            }

            return flag;
        }
    }

    void addTicket(ChunkPos pChunkPos, Ticket<?> pTicket) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(pChunkPos);
        int i = getTicketLevelAt(sortedarrayset);
        Ticket<?> ticket = sortedarrayset.addOrGet(pTicket);
        ticket.setCreatedTick(this.ticketTickCounter);
        if (pTicket.getTicketLevel() < i) {
            this.ticketTracker.update(pChunkPos, pTicket.getTicketLevel(), true);
        }
    }

    void removeTicket(ChunkPos pChunkPos, Ticket<?> pTicket) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.getTickets(pChunkPos);
        if (sortedarrayset.remove(pTicket)) {
        }

        if (sortedarrayset.isEmpty()) {
            this.tickets.remove(pChunkPos);
        }

        this.ticketTracker.update(pChunkPos, getTicketLevelAt(sortedarrayset), false);
    }

    public <T> void addTicket(TicketType<T> pType, ChunkPos pPos, int pLevel, T pValue) {
        this.addTicket(pPos, new Ticket<>(pType, pLevel, pValue));
    }

    public <T> void removeTicket(TicketType<T> pType, ChunkPos pPos, int pLevel, T pValue) {
        Ticket<T> ticket = new Ticket<>(pType, pLevel, pValue);
        this.removeTicket(pPos, ticket);
    }

    public <T> void addRegionTicket(TicketType<T> pType, ChunkPos pPos, int pDistance, T pValue) {
        Ticket<T> ticket = new Ticket<>(pType, 33 - pDistance, pValue);
        this.addTicket(pPos, ticket);
        this.tickingTicketsTracker.addTicket(pPos, ticket);
    }

    public <T> void removeRegionTicket(TicketType<T> pType, ChunkPos pPos, int pDistance, T pValue) {
        Ticket<T> ticket = new Ticket<>(pType, 33 - pDistance, pValue);
        this.removeTicket(pPos, ticket);
        this.tickingTicketsTracker.removeTicket(pPos, ticket);
    }

    private SortedArraySet<Ticket<?>> getTickets(ChunkPos p_140858_) {
        return this.tickets.computeIfAbsent(p_140858_, (p_183923_) -> {
            return SortedArraySet.create(4);
        });
    }

    protected void updateChunkForced(ChunkPos pPos, boolean pAdd) {
        Ticket<ChunkPos> ticket = new Ticket<>(TicketType.FORCED, 31, pPos);
        if (pAdd) {
            this.addTicket(pPos, ticket);
            this.tickingTicketsTracker.addTicket(pPos, ticket);
        } else {
            this.removeTicket(pPos, ticket);
            this.tickingTicketsTracker.removeTicket(pPos, ticket);
        }
    }

    public void addPlayer(SectionPos pSectionPos, ServerPlayer pPlayer) {
        ChunkPos chunkpos = pSectionPos.chunk();
        this.playersPerChunk.computeIfAbsent(chunkpos, (p_183921_) -> {
                                return new ObjectOpenHashSet<>();
                            }).add(pPlayer);
        this.naturalSpawnChunkCounter.update(chunkpos, 0, true);
        this.playerTicketManager.update(chunkpos, 0, true);
        this.tickingTicketsTracker.addTicket(TicketType.PLAYER, chunkpos, this.getPlayerTicketLevel(), chunkpos);
    }

    public void removePlayer(SectionPos pSectionPos, ServerPlayer pPlayer) {
        ChunkPos chunkpos = pSectionPos.chunk();
        ObjectSet<ServerPlayer> objectset = this.playersPerChunk.get(chunkpos);
        objectset.remove(pPlayer);
        if (objectset.isEmpty()) {
            this.playersPerChunk.remove(chunkpos);
            this.naturalSpawnChunkCounter.update(chunkpos, Integer.MAX_VALUE, false);
            this.playerTicketManager.update(chunkpos, Integer.MAX_VALUE, false);
            this.tickingTicketsTracker.removeTicket(TicketType.PLAYER, chunkpos, this.getPlayerTicketLevel(), chunkpos);
        }
    }

    private int getPlayerTicketLevel() {
        return Math.max(0, 31 - this.simulationDistance);
    }

    public boolean inEntityTickingRange(ChunkPos p_183914_) {
        return this.tickingTicketsTracker.getLevel(p_183914_) < 32;
    }

    public boolean inBlockTickingRange(ChunkPos p_183917_) {
        return this.tickingTicketsTracker.getLevel(p_183917_) < 33;
    }

    protected String getTicketDebugString(ChunkPos p_140839_) {
        SortedArraySet<Ticket<?>> sortedarrayset = this.tickets.get(p_140839_);
        return sortedarrayset != null && !sortedarrayset.isEmpty() ? sortedarrayset.first().toString() : "no_ticket";
    }

    protected void updatePlayerTickets(int pViewDistance) {
        this.playerTicketManager.updateViewDistance(pViewDistance);
    }

    public void updateSimulationDistance(int pSimulationDistance) {
        if (pSimulationDistance != this.simulationDistance) {
            this.simulationDistance = pSimulationDistance;
            this.tickingTicketsTracker.replacePlayerTicketsLevel(this.getPlayerTicketLevel());
        }
    }

    public int getNaturalSpawnChunkCount() {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.size();
    }

    public boolean hasPlayersNearby(ChunkPos pChunkPos) {
        this.naturalSpawnChunkCounter.runAllUpdates();
        return this.naturalSpawnChunkCounter.chunks.containsKey(pChunkPos);
    }

    public String getDebugStatus() {
        return this.ticketThrottler.getDebugStatus();
    }

    private void dumpTickets(String pFilename) {
        try {
            FileOutputStream fileoutputstream = new FileOutputStream(new File(pFilename));

            try {
                for (Entry<ChunkPos, SortedArraySet<Ticket<?>>> entry : this.tickets.entrySet()) {
                    ChunkPos chunkpos = entry.getKey();

                    for (Ticket<?> ticket : entry.getValue()) {
                        fileoutputstream.write((chunkpos.x + "\t" + chunkpos.z + "\t" + ticket.getType() + "\t" + ticket.getTicketLevel() + "\t\n").getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Throwable throwable1) {
                try {
                    fileoutputstream.close();
                } catch (Throwable throwable) {
                    throwable1.addSuppressed(throwable);
                }

                throw throwable1;
            }

            fileoutputstream.close();
        } catch (IOException ioexception) {
            LOGGER.error("Failed to dump tickets to {}", pFilename, ioexception);
        }
    }

    @VisibleForTesting
    TickingTracker tickingTracker() {
        return this.tickingTicketsTracker;
    }

    public void removeTicketsOnClosing() {
        ImmutableSet<TicketType<?>> immutableset = ImmutableSet.of(TicketType.UNKNOWN, TicketType.POST_TELEPORT, TicketType.LIGHT);
        ObjectIterator<Entry<ChunkPos, SortedArraySet<Ticket<?>>>> objectiterator = this.tickets.entrySet().iterator();

        while (objectiterator.hasNext()) {
            Entry<ChunkPos, SortedArraySet<Ticket<?>>> entry = objectiterator.next();
            Iterator<Ticket<?>> iterator = entry.getValue().iterator();
            boolean flag = false;

            while (iterator.hasNext()) {
                Ticket<?> ticket = iterator.next();
                if (!immutableset.contains(ticket.getType())) {
                    iterator.remove();
                    flag = true;
                    this.tickingTicketsTracker.removeTicket(entry.getKey(), ticket);
                }
            }

            if (flag) {
                this.ticketTracker.update(entry.getKey(), getTicketLevelAt(entry.getValue()), false);
            }

            if (entry.getValue().isEmpty()) {
                objectiterator.remove();
            }
        }
    }

    public boolean hasTickets() {
        return !this.tickets.isEmpty();
    }

    class ChunkTicketTracker extends ChunkTracker {
        public ChunkTicketTracker() {
            super(ChunkMap.MAX_CHUNK_DISTANCE + 2, 16, 256);
        }

        protected int getLevelFromSource(ChunkPos pPos) {
            SortedArraySet<Ticket<?>> sortedarrayset = DistanceManager.this.tickets.get(pPos);
            if (sortedarrayset == null) {
                return Integer.MAX_VALUE;
            } else {
                return sortedarrayset.isEmpty() ? Integer.MAX_VALUE : sortedarrayset.first().getTicketLevel();
            }
        }

        protected int getLevel(ChunkPos pSectionPos) {
            if (!DistanceManager.this.isChunkToRemove(pSectionPos)) {
                ChunkHolder chunkholder = DistanceManager.this.getChunk(pSectionPos);
                if (chunkholder != null) {
                    return chunkholder.getTicketLevel();
                }
            }

            return ChunkMap.MAX_CHUNK_DISTANCE + 1;
        }

        protected void setLevel(ChunkPos pSectionPos, int pLevel) {
            ChunkHolder chunkholder = DistanceManager.this.getChunk(pSectionPos);
            int i = chunkholder == null ? ChunkMap.MAX_CHUNK_DISTANCE + 1 : chunkholder.getTicketLevel();
            if (i != pLevel) {
                chunkholder = DistanceManager.this.updateChunkScheduling(pSectionPos, pLevel, chunkholder, i);
                if (chunkholder != null) {
                    DistanceManager.this.chunksToUpdateFutures.add(chunkholder);
                }
            }
        }

        public int runDistanceUpdates(int pToUpdateCount) {
            return this.runUpdates(pToUpdateCount);
        }
    }

    class FixedPlayerDistanceChunkTracker extends ChunkTracker {
        protected final Object2ByteMap<ChunkPos> chunks = new Object2ByteOpenHashMap<>();
        protected final int maxDistance;

        protected FixedPlayerDistanceChunkTracker(int p_140891_) {
            super(p_140891_ + 2, 16, 256);
            this.maxDistance = p_140891_;
            this.chunks.defaultReturnValue((byte) (p_140891_ + 2));
        }

        protected int getLevel(ChunkPos pSectionPos) {
            return this.chunks.getByte(pSectionPos);
        }

        protected void setLevel(ChunkPos pSectionPos, int pLevel) {
            byte b0;
            if (pLevel > this.maxDistance) {
                b0 = this.chunks.removeByte(pSectionPos);
            } else {
                b0 = this.chunks.put(pSectionPos, (byte) pLevel);
            }

            this.onLevelChange(pSectionPos, b0, pLevel);
        }

        protected void onLevelChange(ChunkPos pChunkPos, int pOldLevel, int pNewLevel) {
        }

        protected int getLevelFromSource(ChunkPos pPos) {
            return this.havePlayer(pPos) ? 0 : Integer.MAX_VALUE;
        }

        private boolean havePlayer(ChunkPos pChunkPos) {
            ObjectSet<ServerPlayer> objectset = DistanceManager.this.playersPerChunk.get(pChunkPos);
            return objectset != null && !objectset.isEmpty();
        }

        public void runAllUpdates() {
            this.runUpdates(Integer.MAX_VALUE);
        }

        private void dumpChunks(String pFilename) {
            try {
                FileOutputStream fileoutputstream = new FileOutputStream(new File(pFilename));

                try {
                    for (it.unimi.dsi.fastutil.objects.Object2ByteMap.Entry<ChunkPos> entry : this.chunks.object2ByteEntrySet()) {
                        ChunkPos chunkpos = entry.getKey();
                        String s = Byte.toString(entry.getByteValue());
                        fileoutputstream.write((chunkpos.x + "\t" + chunkpos.z + "\t" + s + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Throwable throwable1) {
                    try {
                        fileoutputstream.close();
                    } catch (Throwable throwable) {
                        throwable1.addSuppressed(throwable);
                    }

                    throw throwable1;
                }

                fileoutputstream.close();
            } catch (IOException ioexception) {
                DistanceManager.LOGGER.error("Failed to dump chunks to {}", pFilename, ioexception);
            }
        }
    }

    class PlayerTicketTracker extends DistanceManager.FixedPlayerDistanceChunkTracker {
        private int viewDistance;
        private final Object2IntMap<ChunkPos> queueLevels = Object2IntMaps.synchronize(new Object2IntOpenHashMap<>());
        private final ObjectSet<ChunkPos> toUpdate = new ObjectOpenHashSet<>();

        protected PlayerTicketTracker(int p_140910_) {
            super(p_140910_);
            this.viewDistance = 0;
            this.queueLevels.defaultReturnValue(p_140910_ + 2);
        }

        protected void onLevelChange(ChunkPos pChunkPos, int pOldLevel, int pNewLevel) {
            this.toUpdate.add(pChunkPos);
        }

        public void updateViewDistance(int pViewDistance) {
            for (it.unimi.dsi.fastutil.objects.Object2ByteMap.Entry<ChunkPos> entry : this.chunks.object2ByteEntrySet()) {
                byte b0 = entry.getByteValue();
                ChunkPos i = entry.getKey();
                this.onLevelChange(i, b0, this.haveTicketFor(b0), b0 <= pViewDistance - 2);
            }

            this.viewDistance = pViewDistance;
        }

        private void onLevelChange(ChunkPos p_140919_, int p_140920_, boolean p_140921_, boolean p_140922_) {
            if (p_140921_ != p_140922_) {
                Ticket<?> ticket = new Ticket<>(TicketType.PLAYER, DistanceManager.PLAYER_TICKET_LEVEL, p_140919_);
                if (p_140922_) {
                    DistanceManager.this.ticketThrottlerInput.tell(ChunkTaskPriorityQueueSorter.message(() -> { DistanceManager.this.mainThreadExecutor.execute(() -> {
                                                                                                                    if (this.haveTicketFor(this.getLevel(p_140919_))) {
                                                                                                                        DistanceManager.this.addTicket(p_140919_, ticket);
                                                                                                                        DistanceManager.this.ticketsToRelease.add(p_140919_);
                                                                                                                    } else {
                                                                                                                        DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {}, p_140919_, false));
                                                                                                                    }
                                                                                                                }); }, p_140919_, () -> { return p_140920_; }));
                } else {
                    DistanceManager.this.ticketThrottlerReleaser.tell(ChunkTaskPriorityQueueSorter.release(() -> {
                        DistanceManager.this.mainThreadExecutor.execute(() -> {
                            DistanceManager.this.removeTicket(p_140919_, ticket);
                        });
                    }, p_140919_, true));
                }
            }
        }

        public void runAllUpdates() {
            super.runAllUpdates();
            if (!this.toUpdate.isEmpty()) {
                ObjectIterator<ChunkPos> longiterator = this.toUpdate.iterator();

                while (longiterator.hasNext()) {
                    ChunkPos i = longiterator.next();
                    int j = this.queueLevels.getInt(i);
                    int k = this.getLevel(i);
                    if (j != k) {
                        DistanceManager.this.ticketThrottler.onLevelChange(i, () -> { return this.queueLevels.getInt(i); }, k, (p_140928_) -> {
                            if (p_140928_ >= this.queueLevels.defaultReturnValue()) {
                                this.queueLevels.removeInt(i);
                            } else {
                                this.queueLevels.put(i, p_140928_);
                            } });
                        this.onLevelChange(i, k, this.haveTicketFor(j), this.haveTicketFor(k));
                    }
                }

                this.toUpdate.clear();
            }
        }

        private boolean haveTicketFor(int p_140933_) {
            return p_140933_ <= this.viewDistance - 2;
        }

        boolean hasTicketForChunk(ChunkPos p_183931_) {
            return this.haveTicketFor(this.getLevel(p_183931_));
        }
    }
}
