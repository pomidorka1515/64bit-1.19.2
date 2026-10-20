package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.Int128;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.SectorVec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

public class ChunkMap extends ChunkStorage implements ChunkHolder.PlayerProvider {
    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    private static final int MIN_VIEW_DISTANCE = 3;
    public static final int MAX_VIEW_DISTANCE = 33;
    public static final int MAX_CHUNK_DISTANCE = 33 + ChunkStatus.maxDistance();
    public static final int FORCED_TICKET_LEVEL = 31;
    private final Object2ObjectLinkedOpenHashMap<ChunkPos, ChunkHolder> updatingChunkMap = new Object2ObjectLinkedOpenHashMap<>();
    private volatile Object2ObjectLinkedOpenHashMap<ChunkPos, ChunkHolder> visibleChunkMap = this.updatingChunkMap.clone();
    private final Object2ObjectLinkedOpenHashMap<ChunkPos, ChunkHolder> pendingUnloads = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectSet<ChunkPos> entitiesInLevel = new ObjectOpenHashSet<>();
    final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    private final BlockableEventLoop<Runnable> mainThreadExecutor;
    private ChunkGenerator generator;
    private RandomState randomState;
    private final Supplier<DimensionDataStorage> overworldDataStorage;
    private final PoiManager poiManager;
    final ObjectSet<ChunkPos> toDrop = new ObjectOpenHashSet<>();
    private boolean modified;
    private final ChunkTaskPriorityQueueSorter queueSorter;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> worldgenMailbox;
    private final ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> mainThreadMailbox;
    private final ChunkProgressListener progressListener;
    private final ChunkStatusUpdateListener chunkStatusListener;
    private final ChunkMap.DistanceManager distanceManager;
    private final AtomicInteger tickingGenerated = new AtomicInteger();
    private final StructureTemplateManager structureTemplateManager;
    private final String storageName;
    private final PlayerMap playerMap = new PlayerMap();
    private final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = new Int2ObjectOpenHashMap<>();
    private final Object2ByteMap<ChunkPos> chunkTypeCache = new Object2ByteOpenHashMap<>();
    private final Object2LongMap<ChunkPos> chunkSaveCooldowns = new Object2LongOpenHashMap<>();
    private final Queue<Runnable> unloadQueue = Queues.newConcurrentLinkedQueue();
    int viewDistance;

