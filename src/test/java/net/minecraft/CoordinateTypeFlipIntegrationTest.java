package net.minecraft;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkPosMap;
import net.minecraft.world.level.chunk.storage.RegionPaths;
import net.minecraft.world.level.chunk.storage.RegionPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoordinateTypeFlipIntegrationTest {
    @TempDir Path root;

    @Test
    void everyUpperLimbSurvivesNbtWireChunkKeysAndPaths() {
        ChunkPosMap<String> chunks = new ChunkPosMap<>();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int limb = 2; limb < Int1088.LIMBS; ++limb) {
                for (int sign : new int[] {-1, 1}) {
                    BigInteger expectedX = BigInteger.ONE.shiftLeft(limb * 64)
                            .multiply(BigInteger.valueOf(sign)).add(BigInteger.valueOf(39));
                    BigInteger expectedZ = expectedX.negate().add(BigInteger.valueOf(91));
                    BlockPos original = new BlockPos(Int1088.parseDecimal(expectedX.toString()),
                            Integer.MIN_VALUE, Int1088.parseDecimal(expectedZ.toString()));
                    CompoundTag tag = NbtUtils.writeBlockPos(original);
                    assertEquals(17, tag.getLongArray("X").length);
                    assertEquals(17, tag.getLongArray("Z").length);
                    BlockPos persisted = NbtUtils.readBlockPos(tag);
                    buffer.clear();
                    buffer.writeBlockPos(persisted);
                    BlockPos received = buffer.readBlockPos();
                    assertEquals(original, received);
                    assertFalse(buffer.isReadable());

                    ChunkPos chunk = new ChunkPos(received);
                    assertEquals(expectedX.shiftRight(4).toString(), chunk.x().toDecimalString());
                    assertEquals(expectedZ.shiftRight(4).toString(), chunk.z().toDecimalString());
                    String marker = limb + ":" + sign;
                    assertNull(chunks.put(chunk, marker));
                    assertEquals(marker, chunks.get(chunk.x(), chunk.z()));
                    RegionPos region = new RegionPos(chunk.getRegionX(), chunk.getRegionZ());
                    Path file = RegionPaths.regionFile(root, region);
                    assertEquals(region, RegionPaths.parseRegionFile(root, file).orElseThrow());
                    Path external = RegionPaths.externalChunkFile(root, chunk);
                    assertEquals(chunk, RegionPaths.parseExternalChunkFile(root, external).orElseThrow());
                    assertFalse(Files.exists(file.getParent()), "path construction must not create directories");
                }
            }
            assertEquals(30, chunks.size());
        } finally {
            buffer.release();
        }
    }

    @Test
    void blockDomainEdgesPreserveHeightAcrossBothEncodings() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (Int1088 x : new Int1088[] {Int1088.MIN, Int1088.MAX}) {
                for (int y : new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
                    BlockPos block = new BlockPos(x, y, x.negate());
                    buffer.clear();
                    buffer.writeBlockPos(NbtUtils.readBlockPos(NbtUtils.writeBlockPos(block)));
                    assertEquals(block, buffer.readBlockPos());
                    SectionPos section = SectionPos.of(block);
                    buffer.clear();
                    buffer.writeSectionPos(section);
                    assertEquals(section, buffer.readSectionPos());
                    assertEquals(Math.floorDiv(y, 16), section.y());
                }
            }
        } finally {
            buffer.release();
        }
    }
}
