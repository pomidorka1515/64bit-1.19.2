package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundCustomSoundPacketTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preservesSplitCustomSoundOriginBeyondIntAndDoublePrecision() {
        for (Int1088 coordinate : new Int1088[] {Int1088.ZERO, Int1088.fromLong(Long.MIN_VALUE),
                Int1088.fromLong(Long.MAX_VALUE), Int1088.of(-23L, 19L), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.125D, 64.5D,
                    coordinate, 0.875D);
            ClientboundCustomSoundPacket sent = new ClientboundCustomSoundPacket(new ResourceLocation("test", "far_sound"),
                    SoundSource.AMBIENT, position, 0.5F, 1.5F, 192837465L);

            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                sent.write(bytes);
                bytes.readResourceLocation();
                bytes.readEnum(SoundSource.class);
                assertEquals(coordinate, bytes.readInt1088());
                assertEquals(0.125D, bytes.readDouble());
                assertEquals(64.5D, bytes.readDouble());
                assertEquals(coordinate, bytes.readInt1088());
                assertEquals(0.875D, bytes.readDouble());
                bytes.readerIndex(0);
                ClientboundCustomSoundPacket received = new ClientboundCustomSoundPacket(bytes);

                assertEquals(position, received.getExactPosition());
                assertEquals(new ResourceLocation("test", "far_sound"), received.getName());
                assertEquals(0.5F, received.getVolume());
                assertEquals(1.5F, received.getPitch());
                assertEquals(192837465L, received.getSeed());
                assertEquals(SoundSource.AMBIENT, received.getSource());
                assertFalse(bytes.isReadable());
            } finally {
                bytes.release();
            }
        }
    }

    @Test
    void oldFixedPointAndLowOnlySplitOriginsAreRejected() {
        for (boolean split : new boolean[] {false, true}) {
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                bytes.writeResourceLocation(new ResourceLocation("test", "far_sound"));
                bytes.writeEnum(SoundSource.AMBIENT);
                if (split) {
                    bytes.writeLong(1L).writeDouble(0.125D).writeDouble(64.5D);
                    bytes.writeLong(-1L).writeDouble(0.875D);
                } else {
                    bytes.writeInt(8).writeInt(516).writeInt(-8);
                }
                bytes.writeFloat(0.5F).writeFloat(1.5F).writeLong(192837465L);
                assertThrows(RuntimeException.class, () -> new ClientboundCustomSoundPacket(bytes));
            } finally {
                bytes.release();
            }
        }
    }
}
