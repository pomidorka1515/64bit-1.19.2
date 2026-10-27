package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundChunkPacketTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static List<ChunkPos> chunks() {
        List<ChunkPos> chunks = new ArrayList<>();
        chunks.add(new ChunkPos(0, 0));
        chunks.add(new ChunkPos(-1, 1));
        for (int bit : new int[] {63, 127, 128}) {
            Int1088 high = Int1088.ONE.shiftLeftSaturated(bit);
            chunks.add(new ChunkPos(high, high.negate()));
        }
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 high = Int1088.ONE.shiftLeftSaturated(limb * 64).multiplySaturated(sign);
                chunks.add(new ChunkPos(high.add(Int1088.fromLong(7)), Int1088.fromLong(11)));
                chunks.add(new ChunkPos(Int1088.fromLong(7), high.add(Int1088.fromLong(11))));
            }
        }
        chunks.add(new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK));
        chunks.add(new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MIN_CHUNK));
        return chunks;
    }

    @Test
    void forgetAndCacheCenterKeepEveryLimbOnBothAxes() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (ChunkPos chunk : chunks()) {
                buf.clear();
                new ClientboundForgetLevelChunkPacket(chunk.x(), chunk.z()).write(buf);
                assertEquals(chunk, buf.readChunkPos());
                assertFalse(buf.isReadable());
                buf.readerIndex(0);
                ClientboundForgetLevelChunkPacket forget = new ClientboundForgetLevelChunkPacket(buf);
                assertEquals(chunk.x(), forget.getX());
                assertEquals(chunk.z(), forget.getZ());
                assertFalse(buf.isReadable());
                buf.clear();
                new ClientboundSetChunkCacheCenterPacket(chunk.x(), chunk.z()).write(buf);
                ClientboundSetChunkCacheCenterPacket center = new ClientboundSetChunkCacheCenterPacket(buf);
                assertEquals(chunk.x(), center.getX());
                assertEquals(chunk.z(), center.getZ());
                assertFalse(buf.isReadable());
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void combinedPacketPreservesPayloadAndReconstructsOnlyLocalBlockEntityOffsets() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (ChunkPos chunk : chunks()) {
                buf.clear();
                buf.writeChunkPos(chunk);
                CompoundTag heightmaps = new CompoundTag();
                heightmaps.putLongArray("WORLD_SURFACE", new long[] {1, -1, 42});
                buf.writeNbt(heightmaps);
                byte[] sections = new byte[] {0, 1, -1, 42};
                buf.writeByteArray(sections);
                buf.writeVarInt(2);
                for (int packed : new int[] {0, 255}) {
                    buf.writeByte(packed);
                    buf.writeShort(packed == 0 ? -64 : 319);
                    buf.writeId(Registry.BLOCK_ENTITY_TYPE, BlockEntityType.CHEST);
                    CompoundTag tag = new CompoundTag();
                    tag.putInt("marker", packed);
                    buf.writeNbt(tag);
                }
                ClientboundLightPacketTest.writeLight(buf);
                byte[] wire = new byte[buf.readableBytes()];
                buf.getBytes(0, wire);
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(buf);
                assertEquals(chunk.x(), packet.getX());
                assertEquals(chunk.z(), packet.getZ());
                assertFalse(buf.isReadable());
                assertEquals(heightmaps, packet.getChunkData().getHeightmaps());
                FriendlyByteBuf sectionData = packet.getChunkData().getReadBuffer();
                try {
                    byte[] actual = new byte[sectionData.readableBytes()];
                    sectionData.readBytes(actual);
                    assertArrayEquals(sections, actual);
                } finally {
                    sectionData.release();
                }
                List<BlockPos> positions = new ArrayList<>();
                packet.getChunkData().getBlockEntitiesTagsConsumer(packet.getX(), packet.getZ()).accept((pos, type, tag) -> {
                    positions.add(pos.immutable());
                    assertSame(BlockEntityType.CHEST, type);
                    assertEquals(positions.size() == 1 ? 0 : 255, tag.getInt("marker"));
                });
                assertEquals(List.of(new BlockPos(chunk.x().shiftLeftSaturated(4), -64, chunk.z().shiftLeftSaturated(4)),
                        new BlockPos(chunk.x().shiftLeftSaturated(4).add(Int1088.fromLong(15)), 319, chunk.z().shiftLeftSaturated(4).add(Int1088.fromLong(15)))), positions);
                ClientboundLightPacketTest.assertLight(packet.getLightData());
                buf.clear();
                packet.write(buf);
                byte[] rewritten = new byte[buf.readableBytes()];
                buf.readBytes(rewritten);
                assertArrayEquals(wire, rewritten);
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void sectionUpdatesKeepFullSectionIdentityAndTwelveBitLocalIndices() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (ChunkPos chunk : chunks()) {
                for (int y : new int[] {-4, 19}) {
                    buf.clear();
                    SectionPos section = SectionPos.of(chunk, y);
                    buf.writeSectionPos(section);
                    buf.writeBoolean(true);
                    buf.writeVarInt(2);
                    buf.writeVarLong((long) Block.getId(Blocks.STONE.defaultBlockState()) << 12);
                    buf.writeVarLong(((long) Block.getId(Blocks.GOLD_BLOCK.defaultBlockState()) << 12) | 4095);
                    ClientboundSectionBlocksUpdatePacket packet = new ClientboundSectionBlocksUpdatePacket(buf);
                    assertFalse(buf.isReadable());
                    buf.clear();
                    packet.write(buf);
                    packet = new ClientboundSectionBlocksUpdatePacket(buf);
                    assertTrue(packet.shouldSuppressLightUpdates());
                    List<BlockPos> updates = new ArrayList<>();
                    packet.runUpdates((pos, state) -> {
                        updates.add(pos.immutable());
                        assertSame(updates.size() == 1 ? Blocks.STONE.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState(), state);
                    });
                    assertEquals(List.of(new BlockPos(chunk.x().shiftLeftSaturated(4), y * 16, chunk.z().shiftLeftSaturated(4)),
                            new BlockPos(chunk.x().shiftLeftSaturated(4).add(Int1088.fromLong(15)), y * 16 + 15, chunk.z().shiftLeftSaturated(4).add(Int1088.fromLong(15)))), updates);
                }
            }
        } finally {
            buf.release();
        }
    }

    @Test
    void chunkPacketReadersRejectMalformedCoordinatesBeforeReadingPayload() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (boolean z : new boolean[] {false, true}) {
                buf.clear();
                if (z) buf.writeInt1088(Int1088.ZERO);
                buf.writeVarInt(18);
                assertThrows(DecoderException.class, () -> new ClientboundLevelChunkWithLightPacket(buf));
                buf.readerIndex(0);
                assertThrows(DecoderException.class, () -> new ClientboundForgetLevelChunkPacket(buf));
                buf.readerIndex(0);
                assertThrows(DecoderException.class, () -> new ClientboundSetChunkCacheCenterPacket(buf));
            }
        } finally {
            buf.release();
        }
    }
}
