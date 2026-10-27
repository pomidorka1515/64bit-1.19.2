package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class StorageCoordinateCodecTest {
    @Test void entityChunkPositionIsTwoStrictFixedArrays() throws Exception {
        Method write = EntityStorage.class.getDeclaredMethod("writeChunkPos", CompoundTag.class, ChunkPos.class);
        Method read = EntityStorage.class.getDeclaredMethod("readChunkPos", CompoundTag.class);
        write.setAccessible(true);
        read.setAccessible(true);
        for (ChunkPos pos : positions()) {
            CompoundTag tag = new CompoundTag();
            write.invoke(null, tag, pos);
            assertTrue(tag.contains("Position", Tag.TAG_COMPOUND));
            CompoundTag position = tag.getCompound("Position");
            assertEquals(17, position.getLongArray("x").length);
            assertEquals(17, position.getLongArray("z").length);
            assertEquals(pos, read.invoke(null, tag));
        }
        CompoundTag old = new CompoundTag();
        old.putLongArray("Position", new long[4]);
        assertRejected(read, old);
        CompoundTag invalid = new CompoundTag();
        CompoundTag position = new CompoundTag();
        NbtUtils.writeInt1088(position, "x", Int1088.MAX);
        NbtUtils.writeInt1088(position, "z", Int1088.ZERO);
        invalid.put("Position", position);
        assertRejected(read, invalid);
        position.putLongArray("x", new long[16]);
        assertRejected(read, invalid);
    }

    @Test void chunkSerializerRejectsLegacyAndOutOfDomainCoordinates() throws Exception {
        Method read = ChunkSerializer.class.getDeclaredMethod("readChunkPos", CompoundTag.class, String.class, String.class);
        read.setAccessible(true);
        for (ChunkPos pos : positions()) {
            CompoundTag tag = new CompoundTag();
            NbtUtils.writeInt1088(tag, "xPos", pos.x());
            NbtUtils.writeInt1088(tag, "zPos", pos.z());
            assertEquals(pos, read.invoke(null, tag, "xPos", "zPos"));
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("xPos", 1);
        tag.putLong("xPosHi", 0);
        NbtUtils.writeInt1088(tag, "zPos", Int1088.ZERO);
        assertRejected(read, tag, "xPos", "zPos");
        NbtUtils.writeInt1088(tag, "xPos", Int1088.MAX);
        assertRejected(read, tag, "xPos", "zPos");
    }

    private static ChunkPos[] positions() {
        long[] wide = new long[Int1088.LIMBS];
        wide[16] = 1;
        return new ChunkPos[] {ChunkPos.ZERO,
                new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK),
                new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MIN_CHUNK),
                new ChunkPos(Int1088.of(0, Long.MIN_VALUE, 0), Int1088.fromFixedLimbs(wide))};
    }

    private static void assertRejected(Method method, Object... arguments) {
        InvocationTargetException exception = assertThrows(InvocationTargetException.class, () -> method.invoke(null, arguments));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }
}
