package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.world.phys.AABB;

public interface LevelEntityGetter<T extends EntityAccess> {
   @Nullable
   T get(int p_156931_);

   @Nullable
   T get(UUID p_156939_);

   Iterable<T> getAll();

   /** Visits entities in exact long-coordinate section bounds. */
   default void getInSections(long minSectionX, long maxSectionX, int minSectionY, int maxSectionY,
                              long minSectionZ, long maxSectionZ, Consumer<T> consumer) {
      for (T entity : this.getAll()) consumer.accept(entity);
   }

   <U extends T> void get(EntityTypeTest<T, U> p_156935_, Consumer<U> p_156936_);

   void get(AABB p_156937_, Consumer<T> p_156938_);

   <U extends T> void get(EntityTypeTest<T, U> p_156932_, AABB p_156933_, Consumer<U> p_156934_);
}