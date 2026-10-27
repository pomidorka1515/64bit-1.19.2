package net.minecraft.world.level.chunk.storage;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChunkSerializerTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void chunkPositionArraysRetainEveryLimbAndBothDomainEdges() throws Exception {
        Method read = method("readChunkPos", CompoundTag.class, String.class, String.class);
        for (Int1088 coordinate : coordinates()) {
            CompoundTag tag = position(coordinate, "xPos", "zPos");
            assertEquals(new ChunkPos(coordinate, coordinate.not()), read.invoke(null, tag, "xPos", "zPos"));
            assertEquals(17, tag.getLongArray("xPos").length);
            assertEquals(17, tag.getLongArray("zPos").length);
        }
    }

    @Test
    void chunkPositionRejectsBothMalformedAxesAndOutOfDomainWithoutMutation() throws Exception {
        Method read = method("readChunkPos", CompoundTag.class, String.class, String.class);
        for (String axis : new String[] {"xPos", "zPos"}) {
            for (Tag invalid : new Tag[] {IntTag.valueOf(1), LongTag.valueOf(1), new ListTag(),
                    new LongArrayTag(new long[2]), new LongArrayTag(new long[16]), new LongArrayTag(new long[18])}) {
                CompoundTag tag = position(Int1088.ZERO, "xPos", "zPos");
                tag.put(axis, invalid);
                tag.putLong(axis + "Hi", 0);
                reject(read, tag, "xPos", "zPos");
            }
            CompoundTag missing = position(Int1088.ZERO, "xPos", "zPos");
            missing.remove(axis);
            missing.putLong(axis + "Hi", 1);
            reject(read, missing, "xPos", "zPos");
            for (Int1088 outside : new Int1088[] {ChunkPos.MIN_CHUNK_COORDINATE.subtractSaturated(Int1088.ONE),
                    ChunkPos.MAX_CHUNK_COORDINATE.addSaturated(Int1088.ONE), Int1088.MIN, Int1088.MAX}) {
                CompoundTag tag = position(Int1088.ZERO, "xPos", "zPos");
                NbtUtils.writeInt1088(tag, axis, outside);
                reject(read, tag, "xPos", "zPos");
            }
        }
    }

    @Test
    void structureReferencesRoundTripFullCoordinatesAndRejectCorruptAxes() throws Exception {
        Method write = method("toTag", ObjectSet.class);
        Method read = method("fromTag", ListTag.class);
        ObjectSet<ChunkPos> positions = new ObjectOpenHashSet<>();
        for (Int1088 coordinate : coordinates()) positions.add(new ChunkPos(coordinate, coordinate.not()));
        ListTag encoded = (ListTag) write.invoke(null, positions);
        assertEquals(positions, read.invoke(null, encoded));
        for (String axis : new String[] {"x", "z"}) {
            ListTag malformed = encoded.copy();
            malformed.getCompound(0).putLongArray(axis, new long[16]);
            InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> read.invoke(null, malformed));
            assertInstanceOf(IllegalArgumentException.class, error.getCause());
        }
    }

    private static Int1088[] coordinates() {
        java.util.List<Int1088> result = new java.util.ArrayList<>();
        result.add(Int1088.ZERO);
        result.add(ChunkPos.MIN_CHUNK_COORDINATE);
        result.add(ChunkPos.MAX_CHUNK_COORDINATE);
        result.add(Int1088.ONE.shiftLeftSaturated(127));
        result.add(Int1088.ONE.shiftLeftSaturated(1024));
        for (int limb = 1; limb < 17; ++limb) {
            Int1088 value = Int1088.ONE.shiftLeftSaturated(limb * 64);
            result.add(value);
            result.add(value.negate());
        }
        return result.toArray(Int1088[]::new);
    }

    private static CompoundTag position(Int1088 value, String x, String z) {
        CompoundTag tag = new CompoundTag();
        NbtUtils.writeInt1088(tag, x, value);
        NbtUtils.writeInt1088(tag, z, value.not());
        return tag;
    }

    private static Method method(String name, Class<?>... types) throws Exception {
        Method method = ChunkSerializer.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static void reject(Method read, CompoundTag tag, String x, String z) {
        CompoundTag before = tag.copy();
        InvocationTargetException error = assertThrows(InvocationTargetException.class, () -> read.invoke(null, tag, x, z));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertEquals(before, tag);
    }
}
