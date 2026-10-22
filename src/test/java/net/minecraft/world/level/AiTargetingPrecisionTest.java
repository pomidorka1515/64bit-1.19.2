package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int128;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AiTargetingPrecisionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nearestTargetUsesExactExplicitOriginAndNotSourceFeet() {
        for (Int128 coordinate : new Int128[] {Int128.fromLong(1L << 60), Int128.fromLong(-(1L << 60)),
                Int128.of(123L, -1L), Int128.of(-123L, 0L), Int128.MIN,
                Int128.MAX.subtract(Int128.fromLong(8L))}) {
            SectorVec3 origin = SectorVec3.fromBlockAndFraction(coordinate, 0.5D, 64.0D, coordinate, 0.5D);
            LivingEntity source = new TestLivingEntity();
            LivingEntity far = new TestLivingEntity();
            LivingEntity near = new TestLivingEntity();
            source.applyExactPosition(origin);
            far.applyExactPosition(origin.add(1.0D, 0.0D, 0.0D));
            near.applyExactPosition(origin.add(0.125D, 0.0D, 0.0D));
            TargetingConditions conditions = TargetingConditions.forNonCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
            EntityGetter getter = new EmptyEntityGetter();
            assertSame(near, getter.getNearestEntity(List.of(far, near), conditions, source, origin));
            assertSame(far, getter.getNearestEntity(List.of(near, far), conditions, source, origin.add(1.0D, 0.0D, 0.0D)));
            assertSame(near, getter.getNearestEntity(List.of(far, near), conditions, null, origin));
        }
    }

    @Test
    void spawnObstructionUsesExactEntityBoxesAndIgnoresSelf() {
        Int128 huge = Int128.of(123L, -1L);
        SectorVec3 position = SectorVec3.fromBlockAndFraction(huge, 0.5D, 64.0D, huge.negate(), 0.5D);
        LivingEntity spawning = new TestLivingEntity();
        LivingEntity blocking = new TestLivingEntity();
        spawning.applyExactPosition(position);
        blocking.applyExactPosition(position.add(0.125D, 0.0D, 0.0D));
        var index = (net.minecraft.world.level.entity.LevelEntityGetter<Entity>) java.lang.reflect.Proxy.newProxyInstance(
                EntityGetter.class.getClassLoader(), new Class<?>[] {net.minecraft.world.level.entity.LevelEntityGetter.class},
                (proxy, method, args) -> method.getName().equals("getAll") ? List.of(spawning, blocking)
                        : java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args));
        CollisionGetter level = (CollisionGetter) java.lang.reflect.Proxy.newProxyInstance(EntityGetter.class.getClassLoader(),
                new Class<?>[] {CollisionGetter.class, EntityGetter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getEntityGetterForSectorQueries")) return index;
                    if (method.getName().equals("getSectorBlockCollisions")) return List.of();
                    return java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args);
                });
        assertFalse(level.isUnobstructed(spawning));
        assertFalse(level.noCollision(spawning));
        blocking.applyExactPosition(position.add(1.0D, 0.0D, 0.0D));
        assertTrue(level.isUnobstructed(spawning));
        assertTrue(level.noCollision(spawning));
    }

    @Test
    void walkMemoryRetainsExactPositionAndFraction() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int128.MIN, 0.375D, 64.0D, Int128.MAX, 0.625D);
        WalkTarget target = new WalkTarget(position, 1.0F, 0);
        assertSame(position, target.getTarget().currentExactPosition());
        assertEquals(position.blockPosition(), target.getTarget().currentBlockPosition());
    }

    private static final class TestLivingEntity extends LivingEntity {
        TestLivingEntity() { super(EntityType.PIG, null); }
        public boolean canBeCollidedWith() { return true; }
        public Iterable<net.minecraft.world.item.ItemStack> getArmorSlots() { return List.of(); }
        public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) { return net.minecraft.world.item.ItemStack.EMPTY; }
        public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {}
        public net.minecraft.world.entity.HumanoidArm getMainArm() { return net.minecraft.world.entity.HumanoidArm.RIGHT; }
    }

    private static final class EmptyEntityGetter implements EntityGetter {
        public List<Entity> getEntities(Entity entity, AABB area, Predicate<? super Entity> predicate) { return List.of(); }
        public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB area, Predicate<? super T> predicate) { return List.of(); }
        public List<? extends Player> players() { return List.of(); }
    }
}
