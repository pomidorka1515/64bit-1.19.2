package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import net.minecraft.world.level.levelgen.structure.StructureFeatureIndexSavedData;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.Test;

class CoordinatePersistenceTest {
    @Test
    void blockPositionsAndTicksPreserveFullWidthCoordinates() {
        for (long x : new long[]{Long.MIN_VALUE, -(1L << 40), 1L << 40, Long.MAX_VALUE}) {
            BlockPos pos = new BlockPos(x, 64, ~x);
            assertEquals(pos, NbtUtils.readBlockPos(NbtUtils.writeBlockPos(pos)));
            SavedTick<String> tick = new SavedTick<>("test", pos, 17, TickPriority.HIGH);
            assertEquals(tick, SavedTick.loadTick(tick.save(value -> value), Optional::of).orElseThrow());
        }
    }

    @Test
    void legacyIntegerPositionsAndTicksStillLoad() {
        CompoundTag pos = new CompoundTag();
        pos.putInt("X", -123);
        pos.putInt("Y", 64);
        pos.putInt("Z", 456);
        assertEquals(new BlockPos(-123, 64, 456), NbtUtils.readBlockPos(pos));
        CompoundTag tick = new CompoundTag();
        tick.putString("i", "test");
        tick.putInt("x", -123);
        tick.putInt("y", 64);
        tick.putInt("z", 456);
        assertEquals(new BlockPos(-123, 64, 456), SavedTick.loadTick(tick, Optional::of).orElseThrow().pos());
    }

    @Test
    void forcedChunksAndStructureIndicesSurviveReload() {
        ChunkPos first = new ChunkPos(1L << 40, -(1L << 40));
        ChunkPos second = new ChunkPos(-17, 29);
        ForcedChunksSavedData forced = new ForcedChunksSavedData();
        forced.getChunks().add(first);
        forced.getChunks().add(second);
        assertEquals(forced.getChunks(), ForcedChunksSavedData.load(forced.save(new CompoundTag())).getChunks());

        StructureFeatureIndexSavedData indices = new StructureFeatureIndexSavedData();
        indices.addIndex(first);
        indices.addIndex(second);
        indices.removeIndex(first);
        StructureFeatureIndexSavedData loaded = StructureFeatureIndexSavedData.load(indices.save(new CompoundTag()));
        assertEquals(indices.getAll(), loaded.getAll());
        assertFalse(loaded.hasUnhandledIndex(first));
        assertTrue(loaded.hasUnhandledIndex(second));
    }

    @Test
    void structureReferenceSerializersIncludeEveryChunk() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ChunkPos pos = new ChunkPos(1L << 40, -(1L << 40));
        ObjectSet<ChunkPos> chunks = new ObjectOpenHashSet<>();
        chunks.add(pos);
        Method serializer = ChunkSerializer.class.getDeclaredMethod("toTag", ObjectSet.class);
        serializer.setAccessible(true);
        assertReference((ListTag) serializer.invoke(null, chunks), pos);

        LegacyStructureDataHandler legacy = new LegacyStructureDataHandler(null, java.util.List.of(), java.util.List.of());
        Method migration = LegacyStructureDataHandler.class.getDeclaredMethod("toTag", it.unimi.dsi.fastutil.objects.ObjectList.class);
        migration.setAccessible(true);
        assertReference((ListTag) migration.invoke(legacy, new ObjectArrayList<>(chunks)), pos);
    }

    private static void assertReference(ListTag references, ChunkPos pos) {
        assertEquals(1, references.size());
        assertEquals(pos.x, references.getCompound(0).getLong("x"));
        assertEquals(pos.z, references.getCompound(0).getLong("z"));
    }
}
