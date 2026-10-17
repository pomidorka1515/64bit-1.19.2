package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class ServerboundMovePlayerPacketTest {
    @Test
    void stationaryLookAndGroundUpdatesPreserveExactPosition() {
        for (long coordinate : new long[] {0L, 1L << 54, -(1L << 54), Long.MAX_VALUE - 2L, Long.MIN_VALUE + 2L}) {
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
        assertNull(new ServerboundMovePlayerPacket.Pos(1.0D, 64.0D, 2.0D, true).getExactPosition(current));
    }
}
