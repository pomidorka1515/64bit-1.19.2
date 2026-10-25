package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Lists;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ExplosionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void removingTargetsDoesNotInvalidateExplosionIteration() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        TestLevel level = (TestLevel) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(TestLevel.class);
        level.lookup = new EntityLookup<>();
        Field random = Level.class.getDeclaredField("random");
        random.setAccessible(true);
        random.set(level, RandomSource.create());
        RemovingTarget first = new RemovingTarget(level);
        RemovingTarget second = new RemovingTarget(level);
        RemovingTarget third = new RemovingTarget(level);
        level.lookup.add(first);
        level.lookup.add(second);
        level.lookup.add(third);

        // A non-null TNT source matches the crash: null iterator values must not
        // accidentally compare equal to a source-less explosion's null source.
        Explosion explosion = new Explosion(level, new PrimedTnt(EntityType.TNT, level),
                SectorVec3.fromApproximate(0.0D, 64.0D, 0.0D), 0.0F);
        assertDoesNotThrow(explosion::explode);
        assertEquals(1, first.visits);
        assertEquals(1, second.visits);
        assertEquals(1, third.visits);
        assertEquals(0, level.lookup.count());
    }

    private static class RemovingTarget extends PrimedTnt {
        private final TestLevel testLevel;
        private int visits;

        RemovingTarget(TestLevel level) {
            super(EntityType.TNT, level);
            this.testLevel = level;
        }

        @Override
        public boolean ignoreExplosion() {
            ++this.visits;
            // Model registry removals caused by damage and chained explosions.
            for (Entity target : Lists.newArrayList(this.testLevel.lookup.getAllEntities())) {
                this.testLevel.lookup.remove(target);
            }
            return true;
        }
    }

    private static class TestLevel extends ServerLevel {
        private EntityLookup<Entity> lookup;

        private TestLevel() {
            super(null, null, null, null, null, null, null, false, 0L, List.of(), false);
        }

        @Override
        protected LevelEntityGetter<Entity> getEntities() {
            return new LevelEntityGetterAdapter<>(this.lookup, null);
        }

        @Override
        public void gameEvent(Entity source, GameEvent event, BlockPos position) {}
    }
}
