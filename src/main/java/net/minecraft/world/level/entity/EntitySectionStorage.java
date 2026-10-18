package net.minecraft.world.level.entity;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;

public class EntitySectionStorage<T extends EntityAccess> {
    private final Class<T> entityClass;
    private final Object2ObjectFunction<ChunkPos, Visibility> intialSectionVisibility;
    private final Object2ObjectMap<SectionPos, EntitySection<T>> sections = new Object2ObjectOpenHashMap<>();
    /** Direct chunk index used by load, unload, visibility, and save operations. */
    private final Object2ObjectMap<ChunkPos, ObjectSet<SectionPos>> sectionsByChunk = new Object2ObjectOpenHashMap<>();
    private final ObjectSortedSet<SectionPos> sectionIds = new ObjectAVLTreeSet<>(new Comparator<>() {
        @Override
        public int compare(SectionPos o1, SectionPos o2) {
            int result = Integer.compare(o1.getY(), o2.getY());
            if (result != 0)
                return result;
            result = Long.compare(o1.getZ(), o2.getZ());
            return result != 0 ? result : Long.compare(o1.getX(), o2.getX());
        }
    });

    public EntitySectionStorage(Class<T> pEntityClass, Object2ObjectFunction<ChunkPos, Visibility> pInitialSectionVisibility) {
        this.entityClass = pEntityClass;
        this.intialSectionVisibility = pInitialSectionVisibility;
    }

    public void forEachAccessibleNonEmptySection(AABB pBoundingBox, Consumer<EntitySection<T>> pConsumer) {
        SectorAABB exact = pBoundingBox.getSectorBounds();
        if (exact != null) {
            this.forEachAccessibleNonEmptySection(
                    SectionPos.blockToSectionCoord(WorldBounds.addBlockOffset(exact.minBlockXForRange(), -2)),
                    SectionPos.blockToSectionCoord(WorldBounds.addBlockOffset(exact.maxBlockX(), 2)),
                    (int) SectionPos.posToSectionCoord(exact.minY() - 4.0D),
                    (int) SectionPos.posToSectionCoord(exact.maxY()),
                    SectionPos.blockToSectionCoord(WorldBounds.addBlockOffset(exact.minBlockZForRange(), -2)),
                    SectionPos.blockToSectionCoord(WorldBounds.addBlockOffset(exact.maxBlockZ(), 2)), pConsumer);
            return;
        }
        long minSectionX = SectionPos.posToSectionCoord(pBoundingBox.minX - 2.0D);
        int minSectionY = (int) SectionPos.posToSectionCoord(pBoundingBox.minY - 4.0D);
        long minSectionZ = SectionPos.posToSectionCoord(pBoundingBox.minZ - 2.0D);
        long maxSectionX = SectionPos.posToSectionCoord(pBoundingBox.maxX + 2.0D);
        int maxSectionY = (int) SectionPos.posToSectionCoord(pBoundingBox.maxY);
        long maxSectionZ = SectionPos.posToSectionCoord(pBoundingBox.maxZ + 2.0D);
        this.forEachAccessibleNonEmptySection(minSectionX, maxSectionX, minSectionY, maxSectionY,
                minSectionZ, maxSectionZ, pConsumer);
    }

    private void forEachAccessibleNonEmptySection(long minSectionX, long maxSectionX,
            int minSectionY, int maxSectionY,
            long minSectionZ, long maxSectionZ,
            Consumer<EntitySection<T>> consumer) {
        if (minSectionX > maxSectionX || minSectionY > maxSectionY || minSectionZ > maxSectionZ)
            return;

        // Entity collision and AI ranges normally cover only a handful of sections.
        // Looking those keys up directly avoids the old full-world section scan on
        // every moving entity. For unusually huge ranges, scan the smaller existing
        // index instead so hostile bounds cannot create a near-unbounded loop.
        double requestedSections = ((double) WorldBounds.inclusiveBlockSpan(minSectionX, maxSectionX))
                * ((double) maxSectionY - (double) minSectionY + 1.0D)
                * ((double) WorldBounds.inclusiveBlockSpan(minSectionZ, maxSectionZ));
        if (Double.isFinite(requestedSections) && requestedSections <= (double) this.sectionIds.size()) {
            for (int y = minSectionY;; ++y) {
                for (long z = minSectionZ;; z = WorldBounds.addBlockOffset(z, 1L)) {
                    for (long x = minSectionX;; x = WorldBounds.addBlockOffset(x, 1L)) {
                        this.acceptIfAccessible(SectionPos.of(x, y, z), consumer);
                        if (x == maxSectionX || x == Long.MAX_VALUE)
                            break;
                    }
                    if (z == maxSectionZ || z == Long.MAX_VALUE)
                        break;
                }
                if (y == maxSectionY || y == Integer.MAX_VALUE)
                    break;
            }
            return;
        }

        SectionPos from = SectionPos.of(Long.MIN_VALUE, minSectionY, Long.MIN_VALUE);
        SectionPos to = maxSectionY == Integer.MAX_VALUE ? null
                                                         : SectionPos.of(Long.MIN_VALUE, maxSectionY + 1, Long.MIN_VALUE);
        ObjectBidirectionalIterator<SectionPos> iterator = (to == null
                        ? this.sectionIds.tailSet(from)
                        : this.sectionIds.subSet(from, to))
                                                                   .iterator();
        while (iterator.hasNext()) {
            SectionPos sectionPos = iterator.next();
            if (sectionPos.x() >= minSectionX && sectionPos.x() <= maxSectionX
                    && sectionPos.z() >= minSectionZ && sectionPos.z() <= maxSectionZ) {
                this.acceptIfAccessible(sectionPos, consumer);
            }
        }
    }