    public ChunkMap(ServerLevel pLevel, LevelStorageSource.LevelStorageAccess pLevelStorageAccess, DataFixer pFixerUpper, StructureTemplateManager pStructureManager, Executor pDispatcher, BlockableEventLoop<Runnable> pMainThreadExecutor, LightChunkGetter pLightChunk, ChunkGenerator pGenerator, ChunkProgressListener pProgressListener, ChunkStatusUpdateListener pChunkStatusListener, Supplier<DimensionDataStorage> pOverworldDataStorage, int pViewDistance, boolean pSync) {
        super(pLevelStorageAccess.getDimensionPath(pLevel.dimension()).resolve("region"), pFixerUpper, pSync);
        this.structureTemplateManager = pStructureManager;
        Path path = pLevelStorageAccess.getDimensionPath(pLevel.dimension());
        this.storageName = path.getFileName().toString();
        this.level = pLevel;
        this.generator = pGenerator;
        if (pGenerator instanceof NoiseBasedChunkGenerator noisebasedchunkgenerator) {
            this.randomState = RandomState.create(noisebasedchunkgenerator.generatorSettings().value(), pLevel.registryAccess().registryOrThrow(Registry.NOISE_REGISTRY), pLevel.getSeed());
        } else {
            this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), pLevel.registryAccess().registryOrThrow(Registry.NOISE_REGISTRY), pLevel.getSeed());
        }

        this.mainThreadExecutor = pMainThreadExecutor;
        ProcessorMailbox<Runnable> processormailbox1 = ProcessorMailbox.create(pDispatcher, "worldgen");
        ProcessorHandle<Runnable> processorhandle = ProcessorHandle.of("main", pMainThreadExecutor::tell);
        this.progressListener = pProgressListener;
        this.chunkStatusListener = pChunkStatusListener;
        ProcessorMailbox<Runnable> processormailbox = ProcessorMailbox.create(pDispatcher, "light");
        this.queueSorter = new ChunkTaskPriorityQueueSorter(ImmutableList.of(processormailbox1, processorhandle, processormailbox), pDispatcher, Integer.MAX_VALUE);
        this.worldgenMailbox = this.queueSorter.getProcessor(processormailbox1, false);
        this.mainThreadMailbox = this.queueSorter.getProcessor(processorhandle, false);
        this.lightEngine = new ThreadedLevelLightEngine(pLightChunk, this, this.level.dimensionType().hasSkyLight(), processormailbox, this.queueSorter.getProcessor(processormailbox, false));
        this.distanceManager = new ChunkMap.DistanceManager(pDispatcher, pMainThreadExecutor);
        this.overworldDataStorage = pOverworldDataStorage;
        this.poiManager = new PoiManager(path.resolve("poi"), pFixerUpper, pSync, pLevel.registryAccess(), pLevel);
        this.setViewDistance(pViewDistance);
    }

    protected ChunkGenerator generator() {
        return this.generator;
    }

    protected RandomState randomState() {
        return this.randomState;
    }

    public void debugReloadGenerator() {
        DataResult<JsonElement> dataresult = ChunkGenerator.CODEC.encodeStart(JsonOps.INSTANCE, this.generator);
        DataResult<ChunkGenerator> dataresult1 = dataresult.flatMap((p_183804_) -> {
            return ChunkGenerator.CODEC.parse(JsonOps.INSTANCE, p_183804_);
        });
        dataresult1.result().ifPresent((p_183808_) -> {
            this.generator = p_183808_;
        });
    }

    private static double euclideanDistanceSquared(ChunkPos pChunkPos, Entity pEntity) {
        SectorVec3 position = pEntity.sectorPosition();
        double d0 = WorldBounds.signedDifference(SectionPos.sectionToBlockCoord(pChunkPos.x(), 8), position.blockX()) - position.subX();
        double d1 = WorldBounds.signedDifference(SectionPos.sectionToBlockCoord(pChunkPos.z(), 8), position.blockZ()) - position.subZ();
        return d0 * d0 + d1 * d1;
    }

    public static boolean isChunkInRange(long p_200879_, long p_200880_, long p_200881_, long p_200882_, long p_200883_) {
        return isChunkInRange(Int128.fromLong(p_200879_), Int128.fromLong(p_200880_),
                Int128.fromLong(p_200881_), Int128.fromLong(p_200882_), p_200883_);
    }

    public static boolean isChunkInRange(Int128 p_200879_, Int128 p_200880_, Int128 p_200881_, Int128 p_200882_, long p_200883_) {
        if (p_200883_ < 1L)
            return false;
        double dx = Math.max(0.0D, WorldBounds.distance(p_200879_, p_200881_) - 1.0D);
        double dz = Math.max(0.0D, WorldBounds.distance(p_200880_, p_200882_) - 1.0D);
        double far = Math.max(0.0D, Math.max(dx, dz) - 1.0D);
        double near = Math.min(dx, dz);
        double radius = (double) p_200883_ - 1.0D;
        return near * near + far * far <= radius * radius;
    }

    private static boolean isChunkOnRangeBorder(Int128 p_183829_, Int128 p_183830_, Int128 p_183831_, Int128 p_183832_, int p_183833_) {
        if (!isChunkInRange(p_183829_, p_183830_, p_183831_, p_183832_, p_183833_)) {
            return false;
        }
        Int128 plusX = WorldBounds.tryAddChunkOffset(p_183829_, 1L);
        Int128 plusZ = WorldBounds.tryAddChunkOffset(p_183830_, 1L);
        Int128 minusX = WorldBounds.tryAddChunkOffset(p_183829_, -1L);
        Int128 minusZ = WorldBounds.tryAddChunkOffset(p_183830_, -1L);
        return plusX == null || !isChunkInRange(plusX, p_183830_, p_183831_, p_183832_, p_183833_)
                || plusZ == null || !isChunkInRange(p_183829_, plusZ, p_183831_, p_183832_, p_183833_)
                || minusX == null || !isChunkInRange(minusX, p_183830_, p_183831_, p_183832_, p_183833_)
                || minusZ == null || !isChunkInRange(p_183829_, minusZ, p_183831_, p_183832_, p_183833_);
    }

    /** Visits a small, edge-clipped rectangular chunk range without overflowing. */
    private static void forEachChunkBox(Int128 centerX1, Int128 centerZ1, Int128 centerX2, Int128 centerZ2,
            long edgeRadius, Consumer<ChunkPos> action) {
        if (edgeRadius < 0L) {
            return;
        }
        Int128 minX = WorldBounds.addChunkOffset(centerX1.compareTo(centerX2) <= 0 ? centerX1 : centerX2, -edgeRadius);
        Int128 minZ = WorldBounds.addChunkOffset(centerZ1.compareTo(centerZ2) <= 0 ? centerZ1 : centerZ2, -edgeRadius);
        Int128 maxX = WorldBounds.addChunkOffset(centerX1.compareTo(centerX2) >= 0 ? centerX1 : centerX2, edgeRadius);
        Int128 maxZ = WorldBounds.addChunkOffset(centerZ1.compareTo(centerZ2) >= 0 ? centerZ1 : centerZ2, edgeRadius);
        for (Int128 x = minX;; x = WorldBounds.addChunkOffset(x, 1L)) {
            for (Int128 z = minZ;; z = WorldBounds.addChunkOffset(z, 1L)) {
                action.accept(new ChunkPos(x, z));
                if (z.equals(maxZ))
                    break;
            }
            if (x.equals(maxX))
                break;
        }
    }

    protected ThreadedLevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Nullable
    protected ChunkHolder getUpdatingChunkIfPresent(ChunkPos p_140175_) {
        return this.updatingChunkMap.get(p_140175_);
    }

    @Nullable
    protected ChunkHolder getVisibleChunkIfPresent(ChunkPos p_140328_) {
        return this.visibleChunkMap.get(p_140328_);
    }

    protected IntSupplier getChunkQueueLevel(ChunkPos p_140372_) {
        return () -> {
            ChunkHolder chunkholder = this.getVisibleChunkIfPresent(p_140372_);
            return chunkholder == null ? ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1 : Math.min(chunkholder.getQueueLevel(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1);
        };
    }

    public String getChunkDebugData(ChunkPos pPos) {
        ChunkHolder chunkholder = this.getVisibleChunkIfPresent(pPos);
        if (chunkholder == null) {
            return "null";
        } else {
            String s = chunkholder.getTicketLevel() + "\n";
            ChunkStatus chunkstatus = chunkholder.getLastAvailableStatus();
            ChunkAccess chunkaccess = chunkholder.getLastAvailable();
            if (chunkstatus != null) {
                s = s + "St: \u00a7" + chunkstatus.getIndex() + chunkstatus + "\u00a7r\n";
            }

            if (chunkaccess != null) {
                s = s + "Ch: \u00a7" + chunkaccess.getStatus().getIndex() + chunkaccess.getStatus() + "\u00a7r\n";
            }

            ChunkHolder.FullChunkStatus chunkholder$fullchunkstatus = chunkholder.getFullStatus();
            s = s + "\u00a7" + chunkholder$fullchunkstatus.ordinal() + chunkholder$fullchunkstatus;
            return s + "\u00a7r";
        }
    }

    private CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.ChunkLoadingFailure>> getChunkRangeFuture(ChunkPos p_140211_, int p_140212_, IntFunction<ChunkStatus> p_140213_) {
        List<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> list = new ArrayList<>();
        List<ChunkHolder> list1 = new ArrayList<>();
        Int128 i = p_140211_.x();
        Int128 j = p_140211_.z();

        for (int k = -p_140212_; k <= p_140212_; ++k) {
            for (int l = -p_140212_; l <= p_140212_; ++l) {
                int i1 = Math.max(Math.abs(l), Math.abs(k));
                final ChunkPos chunkpos = new ChunkPos(WorldBounds.addChunkOffset(i, l), WorldBounds.addChunkOffset(j, k));
                ChunkHolder chunkholder = this.getUpdatingChunkIfPresent(chunkpos);
                if (chunkholder == null) {
                    return CompletableFuture.completedFuture(Either.right(new ChunkHolder.ChunkLoadingFailure() {
                        public String toString() {
                            return "Unloaded " + chunkpos;
                        }
                    }));
                }

                ChunkStatus chunkstatus = p_140213_.apply(i1);
                CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completablefuture = chunkholder.getOrScheduleFuture(chunkstatus, this);
                list1.add(chunkholder);
                list.add(completablefuture);
            }
        }

        CompletableFuture<List<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> completablefuture1 = Util.sequence(list);
        CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.ChunkLoadingFailure>> completablefuture2 = completablefuture1.thenApply((p_183730_) -> {
            List<ChunkAccess> list2 = Lists.newArrayList();
            int k1 = 0;

            for (final Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> either : p_183730_) {
                if (either == null) {
                    throw this.debugFuturesAndCreateReportedException(new IllegalStateException("At least one of the chunk futures were null"), "n/a");
                }

                Optional<ChunkAccess> optional = either.left();
                if (!optional.isPresent()) {
                    final int l1 = k1;
                    return Either.right(new ChunkHolder.ChunkLoadingFailure() {
                        public String toString() {
                            long diameter = (long) p_140212_ * 2L + 1L;
                            return "Unloaded " + new ChunkPos(WorldBounds.addChunkOffset(i, l1 % diameter - p_140212_), WorldBounds.addChunkOffset(j, l1 / diameter - p_140212_)) + " " + either.right().get();
                        }
                    });
                }

                list2.add(optional.get());
                ++k1;
            }

            return Either.left(list2);
        });

        for (ChunkHolder chunkholder1 : list1) {
            chunkholder1.addSaveDependency("getChunkRangeFuture " + p_140211_ + " " + p_140212_, completablefuture2);
        }

        return completablefuture2;
    }

    public ReportedException debugFuturesAndCreateReportedException(IllegalStateException p_203752_, String p_203753_) {
        StringBuilder stringbuilder = new StringBuilder();
        Consumer<ChunkHolder> consumer = (p_203756_) -> {
            p_203756_.getAllFutures().forEach((p_203760_) -> {
                ChunkStatus chunkstatus = p_203760_.getFirst();
                CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completablefuture = p_203760_.getSecond();
                if (completablefuture != null && completablefuture.isDone() && completablefuture.join() == null) {
                    stringbuilder.append((Object) p_203756_.getPos()).append(" - status: ").append((Object) chunkstatus).append(" future: ").append((Object) completablefuture).append(System.lineSeparator());
                }
            });
        };
        stringbuilder.append("Updating:").append(System.lineSeparator());
        this.updatingChunkMap.values().forEach(consumer);
        stringbuilder.append("Visible:").append(System.lineSeparator());
        this.visibleChunkMap.values().forEach(consumer);
        CrashReport crashreport = CrashReport.forThrowable(p_203752_, "Chunk loading");
        CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk loading");
        crashreportcategory.setDetail("Details", p_203753_);
        crashreportcategory.setDetail("Futures", stringbuilder);
        return new ReportedException(crashreport);
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> prepareEntityTickingChunk(ChunkPos p_143118_) {
        return this.getChunkRangeFuture(p_143118_, 2, (p_203078_) -> {
                       return ChunkStatus.FULL;
                   }).thenApplyAsync((p_203086_) -> {
            return p_203086_.mapLeft((p_203092_) -> {
                return (LevelChunk) p_203092_.get(p_203092_.size() / 2);
            });
        }, this.mainThreadExecutor);
    }

    @Nullable
    ChunkHolder updateChunkScheduling(ChunkPos pChunkPos, int pNewLevel, @Nullable ChunkHolder pHolder, int pOldLevel) {
        if (pOldLevel > MAX_CHUNK_DISTANCE && pNewLevel > MAX_CHUNK_DISTANCE) {
            return pHolder;
        } else {
            if (pHolder != null) {
                pHolder.setTicketLevel(pNewLevel);
            }

            if (pHolder != null) {
                if (pNewLevel > MAX_CHUNK_DISTANCE) {
                    this.toDrop.add(pChunkPos);
                } else {
                    this.toDrop.remove(pChunkPos);
                }
            }

            if (pNewLevel <= MAX_CHUNK_DISTANCE && pHolder == null) {
                pHolder = this.pendingUnloads.remove(pChunkPos);
                if (pHolder != null) {
                    pHolder.setTicketLevel(pNewLevel);
                } else {
                    pHolder = new ChunkHolder(pChunkPos, pNewLevel, this.level, this.lightEngine, this.queueSorter, this);
                }

                this.updatingChunkMap.put(pChunkPos, pHolder);
                this.modified = true;
            }

            return pHolder;
        }
    }

    public void close() throws IOException {
        try {
            this.queueSorter.close();
            this.poiManager.close();
        } finally {
            super.close();
        }
    }

    protected void saveAllChunks(boolean pFlush) {
        if (pFlush) {
            List<ChunkHolder> list = this.visibleChunkMap.values().stream().filter(ChunkHolder::wasAccessibleSinceLastSave).peek(ChunkHolder::refreshAccessibility).collect(Collectors.toList());
            MutableBoolean mutableboolean = new MutableBoolean();

            do {
                mutableboolean.setFalse();
                list.stream().map((p_203102_) -> {
                                 CompletableFuture<ChunkAccess> completablefuture;
                                 do {
                                     completablefuture = p_203102_.getChunkToSave();
                                     this.mainThreadExecutor.managedBlock(completablefuture::isDone);
                                 } while (completablefuture != p_203102_.getChunkToSave());

                                 return completablefuture.join();
                             })
                        .filter((p_203088_) -> {
                            return p_203088_ instanceof ImposterProtoChunk || p_203088_ instanceof LevelChunk;
                        })
                        .filter(this::save)
                        .forEach((p_203051_) -> {
                            mutableboolean.setTrue();
                        });
            } while (mutableboolean.isTrue());

            this.processUnloads(() -> {
                return true;
            });
            this.flushWorker();
        } else {
            this.visibleChunkMap.values().forEach(this::saveChunkIfNeeded);
        }
    }

    protected void tick(BooleanSupplier pHasMoreTime) {
        ProfilerFiller profilerfiller = this.level.getProfiler();
        profilerfiller.push("poi");
        this.poiManager.tick(pHasMoreTime);
        profilerfiller.popPush("chunk_unload");
        if (!this.level.noSave()) {
            this.processUnloads(pHasMoreTime);
        }

        profilerfiller.pop();
    }

    public boolean hasWork() {
        return this.lightEngine.hasLightWork() || !this.pendingUnloads.isEmpty() /* || !this.updatingChunkMap.isEmpty() */ || this.poiManager.hasWork() || !this.toDrop.isEmpty() || !this.unloadQueue.isEmpty() || this.queueSorter.hasWork() || this.distanceManager.hasTickets();
    }

    private void processUnloads(BooleanSupplier pHasMoreTime) {
        ObjectIterator<ChunkPos> longiterator = this.toDrop.iterator();

        for (int i = 0; longiterator.hasNext() && (pHasMoreTime.getAsBoolean() || i < 200 || this.toDrop.size() > 2000); longiterator.remove()) {
            ChunkPos j = longiterator.next();
            ChunkHolder chunkholder = this.updatingChunkMap.remove(j);
            if (chunkholder != null) {
                this.pendingUnloads.put(j, chunkholder);
                this.modified = true;
                ++i;
                this.scheduleUnload(j, chunkholder);
            }
        }

        int k = Math.max(0, this.unloadQueue.size() - 2000);

        Runnable runnable;
        while ((pHasMoreTime.getAsBoolean() || k > 0) && (runnable = this.unloadQueue.poll()) != null) {
            --k;
            runnable.run();
        }

        int l = 0;
        ObjectIterator<ChunkHolder> objectiterator = this.visibleChunkMap.values().iterator();

        while (l < 20 && pHasMoreTime.getAsBoolean() && objectiterator.hasNext()) {
            if (this.saveChunkIfNeeded(objectiterator.next())) {
                ++l;
            }
        }
    }

    private void scheduleUnload(ChunkPos pChunkPos, ChunkHolder pChunkHolder) {
        CompletableFuture<ChunkAccess> completablefuture = pChunkHolder.getChunkToSave();
        completablefuture.thenAcceptAsync((p_203002_) -> {
                             CompletableFuture<ChunkAccess> completablefuture1 = pChunkHolder.getChunkToSave();
                             if (completablefuture1 != completablefuture) {
                                 this.scheduleUnload(pChunkPos, pChunkHolder);
                             } else {
                                 if (this.pendingUnloads.remove(pChunkPos, pChunkHolder) && p_203002_ != null) {
                                     if (p_203002_ instanceof LevelChunk) {
                                         ((LevelChunk) p_203002_).setLoaded(false);
                                     }

                                     this.save(p_203002_);
                                     if (this.entitiesInLevel.remove(pChunkPos) && p_203002_ instanceof LevelChunk) {
                                         LevelChunk levelchunk = (LevelChunk) p_203002_;
                                         this.level.unload(levelchunk);
                                     }

                                     this.lightEngine.updateChunkStatus(p_203002_.getPos());
                                     this.lightEngine.tryScheduleUpdate();
                                     this.progressListener.onStatusChange(p_203002_.getPos(), (ChunkStatus) null);
                                     this.chunkSaveCooldowns.removeLong(p_203002_.getPos());
                                 }
                             }
                         }, this.unloadQueue::add).whenComplete((p_202996_, p_202997_) -> {
            if (p_202997_ != null) {
                LOGGER.error("Failed to save chunk {}", pChunkHolder.getPos(), p_202997_);
            }
        });
    }

    protected boolean promoteChunkMap() {
        if (!this.modified) {
            return false;
        } else {
            this.visibleChunkMap = this.updatingChunkMap.clone();
            this.modified = false;
            return true;
        }
    }

    public CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> schedule(ChunkHolder pHolder, ChunkStatus pStatus) {
        ChunkPos chunkpos = pHolder.getPos();
        if (pStatus == ChunkStatus.EMPTY) {
            return this.scheduleChunkLoad(chunkpos);
        } else {
            if (pStatus == ChunkStatus.LIGHT) {
                this.distanceManager.addTicket(TicketType.LIGHT, chunkpos, 33 + ChunkStatus.getDistance(ChunkStatus.LIGHT), chunkpos);
            }

            Optional<ChunkAccess> optional = pHolder.getOrScheduleFuture(pStatus.getParent(), this).getNow(ChunkHolder.UNLOADED_CHUNK).left();
            if (optional.isPresent() && optional.get().getStatus().isOrAfter(pStatus)) {
                CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completablefuture = pStatus.load(this.level, this.structureTemplateManager, this.lightEngine, (p_203081_) -> {
                    return this.protoChunkToFullChunk(pHolder);
                }, optional.get());
                this.progressListener.onStatusChange(chunkpos, pStatus);
                return completablefuture;
            } else {
                return this.scheduleChunkGeneration(pHolder, pStatus);
            }
        }
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> scheduleChunkLoad(ChunkPos pChunkPos) {
        return this.readChunk(pChunkPos).thenApply((p_214925_) -> {
                                            return p_214925_.filter((p_214928_) -> {
                                                boolean flag = isChunkDataValid(p_214928_);
                                                if (!flag) {
                                                    LOGGER.error("Chunk file at {} is missing level data, skipping", (Object) pChunkPos);
                                                }

                                                return flag;
                                            });
                                        })
                .<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>thenApplyAsync((p_214891_) -> {
                    this.level.getProfiler().incrementCounter("chunkLoad");
                    if (p_214891_.isPresent()) {
                        ChunkAccess chunkaccess = ChunkSerializer.read(this.level, this.poiManager, pChunkPos, p_214891_.get());
                        this.markPosition(pChunkPos, chunkaccess.getStatus().getChunkType());
                        return Either.left(chunkaccess);
                    } else {
                        return Either.left(this.createEmptyChunk(pChunkPos));
                    }
                }, this.mainThreadExecutor)
                .exceptionallyAsync((p_214888_) -> {
                    return this.handleChunkLoadFailure(p_214888_, pChunkPos);
                }, this.mainThreadExecutor);
    }

    private static boolean isChunkDataValid(CompoundTag pTag) {
        return pTag.contains("Status", 8);
    }

    private Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure> handleChunkLoadFailure(Throwable pException, ChunkPos pChunkPos) {
        if (pException instanceof ReportedException reportedexception) {
            Throwable throwable = reportedexception.getCause();
            if (!(throwable instanceof IOException)) {
                this.markPositionReplaceable(pChunkPos);
                throw reportedexception;
            }

            LOGGER.error("Couldn't load chunk {}", pChunkPos, throwable);
        } else if (pException instanceof IOException) {
            LOGGER.error("Couldn't load chunk {}", pChunkPos, pException);
        }

        return Either.left(this.createEmptyChunk(pChunkPos));
    }

    private ChunkAccess createEmptyChunk(ChunkPos pChunkPos) {
        this.markPositionReplaceable(pChunkPos);
        return new ProtoChunk(pChunkPos, UpgradeData.EMPTY, this.level, this.level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY), (BlendingData) null);
    }

    private void markPositionReplaceable(ChunkPos pChunkPos) {
        this.chunkTypeCache.put(pChunkPos, (byte) -1);
    }

    private byte markPosition(ChunkPos pChunkPos, ChunkStatus.ChunkType pChunkType) {
        return this.chunkTypeCache.put(pChunkPos, (byte) (pChunkType == ChunkStatus.ChunkType.PROTOCHUNK ? -1 : 1));
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> scheduleChunkGeneration(ChunkHolder pChunkHolder, ChunkStatus pChunkStatus) {
        ChunkPos chunkpos = pChunkHolder.getPos();
        CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.ChunkLoadingFailure>> completablefuture = this.getChunkRangeFuture(chunkpos, pChunkStatus.getRange(), (p_214935_) -> {
            return this.getDependencyStatus(pChunkStatus, p_214935_);
        });
        this.level.getProfiler().incrementCounter(() -> {
            return "chunkGenerate " + pChunkStatus.getName();
        });
        Executor executor = (p_214958_) -> {
            this.worldgenMailbox.tell(ChunkTaskPriorityQueueSorter.message(pChunkHolder, p_214958_));
        };
        return completablefuture.thenComposeAsync((p_214873_) -> {
            return p_214873_.map((p_214879_) -> {
            try {
               CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completablefuture1 = pChunkStatus.generate(executor, this.level, this.generator, this.structureTemplateManager, this.lightEngine, (p_214919_) -> {
                  return this.protoChunkToFullChunk(pChunkHolder);
               }, p_214879_, false);
               this.progressListener.onStatusChange(chunkpos, pChunkStatus);
               return completablefuture1;
            } catch (Exception exception) {
               exception.getStackTrace();
               CrashReport crashreport = CrashReport.forThrowable(exception, "Exception generating new chunk");
               CrashReportCategory crashreportcategory = crashreport.addCategory("Chunk to be generated");
               crashreportcategory.setDetail("Location", chunkpos.x() + "," + chunkpos.z());
               crashreportcategory.setDetail("Position hash", chunkpos.hashCode());
               crashreportcategory.setDetail("Generator", this.generator);
               this.mainThreadExecutor.execute(() -> {
                  throw new ReportedException(crashreport);
               });
               throw new ReportedException(crashreport);
            } }, (p_214867_) -> {
            this.releaseLightTicket(chunkpos);
            return CompletableFuture.completedFuture(Either.right(p_214867_)); });
        }, executor);
    }

    protected void releaseLightTicket(ChunkPos pChunkPos) {
        this.mainThreadExecutor.tell(Util.name(() -> { this.distanceManager.removeTicket(TicketType.LIGHT, pChunkPos, 33 + ChunkStatus.getDistance(ChunkStatus.LIGHT), pChunkPos); }, () -> { return "release light ticket " + pChunkPos; }));
    }

    private ChunkStatus getDependencyStatus(ChunkStatus p_140263_, int p_140264_) {
        ChunkStatus chunkstatus;
        if (p_140264_ == 0) {
            chunkstatus = p_140263_.getParent();
        } else {
            chunkstatus = ChunkStatus.getStatusAroundFullChunk(ChunkStatus.getDistance(p_140263_) + p_140264_);
        }

        return chunkstatus;
    }

    private static void postLoadProtoChunk(ServerLevel p_143065_, List<CompoundTag> p_143066_) {
        if (!p_143066_.isEmpty()) {
            p_143065_.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(p_143066_, p_143065_));
        }
    }

    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> protoChunkToFullChunk(ChunkHolder p_140384_) {
        CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> completablefuture = p_140384_.getFutureIfPresentUnchecked(ChunkStatus.FULL.getParent());
        return completablefuture.thenApplyAsync((p_214853_) -> {
         ChunkStatus chunkstatus = ChunkHolder.getStatus(p_140384_.getTicketLevel());
         return !chunkstatus.isOrAfter(ChunkStatus.FULL) ? ChunkHolder.UNLOADED_CHUNK : p_214853_.mapLeft((p_214856_) -> {
            ChunkPos chunkpos = p_140384_.getPos();
            ProtoChunk protochunk = (ProtoChunk)p_214856_;
            LevelChunk levelchunk;
            if (protochunk instanceof ImposterProtoChunk) {
               levelchunk = ((ImposterProtoChunk)protochunk).getWrapped();
            } else {
               levelchunk = new LevelChunk(this.level, protochunk, (p_214900_) -> {
                  postLoadProtoChunk(this.level, protochunk.getEntities());
               });
               p_140384_.replaceProtoChunk(new ImposterProtoChunk(levelchunk, false));
            }

            levelchunk.setFullStatus(() -> {
               return ChunkHolder.getFullChunkStatus(p_140384_.getTicketLevel());
            });
            levelchunk.runPostLoad();
            if (this.entitiesInLevel.add(chunkpos)) {
               levelchunk.setLoaded(true);
               levelchunk.registerAllBlockEntitiesAfterLevelLoad();
               levelchunk.registerTickContainerInLevel(this.level);
            }

            return levelchunk;
         }); }, (p_214951_) -> { this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(p_214951_, p_140384_.getPos(), p_140384_::getTicketLevel)); });
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> prepareTickingChunk(ChunkHolder pHolder) {
        ChunkPos chunkpos = pHolder.getPos();
        CompletableFuture<Either<List<ChunkAccess>, ChunkHolder.ChunkLoadingFailure>> completablefuture = this.getChunkRangeFuture(chunkpos, 1, (p_214916_) -> {
            return ChunkStatus.FULL;
        });
        CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> completablefuture1 = completablefuture.thenApplyAsync((p_212878_) -> { return p_212878_.mapLeft((p_214939_) -> {
                                                                                                                                                          return (LevelChunk) p_214939_.get(p_214939_.size() / 2);
                                                                                                                                                      }); }, (p_214944_) -> { this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(pHolder, p_214944_)); }).thenApplyAsync((p_214930_) -> {
            return p_214930_.ifLeft((p_214960_) -> {
                p_214960_.postProcessGeneration();
                this.level.startTickingChunk(p_214960_);
            });
        }, this.mainThreadExecutor);
        completablefuture1.thenAcceptAsync((p_214882_) -> { p_214882_.ifLeft((p_214885_) -> {
                                                                this.tickingGenerated.getAndIncrement();
                                                                MutableObject<ClientboundLevelChunkWithLightPacket> mutableobject = new MutableObject<>();
                                                                this.getPlayers(chunkpos, false).forEach((p_214911_) -> {
                                                                    this.playerLoadedChunk(p_214911_, mutableobject, p_214885_);
                                                                });
                                                            }); }, (p_214922_) -> { this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(pHolder, p_214922_)); });
        return completablefuture1;
    }

    public CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> prepareAccessibleChunk(ChunkHolder pHolder) {
        return this.getChunkRangeFuture(pHolder.getPos(), 1, ChunkStatus::getStatusAroundFullChunk).thenApplyAsync((p_214893_) -> { return p_214893_.mapLeft((p_214905_) -> {
                                                                                                                                          return (LevelChunk) p_214905_.get(p_214905_.size() / 2);
                                                                                                                                      }); }, (p_214859_) -> { this.mainThreadMailbox.tell(ChunkTaskPriorityQueueSorter.message(pHolder, p_214859_)); });
    }

    public int getTickingGenerated() {
        return this.tickingGenerated.get();
    }

    private boolean saveChunkIfNeeded(ChunkHolder p_198875_) {
        if (!p_198875_.wasAccessibleSinceLastSave()) {
            return false;
        } else {
            ChunkAccess chunkaccess = p_198875_.getChunkToSave().getNow((ChunkAccess) null);
            if (!(chunkaccess instanceof ImposterProtoChunk) && !(chunkaccess instanceof LevelChunk)) {
                return false;
            } else {
                ChunkPos i = chunkaccess.getPos();
                long j = this.chunkSaveCooldowns.getOrDefault(i, -1L);
                long k = System.currentTimeMillis();
                if (k < j) {
                    return false;
                } else {
                    boolean flag = this.save(chunkaccess);
                    p_198875_.refreshAccessibility();
                    if (flag) {
                        this.chunkSaveCooldowns.put(i, k + 10000L);
                    }

                    return flag;
                }
            }
        }
    }

    private boolean save(ChunkAccess p_140259_) {
        this.poiManager.flush(p_140259_.getPos());
        if (!p_140259_.isUnsaved()) {
            return false;
        } else {
            p_140259_.setUnsaved(false);
            ChunkPos chunkpos = p_140259_.getPos();

            try {
                ChunkStatus chunkstatus = p_140259_.getStatus();
                if (chunkstatus.getChunkType() != ChunkStatus.ChunkType.LEVELCHUNK) {
                    if (this.isExistingChunkFull(chunkpos)) {
                        return false;
                    }

                    if (chunkstatus == ChunkStatus.EMPTY && p_140259_.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) {
                        return false;
                    }
                }

                this.level.getProfiler().incrementCounter("chunkSave");
                CompoundTag compoundtag = ChunkSerializer.write(this.level, p_140259_);
                this.write(chunkpos, compoundtag);
                this.markPosition(chunkpos, chunkstatus.getChunkType());
                return true;
            } catch (Exception exception) {
                LOGGER.error("Failed to save chunk {},{}", chunkpos.x(), chunkpos.z(), exception);
                return false;
            }
        }
    }

    private boolean isExistingChunkFull(ChunkPos pChunkPos) {
        byte b0 = this.chunkTypeCache.getByte(pChunkPos);
        if (b0 != 0) {
            return b0 == 1;
        } else {
            CompoundTag compoundtag;
            try {
                compoundtag = this.readChunk(pChunkPos).join().orElse((CompoundTag) null);
                if (compoundtag == null) {
                    this.markPositionReplaceable(pChunkPos);
                    return false;
                }
            } catch (Exception exception) {
                LOGGER.error("Failed to read chunk {}", pChunkPos, exception);
                this.markPositionReplaceable(pChunkPos);
                return false;
            }

            ChunkStatus.ChunkType chunkstatus$chunktype = ChunkSerializer.getChunkTypeFromTag(compoundtag);
            return this.markPosition(pChunkPos, chunkstatus$chunktype) == 1;
        }
    }

    protected void setViewDistance(int pViewDistance) {
        int i = Mth.clamp(pViewDistance + 1, 3, 33);
        if (i != this.viewDistance) {
            int j = this.viewDistance;
            this.viewDistance = i;
            this.distanceManager.updatePlayerTickets(this.viewDistance + 1);

            for (ChunkHolder chunkholder : this.updatingChunkMap.values()) {
                ChunkPos chunkpos = chunkholder.getPos();
                MutableObject<ClientboundLevelChunkWithLightPacket> mutableobject = new MutableObject<>();
                this.getPlayers(chunkpos, false).forEach((p_214864_) -> {
                    SectionPos sectionpos = p_214864_.getLastSectionPos();
                    boolean flag = isChunkInRange(chunkpos.x(), chunkpos.z(), sectionpos.x(), sectionpos.z(), j);
                    boolean flag1 = isChunkInRange(chunkpos.x(), chunkpos.z(), sectionpos.x(), sectionpos.z(), this.viewDistance);
                    this.updateChunkTracking(p_214864_, chunkpos, mutableobject, flag, flag1);
                });
            }
        }
    }

    protected void updateChunkTracking(ServerPlayer pPlayer, ChunkPos pChunkPos, MutableObject<ClientboundLevelChunkWithLightPacket> pPacketCache, boolean pWasLoaded, boolean pLoad) {
        if (pPlayer.level == this.level) {
            if (pLoad && !pWasLoaded) {
                ChunkHolder chunkholder = this.getVisibleChunkIfPresent(pChunkPos);
                if (chunkholder != null) {
                    LevelChunk levelchunk = chunkholder.getTickingChunk();
                    if (levelchunk != null) {
                        this.playerLoadedChunk(pPlayer, pPacketCache, levelchunk);
                    }

                    DebugPackets.sendPoiPacketsForChunk(this.level, pChunkPos);
                }
            }

            if (!pLoad && pWasLoaded) {
                pPlayer.untrackChunk(pChunkPos);
            }
        }
    }

    public int size() {
        return this.visibleChunkMap.size();
    }

    public net.minecraft.server.level.DistanceManager getDistanceManager() {
        return this.distanceManager;
    }

    protected Iterable<ChunkHolder> getChunks() {
        return Iterables.unmodifiableIterable(this.visibleChunkMap.values());
    }

    void dumpChunks(Writer p_140275_) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder().addColumn("x").addColumn("z").addColumn("level").addColumn("in_memory").addColumn("status").addColumn("full_status").addColumn("accessible_ready").addColumn("ticking_ready").addColumn("entity_ticking_ready").addColumn("ticket").addColumn("spawning").addColumn("block_entity_count").addColumn("ticking_ticket").addColumn("ticking_level").addColumn("block_ticks").addColumn("fluid_ticks").build(p_140275_);
        TickingTracker tickingtracker = this.distanceManager.tickingTracker();

        for (Entry<ChunkPos, ChunkHolder> entry : this.visibleChunkMap.entrySet()) {
            ChunkPos chunkpos = entry.getKey();
            ChunkHolder chunkholder = entry.getValue();
            Optional<ChunkAccess> optional = Optional.ofNullable(chunkholder.getLastAvailable());
            Optional<LevelChunk> optional1 = optional.flatMap((p_214932_) -> {
                return p_214932_ instanceof LevelChunk ? Optional.of((LevelChunk) p_214932_) : Optional.empty();
            });
            csvoutput.writeRow(chunkpos.x(), chunkpos.z(), chunkholder.getTicketLevel(), optional.isPresent(), optional.map(ChunkAccess::getStatus).orElse((ChunkStatus) null), optional1.map(LevelChunk::getFullStatus).orElse((ChunkHolder.FullChunkStatus) null), printFuture(chunkholder.getFullChunkFuture()), printFuture(chunkholder.getTickingChunkFuture()), printFuture(chunkholder.getEntityTickingChunkFuture()), this.distanceManager.getTicketDebugString(chunkpos), this.anyPlayerCloseEnoughForSpawning(chunkpos), optional1.map((p_214953_) -> {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            return p_214953_.getBlockEntities().size();
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        })
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       .orElse(0),
                    tickingtracker.getTicketDebugString(chunkpos), tickingtracker.getLevel(chunkpos), optional1.map((p_214946_) -> {
                                                                                                                   return p_214946_.getBlockTicks().count();
                                                                                                               })
                                                                                                              .orElse(0),
                    optional1.map((p_214937_) -> {
                                 return p_214937_.getFluidTicks().count();
                             })
                            .orElse(0));
        }
    }

    private static String printFuture(CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> p_140279_) {
        try {
            Either<LevelChunk, ChunkHolder.ChunkLoadingFailure> either = p_140279_.getNow((Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>) null);
            return either != null ? either.map((p_214897_) -> { return "done"; }, (p_214850_) -> { return "unloaded"; }) : "not completed";
        } catch (CompletionException completionexception) {
            return "failed " + completionexception.getCause().getMessage();
        } catch (CancellationException cancellationexception) {
            return "cancelled";
        }
    }

    private CompletableFuture<Optional<CompoundTag>> readChunk(ChunkPos pPos) {
        return this.read(pPos).thenApplyAsync((p_214907_) -> {
            return p_214907_.map(this::upgradeChunkTag);
        }, Util.backgroundExecutor());
    }

    private CompoundTag upgradeChunkTag(CompoundTag p_214948_) {
        return this.upgradeChunkTag(this.level.dimension(), this.overworldDataStorage, p_214948_, this.generator.getTypeNameForDataFixer());
    }

    boolean anyPlayerCloseEnoughForSpawning(ChunkPos pChunkPos) {
        if (!this.distanceManager.hasPlayersNearby(pChunkPos)) {
            return false;
        } else {
            for (ServerPlayer serverplayer : this.playerMap.getPlayers(pChunkPos)) {
                if (this.playerIsCloseEnoughForSpawning(serverplayer, pChunkPos)) {
                    return true;
                }
            }

            return false;
        }
    }

    public List<ServerPlayer> getPlayersCloseForSpawning(ChunkPos pChunkPos) {
        if (!this.distanceManager.hasPlayersNearby(pChunkPos)) {
            return List.of();
        } else {
            ImmutableList.Builder<ServerPlayer> builder = ImmutableList.builder();

            for (ServerPlayer serverplayer : this.playerMap.getPlayers(pChunkPos)) {
                if (this.playerIsCloseEnoughForSpawning(serverplayer, pChunkPos)) {
                    builder.add(serverplayer);
                }
            }

            return builder.build();
        }
    }

    private boolean playerIsCloseEnoughForSpawning(ServerPlayer pPlayer, ChunkPos pChunkPos) {
        if (pPlayer.isSpectator()) {
            return false;
        } else {
            double d0 = euclideanDistanceSquared(pChunkPos, pPlayer);
            return d0 < 16384.0D;
        }
    }

    private boolean skipPlayer(ServerPlayer pPlayer) {
        return pPlayer.isSpectator() && !this.level.getGameRules().getBoolean(GameRules.RULE_SPECTATORSGENERATECHUNKS);
    }

    void updatePlayerStatus(ServerPlayer pPlayer, boolean pTrack) {
        boolean flag = this.skipPlayer(pPlayer);
        boolean flag1 = this.playerMap.ignoredOrUnknown(pPlayer);
        Int128 i = SectionPos.blockToSectionCoord(pPlayer.getBlockX());
        Int128 j = SectionPos.blockToSectionCoord(pPlayer.getBlockZ());
        if (pTrack) {
            this.playerMap.addPlayer(new ChunkPos(i, j), pPlayer, flag);
            this.updatePlayerPos(pPlayer);
            if (!flag) {
                this.distanceManager.addPlayer(SectionPos.of(pPlayer), pPlayer);
            }
        } else {
            SectionPos sectionpos = pPlayer.getLastSectionPos();
            this.playerMap.removePlayer(sectionpos.chunk(), pPlayer);
            if (!flag1) {
                this.distanceManager.removePlayer(sectionpos, pPlayer);
            }
        }

        forEachChunkBox(i, j, i, j, (long) this.viewDistance + 1L, (chunkpos) -> {
            if (isChunkInRange(chunkpos.x(), chunkpos.z(), i, j, this.viewDistance)) {
                this.updateChunkTracking(pPlayer, chunkpos, new MutableObject<>(), !pTrack, pTrack);
            }
        });
    }

    private SectionPos updatePlayerPos(ServerPlayer pPlayer) {
        SectionPos sectionpos = SectionPos.of(pPlayer);
        pPlayer.setLastSectionPos(sectionpos);
        pPlayer.connection.send(new ClientboundSetChunkCacheCenterPacket(sectionpos.x(), sectionpos.z()));
        return sectionpos;
    }

    public void move(ServerPlayer pPlayer) {
        for (ChunkMap.TrackedEntity chunkmap$trackedentity : this.entityMap.values()) {
            if (chunkmap$trackedentity.entity == pPlayer) {
                chunkmap$trackedentity.updatePlayers(this.level.players());
            } else {
                chunkmap$trackedentity.updatePlayer(pPlayer);
            }
        }

        Int128 i2 = SectionPos.blockToSectionCoord(pPlayer.getBlockX());
        Int128 j2 = SectionPos.blockToSectionCoord(pPlayer.getBlockZ());
        SectionPos sectionpos = pPlayer.getLastSectionPos();
        SectionPos sectionpos1 = SectionPos.of(pPlayer);
        ChunkPos i = sectionpos.chunk();
        ChunkPos j = sectionpos1.chunk();
        boolean flag = this.playerMap.ignored(pPlayer);
        boolean flag1 = this.skipPlayer(pPlayer);
        boolean flag2 = !sectionpos.equals(sectionpos1);
        if (flag2 || flag != flag1) {
            this.updatePlayerPos(pPlayer);
            if (!flag) {
                this.distanceManager.removePlayer(sectionpos, pPlayer);
            }

            if (!flag1) {
                this.distanceManager.addPlayer(sectionpos1, pPlayer);
            }

            if (!flag && flag1) {
                this.playerMap.ignorePlayer(pPlayer);
            }

            if (flag && !flag1) {
                this.playerMap.unIgnorePlayer(pPlayer);
            }

            if (!i.equals(j)) {
                this.playerMap.updatePlayer(i, j, pPlayer);
            }
        }

        Int128 k = sectionpos.x();
        Int128 l = sectionpos.z();
        if (WorldBounds.distance(k, i2) <= (long) this.viewDistance * 2L
                && WorldBounds.distance(l, j2) <= (long) this.viewDistance * 2L) {
            forEachChunkBox(k, l, i2, j2, (long) this.viewDistance + 1L, (chunkpos) -> {
                boolean flag5 = isChunkInRange(chunkpos.x(), chunkpos.z(), k, l, this.viewDistance);
                boolean flag6 = isChunkInRange(chunkpos.x(), chunkpos.z(), i2, j2, this.viewDistance);
                this.updateChunkTracking(pPlayer, chunkpos, new MutableObject<>(), flag5, flag6);
            });
        } else {
            forEachChunkBox(k, l, k, l, (long) this.viewDistance + 1L, (chunkpos) -> {
                if (isChunkInRange(chunkpos.x(), chunkpos.z(), k, l, this.viewDistance)) {
                    this.updateChunkTracking(pPlayer, chunkpos, new MutableObject<>(), true, false);
                }
            });

            forEachChunkBox(i2, j2, i2, j2, (long) this.viewDistance + 1L, (chunkpos) -> {
                if (isChunkInRange(chunkpos.x(), chunkpos.z(), i2, j2, this.viewDistance)) {
                    this.updateChunkTracking(pPlayer, chunkpos, new MutableObject<>(), false, true);
                }
            });
        }
    }

    public List<ServerPlayer> getPlayers(ChunkPos pPos, boolean pBoundaryOnly) {
        Set<ServerPlayer> set = this.playerMap.getPlayers(pPos);
        ImmutableList.Builder<ServerPlayer> builder = ImmutableList.builder();

        for (ServerPlayer serverplayer : set) {
            SectionPos sectionpos = serverplayer.getLastSectionPos();
            if (pBoundaryOnly && isChunkOnRangeBorder(pPos.x(), pPos.z(), sectionpos.x(), sectionpos.z(), this.viewDistance) || !pBoundaryOnly && isChunkInRange(pPos.x(), pPos.z(), sectionpos.x(), sectionpos.z(), this.viewDistance)) {
                builder.add(serverplayer);
            }
        }

        return builder.build();
    }

    protected void addEntity(Entity pEntity) {
        if (!(pEntity instanceof EnderDragonPart)) {
            EntityType<?> entitytype = pEntity.getType();
            int i = entitytype.clientTrackingRange() * 16;
            if (i != 0) {
                int j = entitytype.updateInterval();
                if (this.entityMap.containsKey(pEntity.getId())) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
                } else {
                    ChunkMap.TrackedEntity chunkmap$trackedentity = new ChunkMap.TrackedEntity(pEntity, i, j, entitytype.trackDeltas());
                    this.entityMap.put(pEntity.getId(), chunkmap$trackedentity);
                    chunkmap$trackedentity.updatePlayers(this.level.players());
                    if (pEntity instanceof ServerPlayer) {
                        ServerPlayer serverplayer = (ServerPlayer) pEntity;
                        this.updatePlayerStatus(serverplayer, true);

                        for (ChunkMap.TrackedEntity chunkmap$trackedentity1 : this.entityMap.values()) {
                            if (chunkmap$trackedentity1.entity != serverplayer) {
                                chunkmap$trackedentity1.updatePlayer(serverplayer);
                            }
                        }
                    }
                }
            }
        }
    }

    protected void removeEntity(Entity pEntity) {
        if (pEntity instanceof ServerPlayer serverplayer) {
            this.updatePlayerStatus(serverplayer, false);

            for (ChunkMap.TrackedEntity chunkmap$trackedentity : this.entityMap.values()) {
                chunkmap$trackedentity.removePlayer(serverplayer);
            }
        }

        ChunkMap.TrackedEntity chunkmap$trackedentity1 = this.entityMap.remove(pEntity.getId());
        if (chunkmap$trackedentity1 != null) {
            chunkmap$trackedentity1.broadcastRemoved();
        }
    }

    protected void tick() {
        List<ServerPlayer> list = Lists.newArrayList();
        List<ServerPlayer> list1 = this.level.players();

        for (ChunkMap.TrackedEntity chunkmap$trackedentity : this.entityMap.values()) {
            SectionPos sectionpos = chunkmap$trackedentity.lastSectionPos;
            SectionPos sectionpos1 = SectionPos.of(chunkmap$trackedentity.entity);
            boolean flag = !Objects.equals(sectionpos, sectionpos1);
            if (flag) {
                chunkmap$trackedentity.updatePlayers(list1);
                Entity entity = chunkmap$trackedentity.entity;
                if (entity instanceof ServerPlayer) {
                    list.add((ServerPlayer) entity);
                }

                chunkmap$trackedentity.lastSectionPos = sectionpos1;
            }

            if (flag || this.distanceManager.inEntityTickingRange(sectionpos1.chunk())) {
                chunkmap$trackedentity.serverEntity.sendChanges();
            }
        }

        if (!list.isEmpty()) {
            for (ChunkMap.TrackedEntity chunkmap$trackedentity1 : this.entityMap.values()) {
                chunkmap$trackedentity1.updatePlayers(list);
            }
        }
    }

    public void broadcast(Entity pEntity, Packet<?> pPacket) {
        ChunkMap.TrackedEntity chunkmap$trackedentity = this.entityMap.get(pEntity.getId());
        if (chunkmap$trackedentity != null) {
            chunkmap$trackedentity.broadcast(pPacket);
        }
    }

    protected void broadcastAndSend(Entity pEntity, Packet<?> pPacket) {
        ChunkMap.TrackedEntity chunkmap$trackedentity = this.entityMap.get(pEntity.getId());
        if (chunkmap$trackedentity != null) {
            chunkmap$trackedentity.broadcastAndSend(pPacket);
        }
    }

    private void playerLoadedChunk(ServerPlayer pPlayer, MutableObject<ClientboundLevelChunkWithLightPacket> pPacketCache, LevelChunk pChunk) {
        if (pPacketCache.getValue() == null) {
            pPacketCache.setValue(new ClientboundLevelChunkWithLightPacket(pChunk, this.lightEngine, (BitSet) null, (BitSet) null, true));
        }

        pPlayer.trackChunk(pChunk.getPos(), pPacketCache.getValue());
        DebugPackets.sendPoiPacketsForChunk(this.level, pChunk.getPos());
        List<Entity> list = Lists.newArrayList();
        List<Entity> list1 = Lists.newArrayList();

        for (ChunkMap.TrackedEntity chunkmap$trackedentity : this.entityMap.values()) {
            Entity entity = chunkmap$trackedentity.entity;
            if (entity != pPlayer && entity.chunkPosition().equals(pChunk.getPos())) {
                chunkmap$trackedentity.updatePlayer(pPlayer);
                if (entity instanceof Mob && ((Mob) entity).getLeashHolder() != null) {
                    list.add(entity);
                }

                if (!entity.getPassengers().isEmpty()) {
                    list1.add(entity);
                }
            }
        }

        if (!list.isEmpty()) {
            for (Entity entity1 : list) {
                pPlayer.connection.send(new ClientboundSetEntityLinkPacket(entity1, ((Mob) entity1).getLeashHolder()));
            }
        }

        if (!list1.isEmpty()) {
            for (Entity entity2 : list1) {
                pPlayer.connection.send(new ClientboundSetPassengersPacket(entity2));
            }
        }
    }

    protected PoiManager getPoiManager() {
        return this.poiManager;
    }

    public String getStorageName() {
        return this.storageName;
    }

    void onFullChunkStatusChange(ChunkPos pPos, ChunkHolder.FullChunkStatus pFullChunkStatus) {
        this.chunkStatusListener.onChunkStatusChange(pPos, pFullChunkStatus);
    }

    class DistanceManager extends net.minecraft.server.level.DistanceManager {
        protected DistanceManager(Executor p_140459_, Executor p_140460_) {
            super(p_140459_, p_140460_);
        }

        protected boolean isChunkToRemove(ChunkPos pChunkPos) {
            return ChunkMap.this.toDrop.contains(pChunkPos);
        }

        @Nullable
        protected ChunkHolder getChunk(ChunkPos pChunkPos) {
            return ChunkMap.this.getUpdatingChunkIfPresent(pChunkPos);
        }

        @Nullable
        protected ChunkHolder updateChunkScheduling(ChunkPos pChunkPos, int pNewLevel, @Nullable ChunkHolder pHolder, int pOldLevel) {
            return ChunkMap.this.updateChunkScheduling(pChunkPos, pNewLevel, pHolder, pOldLevel);
        }
    }

    class TrackedEntity {
        final ServerEntity serverEntity;
        final Entity entity;
        private final int range;
        SectionPos lastSectionPos;
        private final Set<ServerPlayerConnection> seenBy = Sets.newIdentityHashSet();

        public TrackedEntity(Entity p_140478_, int p_140479_, int p_140480_, boolean p_140481_) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, p_140478_, p_140480_, p_140481_, this::broadcast);
            this.entity = p_140478_;
            this.range = p_140479_;
            this.lastSectionPos = SectionPos.of(p_140478_);
        }

        public boolean equals(Object pOther) {
            if (pOther instanceof ChunkMap.TrackedEntity) {
                return ((ChunkMap.TrackedEntity) pOther).entity.getId() == this.entity.getId();
            } else {
                return false;
            }
        }

        public int hashCode() {
            return this.entity.getId();
        }

        public void broadcast(Packet<?> p_140490_) {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                serverplayerconnection.send(p_140490_);
            }
        }

        public void broadcastAndSend(Packet<?> pPacket) {
            this.broadcast(pPacket);
            if (this.entity instanceof ServerPlayer) {
                ((ServerPlayer) this.entity).connection.send(pPacket);
            }
        }

        public void broadcastRemoved() {
            for (ServerPlayerConnection serverplayerconnection : this.seenBy) {
                this.serverEntity.removePairing(serverplayerconnection.getPlayer());
            }
        }

        public void removePlayer(ServerPlayer pPlayer) {
            if (this.seenBy.remove(pPlayer.connection)) {
                this.serverEntity.removePairing(pPlayer);
            }
        }

        public void updatePlayer(ServerPlayer pPlayer) {
            if (pPlayer != this.entity) {
                SectorVec3 playerPosition = pPlayer.sectorPosition();
                SectorVec3 entityPosition = this.entity.sectorPosition();
                double dx = WorldBounds.signedDifference(playerPosition.blockX(), entityPosition.blockX())
                        + playerPosition.subX() - entityPosition.subX();
                double dz = WorldBounds.signedDifference(playerPosition.blockZ(), entityPosition.blockZ())
                        + playerPosition.subZ() - entityPosition.subZ();
                double d0 = (double) Math.min(this.getEffectiveRange(), (ChunkMap.this.viewDistance - 1) * 16);
                double d1 = dx * dx + dz * dz;
                double d2 = d0 * d0;
                boolean flag = d1 <= d2 && this.entity.broadcastToPlayer(pPlayer);
                if (flag) {
                    if (this.seenBy.add(pPlayer.connection)) {
                        this.serverEntity.addPairing(pPlayer);
                    }
                } else if (this.seenBy.remove(pPlayer.connection)) {
                    this.serverEntity.removePairing(pPlayer);
                }
            }
        }

        private int scaledRange(int p_140484_) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(p_140484_);
        }

        private int getEffectiveRange() {
            int i = this.range;

            for (Entity entity : this.entity.getIndirectPassengers()) {
                int j = entity.getType().clientTrackingRange() * 16;
                if (j > i) {
                    i = j;
                }
            }

            return this.scaledRange(i);
        }

        public void updatePlayers(List<ServerPlayer> pPlayersList) {
            for (ServerPlayer serverplayer : pPlayersList) {
                this.updatePlayer(serverplayer);
            }
        }
    }
}
