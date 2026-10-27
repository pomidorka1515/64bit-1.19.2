package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundExplodePacketTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preservesSplitExplosionOriginBeyondDoublePrecision() {
        for (Int1088 coordinate : new Int1088[] {Int1088.ZERO, Int1088.fromLong(Long.MIN_VALUE),
                Int1088.fromLong(Long.MAX_VALUE), Int1088.of(-1L, 37L), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.375D, 71.25D, coordinate, 0.625D);
            List<BlockPos> affected = List.of(
                    new BlockPos(coordinate.addSaturated(Int1088.fromLong(-3L)), 70, coordinate.addSaturated(Int1088.fromLong(5L))),
                    new BlockPos(coordinate.addSaturated(Int1088.fromLong(4L)), 73, coordinate.addSaturated(Int1088.fromLong(-2L))),
                    new BlockPos(Int1088.ONE.shiftLeftSaturated(1024), Integer.MAX_VALUE, Int1088.MIN));
            ClientboundExplodePacket sent = new ClientboundExplodePacket(position, 5.0F, affected,
                    new Vec3(0.25D, -0.5D, 0.75D));

            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                sent.write(bytes);
                assertEquals(coordinate, bytes.readInt1088());
                assertEquals(0.375D, bytes.readDouble());
                assertEquals(71.25D, bytes.readDouble());
                assertEquals(coordinate, bytes.readInt1088());
                assertEquals(0.625D, bytes.readDouble());
                bytes.readerIndex(0);
                ClientboundExplodePacket received = new ClientboundExplodePacket(bytes);

                assertEquals(position, received.getExactPosition());
                assertEquals(affected, received.getToBlow());
                assertEquals(5.0F, received.getPower());
                assertEquals(0.25F, received.getKnockbackX());
                assertEquals(-0.5F, received.getKnockbackY());
                assertEquals(0.75F, received.getKnockbackZ());
                assertFalse(bytes.isReadable());
            } finally {
                bytes.release();
            }
        }
    }

    @Test
    void oldDoubleAndLowOnlySplitOriginsAreRejected() {
        for (boolean split : new boolean[] {false, true}) {
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                if (split) {
                    bytes.writeLong(1L).writeDouble(0.375D).writeDouble(71.25D);
                    bytes.writeLong(-1L).writeDouble(0.625D);
                } else {
                    bytes.writeDouble(1.375D).writeDouble(71.25D).writeDouble(-0.375D);
                }
                bytes.writeFloat(5.0F);
                bytes.writeVarInt(0);
                bytes.writeFloat(0.25F).writeFloat(-0.5F).writeFloat(0.75F);
                assertThrows(RuntimeException.class, () -> new ClientboundExplodePacket(bytes));
            } finally {
                bytes.release();
            }
        }
    }
}
