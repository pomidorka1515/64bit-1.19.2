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
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

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
		if (result != 0) return result;
		result = Long.compare(o1.getZ(), o2.getZ());
		return result != 0 ? result : Long.compare(o1.getX(), o2.getX());
	}
   });

   public EntitySectionStorage(Class<T> p_156855_, Object2ObjectFunction<ChunkPos, Visibility> p_156856_) {
      this.entityClass = p_156855_;
      this.intialSectionVisibility = p_156856_;
   }

   public void forEachAccessibleNonEmptySection(AABB p_188363_, Consumer<EntitySection<T>> p_188364_) {
      long minSectionX = SectionPos.posToSectionCoord(p_188363_.minX - 2.0D);
      int minSectionY = (int)SectionPos.posToSectionCoord(p_188363_.minY - 4.0D);
      long minSectionZ = SectionPos.posToSectionCoord(p_188363_.minZ - 2.0D);
      long maxSectionX = SectionPos.posToSectionCoord(p_188363_.maxX + 2.0D);
      int maxSectionY = (int)SectionPos.posToSectionCoord(p_188363_.maxY);
      long maxSectionZ = SectionPos.posToSectionCoord(p_188363_.maxZ + 2.0D);
      this.forEachAccessibleNonEmptySection(minSectionX, maxSectionX, minSectionY, maxSectionY,
            minSectionZ, maxSectionZ, p_188364_);
   }

   private void forEachAccessibleNonEmptySection(long minSectionX, long maxSectionX,
                                                  int minSectionY, int maxSectionY,
                                                  long minSectionZ, long maxSectionZ,
                                                  Consumer<EntitySection<T>> consumer) {
      if (minSectionX > maxSectionX || minSectionY > maxSectionY || minSectionZ > maxSectionZ) return;

      // Entity collision and AI ranges normally cover only a handful of sections.
      // Looking those keys up directly avoids the old full-world section scan on
      // every moving entity. For unusually huge ranges, scan the smaller existing
      // index instead so hostile bounds cannot create a near-unbounded loop.
      double requestedSections = ((double)maxSectionX - (double)minSectionX + 1.0D)
            * ((double)maxSectionY - (double)minSectionY + 1.0D)
            * ((double)maxSectionZ - (double)minSectionZ + 1.0D);
      if (Double.isFinite(requestedSections) && requestedSections <= (double)this.sectionIds.size()) {
         for (int y = minSectionY; ; ++y) {
            for (long z = minSectionZ; ; ++z) {
               for (long x = minSectionX; ; ++x) {
                  this.acceptIfAccessible(SectionPos.of(x, y, z), consumer);
                  if (x == maxSectionX || x == Long.MAX_VALUE) break;
               }
               if (z == maxSectionZ || z == Long.MAX_VALUE) break;
            }
            if (y == maxSectionY || y == Integer.MAX_VALUE) break;
         }
         return;
      }

      SectionPos from = SectionPos.of(Long.MIN_VALUE, minSectionY, Long.MIN_VALUE);
      SectionPos to = maxSectionY == Integer.MAX_VALUE ? null
            : SectionPos.of(Long.MIN_VALUE, maxSectionY + 1, Long.MIN_VALUE);
      ObjectBidirectionalIterator<SectionPos> iterator = (to == null
            ? this.sectionIds.tailSet(from) : this.sectionIds.subSet(from, to)).iterator();
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

   public Stream<EntitySection<T>> getExistingSectionsInChunk(ChunkPos p_156889_) {
      return this.getExistingSectionPositionsInChunk(p_156889_).map(this.sections::get).filter(Objects::nonNull);
   }

   private static ChunkPos getChunkKeyFromSectionKey(SectionPos p_156900_) {
      return new ChunkPos(p_156900_.x(), p_156900_.z());
   }

   public EntitySection<T> getOrCreateSection(SectionPos p_156894_) {
      return this.sections.computeIfAbsent(p_156894_, this::createSection);
   }

   @Nullable
   public EntitySection<T> getSection(SectionPos p_156896_) {
      return this.sections.get(p_156896_);
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

   public void getEntities(AABB p_156891_, Consumer<T> p_156892_) {
      this.forEachAccessibleNonEmptySection(p_156891_, (p_188368_) -> {
         p_188368_.getEntities(p_156891_, p_156892_);
      });
   }

   public <U extends T> void getEntities(EntityTypeTest<T, U> p_156864_, AABB p_156865_, Consumer<U> p_156866_) {
      this.forEachAccessibleNonEmptySection(p_156865_, (p_188361_) -> {
         p_188361_.getEntities(p_156864_, p_156865_, p_156866_);
      });
   }

   public void remove(SectionPos sectionPos) {
      this.sections.remove(sectionPos);
      this.sectionIds.remove(sectionPos);
      ChunkPos chunkPos = getChunkKeyFromSectionKey(sectionPos);
      ObjectSet<SectionPos> positions = this.sectionsByChunk.get(chunkPos);
      if (positions != null) {
         positions.remove(sectionPos);
         if (positions.isEmpty()) this.sectionsByChunk.remove(chunkPos);
      }
   }

   @VisibleForDebug
   public int count() {
      return this.sectionIds.size();
   }
}
