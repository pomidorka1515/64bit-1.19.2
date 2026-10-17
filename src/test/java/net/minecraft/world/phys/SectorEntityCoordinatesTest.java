package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.BlockPos;
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
    void lookTargetPacketAndRotationRetainSubBlockOffsetsAtLongEdges() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Entity entity = new TestEntity();
        SectorVec3 origin = SectorVec3.fromBlockAndFraction(Long.MAX_VALUE - 4L, 0.25D, 10.0D,
                Long.MIN_VALUE + 4L, 0.5D);
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
    void portalDestinationRetainsFractionsAtLongEdges() {
        SectorVec3 destination = SectorVec3.fromBlockAndFraction(Long.MAX_VALUE - 2L, 0.5D, 64.0D, Long.MIN_VALUE + 2L, 0.25D);
        net.minecraft.world.level.portal.PortalInfo portal = new net.minecraft.world.level.portal.PortalInfo(destination, Vec3.ZERO, 90.0F, 0.0F);
        assertEquals(destination, portal.exactPos);
        assertEquals(new BlockPos(Long.MAX_VALUE - 2L, 64, Long.MIN_VALUE + 2L), portal.exactPos.blockPosition());
    }

    @Test
    void nonPlayerEntityMovementKeepsExactCoordinatesAndBounds() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Entity entity = new TestEntity();
        assertTrue(entity.hasSectorPosition());
        entity.noPhysics = true;
        long coordinate = 1L << 60;
        SectorVec3 start = SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 10.0D, -coordinate, 0.75D);
        entity.applyExactPosition(start);
        entity.setOldExactPosition(start);
        entity.move(MoverType.SELF, new Vec3(0.125D, 0.5D, -0.125D));
        assertEquals(coordinate, entity.getBlockX());
        assertEquals(-coordinate, entity.getBlockZ());
        assertEquals(0.375D, entity.sectorPosition().subX());
        assertEquals(0.625D, entity.sectorPosition().subZ());
        assertEquals(new Vec3(0.125D, 0.5D, -0.125D), entity.sectorPositionDelta());
        assertNotNull(entity.getBoundingBox().getSectorBounds());
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
        for (long coordinate : new long[] {1L << 54, -(1L << 54), Long.MAX_VALUE - 8L, Long.MIN_VALUE + 8L}) {
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
