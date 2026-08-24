package net.minecraft.world.level.entity;

import java.util.Comparator;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
   private final ObjectSortedSet<SectionPos> sectionIds = new ObjectAVLTreeSet<>(new Comparator<>() {
	@Override
	public int compare(SectionPos o1, SectionPos o2) {
		if(o1.getY() != o2.getY()) return o1.getY() - o2.getY();
		if(o1.getZ() != o2.getZ()) return (int) (o1.getZ() - o2.getZ());
		if(o1.getX() != o2.getX()) return (int) (o1.getX() - o2.getX());
		return 0;
	}
   });

   public EntitySectionStorage(Class<T> p_156855_, Object2ObjectFunction<ChunkPos, Visibility> p_156856_) {
      this.entityClass = p_156855_;
      this.intialSectionVisibility = p_156856_;
   }

   public void forEachAccessibleNonEmptySection(AABB p_188363_, Consumer<EntitySection<T>> p_188364_) {
      int i = 2;
      long j = SectionPos.posToSectionCoord(p_188363_.minX - 2.0D);
      int k = (int) SectionPos.posToSectionCoord(p_188363_.minY - 4.0D);
      long l = SectionPos.posToSectionCoord(p_188363_.minZ - 2.0D);
      long i1 = SectionPos.posToSectionCoord(p_188363_.maxX + 2.0D);
      int j1 = (int) SectionPos.posToSectionCoord(p_188363_.maxY + 0.0D);
      long k1 = SectionPos.posToSectionCoord(p_188363_.maxZ + 2.0D);

      ObjectBidirectionalIterator<SectionPos> longiterator = sectionIds.iterator();

      while(longiterator.hasNext()) {
         SectionPos k2 = longiterator.next();
         int l2 = k2.y();
         long i3 = k2.z();
         long l3 = k2.x();
         if (l3 >= j && l3 <= i1 && l2 >= k && l2 <= j1 && i3 >= l && i3 <= k1) {
            EntitySection<T> entitysection = this.sections.get(k2);
            if (entitysection != null && !entitysection.isEmpty() && entitysection.getStatus().isAccessible()) {
               p_188364_.accept(entitysection);
            }
         }
      }

   }

   public Stream<SectionPos> getExistingSectionPositionsInChunk(ChunkPos p_156862_) {
      long i = p_156862_.x;
      long j = p_156862_.z;
      ObjectSortedSet<SectionPos> longsortedset = this.getChunkSections(i, j);
      if (longsortedset.isEmpty()) {
         return Stream.empty();
      } else {
         ObjectBidirectionalIterator<SectionPos> oflong = longsortedset.iterator();
         return StreamSupport.stream(Spliterators.spliteratorUnknownSize(oflong, 1301), false).filter(e -> e.x() == i && e.z() == j);
      }
   }

   private ObjectSortedSet<SectionPos> getChunkSections(long p_156859_, long p_156860_) {
//      SectionPos i = SectionPos.of(p_156859_, 0, p_156860_);
//      SectionPos j = SectionPos.of(p_156859_, -1, p_156860_);
      return this.sectionIds/*.subSet(j, i.offset(0, 1, 0))*/;
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

   private EntitySection<T> createSection(SectionPos p_156902_) {
      ChunkPos i = getChunkKeyFromSectionKey(p_156902_);
      Visibility visibility = this.intialSectionVisibility.get(i);
      this.sectionIds.add(p_156902_);
      return new EntitySection<>(this.entityClass, visibility);
   }

   public ObjectSet<ChunkPos> getAllChunksWithExistingSections() {
      ObjectSet<ChunkPos> longset = new ObjectOpenHashSet<>();
      this.sections.keySet().forEach((java.util.function.Consumer<SectionPos>)(p_156886_) -> {
         longset.add(getChunkKeyFromSectionKey(p_156886_));
      });
      return longset;
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

   public void remove(SectionPos p_156898_) {
      this.sections.remove(p_156898_);
      this.sectionIds.remove(p_156898_);
   }

   @VisibleForDebug
   public int count() {
      return this.sectionIds.size();
   }
}
