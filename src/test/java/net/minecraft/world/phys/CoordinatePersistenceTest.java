package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.Test;

class CoordinatePersistenceTest {
    @Test
    void blockPositionsAndTicksPreserveFullWidthCoordinates() {
        for (Int128 x : new Int128[]{Int128.MIN, Int128.of(-123, 17), Int128.of(123, -17), Int128.MAX}) {
            BlockPos pos = new BlockPos(x, 64, x.not());
            assertEquals(pos, NbtUtils.readBlockPos(NbtUtils.writeBlockPos(pos)));
            SavedTick<String> tick = new SavedTick<>("test", pos, 17, TickPriority.HIGH);
            assertEquals(tick, SavedTick.loadTick(tick.save(value -> value), Optional::of).orElseThrow());
        }
    }

    @Test
    void legacyIntegerPositionsAndTicksAreRejected() {
        CompoundTag pos = new CompoundTag();
        pos.putInt("X", -123);
        pos.putInt("Y", 64);
        pos.putInt("Z", 456);
        assertThrows(IllegalArgumentException.class, () -> NbtUtils.readBlockPos(pos));
        CompoundTag tick = new CompoundTag();
        tick.putString("i", "test");
        tick.putInt("x", -123);
        tick.putInt("y", 64);
        tick.putInt("z", 456);
        assertThrows(IllegalArgumentException.class, () -> SavedTick.loadTick(tick, Optional::of));
    }

    @Test
    void missingHighWordsAreRejectedEvenForLongCoordinates() {
        BlockPos position = new BlockPos(Int128.of(123, 17), 64, Int128.of(-123, 29));
        for (String key : new String[] {"XHi", "ZHi"}) {
            CompoundTag tag = NbtUtils.writeBlockPos(position);
            tag.remove(key);
            assertThrows(IllegalArgumentException.class, () -> NbtUtils.readBlockPos(tag));
        }
        SavedTick<String> tick = new SavedTick<>("test", position, 17, TickPriority.HIGH);
        for (String key : new String[] {"xHi", "zHi"}) {
            CompoundTag tag = tick.save(value -> value);
            tag.remove(key);
            assertThrows(IllegalArgumentException.class, () -> SavedTick.loadTick(tag, Optional::of));
        }
    }

    @Test
    void forcedChunksSurviveReload() {
        ChunkPos first = new ChunkPos(Int128.of(123, 17), Int128.of(-123, 29));
        ChunkPos second = new ChunkPos(-17, 29);
        ForcedChunksSavedData forced = new ForcedChunksSavedData();
        forced.getChunks().add(first);
        forced.getChunks().add(second);
        assertEquals(forced.getChunks(), ForcedChunksSavedData.load(forced.save(new CompoundTag())).getChunks());

    }

    @Test
    void structureReferenceSerializersIncludeEveryChunk() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ChunkPos pos = new ChunkPos(Int128.of(123, 17), Int128.of(-123, 29));
        ObjectSet<ChunkPos> chunks = new ObjectOpenHashSet<>();
        chunks.add(pos);
        Method serializer = ChunkSerializer.class.getDeclaredMethod("toTag", ObjectSet.class);
        serializer.setAccessible(true);
        assertReference((ListTag) serializer.invoke(null, chunks), pos);

    }

    private static void assertReference(ListTag references, ChunkPos pos) {
        assertEquals(1, references.size());
        assertEquals(pos.x(), NbtUtils.readInt128(references.getCompound(0), "x"));
        assertEquals(pos.z(), NbtUtils.readInt128(references.getCompound(0), "z"));
    }
}
