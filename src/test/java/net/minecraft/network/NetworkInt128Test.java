package net.minecraft.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class NetworkInt128Test {
    private static final Int128[] COORDINATES = {Int128.ZERO, Int128.fromLong(Long.MIN_VALUE),
            Int128.fromLong(Long.MAX_VALUE), Int128.of(1L, -1L), Int128.of(-2L, 1L), Int128.MIN, Int128.MAX};

    @Test
    void int128UsesExactlyHighThenLowLongs() {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (Int128 coordinate : COORDINATES) {
                bytes.clear();
                bytes.writeInt128(coordinate);
                assertEquals(16, bytes.readableBytes());
                assertEquals(coordinate.hi(), bytes.readLong());
                assertEquals(coordinate.lo(), bytes.readLong());
                bytes.readerIndex(0);
                assertEquals(coordinate, bytes.readInt128());
                assertFalse(bytes.isReadable());
                bytes.clear();
                bytes.writeInt128(coordinate.hi(), coordinate.lo());
                assertEquals(coordinate, bytes.readInt128());
            }
        } finally {
            bytes.release();
        }
    }

    @Test
    void coordinatePrimitivesRetainBothHalvesAndIntegerHeight() {
        FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (Int128 x : COORDINATES) {
                for (Int128 z : COORDINATES) {
                    for (int y : new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
                        BlockPos block = new BlockPos(x, y, z);
                        bytes.clear();
                        bytes.writeBlockPos(block);
                        assertPositionWords(bytes, x, y, z);
                        assertEquals(block, bytes.readBlockPos());
                        assertFalse(bytes.isReadable());
                        SectionPos section = SectionPos.of(x, y, z);
                        bytes.clear();
                        bytes.writeSectionPos(section);
                        assertPositionWords(bytes, section.x(), y, section.z());
                        assertEquals(section, bytes.readSectionPos());
                        assertFalse(bytes.isReadable());
                    }
                    // ChunkPos clamps to its derived block-addressable domain.
                    ChunkPos chunk = new ChunkPos(x, z);
                    bytes.clear();
                    bytes.writeChunkPos(chunk);
                    assertEquals(32, bytes.readableBytes());
                    assertEquals(chunk.xHi(), bytes.readLong());
                    assertEquals(chunk.xLo(), bytes.readLong());
                    assertEquals(chunk.zHi(), bytes.readLong());
                    assertEquals(chunk.zLo(), bytes.readLong());
                    bytes.readerIndex(0);
                    assertEquals(chunk, bytes.readChunkPos());
                    assertFalse(bytes.isReadable());
                }
            }
        } finally {
            bytes.release();
        }
    }

    private static void assertPositionWords(FriendlyByteBuf bytes, Int128 x, int y, Int128 z) {
        assertEquals(36, bytes.readableBytes());
        assertEquals(x.hi(), bytes.readLong());
        assertEquals(x.lo(), bytes.readLong());
        assertEquals(y, bytes.readInt());
        assertEquals(z.hi(), bytes.readLong());
        assertEquals(z.lo(), bytes.readLong());
        bytes.readerIndex(0);
    }

    @Test
    void truncatedAndOldCoordinateFormsHaveNoDecodeFallback() {
        assertTruncated(16, FriendlyByteBuf::readInt128);
        assertTruncated(36, FriendlyByteBuf::readBlockPos);
        assertTruncated(32, FriendlyByteBuf::readChunkPos);
        assertTruncated(36, FriendlyByteBuf::readSectionPos);
    }

    private static void assertTruncated(int width, Function<FriendlyByteBuf, ?> reader) {
        // Includes packed-long, low-only and old three-long coordinate forms.
        for (int size = 0; size < width; ++size) {
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                bytes.writeZero(size);
                assertThrows(IndexOutOfBoundsException.class, () -> reader.apply(bytes));
            } finally {
                bytes.release();
            }
        }
    }
}
