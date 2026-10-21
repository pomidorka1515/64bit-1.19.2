package net.minecraft.world.level.entity;

import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.Int128;
import net.minecraft.world.phys.AABB;

public class LevelEntityGetterAdapter<T extends EntityAccess> implements LevelEntityGetter<T> {
    private final EntityLookup<T> visibleEntities;
    private final EntitySectionStorage<T> sectionStorage;

    public LevelEntityGetterAdapter(EntityLookup<T> pVisibleEntities, EntitySectionStorage<T> pSectionStorage) {
        this.visibleEntities = pVisibleEntities;
        this.sectionStorage = pSectionStorage;
    }

    @Nullable
    public T get(int pId) {
        return this.visibleEntities.getEntity(pId);
    }

    @Nullable
    public T get(UUID pUuid) {
        return this.visibleEntities.getEntity(pUuid);
    }

    public Iterable<T> getAll() {
        return this.visibleEntities.getAllEntities();
    }

    @Override
    public void getInSections(Int128 minSectionX, Int128 maxSectionX, int minSectionY, int maxSectionY,
            Int128 minSectionZ, Int128 maxSectionZ, Consumer<T> consumer) {
        this.sectionStorage.getEntitiesInSections(minSectionX, maxSectionX, minSectionY, maxSectionY,
                minSectionZ, maxSectionZ, consumer);
    }

    public <U extends T> void get(EntityTypeTest<T, U> pTest, Consumer<U> pConsumer) {
        this.visibleEntities.getEntities(pTest, pConsumer);
    }

    public void get(AABB pBoundingBox, Consumer<T> pConsumer) {
        this.sectionStorage.getEntities(pBoundingBox, pConsumer);
    }

    public <U extends T> void get(EntityTypeTest<T, U> pTest, AABB pBounds, Consumer<U> pConsumer) {
        this.sectionStorage.getEntities(pTest, pBounds, pConsumer);
    }
}
