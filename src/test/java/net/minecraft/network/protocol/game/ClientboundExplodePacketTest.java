package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
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
        for (Int128 coordinate : new Int128[] {Int128.ZERO, Int128.fromLong(Long.MIN_VALUE),
                Int128.fromLong(Long.MAX_VALUE), Int128.of(37L, -1L), Int128.MIN, Int128.MAX}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.375D, 71.25D, coordinate, 0.625D);
            List<BlockPos> affected = List.of(
                    new BlockPos(coordinate.addSaturated(Int128.fromLong(-3L)), 70, coordinate.addSaturated(Int128.fromLong(5L))),
                    new BlockPos(coordinate.addSaturated(Int128.fromLong(4L)), 73, coordinate.addSaturated(Int128.fromLong(-2L))));
            ClientboundExplodePacket sent = new ClientboundExplodePacket(position, 5.0F, affected,
                    new Vec3(0.25D, -0.5D, 0.75D));

            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                sent.write(bytes);
                assertEquals(79, bytes.readableBytes());
                assertEquals(coordinate.hi(), bytes.readLong());
                assertEquals(coordinate.lo(), bytes.readLong());
                assertEquals(0.375D, bytes.readDouble());
                assertEquals(71.25D, bytes.readDouble());
                assertEquals(coordinate.hi(), bytes.readLong());
                assertEquals(coordinate.lo(), bytes.readLong());
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
                assertThrows(IndexOutOfBoundsException.class, () -> new ClientboundExplodePacket(bytes));
            } finally {
                bytes.release();
            }
        }
    }
}
