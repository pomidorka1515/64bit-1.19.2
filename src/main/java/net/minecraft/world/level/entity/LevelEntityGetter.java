package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.Int128;
import net.minecraft.world.phys.AABB;

public interface LevelEntityGetter<T extends EntityAccess> {
    @Nullable
    T get(int pId);

    @Nullable
    T get(UUID pUuid);

    Iterable<T> getAll();

    /** Visits entities in exact full-width section bounds. */
    default void getInSections(Int128 minSectionX, Int128 maxSectionX, int minSectionY, int maxSectionY,
            Int128 minSectionZ, Int128 maxSectionZ, Consumer<T> consumer) {
        for (T entity : this.getAll()) consumer.accept(entity);
    }

    <U extends T> void get(EntityTypeTest<T, U> pTest, Consumer<U> pConsumer);

    void get(AABB pBoundingBox, Consumer<T> pConsumer);

    <U extends T> void get(EntityTypeTest<T, U> pTest, AABB pBounds, Consumer<U> pConsumer);
}
