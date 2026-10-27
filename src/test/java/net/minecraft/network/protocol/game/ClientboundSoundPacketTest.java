package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundSoundPacketTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void preservesSplitSoundOriginBeyondIntAndDoublePrecision() {
        for (Int1088 coordinate : new Int1088[] {Int1088.ZERO, Int1088.fromLong(Long.MIN_VALUE),
                Int1088.fromLong(Long.MAX_VALUE), Int1088.of(-41L, 37L), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
            SectorVec3 position = SectorVec3.fromBlockAndFraction(coordinate, 0.375D, 72.25D,
                    coordinate, 0.625D);
            ClientboundSoundPacket sent = new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_HARP, SoundSource.BLOCKS,
                    position, 1.25F, 0.75F, 918273645L);

            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                sent.write(bytes);
                bytes.readById(Registry.SOUND_EVENT);
                bytes.readEnum(SoundSource.class);
                assertEquals(coordinate, bytes.readInt1088());
                assertEquals(0.375D, bytes.readDouble());
                assertEquals(72.25D, bytes.readDouble());
                assertEquals(coordinate, bytes.readInt1088());
                assertEquals(0.625D, bytes.readDouble());
                bytes.readerIndex(0);
                ClientboundSoundPacket received = new ClientboundSoundPacket(bytes);

                assertEquals(position, received.getExactPosition());
                assertEquals(1.25F, received.getVolume());
                assertEquals(0.75F, received.getPitch());
                assertEquals(918273645L, received.getSeed());
                assertEquals(SoundEvents.NOTE_BLOCK_HARP, received.getSound());
                assertEquals(SoundSource.BLOCKS, received.getSource());
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
                bytes.writeId(Registry.SOUND_EVENT, SoundEvents.NOTE_BLOCK_HARP);
                bytes.writeEnum(SoundSource.BLOCKS);
                if (split) {
                    bytes.writeLong(1L).writeDouble(0.375D).writeDouble(72.25D);
                    bytes.writeLong(-1L).writeDouble(0.625D);
                } else {
                    bytes.writeInt(8).writeInt(578).writeInt(-8);
                }
                bytes.writeFloat(1.25F).writeFloat(0.75F).writeLong(918273645L);
                assertThrows(RuntimeException.class, () -> new ClientboundSoundPacket(bytes));
            } finally {
                bytes.release();
            }
        }
    }
}
