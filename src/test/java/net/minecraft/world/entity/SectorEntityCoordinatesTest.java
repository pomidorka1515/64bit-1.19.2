package net.minecraft.world.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.ExactVec3;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SectorEntityCoordinatesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void targetAdaptersRetainExactCarriersRatherThanTheirDoubleMirrors() {
        DragonChargePlayerPhase charge = new DragonChargePlayerPhase(null);
        CapturingJump jump = new CapturingJump();
        for (Int1088 coordinate : coordinates()) {
            SectorVec3 target = position(coordinate);
            WalkTarget walk = new WalkTarget(target.toExactVec3(), 0.5F, 2);
            charge.setTarget(target.toExactVec3());
            jump.accept(target.toExactVec3());
            assertAll(
                    () -> assertEquals(target, walk.getTarget().currentExactPosition()),
                    () -> assertEquals(target, charge.getExactFlyTargetLocation()),
                    () -> assertEquals(target, ExactVec3.position(charge.getFlyTargetLocation())),
                    () -> assertEquals(target, jump.target));
        }
        Vec3 ordinary = new Vec3(-2.75D, 64.5D, 3.125D);
        assertEquals(SectorVec3.fromApproximate(ordinary),
                new WalkTarget(ordinary, 1.0F, 0).getTarget().currentExactPosition());
        charge.setTarget(ordinary);
        assertEquals(SectorVec3.fromApproximate(ordinary), charge.getExactFlyTargetLocation());
    }

    @Test
    void concreteVehicleProjectileItemAndHangingConstructorsRetainWideAnchors() throws Exception {
        Level level = EntityLoadingTestLevel.create();
        java.lang.reflect.Field random = Level.class.getDeclaredField("random");
        random.setAccessible(true);
        random.set(level, net.minecraft.util.RandomSource.create(1088L));
        for (Int1088 coordinate : coordinates()) {
            SectorVec3 position = position(coordinate);
            Entity[] entities = {
                    new net.minecraft.world.entity.vehicle.Boat(level, position),
                    new net.minecraft.world.entity.item.ItemEntity(level, position,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)),
                    new net.minecraft.world.entity.item.PrimedTnt(level, position, null),
                    new net.minecraft.world.entity.projectile.FireworkRocketEntity(level, position,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET))
            };
            for (Entity entity : entities) {
                assertEquals(position, entity.exactPosition(), entity.getClass().getSimpleName());
            }
            for (net.minecraft.world.entity.vehicle.AbstractMinecart.Type type
                    : net.minecraft.world.entity.vehicle.AbstractMinecart.Type.values()) {
                Entity cart = net.minecraft.world.entity.vehicle.AbstractMinecart.createMinecart(level, position, type);
                assertEquals(position, cart.exactPosition(), type.name());
                assertEquals(position, cart.oldExactPosition(), type.name());
            }
            net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(
                    level, position.blockPosition(), net.minecraft.core.Direction.NORTH);
            assertEquals(position.blockPosition(), frame.getPos());
            assertEquals(position.blockX(), frame.exactPosition().blockX());
        }
    }

    @Test
    void movementInterpolationBoundsAndDistanceCrossEveryLimbSeam() {
        TestEntity entity = new TestEntity(null);
        TestEntity other = new TestEntity(null);
        entity.noPhysics = true;
        for (int bit = 64; bit <= 1024; bit += 64) {
            for (int sign : new int[] {-1, 1}) {
                BigInteger base = BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign));
                SectorVec3 start = position(value(base.subtract(BigInteger.ONE)));
                entity.moveTo(start, 30.0F, -10.0F);
                entity.move(MoverType.SELF, new Vec3(1.5D, 0.5D, -1.5D));
                assertEquals(value(base.add(BigInteger.ONE)), entity.getBlockX());
                assertEquals(0.25D, entity.exactPosition().subX());
                assertEquals(new Vec3(1.5D, 0.5D, -1.5D), entity.sectorPositionDelta());
                assertEquals(start.add(0.75D, 0.25D, -0.75D), entity.interpolatedExactPosition(0.5F));
                assertEquals(entity.exactPosition().blockPosition(), entity.blockPosition());
                assertEquals(new net.minecraft.world.level.ChunkPos(entity.blockPosition()), entity.chunkPosition());
                assertTrue(entity.getSectorBoundingBox().contains(entity.exactPosition().add(0.0D, 0.1D, 0.0D)));
                other.applyExactPosition(entity.exactPosition().add(0.25D, 0.5D, -0.125D));
                assertEquals(0.328125D, entity.distanceToSqr(other));
                entity.absMoveTo(start, 450.0F, 100.0F);
                assertEquals(start, entity.exactPosition());
                assertEquals(start, entity.oldExactPosition());
                assertEquals(90.0F, entity.getYRot());
                assertEquals(90.0F, entity.getXRot());
            }
        }
    }

    @Test
    void ridingTeleportAndDismountKeepWidePositions() {
        Level level = EntityLoadingTestLevel.create();
        TestEntity vehicle = new TestEntity(level);
        TestEntity passenger = new TestEntity(level);
        assertTrue(passenger.startRiding(vehicle, true));
        for (Int1088 coordinate : coordinates()) {
            SectorVec3 target = position(coordinate);
            vehicle.teleportTo(target);
            assertEquals(target, vehicle.exactPosition());
            assertEquals(target.withY(target.y() + vehicle.getPassengersRidingOffset()
                    + passenger.getMyRidingOffset()), passenger.exactPosition());
        }
        passenger.stopRiding();
        SectorVec3 destination = position(Int1088.ONE.shiftLeftSaturated(1024)).add(0.5D, 1.0D, -0.5D);
        passenger.dismountToExact(destination);
        assertEquals(destination, passenger.exactPosition());
        assertEquals(destination, passenger.oldExactPosition());
        assertFalse(passenger.isPassenger());
    }

    @Test
    void movementSaturatesOutwardAndStillAllowsInwardMovement() {
        TestEntity entity = new TestEntity(null);
        entity.noPhysics = true;
        entity.moveTo(position(Int1088.MAX), 0.0F, 0.0F);
        entity.move(MoverType.SELF, new Vec3(2.0D, 0.0D, -2.0D));
        assertEquals(Int1088.MAX, entity.getBlockX());
        assertEquals(Math.nextDown(1.0D), entity.exactPosition().subX());
        assertEquals(Int1088.MIN, entity.getBlockZ());
        assertEquals(0.0D, entity.exactPosition().subZ());
        entity.move(MoverType.SELF, new Vec3(-1.0D, 0.0D, 1.0D));
        assertEquals(Int1088.MAX.subtractSaturated(Int1088.ONE), entity.getBlockX());
        assertEquals(Int1088.MIN.addSaturated(Int1088.ONE), entity.getBlockZ());
    }

    @Test
    void portalScalingMatchesIndependentDecimalOracle() throws Exception {
        Method scale = Entity.class.getDeclaredMethod("scalePortalPosition", SectorVec3.class, double.class);
        scale.setAccessible(true);
        for (Int1088 coordinate : coordinates()) {
            for (double fraction : new double[] {0.0D, 0.125D, 0.75D}) {
                SectorVec3 origin = SectorVec3.fromBlockAndFraction(coordinate, fraction, 72.5D,
                        coordinate.not(), 1.0D - fraction - 0.125D);
                for (double factor : new double[] {0.125D, 1.0D, 8.0D}) {
                    SectorVec3 actual = (SectorVec3) scale.invoke(null, origin, factor);
                    assertAxis(origin.blockX(), origin.subX(), factor, actual.blockX(), actual.subX());
                    assertAxis(origin.blockZ(), origin.subZ(), factor, actual.blockZ(), actual.subZ());
                    assertEquals(origin.y(), actual.y());
                }
            }
        }
    }

    private static void assertAxis(Int1088 block, double fraction, double factor,
            Int1088 actualBlock, double actualFraction) {
        BigDecimal scaled = new BigDecimal(new BigInteger(block.toDecimalString()))
                .add(new BigDecimal(fraction)).multiply(new BigDecimal(factor));
        BigInteger floor = scaled.setScale(0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
        BigInteger min = BigInteger.ONE.shiftLeft(1087).negate();
        BigInteger max = BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE);
        if (floor.compareTo(min) < 0) {
            assertEquals(Int1088.MIN, actualBlock);
            assertEquals(0.0D, actualFraction);
        } else if (floor.compareTo(max) > 0) {
            assertEquals(Int1088.MAX, actualBlock);
            assertEquals(Math.nextDown(1.0D), actualFraction);
        } else {
            assertEquals(value(floor), actualBlock);
            assertEquals(scaled.subtract(new BigDecimal(floor)).doubleValue(), actualFraction);
        }
    }

    static Int1088[] coordinates() {
        java.util.List<Int1088> values = new java.util.ArrayList<>();
        values.add(Int1088.ZERO);
        values.add(Int1088.ONE);
        values.add(Int1088.fromLong(-1L));
        for (int bit : new int[] {53, 63, 127, 1024, 1084}) {
            BigInteger power = BigInteger.ONE.shiftLeft(bit);
            for (int sign : new int[] {-1, 1}) {
                for (int offset = -1; offset <= 1; offset++) {
                    values.add(value(power.multiply(BigInteger.valueOf(sign)).add(BigInteger.valueOf(offset))));
                }
            }
        }
        for (int bit = 64; bit <= 1024; bit += 64) {
            values.add(value(BigInteger.ONE.shiftLeft(bit).add(BigInteger.valueOf(7L))));
        }
        values.add(Int1088.fromDoubleFloorSaturated(Double.MAX_VALUE));
        values.add(Int1088.MIN);
        values.add(Int1088.MAX);
        return values.toArray(Int1088[]::new);
    }

    static SectorVec3 position(Int1088 coordinate) {
        return SectorVec3.fromBlockAndFraction(coordinate, 0.75D, 64.5D, coordinate.not(), 0.25D);
    }

    private static Int1088 value(BigInteger value) {
        return Int1088.parseDecimal(value.toString());
    }

    private static final class TestEntity extends Entity {
        TestEntity(Level level) { super(EntityType.PIG, level); }
        protected void defineSynchedData() {}
        protected void readAdditionalSaveData(CompoundTag tag) {}
        protected void addAdditionalSaveData(CompoundTag tag) {}
        public Packet<?> getAddEntityPacket() { return null; }
    }

    private static final class CapturingJump extends net.minecraft.world.entity.ai.behavior.LongJumpToRandomPos<Mob> {
        private SectorVec3 target;

        CapturingJump() {
            super(net.minecraft.util.valueproviders.UniformInt.of(1, 2), 1, 1, 1.0F, mob -> null);
        }

        void accept(Vec3 target) {
            this.calculateOptimalJumpVector(null, target);
        }

        @Override
        protected Vec3 calculateOptimalJumpVector(Mob mob, SectorVec3 target) {
            this.target = target;
            return Vec3.ZERO;
        }
    }
}
