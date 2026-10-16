package net.minecraft.world.level.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class TransientEntitySectionManager<T extends EntityAccess> {
    static final Logger LOGGER = LogUtils.getLogger();
    final LevelCallback<T> callbacks;
    final EntityLookup<T> entityStorage;
    final EntitySectionStorage<T> sectionStorage;
    private final ObjectSet<ChunkPos> tickingChunks = new ObjectOpenHashSet<>();
    private final LevelEntityGetter<T> entityGetter;

    public TransientEntitySectionManager(Class<T> pClazz, LevelCallback<T> pCallbacks) {
        this.entityStorage = new EntityLookup<>();
        this.sectionStorage = new EntitySectionStorage<>(pClazz, (p_157647_) -> {
            return this.tickingChunks.contains(p_157647_) ? Visibility.TICKING : Visibility.TRACKED;
        });
        this.callbacks = pCallbacks;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.entityStorage, this.sectionStorage);
    }

    public void startTicking(ChunkPos pPos) {
        this.tickingChunks.add(pPos);
        this.sectionStorage.getExistingSectionsInChunk(pPos).forEach((p_157663_) -> {
            Visibility visibility = p_157663_.updateChunkStatus(Visibility.TICKING);
            if (!visibility.isTicking()) {
                p_157663_.getEntities().filter((p_157666_) -> {
                                           return !p_157666_.isAlwaysTicking();
                                       })
                        .forEach(this.callbacks::onTickingStart);
            }
        });
    }

    public void stopTicking(ChunkPos pPos) {
        this.tickingChunks.remove(pPos);
        this.sectionStorage.getExistingSectionsInChunk(pPos).forEach((p_157656_) -> {
            Visibility visibility = p_157656_.updateChunkStatus(Visibility.TRACKED);
            if (visibility.isTicking()) {
                p_157656_.getEntities().filter((p_157661_) -> {
                                           return !p_157661_.isAlwaysTicking();
                                       })
                        .forEach(this.callbacks::onTickingEnd);
            }
        });
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public void addEntity(T pEntity) {
        this.entityStorage.add(pEntity);
        SectionPos i = SectionPos.of(pEntity.blockPosition());
        EntitySection<T> entitysection = this.sectionStorage.getOrCreateSection(i);
        entitysection.add(pEntity);
        pEntity.setLevelCallback(new TransientEntitySectionManager.Callback(pEntity, i, entitysection));
        this.callbacks.onCreated(pEntity);
        this.callbacks.onTrackingStart(pEntity);
        if (pEntity.isAlwaysTicking() || entitysection.getStatus().isTicking()) {
            this.callbacks.onTickingStart(pEntity);
        }
    }

    @VisibleForDebug
    public int count() {
        return this.entityStorage.count();
    }

    void removeSectionIfEmpty(SectionPos pSection, EntitySection<T> pEntitySection) {
        if (pEntitySection.isEmpty()) {
            this.sectionStorage.remove(pSection);
        }
    }

    @VisibleForDebug
    public String gatherStats() {
        return this.entityStorage.count() + "," + this.sectionStorage.count() + "," + this.tickingChunks.size();
    }

    class Callback implements EntityInLevelCallback {
        private final T entity;
        private SectionPos currentSectionKey;
        private EntitySection<T> currentSection;

        Callback(T p_157673_, SectionPos p_157674_, EntitySection<T> p_157675_) {
            this.entity = p_157673_;
            this.currentSectionKey = p_157674_;
            this.currentSection = p_157675_;
        }

        public void onMove() {
            BlockPos blockpos = this.entity.blockPosition();
            SectionPos i = SectionPos.of(blockpos);
            if (!i.equals(this.currentSectionKey)) {
                Visibility visibility = this.currentSection.getStatus();
                if (!this.currentSection.remove(this.entity)) {
                    TransientEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (moving to {})", this.entity, this.currentSectionKey, i);
                }

                TransientEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> entitysection = TransientEntitySectionManager.this.sectionStorage.getOrCreateSection(i);
                entitysection.add(this.entity);
                this.currentSection = entitysection;
                this.currentSectionKey = i;
                TransientEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                if (!this.entity.isAlwaysTicking()) {
                    boolean flag = visibility.isTicking();
                    boolean flag1 = entitysection.getStatus().isTicking();
                    if (flag && !flag1) {
                        TransientEntitySectionManager.this.callbacks.onTickingEnd(this.entity);
                    } else if (!flag && flag1) {
                        TransientEntitySectionManager.this.callbacks.onTickingStart(this.entity);
                    }
                }
            }
        }

        public void onRemove(Entity.RemovalReason pReason) {
            if (!this.currentSection.remove(this.entity)) {
                TransientEntitySectionManager.LOGGER.warn("Entity {} wasn't found in section {} (destroying due to {})", this.entity, this.currentSectionKey, pReason);
            }

            Visibility visibility = this.currentSection.getStatus();
            if (visibility.isTicking() || this.entity.isAlwaysTicking()) {
                TransientEntitySectionManager.this.callbacks.onTickingEnd(this.entity);
            }

            TransientEntitySectionManager.this.callbacks.onTrackingEnd(this.entity);
            TransientEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            TransientEntitySectionManager.this.entityStorage.remove(this.entity);
            this.entity.setLevelCallback(NULL);
            TransientEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }
}
