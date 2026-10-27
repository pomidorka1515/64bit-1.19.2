package net.minecraft.world.level.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkPosMap;
import org.slf4j.Logger;

public class PersistentEntitySectionManager<T extends EntityAccess> implements AutoCloseable {
    static final Logger LOGGER = LogUtils.getLogger();
    final Set<UUID> knownUuids = Sets.newHashSet();
    final LevelCallback<T> callbacks;
    private final EntityPersistentStorage<T> permanentStorage;
    private final EntityLookup<T> visibleEntityStorage;
    final EntitySectionStorage<T> sectionStorage;
    private final LevelEntityGetter<T> entityGetter;
    private final ChunkPosMap<Visibility> chunkVisibility = new ChunkPosMap<>();
    private final ChunkPosMap<PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses = new ChunkPosMap<>();
    private final ObjectSet<ChunkPos> chunksToUnload = new ObjectOpenHashSet<>();
    private final Queue<ChunkEntities<T>> loadingInbox = Queues.newConcurrentLinkedQueue();

    public PersistentEntitySectionManager(Class<T> pEntityClass, LevelCallback<T> pCallbacks, EntityPersistentStorage<T> pPermanentStorage) {
        this.visibleEntityStorage = new EntityLookup<>();
        this.sectionStorage = new EntitySectionStorage<>(pEntityClass, this.chunkVisibility);
        this.chunkVisibility.defaultReturnValue(Visibility.HIDDEN);
        this.chunkLoadStatuses.defaultReturnValue(PersistentEntitySectionManager.ChunkLoadStatus.FRESH);
        this.callbacks = pCallbacks;
        this.permanentStorage = pPermanentStorage;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.visibleEntityStorage, this.sectionStorage);
    }

    void removeSectionIfEmpty(SectionPos pSectionKey, EntitySection<T> pSection) {
        if (pSection.isEmpty()) {
            this.sectionStorage.remove(pSectionKey);
        }
    }

    private boolean addEntityUuid(T pEntity) {
        if (!this.knownUuids.add(pEntity.getUUID())) {
            LOGGER.warn("UUID of added entity already exists: {}", (Object) pEntity);
            return false;
        } else {
            return true;
        }
    }

    public boolean addNewEntity(T pEntity) {
        return this.addEntity(pEntity, false);
    }

    private boolean addEntity(T pEntity, boolean pWorldGenSpawned) {
        if (!this.addEntityUuid(pEntity)) {
            return false;
        } else {
            SectionPos i = SectionPos.of(pEntity.blockPosition());
            EntitySection<T> entitysection = this.sectionStorage.getOrCreateSection(i);
            entitysection.add(pEntity);
            pEntity.setLevelCallback(new PersistentEntitySectionManager.Callback(pEntity, i, entitysection));
            if (!pWorldGenSpawned) {
                this.callbacks.onCreated(pEntity);
            }

            Visibility visibility = getEffectiveStatus(pEntity, entitysection.getStatus());
            if (visibility.isAccessible()) {
                this.startTracking(pEntity);
            }

            if (visibility.isTicking()) {
                this.startTicking(pEntity);
            }

            return true;
        }
    }

    static <T extends EntityAccess> Visibility getEffectiveStatus(T pEntity, Visibility pVisibility) {
        return pEntity.isAlwaysTicking() ? Visibility.TICKING : pVisibility;
    }

    public void addLegacyChunkEntities(Stream<T> pEntities) {
        pEntities.forEach((p_157607_) -> {
            this.addEntity(p_157607_, true);
        });
    }

    public void addWorldGenChunkEntities(Stream<T> pEntities) {
        pEntities.forEach((p_157605_) -> {
            this.addEntity(p_157605_, false);
        });
    }

    void startTicking(T p_157565_) {
        this.callbacks.onTickingStart(p_157565_);
    }

    void stopTicking(T p_157571_) {
        this.callbacks.onTickingEnd(p_157571_);
    }

    void startTracking(T p_157576_) {
        this.visibleEntityStorage.add(p_157576_);
        this.callbacks.onTrackingStart(p_157576_);
    }

    void stopTracking(T p_157581_) {
        this.callbacks.onTrackingEnd(p_157581_);
        this.visibleEntityStorage.remove(p_157581_);
    }

    public void updateChunkStatus(ChunkPos pPos, ChunkHolder.FullChunkStatus pStatus) {
        Visibility visibility = Visibility.fromFullChunkStatus(pStatus);
        this.updateChunkStatus(pPos, visibility);
    }

    public void updateChunkStatus(ChunkPos pPos, Visibility pVisibility) {
        if (pVisibility == Visibility.HIDDEN) {
            this.chunkVisibility.remove(pPos);
            this.chunksToUnload.add(pPos);
        } else {
            this.chunkVisibility.put(pPos, pVisibility);
            this.chunksToUnload.remove(pPos);
            this.ensureChunkQueuedForLoad(pPos);
        }

        this.sectionStorage.getExistingSectionsInChunk(pPos).forEach((p_157545_) -> {
            Visibility visibility = p_157545_.updateChunkStatus(pVisibility);
            boolean flag = visibility.isAccessible();
            boolean flag1 = pVisibility.isAccessible();
            boolean flag2 = visibility.isTicking();
            boolean flag3 = pVisibility.isTicking();
            if (flag2 && !flag3) {
                p_157545_.getEntities().filter((p_157603_) -> {
                                           return !p_157603_.isAlwaysTicking();
                                       })
                        .forEach(this::stopTicking);
            }

            if (flag && !flag1) {
                p_157545_.getEntities().filter((p_157601_) -> {
                                           return !p_157601_.isAlwaysTicking();
                                       })
                        .forEach(this::stopTracking);
            } else if (!flag && flag1) {
                p_157545_.getEntities().filter((p_157599_) -> {
                                           return !p_157599_.isAlwaysTicking();
                                       })
                        .forEach(this::startTracking);
            }

            if (!flag2 && flag3) {
                p_157545_.getEntities().filter((p_157597_) -> {
                                           return !p_157597_.isAlwaysTicking();
                                       })
                        .forEach(this::startTicking);
            }
        });
    }

    private void ensureChunkQueuedForLoad(ChunkPos pChunkPosValue) {
        PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager$chunkloadstatus = this.chunkLoadStatuses.get(pChunkPosValue);
        if (persistententitysectionmanager$chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(pChunkPosValue);
        }
    }

    private boolean storeChunkSections(ChunkPos p_157513_, Consumer<T> p_157514_) {
        PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager$chunkloadstatus = this.chunkLoadStatuses.get(p_157513_);
        if (persistententitysectionmanager$chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.PENDING) {
            return false;
        } else {
            List<T> list = this.sectionStorage.getExistingSectionsInChunk(p_157513_).flatMap((p_157542_) -> {
                                                                                        return p_157542_.getEntities().filter(EntityAccess::shouldBeSaved);
                                                                                    })
                                   .collect(Collectors.toList());
            if (list.isEmpty()) {
                if (persistententitysectionmanager$chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                    this.permanentStorage.storeEntities(new ChunkEntities<>(p_157513_, ImmutableList.of()));
                }

                return true;
            } else if (persistententitysectionmanager$chunkloadstatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
                this.requestChunkLoad(p_157513_);
                return false;
            } else {
                this.permanentStorage.storeEntities(new ChunkEntities<>(p_157513_, list));
                list.forEach(p_157514_);
                return true;
            }
        }
    }

    private void requestChunkLoad(ChunkPos pChunkPosValue) {
        this.chunkLoadStatuses.put(pChunkPosValue, PersistentEntitySectionManager.ChunkLoadStatus.PENDING);
        ChunkPos chunkpos = pChunkPosValue;
        this.permanentStorage.loadEntities(chunkpos).thenAccept(this.loadingInbox::add).exceptionally((p_157532_) -> {
            LOGGER.error("Failed to read chunk {}", chunkpos, p_157532_);
            return null;
        });
    }

    private boolean processChunkUnload(ChunkPos pChunkPosValue) {
        boolean flag = this.storeChunkSections(pChunkPosValue, (p_157595_) -> {
            p_157595_.getPassengersAndSelf().forEach(this::unloadEntity);
        });
        if (!flag) {
            return false;
        } else {
            this.chunkLoadStatuses.remove(pChunkPosValue);
            return true;
        }
    }

    private void unloadEntity(EntityAccess p_157586_) {
        p_157586_.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        p_157586_.setLevelCallback(EntityInLevelCallback.NULL);
    }

    private void processUnloads() {
        this.chunksToUnload.removeIf((p_157584_) -> {
            return this.chunkVisibility.get(p_157584_) != Visibility.HIDDEN ? true : this.processChunkUnload(p_157584_);
        });
    }

    private void processPendingLoads() {
        ChunkEntities<T> chunkentities;
        while ((chunkentities = this.loadingInbox.poll()) != null) {
            chunkentities.getEntities().forEach((p_157593_) -> {
                this.addEntity(p_157593_, true);
            });
            this.chunkLoadStatuses.put(chunkentities.getPos(), PersistentEntitySectionManager.ChunkLoadStatus.LOADED);
        }
    }

    public void tick() {
        this.processPendingLoads();
        this.processUnloads();
    }

    private ObjectSet<ChunkPos> getAllChunksToSave() {
        ObjectSet<ChunkPos> longset = this.sectionStorage.getAllChunksWithExistingSections();

        for (Entry<ChunkPos, ChunkLoadStatus> entry : Object2ObjectMaps.fastIterable(this.chunkLoadStatuses)) {
            if (entry.getValue() == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                longset.add(entry.getKey());
            }
        }

        return longset;
    }

    public void autoSave() {
        this.getAllChunksToSave().forEach((p_157579_) -> {
            boolean flag = this.chunkVisibility.get(p_157579_) == Visibility.HIDDEN;
            if (flag) {
                this.processChunkUnload(p_157579_);
            } else {
                this.storeChunkSections(p_157579_, (p_157591_) -> {});
            }
        });
    }

    public void saveAll() {
        ObjectSet<ChunkPos> longset = this.getAllChunksToSave();

        while (!longset.isEmpty()) {
            this.permanentStorage.flush(false);
            this.processPendingLoads();
            longset.removeIf((p_157574_) -> {
                boolean flag = this.chunkVisibility.get(p_157574_) == Visibility.HIDDEN;
                return flag ? this.processChunkUnload(p_157574_) : this.storeChunkSections(p_157574_, (p_157589_) -> {});
            });
        }

        this.permanentStorage.flush(true);
    }

    public void close() throws IOException {
        this.saveAll();
        this.permanentStorage.close();
    }

    public boolean isLoaded(UUID p_157551_) {
        return this.knownUuids.contains(p_157551_);
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public boolean canPositionTick(BlockPos p_202168_) {
        return this.chunkVisibility.getContaining(p_202168_).isTicking();
    }

    public boolean canPositionTick(ChunkPos p_202166_) {
        return this.chunkVisibility.get(p_202166_.x(), p_202166_.z()).isTicking();
    }

    public boolean areEntitiesLoaded(ChunkPos p_157508_) {
        return this.chunkLoadStatuses.get(p_157508_.x(), p_157508_.z()) == PersistentEntitySectionManager.ChunkLoadStatus.LOADED;
    }

    public void dumpSections(Writer p_157549_) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder().addColumn("x").addColumn("y").addColumn("z").addColumn("visibility").addColumn("load_status").addColumn("entity_count").build(p_157549_);
        this.sectionStorage.getAllChunksWithExistingSections().forEach((ChunkPos p_157517_) -> {
            PersistentEntitySectionManager.ChunkLoadStatus persistententitysectionmanager$chunkloadstatus = this.chunkLoadStatuses.get(p_157517_);
            this.sectionStorage.getExistingSectionPositionsInChunk(p_157517_).forEach((p_157521_) -> {
                EntitySection<T> entitysection = this.sectionStorage.getSection(p_157521_);
                if (entitysection != null) {
                    try {
                        csvoutput.writeRow(p_157521_.x(), p_157521_.y(), p_157521_.z(), entitysection.getStatus(), persistententitysectionmanager$chunkloadstatus, entitysection.size());
                    } catch (IOException ioexception) {
                        throw new UncheckedIOException(ioexception);
                    }
                }
            });
        });
    }

    @VisibleForDebug
    public String gatherStats() {
        return this.knownUuids.size() + "," + this.visibleEntityStorage.count() + "," + this.sectionStorage.count() + "," + this.chunkLoadStatuses.size() + "," + this.chunkVisibility.size() + "," + this.loadingInbox.size() + "," + this.chunksToUnload.size();
    }

    class Callback implements EntityInLevelCallback {
        private final T entity;
        private SectionPos currentSectionKey;
        private EntitySection<T> currentSection;

        Callback(T p_157614_, SectionPos p_157615_, EntitySection<T> p_157616_) {
            this.entity = p_157614_;
            this.currentSectionKey = p_157615_;
            this.currentSection = p_157616_;
        }

        public void onMove() {
            BlockPos blockpos = this.entity.blockPosition();
            SectionPos i = SectionPos.of(blockpos);
            if (!i.equals(this.currentSectionKey)) {
                Visibility visibility = this.currentSection.getStatus();
                if (!this.currentSection.remove(this.entity)) {
                    PersistentEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (moving to {})", this.entity, this.currentSectionKey, i);
                }

                PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> entitysection = PersistentEntitySectionManager.this.sectionStorage.getOrCreateSection(i);
                entitysection.add(this.entity);
                this.currentSection = entitysection;
                this.currentSectionKey = i;
                this.updateStatus(visibility, entitysection.getStatus());
            }
        }

        private void updateStatus(Visibility p_157621_, Visibility p_157622_) {
            Visibility visibility = PersistentEntitySectionManager.getEffectiveStatus(this.entity, p_157621_);
            Visibility visibility1 = PersistentEntitySectionManager.getEffectiveStatus(this.entity, p_157622_);
            if (visibility == visibility1) {
                if (visibility1.isAccessible()) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }

            } else {
                boolean flag = visibility.isAccessible();
                boolean flag1 = visibility1.isAccessible();
                if (flag && !flag1) {
                    PersistentEntitySectionManager.this.stopTracking(this.entity);
                } else if (!flag && flag1) {
                    PersistentEntitySectionManager.this.startTracking(this.entity);
                }

                boolean flag2 = visibility.isTicking();
                boolean flag3 = visibility1.isTicking();
                if (flag2 && !flag3) {
                    PersistentEntitySectionManager.this.stopTicking(this.entity);
                } else if (!flag2 && flag3) {
                    PersistentEntitySectionManager.this.startTicking(this.entity);
                }

                if (flag1) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            }
        }

        public void onRemove(Entity.RemovalReason pReason) {
            if (!this.currentSection.remove(this.entity)) {
                PersistentEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (destroying due to {})", this.entity, this.currentSectionKey, pReason);
            }

            Visibility visibility = PersistentEntitySectionManager.getEffectiveStatus(this.entity, this.currentSection.getStatus());
            if (visibility.isTicking()) {
                PersistentEntitySectionManager.this.stopTicking(this.entity);
            }

            if (visibility.isAccessible()) {
                PersistentEntitySectionManager.this.stopTracking(this.entity);
            }

            if (pReason.shouldDestroy()) {
                PersistentEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            }

            PersistentEntitySectionManager.this.knownUuids.remove(this.entity.getUUID());
            this.entity.setLevelCallback(NULL);
            PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }

    static enum ChunkLoadStatus {
        FRESH,
        PENDING,
        LOADED;
    }
}
