package net.minecraft.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Random;
import net.minecraft.core.Int1088;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class NetworkInt1088Test {
    @Test
    void positionsRetainFullIdentityAndIntegerHeight() {
        Int1088 wide = Int1088.ONE.shiftLeftSaturated(1024);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int y : new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
                BlockPos block = new BlockPos(wide, y, Int1088.MIN);
                buf.clear();
                buf.writeBlockPos(block);
                assertEquals(block, buf.readBlockPos());
                SectionPos section = SectionPos.of(wide, y, Int1088.MIN);
                buf.clear();
                buf.writeSectionPos(section);
                assertEquals(section, buf.readSectionPos());
            }
            ChunkPos chunk = new ChunkPos(wide, wide.negate());
            buf.clear();
            buf.writeChunkPos(chunk);
            assertEquals(chunk, buf.readChunkPos());
            buf.clear();
            buf.writeBlockPos(new BlockPos(wide, 42, Int1088.MAX));
            for (int size = 0; size < buf.writerIndex(); ++size) {
                FriendlyByteBuf truncated = new FriendlyByteBuf(buf.copy(0, size));
                try {
                    assertThrows(RuntimeException.class, truncated::readBlockPos);
                } finally {
                    truncated.release();
                }
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void rejectsInvalidSectorFractions() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (double fraction : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -0.5D, 1.0D}) {
                buf.clear();
                buf.writeInt1088(Int1088.ZERO).writeDouble(fraction).writeDouble(0.0D);
                buf.writeInt1088(Int1088.ZERO).writeDouble(0.0D);
                assertThrows(DecoderException.class, buf::readSectorVec3);
                buf.clear();
                buf.writeInt1088(Int1088.ZERO).writeDouble(0.0D).writeDouble(0.0D);
                buf.writeInt1088(Int1088.ZERO).writeDouble(fraction);
                assertThrows(DecoderException.class, buf::readSectorVec3);
            }
            for (double y : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                buf.clear();
                buf.writeInt1088(Int1088.ZERO).writeDouble(0.0D).writeDouble(y);
                buf.writeInt1088(Int1088.ZERO).writeDouble(0.0D);
                assertThrows(DecoderException.class, buf::readSectorVec3);
            }
            buf.clear();
            SectorVec3 position = SectorVec3.fromBlockAndFraction(Int1088.MAX, 0.5D, 42.0D, Int1088.MIN, 0.25D);
            buf.writeSectorVec3(position);
            assertEquals(position, buf.readSectorVec3());
        } finally {
            buf.release();
        }
    }
    @Test
    void roundTripsAndUsesCanonicalLimbOrder() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            Int1088[] values = {Int1088.ZERO, Int1088.ONE, Int1088.fromLong(-1),
                    Int1088.fromLong(Long.MIN_VALUE), Int1088.fromLong(Long.MAX_VALUE),
                    Int1088.of(Long.MIN_VALUE, 0), Int1088.of(0, Long.MIN_VALUE, 0),
                    Int1088.of(0, Long.MIN_VALUE), Int1088.of(0, 0, 1),
                    Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX};
            for (Int1088 value : values) checkRoundTrip(buf, value);
            Random random = new Random(1088);
            for (int i = 0; i < 1000; ++i) {
                long[] limbs = new long[1 + random.nextInt(Int1088.LIMBS)];
                for (int j = 0; j < limbs.length; ++j) limbs[j] = random.nextLong();
                checkRoundTrip(buf, Int1088.of(limbs));
            }
            for (long value : new long[] {0, 1, -1}) {
                buf.clear();
                buf.writeInt1088(Int1088.fromLong(value));
                assertEquals(2, buf.readableBytes());
            }
        } finally {
            buf.release();
        }
    }

    private static void checkRoundTrip(FriendlyByteBuf buf, Int1088 value) {
        buf.clear();
        assertSame(buf, buf.writeInt1088(value));
        assertEquals(value.limbCount(), buf.readVarInt());
        for (int i = 0; i < value.limbCount(); ++i) {
            long limb = value.limb(i);
            assertEquals((limb << 1) ^ (limb >> 63), buf.readVarLong());
        }
        assertFalse(buf.isReadable());
        buf.readerIndex(0);
        assertEquals(value, buf.readInt1088());
        assertFalse(buf.isReadable());
    }

    @Test
    void rejectsMalformedAndNonCanonicalEncodings() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int count : new int[] {0, 18, -1}) {
                buf.clear();
                buf.writeVarInt(count);
                assertThrows(DecoderException.class, buf::readInt1088);
            }
            for (long limb : new long[] {0, -1}) {
                buf.clear();
                buf.writeVarInt(2).writeVarLong(limb == 0 ? 0 : 1).writeVarLong(limb == 0 ? 0 : 1);
                assertThrows(DecoderException.class, buf::readInt1088);
            }
            buf.clear();
            buf.writeByte(0x81).writeByte(0).writeByte(0);
            assertThrows(DecoderException.class, buf::readInt1088);
            buf.clear();
            buf.writeByte(1).writeByte(0x80).writeByte(0);
            assertThrows(DecoderException.class, buf::readInt1088);
            buf.clear();
            buf.writeByte(1);
            for (int i = 0; i < 9; ++i) buf.writeByte(0x80);
            buf.writeByte(2);
            assertThrows(DecoderException.class, buf::readInt1088);
            buf.clear();
            buf.writeByte(1);
            for (int i = 0; i < 10; ++i) buf.writeByte(0x80);
            buf.writeByte(0);
            assertThrows(DecoderException.class, buf::readInt1088);

            buf.clear();
            buf.writeInt1088(Int1088.MAX);
            int length = buf.readableBytes();
            for (int size = 0; size < length; ++size) {
                FriendlyByteBuf truncated = new FriendlyByteBuf(buf.copy(0, size));
                try {
                    assertThrows(DecoderException.class, truncated::readInt1088, "size " + size);
                } finally {
                    truncated.release();
                }
            }
        } finally {
            buf.release();
        }
    }
}
