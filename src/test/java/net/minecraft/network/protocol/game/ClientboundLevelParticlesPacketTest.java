package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int128;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundLevelParticlesPacketTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preservesSplitParticleOriginBeyondDoublePrecision() {
        for (Int128 coordinate : new Int128[] {Int128.ZERO, Int128.fromLong(Long.MIN_VALUE),
                Int128.fromLong(Long.MAX_VALUE), Int128.of(17L, -29L), Int128.MIN, Int128.MAX}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.375D, 72.25D, coordinate, 0.625D);
            ClientboundLevelParticlesPacket sent = new ClientboundLevelParticlesPacket(ParticleTypes.FLAME, true,
                    position, 1.25F, 2.5F, 3.75F, 0.5F, 14);

            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                sent.write(bytes);
                bytes.readById(Registry.PARTICLE_TYPE);
                assertTrue(bytes.readBoolean());
                assertEquals(76, bytes.readableBytes());
                assertEquals(coordinate.hi(), bytes.readLong());
                assertEquals(coordinate.lo(), bytes.readLong());
                assertEquals(0.375D, bytes.readDouble());
                assertEquals(72.25D, bytes.readDouble());
                assertEquals(coordinate.hi(), bytes.readLong());
                assertEquals(coordinate.lo(), bytes.readLong());
                assertEquals(0.625D, bytes.readDouble());
                bytes.readerIndex(0);
                ClientboundLevelParticlesPacket received = new ClientboundLevelParticlesPacket(bytes);

                assertEquals(position, received.getExactPosition());
                assertEquals(1.25F, received.getXDist());
                assertEquals(2.5F, received.getYDist());
                assertEquals(3.75F, received.getZDist());
                assertEquals(0.5F, received.getMaxSpeed());
                assertEquals(14, received.getCount());
                assertTrue(received.isOverrideLimiter());
                assertEquals(ParticleTypes.FLAME, received.getParticle());
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
                bytes.writeId(Registry.PARTICLE_TYPE, ParticleTypes.FLAME);
                bytes.writeBoolean(true);
                if (split) {
                    bytes.writeLong(1L).writeDouble(0.375D).writeDouble(72.25D);
                    bytes.writeLong(-1L).writeDouble(0.625D);
                } else {
                    bytes.writeDouble(1.375D).writeDouble(72.25D).writeDouble(-0.375D);
                }
                bytes.writeFloat(1.25F).writeFloat(2.5F).writeFloat(3.75F).writeFloat(0.5F).writeInt(14);
                assertThrows(IndexOutOfBoundsException.class, () -> new ClientboundLevelParticlesPacket(bytes));
            } finally {
                bytes.release();
            }
        }
    }
}
