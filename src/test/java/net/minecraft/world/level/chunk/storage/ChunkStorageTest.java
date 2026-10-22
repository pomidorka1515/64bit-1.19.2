package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import net.minecraft.core.Int128;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkStorageTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsCurrentPayloadWithoutMigrationOrVersionRewriting() throws Exception {
        ChunkPos pos = new ChunkPos(Int128.of(123, 17), Int128.of(-123, 29));
        CompoundTag tag = new CompoundTag();
        NbtUtils.writeInt128(tag, "xPos", pos.x());
        NbtUtils.writeInt128(tag, "zPos", pos.z());
        tag.putInt("DataVersion", 1);
        tag.putString("custom", "unchanged");
        CompoundTag expected = tag.copy();

        try (ChunkStorage storage = new ChunkStorage(directory, true)) {
            storage.write(pos, tag);
            storage.flushWorker();
        }
        try (ChunkStorage storage = new ChunkStorage(directory, true)) {
            assertEquals(expected, storage.read(pos).join().orElseThrow());
        }
        assertEquals(expected, tag);
    }
}
