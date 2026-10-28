package net.minecraft.server.commands;

import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameTestCommandCoordinatesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void spawnItemKeepsTheExactAbsoluteVectorCarrier() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        CapturingLevel level = (CapturingLevel) ((sun.misc.Unsafe) field.get(null)).allocateInstance(CapturingLevel.class);
        Field random = net.minecraft.world.level.Level.class.getDeclaredField("random");
        random.setAccessible(true);
        random.set(level, net.minecraft.util.RandomSource.create(1088L));
        for (Int1088 axis : new Int1088[] {Int1088.ZERO, Int1088.parseDecimal("9007199254740993"),
                Int1088.ONE.shiftLeftSaturated(127), Int1088.ONE.shiftLeftSaturated(1024),
                Int1088.MIN, Int1088.MAX}) {
            SectorVec3 target = SectorVec3.fromBlockAndFraction(axis, 0.25D, 64.5D, axis, 0.75D);
            GameTestHelper helper = new GameTestHelper(null) {
                @Override public ServerLevel getLevel() { return level; }
                @Override public Vec3 absoluteVec(Vec3 local) { return target.toExactVec3(); }
            };
            ItemEntity item = helper.spawnItem(Items.STONE, 1, 2, 3);
            assertSame(item, level.added);
            assertEquals(target, item.exactPosition());
            assertEquals(Vec3.ZERO, item.getDeltaMovement());
            assertTrue(item.getItem().is(Items.STONE));
        }
    }

    private static class CapturingLevel extends ServerLevel {
        Entity added;
        private CapturingLevel() { super(null, null, null, null, null, null, null, false, 0L, java.util.List.of(), false); }
        @Override public boolean addFreshEntity(Entity entity) { added = entity; return true; }
    }

}