    private void acceptIfAccessible(SectionPos sectionPos, Consumer<EntitySection<T>> consumer) {
        EntitySection<T> section = this.sections.get(sectionPos);
        if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
            consumer.accept(section);
        }
    }

    public Stream<SectionPos> getExistingSectionPositionsInChunk(ChunkPos chunkPos) {
        ObjectSet<SectionPos> positions = this.sectionsByChunk.get(chunkPos);
        return positions == null ? Stream.empty() : positions.stream();
    }

    public Stream<EntitySection<T>> getExistingSectionsInChunk(ChunkPos pPos) {
        return this.getExistingSectionPositionsInChunk(pPos).map(this.sections::get).filter(Objects::nonNull);
    }

    private static ChunkPos getChunkKeyFromSectionKey(SectionPos pPos) {
        return new ChunkPos(pPos.x(), pPos.z());
    }

    public EntitySection<T> getOrCreateSection(SectionPos pSectionPos) {
        return this.sections.computeIfAbsent(pSectionPos, this::createSection);
    }

    @Nullable
    public EntitySection<T> getSection(SectionPos pSectionPos) {
        return this.sections.get(pSectionPos);
    }

    private EntitySection<T> createSection(SectionPos sectionPos) {
        ChunkPos chunkPos = getChunkKeyFromSectionKey(sectionPos);
        Visibility visibility = this.intialSectionVisibility.get(chunkPos);
        this.sectionIds.add(sectionPos);
        this.sectionsByChunk.computeIfAbsent(chunkPos, ignored -> new ObjectOpenHashSet<>()).add(sectionPos);
        return new EntitySection<>(this.entityClass, visibility);
    }

    public ObjectSet<ChunkPos> getAllChunksWithExistingSections() {
        return new ObjectOpenHashSet<>(this.sectionsByChunk.keySet());
    }

    /** Visits accessible sections using exact long X/Z section coordinates. */
    public void getEntitiesInSections(long minSectionX, long maxSectionX, int minSectionY, int maxSectionY,
            long minSectionZ, long maxSectionZ, Consumer<T> consumer) {
        this.forEachAccessibleNonEmptySection(minSectionX, maxSectionX, minSectionY, maxSectionY,
                minSectionZ, maxSectionZ, section -> section.getEntities().forEach(consumer));
    }

    public void getEntities(AABB pBounds, Consumer<T> pConsumer) {
        this.forEachAccessibleNonEmptySection(pBounds, (p_188368_) -> {
            p_188368_.getEntities(pBounds, pConsumer);
        });
    }

    public <U extends T> void getEntities(EntityTypeTest<T, U> pTest, AABB pBounds, Consumer<U> pConsumer) {
        this.forEachAccessibleNonEmptySection(pBounds, (p_188361_) -> {
            p_188361_.getEntities(pTest, pBounds, pConsumer);
        });
    }

    public void remove(SectionPos sectionPos) {
        this.sections.remove(sectionPos);
        this.sectionIds.remove(sectionPos);
        ChunkPos chunkPos = getChunkKeyFromSectionKey(sectionPos);
        ObjectSet<SectionPos> positions = this.sectionsByChunk.get(chunkPos);
        if (positions != null) {
            positions.remove(sectionPos);
            if (positions.isEmpty())
                this.sectionsByChunk.remove(chunkPos);
        }
    }

    @VisibleForDebug
    public int count() {
        return this.sectionIds.size();
    }
}
