package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntitySelectorCoordinateTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void selfSelectorsUseExactOriginsBoxesAndLocalDistances() throws Exception {
        for (Int1088 axis : axes()) {
            TestEntity entity = entity(SectorVec3.fromBlockAndFraction(axis, 0.25D, 64, axis, 0.75D));
            CommandSourceStack source = source(entity);
            assertEquals(List.of(entity), parse("@s[x=" + axis + ",z=" + axis + ",dx=1,dy=1,dz=1,distance=..1]").findEntities(source));
            assertTrue(parse("@s[x=" + axis + ",y=100,z=" + axis + ",dx=-2,dy=1,dz=-2]").findEntities(source).isEmpty());
            assertEquals(List.of(entity), parse("@s[distance=0]").findEntities(source));
            assertTrue(parse("@s[distance=1..]").findEntities(source).isEmpty());
            assertEquals(entity.exactPosition(), net.minecraft.world.phys.ExactVec3.position(
                    net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET.apply(entity)));
            assertEquals(entity.exactPosition().add(0, entity.getEyeHeight(), 0),
                    net.minecraft.world.phys.ExactVec3.position(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES.apply(source)));
        }
    }

    @Test
    void distantSelectorRangesDoNotUseSignedLongSentinelsOrOverflowedSquares() throws Exception {
        for (int bit : new int[] {127, 512, 1023}) {
            TestEntity entity = entity(SectorVec3.fromBlockAndFraction(Int1088.ONE.shiftLeftSaturated(bit), 0, 64, Int1088.ZERO, 0));
            String lower = BigInteger.ONE.shiftLeft(bit - 1).toString();
            assertEquals(List.of(entity), parse("@s[x=0,z=0,distance=" + lower + "..]").findEntities(source(entity)));
            assertTrue(parse("@s[x=0,z=0,distance=.." + lower + "]").findEntities(source(entity)).isEmpty());
        }
    }

    @Test
    void nearestAndFurthestSortByWideDifferencesRatherThanLongSentinels() throws Exception {
        var sort = EntitySelector.class.getDeclaredMethod("sortAndLimit", Vec3.class, List.class);
        sort.setAccessible(true);
        var origin = EntitySelector.class.getDeclaredField("exactOriginForSorting");
        origin.setAccessible(true);
        for (int bit : new int[] {127, 512, 1023, 1025, 1086}) {
            TestEntity near = entity(SectorVec3.fromBlockAndFraction(Int1088.ONE.shiftLeftSaturated(bit - 1), 0, 64, Int1088.ZERO, 0));
            TestEntity far = entity(SectorVec3.fromBlockAndFraction(Int1088.ONE.shiftLeftSaturated(bit), 0, 64, Int1088.ZERO, 0));
            for (String order : new String[] {"nearest", "furthest"}) {
                EntitySelector selector = parse("@e[sort=" + order + ",limit=1]");
                origin.set(selector, SectorVec3.fromBlockAndFraction(Int1088.ZERO, 0, 64, Int1088.ZERO, 0));
                List<Entity> candidates = new ArrayList<>(order.equals("nearest") ? List.of(far, near) : List.of(near, far));
                assertEquals(List.of(order.equals("nearest") ? near : far), sort.invoke(selector, Vec3.ZERO, candidates));
            }
        }
    }

    private static Int1088[] axes() {
        return new Int1088[] {Int1088.ZERO, Int1088.fromLong(-1), Int1088.parseDecimal("9007199254740993"),
                Int1088.ONE.shiftLeftSaturated(127), Int1088.ONE.shiftLeftSaturated(1024),
                Int1088.ONE.shiftLeftSaturated(1024).negate(), Int1088.MIN, Int1088.MAX};
    }

    private static EntitySelector parse(String text) throws Exception {
        return new EntitySelectorParser(new StringReader(text)).parse();
    }

    private static TestEntity entity(SectorVec3 position) {
        TestEntity entity = new TestEntity();
        entity.moveTo(position, 0, 0);
        return entity;
    }

    private static CommandSourceStack source(Entity entity) {
        return new CommandSourceStack(null, Vec3.ZERO, Vec2.ZERO, null, 2, "test", Component.literal("test"), null, entity)
                .withExactPosition(entity.exactPosition());
    }

    private static class TestEntity extends Entity {
        TestEntity() { super(EntityType.PIG, null); }
        protected void defineSynchedData() { }
        protected void readAdditionalSaveData(CompoundTag tag) { }
        protected void addAdditionalSaveData(CompoundTag tag) { }
        public Packet<?> getAddEntityPacket() { throw new UnsupportedOperationException(); }
    }

    @Test
    void wideSelectorsUseFiniteMirrorsAndRejectInvalidLiterals() {
        EntitySelectorParser parser = new EntitySelectorParser(new StringReader(""));
        String wide = BigInteger.ONE.shiftLeft(1024).add(BigInteger.ONE).toString();
        parser.setExactX(wide);
        parser.setExactZ("-" + wide);
        assertTrue(Double.isFinite(parser.getX()));
        assertTrue(Double.isFinite(parser.getZ()));
        assertNotNull(parser.getSelector());
        assertThrows(IllegalArgumentException.class, () -> parser.setExactX("NaN"));
        assertThrows(IllegalArgumentException.class, () -> parser.setExactZ(BigInteger.ONE.shiftLeft(1087).toString()));
    }
}
