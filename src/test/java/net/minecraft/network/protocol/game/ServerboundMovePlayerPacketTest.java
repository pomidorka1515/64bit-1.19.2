package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class ServerboundMovePlayerPacketTest {
    @Test
    void passengerInputKeepsLocalVelocitySentinelAndRotations() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ServerboundMovePlayerPacket.PosRot.vehicleInput(-0.25D, 0.75D, 90.0F, -15.0F, true).write(buffer);
            ServerboundMovePlayerPacket.PosRot received = ServerboundMovePlayerPacket.PosRot.read(buffer);
            SectorVec3 input = received.getExactPosition(0.0D);
            assertEquals(Int1088.fromLong(-1), input.blockX());
            assertEquals(0.75D, input.subX());
            assertEquals(Int1088.ZERO, input.blockZ());
            assertEquals(0.75D, input.subZ());
            assertEquals(-999.0D, received.getY(0));
            assertEquals(90.0F, received.getYRot(0));
            assertEquals(-15.0F, received.getXRot(0));
            assertTrue(received.isOnGround());
            assertFalse(buffer.isReadable());
            assertThrows(IllegalArgumentException.class, () ->
                    ServerboundMovePlayerPacket.PosRot.vehicleInput(Double.NaN, 0, 0, 0, false));
        } finally {
            buffer.release();
        }
    }
    @Test
    void stationaryLookAndGroundUpdatesPreserveExactPosition() {
        for (Int1088 coordinate : new Int1088[] {Int1088.ZERO, Int1088.of(-1L, 42L), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.75D, 64.0D, coordinate, 0.25D);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                for (int tick = 0; tick < 100; ++tick) {
                    new ServerboundMovePlayerPacket.Rot(tick, 15.0F, true).write(buffer);
                    var look = ServerboundMovePlayerPacket.Rot.read(buffer);
                    assertFalse(look.hasPosition());
                    assertEquals(position, look.getExactPosition(position));
                    buffer.clear();
                    new ServerboundMovePlayerPacket.StatusOnly(true).write(buffer);
                    var ground = ServerboundMovePlayerPacket.StatusOnly.read(buffer);
                    assertEquals(position, ground.getExactPosition(position));
                    buffer.clear();
                }
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void positionalUpdateUsesPacketTargetInsteadOfStationaryFallback() {
        SectorVec3 current = SectorVec3.fromBlockAndFraction(1L << 60, 0.75D, 64.0D, 0L, 0.25D);
        SectorVec3 target = current.add(0.125D, 0.5D, -0.125D);
        assertEquals(target, new ServerboundMovePlayerPacket.Pos(target, true).getExactPosition(current));
        assertEquals(target, new ServerboundMovePlayerPacket.PosRot(target, 90.0F, 0.0F, true).getExactPosition(current));
        assertEquals(SectorVec3.fromApproximate(1.0D, 64.0D, 2.0D),
                new ServerboundMovePlayerPacket.Pos(SectorVec3.fromBlockAndFraction(1L, 0.0D, 64.0D, 2L, 0.0D), true).getExactPosition(current));
    }

    @Test
    void positionVariantsUseCanonicalFullWidthCoordinates() {
        for (Int1088 coordinate : new Int1088[] {Int1088.ZERO, Int1088.fromLong(Long.MIN_VALUE),
                Int1088.fromLong(Long.MAX_VALUE), Int1088.of(-1L, 42L), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
            SectorVec3 target = SectorVec3.fromBlockAndFraction(coordinate, 0.75D, 64.5D, coordinate, 0.25D);
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                for (boolean rotated : new boolean[] {false, true}) {
                    bytes.clear();
                    ServerboundMovePlayerPacket sent = rotated
                            ? new ServerboundMovePlayerPacket.PosRot(target, 90.0F, -15.0F, true)
                            : new ServerboundMovePlayerPacket.Pos(target, true);
                    sent.write(bytes);
                    assertEquals(coordinate, bytes.readInt1088());
                    assertEquals(0.75D, bytes.readDouble());
                    assertEquals(64.5D, bytes.readDouble());
                    assertEquals(coordinate, bytes.readInt1088());
                    assertEquals(0.25D, bytes.readDouble());
                    bytes.readerIndex(0);
                    ServerboundMovePlayerPacket received = rotated
                            ? ServerboundMovePlayerPacket.PosRot.read(bytes) : ServerboundMovePlayerPacket.Pos.read(bytes);
                    assertEquals(target, received.getExactPosition(SectorVec3.fromApproximate(0, 0, 0)));
                    assertEquals(rotated, received.hasRotation());
                    assertTrue(received.hasExactPosition());
                    assertTrue(received.isOnGround());
                    if (rotated) {
                        assertEquals(90.0F, received.getYRot(0));
                        assertEquals(-15.0F, received.getXRot(0));
                    }
                    assertFalse(bytes.isReadable());
                }
            } finally {
                bytes.release();
            }
        }
    }

    @Test
    void oldDoubleAndLowOnlySplitMovementPayloadsAreRejected() {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (boolean rotated : new boolean[] {false, true}) {
                for (boolean split : new boolean[] {false, true}) {
                    bytes.clear();
                    if (split) {
                        bytes.writeLong(1L).writeDouble(0.25D).writeDouble(64.0D);
                        bytes.writeLong(-1L).writeDouble(0.75D);
                    } else {
                        bytes.writeDouble(1.25D).writeDouble(64.0D).writeDouble(-0.25D);
                    }
                    if (rotated) {
                        bytes.writeFloat(90.0F).writeFloat(15.0F);
                    }
                    bytes.writeBoolean(true);
                    assertThrows(RuntimeException.class, () -> {
                        if (rotated) ServerboundMovePlayerPacket.PosRot.read(bytes);
                        else ServerboundMovePlayerPacket.Pos.read(bytes);
                    });
                }
            }
        } finally {
            bytes.release();
        }
    }
}
