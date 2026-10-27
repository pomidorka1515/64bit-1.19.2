package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import org.junit.jupiter.api.Test;

class SectorEntityCoordinatesTest {
    @Test
    void entityQueryBridgeKeepsFullWidthBoundsAndLocalDimensions() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Entity entity = new TestEntity();
        for (Int1088 coordinate : new Int1088[] {Int1088.of(7L, 1234L), Int1088.of(7L, -1234L)}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 10.0D,
                    coordinate, 0.75D);
            entity.applyExactPosition(position);
            AABB bridge = entity.exactBoundingBox();
            assertEquals(entity.getSectorBoundingBox(), bridge.getSectorBounds());
            assertEquals(entity.getBbWidth(), bridge.getXsize(), 1.0E-6D);
            assertEquals(entity.getBbHeight(), bridge.getYsize(), 1.0E-6D);
            assertTrue(bridge.inflate(0.5D).contains(position.toExactVec3()));
            assertFalse(bridge.intersects(AABB.fromSectorBounds(
                    entity.getSectorBoundingBox().move(10.0D, 0.0D, 0.0D))));
        }
    }

    @Test
    void auditedLossyEntityEntryPointsAreRemoved() {
        assertThrows(NoSuchMethodException.class, () -> Entity.class.getMethod("getX"));
        assertThrows(NoSuchMethodException.class, () -> Entity.class.getMethod("getZ"));
        assertThrows(NoSuchMethodException.class, () -> Entity.class.getMethod("position"));
        assertThrows(NoSuchMethodException.class, () -> Entity.class.getMethod("getBoundingBox"));
        assertThrows(NoSuchMethodException.class, () -> Entity.class.getMethod("getEyePosition"));
        assertThrows(NoSuchMethodException.class, () -> Entity.class.getMethod("setPosRaw", double.class, double.class, double.class));
        assertThrows(NoSuchMethodException.class, () -> SectorVec3.class.getMethod("toApproximateVec3"));
    }

    @Test
    void lookTargetPacketAndRotationRetainSubBlockOffsetsAtFullRangeEdges() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Entity entity = new TestEntity();
        SectorVec3 origin = SectorVec3.fromBlockAndFraction(Int1088.MAX.subtract(Int1088.fromLong(4L)), 0.25D, 10.0D,
                Int1088.MIN.add(Int1088.fromLong(4L)), 0.5D);
        entity.applyExactPosition(origin);
        SectorVec3 target = origin.add(0.5D, 0.0D, 0.0D);
        net.minecraft.commands.arguments.EntityAnchorArgument.Anchor anchor =
                net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.FEET;
        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer());
        try {
            new net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket(anchor, target).write(buffer);
            var decoded = new net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket(buffer);
            assertEquals(target, decoded.getExactPosition(null));
            assertEquals(0, buffer.readableBytes());
            entity.lookAt(decoded.getFromAnchor(), decoded.getExactPosition(null));
            assertEquals(-90.0F, entity.getYRot(), 0.001F);
            assertEquals(0.0F, entity.getXRot(), 0.001F);
        } finally {
            buffer.release();
        }
    }

    @Test
    void exactAndLegacyDistanceTargetsUseSplitEntityPosition() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Entity entity = new TestEntity();
        long coordinate = 1L << 60;
        SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 10.0D, coordinate, 0.5D);
        entity.applyExactPosition(position);
        assertEquals(0.3125D, entity.distanceToSqr((double) coordinate, 10.0D, (double) coordinate));
        assertEquals(0.3125D, entity.distanceToSqr(new Vec3(coordinate, 10.0D, coordinate)));
        assertEquals(0.015625D, entity.distanceToSqr(position.add(0.125D, 0.0D, 0.0D)));
    }

    @Test
    void portalDestinationRetainsFractionsAtFullRangeEdges() {
        Int1088 x = Int1088.MAX.subtract(Int1088.fromLong(2L));
        Int1088 z = Int1088.MIN.add(Int1088.fromLong(2L));
        SectorVec3 destination = SectorVec3.fromBlockAndFraction(x, 0.5D, 64.0D, z, 0.25D);
        net.minecraft.world.level.portal.PortalInfo portal = new net.minecraft.world.level.portal.PortalInfo(destination, Vec3.ZERO, 90.0F, 0.0F);
        assertEquals(destination, portal.exactPos);
        assertEquals(new BlockPos(x, 64, z), portal.exactPos.blockPosition());
        assertEquals(destination, ExactVec3.position(portal.pos));
        assertEquals(destination, new net.minecraft.world.level.portal.PortalInfo(portal.pos,
                Vec3.ZERO, 0.0F, 0.0F).exactPos);
    }

    @Test
    void eyeInterpolationAndHitDistancesRetainFullWidthFractions() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Entity entity = new TestEntity();
        for (Int1088 coordinate : new Int1088[] {Int1088.of(7L, 1234L), Int1088.of(7L, -1234L)}) {
            SectorVec3 start = SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 10.0D,
                    coordinate, 0.75D);
            entity.applyExactPosition(start.add(0.5D, 2.0D, -0.5D));
            entity.setOldExactPosition(start);
            assertEquals(start.add(0.25D, 1.0D + entity.getEyeHeight(), -0.25D),
                    entity.exactEyePosition(0.5F));
            EntityHitResult hit = new EntityHitResult(entity);
            assertEquals(entity.exactPosition(), ExactVec3.position(hit.getLocation()));
            assertEquals(0.0D, hit.distanceTo(entity));
            assertEquals(0.0625D, new EntityHitResult(entity,
                    entity.exactPosition().add(0.25D, 0.0D, 0.0D)).distanceTo(entity));
            assertEquals(entity.exactPosition().add(0.0D, 0.5D, 0.0D),
                    new net.minecraft.world.level.gameevent.EntityPositionSource(entity, 0.5F)
                            .exactPosition(null).orElseThrow());
        }
    }

    @Test
    void nonPlayerEntityMovementKeepsExactCoordinatesAndBounds() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Entity entity = new TestEntity();
        assertTrue(entity.hasSectorPosition());
        entity.noPhysics = true;
        Int1088 coordinate = Int1088.ONE.shiftLeftSaturated(1024).subtractSaturated(Int1088.ONE);
        SectorVec3 start = SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 10.0D, coordinate.negate(), 0.75D);
        entity.applyExactPosition(start);
        entity.setOldExactPosition(start);
        entity.move(MoverType.SELF, new Vec3(0.125D, 0.5D, -0.125D));
        assertEquals(coordinate, entity.getBlockX());
        assertEquals(coordinate.negate(), entity.getBlockZ());
        assertEquals(0.375D, entity.sectorPosition().subX());
        assertEquals(0.625D, entity.sectorPosition().subZ());
        assertEquals(new Vec3(0.125D, 0.5D, -0.125D), entity.sectorPositionDelta());
        assertNotNull(entity.exactBoundingBox().getSectorBounds());
        assertEquals(0.3125D, entity.interpolatedExactPosition(0.5F).subX());
    }

    private static final class TestEntity extends Entity {
        TestEntity() {
            super(EntityType.PIG, null);
        }
        protected void defineSynchedData() {}
        protected void readAdditionalSaveData(CompoundTag tag) {}
        protected void addAdditionalSaveData(CompoundTag tag) {}
        public Packet<?> getAddEntityPacket() {
            return null;
        }
    }

    @Test
    void blockTargetDistancesKeepFractionsBeyondDoublePrecision() {
        for (Int1088 coordinate : new Int1088[] {Int1088.fromLong(1L << 54), Int1088.of(-1L, 5L),
                Int1088.MAX.subtract(Int1088.fromLong(8L)), Int1088.MIN.add(Int1088.fromLong(8L))}) {
            BlockPos target = new BlockPos(coordinate, 20, coordinate);
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.75D, 20.5D, coordinate, 0.5D);
            assertEquals(0.0625D, target.distToCenterSqr(position), 1.0E-12D);
            assertFalse(target.closerToCenterThan(position, 0.25D));
            assertTrue(target.closerToCenterThan(position, 0.26D));
            assertEquals(1.5625D, target.distToCenterSqr(position.add(1.0D, 0.0D, 0.0D)), 1.0E-12D);
        }
    }

    @Test
    void nearbyBlockTargetsDoNotCollapseOntoSameDouble() {
        long coordinate = 1L << 60;
        SectorVec3 entity = SectorVec3.fromBlockAndFraction(coordinate, 0.5D, 10.5D, -coordinate, 0.5D);
        assertTrue(new BlockPos(coordinate, 10, -coordinate).closerToCenterThan(entity, 0.1D));
        assertFalse(new BlockPos(coordinate + 1L, 10, -coordinate).closerToCenterThan(entity, 0.1D));
        assertEquals(1.0D, new BlockPos(coordinate, 10, -coordinate).distSqr(new BlockPos(coordinate + 1L, 10, -coordinate)));
    }
}
