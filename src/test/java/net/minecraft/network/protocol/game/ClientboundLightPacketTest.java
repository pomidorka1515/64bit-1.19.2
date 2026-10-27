package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.junit.jupiter.api.Test;

class ClientboundLightPacketTest {
    static BitSet mask(int... indices) {
        BitSet mask = new BitSet();
        for (int index : indices) mask.set(index);
        return mask;
    }

    static byte[] lightBytes(int value) {
        byte[] bytes = new byte[2048];
        java.util.Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    static void writeLight(FriendlyByteBuf buf) {
        buf.writeBoolean(true);
        buf.writeBitSet(mask(0, 17));
        buf.writeBitSet(mask(1, 16));
        buf.writeBitSet(mask(2));
        buf.writeBitSet(mask(3));
        buf.writeVarInt(2);
        buf.writeByteArray(lightBytes(1));
        buf.writeByteArray(lightBytes(2));
        buf.writeVarInt(2);
        buf.writeByteArray(lightBytes(3));
        buf.writeByteArray(lightBytes(4));
    }

    static void assertLight(ClientboundLightUpdatePacketData data) {
        assertTrue(data.getTrustEdges());
        assertEquals(mask(0, 17), data.getSkyYMask());
        assertEquals(mask(1, 16), data.getBlockYMask());
        assertEquals(mask(2), data.getEmptySkyYMask());
        assertEquals(mask(3), data.getEmptyBlockYMask());
        assertEquals(2, data.getSkyUpdates().size());
        assertEquals(2, data.getBlockUpdates().size());
        assertArrayEquals(lightBytes(1), data.getSkyUpdates().get(0));
        assertArrayEquals(lightBytes(2), data.getSkyUpdates().get(1));
        assertArrayEquals(lightBytes(3), data.getBlockUpdates().get(0));
        assertArrayEquals(lightBytes(4), data.getBlockUpdates().get(1));
    }

    @Test
    void standaloneLightRetainsEveryChunkLimbAndLocalMasksAndArrays() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (ChunkPos chunk : ClientboundChunkPacketTest.chunks()) {
                buf.clear();
                buf.writeChunkPos(chunk);
                writeLight(buf);
                ClientboundLightUpdatePacket packet = new ClientboundLightUpdatePacket(buf);
                assertEquals(chunk.x(), packet.getX());
                assertEquals(chunk.z(), packet.getZ());
                assertFalse(buf.isReadable());
                assertLight(packet.getLightData());
                buf.clear();
                packet.write(buf);
                packet = new ClientboundLightUpdatePacket(buf);
                assertEquals(chunk.x(), packet.getX());
                assertEquals(chunk.z(), packet.getZ());
                assertLight(packet.getLightData());
                assertFalse(buf.isReadable());
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void outgoingLightQueriesFullSectionsAndCopiesDataInLocalIndexOrder() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (ChunkPos chunk : ClientboundChunkPacketTest.chunks()) {
                List<SectionPos> queried = new ArrayList<>();
                DataLayer source = new DataLayer(lightBytes(7));
                LevelLightEngine engine = new LevelLightEngine(new LightChunkGetter() {
                    public BlockGetter getChunkForLighting(Int1088 x, Int1088 z) { return null; }
                    public BlockGetter getLevel() { return null; }
                }, false, false) {
                    public int getMinLightSection() { return -5; }
                    public int getLightSectionCount() { return 18; }
                    public LayerLightEventListener getLayerListener(LightLayer layer) {
                        return new LayerLightEventListener() {
                            public DataLayer getDataLayerData(SectionPos pos) {
                                queried.add(pos);
                                assertEquals(chunk.x(), pos.getX());
                                assertEquals(chunk.z(), pos.getZ());
                                if (layer == LightLayer.SKY && pos.getY() == -3) return new DataLayer();
                                return source;
                            }
                            public int getLightValue(BlockPos pos) { return 0; }
                            public void checkBlock(BlockPos pos) {}
                            public void onBlockEmissionIncrease(BlockPos pos, int level) {}
                            public boolean hasLightWork() { return false; }
                            public int runUpdates(int count, boolean sky, boolean edges) { return count; }
                            public void updateSectionStatus(SectionPos pos, boolean empty) {}
                            public void enableLightSources(ChunkPos pos, boolean enable) {}
                        };
                    }
                };
                ClientboundLightUpdatePacket packet = new ClientboundLightUpdatePacket(chunk, engine, mask(0, 2, 17), mask(1, 16), false);
                assertEquals(List.of(SectionPos.of(chunk, -5), SectionPos.of(chunk, -4), SectionPos.of(chunk, -3),
                        SectionPos.of(chunk, 11), SectionPos.of(chunk, 12)), queried);
                source.getData()[0] = 0; // packet construction must snapshot the engine's mutable arrays
                buf.clear();
                packet.write(buf);
                ClientboundLightUpdatePacket decoded = new ClientboundLightUpdatePacket(buf);
                assertEquals(chunk.x(), decoded.getX());
                assertEquals(chunk.z(), decoded.getZ());
                ClientboundLightUpdatePacketData data = decoded.getLightData();
                assertFalse(data.getTrustEdges());
                assertEquals(mask(0, 17), data.getSkyYMask());
                assertEquals(mask(2), data.getEmptySkyYMask());
                assertEquals(mask(1, 16), data.getBlockYMask());
                assertTrue(data.getEmptyBlockYMask().isEmpty());
                assertEquals(2, data.getSkyUpdates().size());
                assertEquals(2, data.getBlockUpdates().size());
                for (byte[] bytes : data.getSkyUpdates()) assertArrayEquals(lightBytes(7), bytes);
                for (byte[] bytes : data.getBlockUpdates()) assertArrayEquals(lightBytes(7), bytes);
                assertFalse(buf.isReadable());
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void lightRejectsMalformedCoordinatesAndOversizedLocalArrays() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (boolean z : new boolean[] {false, true}) {
                buf.clear();
                if (z) buf.writeInt1088(Int1088.ZERO);
                buf.writeVarInt(18);
                assertThrows(DecoderException.class, () -> new ClientboundLightUpdatePacket(buf));
            }
            buf.clear();
            buf.writeChunkPos(new ChunkPos(0, 0));
            buf.writeBoolean(false);
            for (int i = 0; i < 4; ++i) buf.writeBitSet(new BitSet());
            buf.writeVarInt(1);
            buf.writeVarInt(2049);
            assertThrows(DecoderException.class, () -> new ClientboundLightUpdatePacket(buf));
        } finally {
            buf.release();
        }
    }
}
