package net.minecraft.world.level.saveddata;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.LevelVersion;
import net.minecraft.world.level.saveddata.maps.MapFrame;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.ticks.SavedTick;
import net.minecraft.world.ticks.TickPriority;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SavedDataTest {
    @TempDir Path directory;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void forcedChunksUseIndependentCompoundsAndSurviveCompressedReload() throws Exception {
        ForcedChunksSavedData data = new ForcedChunksSavedData();
        for (Int1088 coordinate : coordinates()) {
            data.getChunks().add(new ChunkPos(coordinate.shiftRight(4), coordinate.not().shiftRight(4)));
        }
        CompoundTag tag = disk(data.save(new CompoundTag()));
        ListTag chunks = tag.getList("Forced", Tag.TAG_COMPOUND);
        assertEquals(data.getChunks().size(), chunks.size());
        for (Tag entry : chunks) {
            CompoundTag chunk = (CompoundTag) entry;
            assertArrayShape(chunk, "x", "z");
            assertEquals(2, chunk.size());
        }
        ForcedChunksSavedData loaded = ForcedChunksSavedData.load(tag);
        assertEquals(data.getChunks(), loaded.getChunks());
        ChunkPos removed = loaded.getChunks().iterator().next();
        loaded.getChunks().remove(removed);
        assertTrue(data.getChunks().contains(removed));
    }

    @Test
    void forcedChunksRejectLegacyContainersMalformedAxesAndChunkOverflow() {
        for (Tag invalid : new Tag[] {new LongArrayTag(new long[34]), new CompoundTag(), LongTag.valueOf(0)}) {
            CompoundTag tag = new CompoundTag();
            tag.put("Forced", invalid);
            rejectUnchanged(tag, ForcedChunksSavedData::load);
        }
        rejectUnchanged(new CompoundTag(), ForcedChunksSavedData::load);
        CompoundTag position = new CompoundTag();
        NbtUtils.writeInt1088(position, "x", Int1088.ZERO);
        NbtUtils.writeInt1088(position, "z", Int1088.ZERO);
        rejectAxes(position, "x", "z", malformed -> {
            ListTag list = new ListTag();
            list.add(malformed);
            CompoundTag tag = new CompoundTag();
            tag.put("Forced", list);
            ForcedChunksSavedData.load(tag);
        });
        for (String axis : new String[] {"x", "z"}) {
            for (Int1088 outside : new Int1088[] {ChunkPos.MIN_CHUNK_COORDINATE.subtractSaturated(Int1088.ONE),
                    ChunkPos.MAX_CHUNK_COORDINATE.addSaturated(Int1088.ONE), Int1088.MIN, Int1088.MAX}) {
                CompoundTag malformed = position.copy();
                NbtUtils.writeInt1088(malformed, axis, outside);
                ListTag list = new ListTag();
                list.add(malformed);
                CompoundTag tag = new CompoundTag();
                tag.put("Forced", list);
                rejectUnchanged(tag, ForcedChunksSavedData::load);
            }
        }
    }

    @Test
    void mapOriginsAndFrameMarkersRetainEveryLimbWithoutDoubleFallback() throws Exception {
        for (Int1088 coordinate : coordinates()) {
            CompoundTag tag = MapItemSavedData.createForClient((byte) 2, true, Level.OVERWORLD).save(new CompoundTag());
            NbtUtils.writeInt1088(tag, "xCenter", coordinate);
            NbtUtils.writeInt1088(tag, "zCenter", coordinate.not());
            BlockPos pos = new BlockPos(coordinate, 70, coordinate.not());
            ListTag frames = new ListTag();
            frames.add(new MapFrame(pos, 90, 42).save());
            tag.put("frames", frames);
            MapItemSavedData loaded = MapItemSavedData.load(disk(tag));
            assertEquals(coordinate, loaded.x);
            assertEquals(coordinate.not(), loaded.z);
            CompoundTag saved = loaded.save(new CompoundTag());
            assertArrayShape(saved, "xCenter", "zCenter");
            assertEquals(pos, MapFrame.load(saved.getList("frames", Tag.TAG_COMPOUND).getCompound(0)).getPos());
            assertEquals(tag, saved);
            rejectAxes(tag, "xCenter", "zCenter", MapItemSavedData::load);
            CompoundTag corruptFrame = tag.copy();
            corruptFrame.getList("frames", Tag.TAG_COMPOUND).getCompound(0).getCompound("Pos").putDouble("X", 0);
            rejectUnchanged(corruptFrame, MapItemSavedData::load);
        }
    }

    @Test
    void ticksAndRaidCentersRoundTripAtEveryLimbAndRejectCorruption() throws Exception {
        for (Int1088 coordinate : coordinates()) {
            BlockPos pos = new BlockPos(coordinate, -17, coordinate.not());
            SavedTick<String> tick = new SavedTick<>("test", pos, 31, TickPriority.HIGH);
            CompoundTag tag = disk(tick.save(value -> value));
            assertArrayShape(tag, "x", "z");
            assertEquals(Tag.TAG_INT, tag.getTagType("y"));
            assertEquals(tick, SavedTick.loadTick(tag, Optional::of).orElseThrow());
            rejectAxes(tag, "x", "z", invalid -> SavedTick.loadTick(invalid, Optional::of));

            CompoundTag raidTag = new CompoundTag();
            NbtUtils.writeInt1088(raidTag, "CX", coordinate);
            NbtUtils.writeInt1088(raidTag, "CZ", coordinate.not());
            raidTag.putInt("CY", pos.getY());
            Raid raid = new Raid(null, disk(raidTag));
            assertEquals(pos, raid.getCenter());
            CompoundTag saved = disk(raid.save(new CompoundTag()));
            assertArrayShape(saved, "CX", "CZ");
            assertEquals(Tag.TAG_INT, saved.getTagType("CY"));
            assertEquals(pos, new Raid(null, saved).getCenter());
            rejectAxes(saved, "CX", "CZ", invalid -> new Raid(null, invalid));
        }
    }

    @Test
    void poiRecordsUseStrictAxisArraysIncludingRegistryOpsAndKeepJsonRepresentation() throws Exception {
        var registries = RegistryAccess.builtinCopy();
        var type = registries.registryOrThrow(Registry.POINT_OF_INTEREST_TYPE_REGISTRY).getHolderOrThrow(PoiTypes.HOME);
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        var jsonOps = RegistryOps.create(JsonOps.INSTANCE, registries);
        var codec = PoiRecord.codec(() -> {});
        var sectionCodec = PoiSection.codec(() -> {});
        var readColumn = SectionStorage.class.getDeclaredMethod("readColumn", ChunkPos.class, DynamicOps.class, Object.class);
        readColumn.setAccessible(true);
        try (SectionStorage<PoiSection> storage = new SectionStorage<>(directory.resolve("poi"),
                PoiSection::codec, PoiSection::new, false, registries, LevelHeightAccessor.create(0, 16))) {
            for (Int1088 coordinate : coordinates()) {
                BlockPos pos = new BlockPos(coordinate, 7, coordinate.not());
                PoiRecord record = new PoiRecord(pos, type, () -> {});
                CompoundTag encoded = (CompoundTag) codec.encodeStart(ops, record).getOrThrow(false, fail -> {});
                assertArrayShape(encoded.getCompound("pos"), "X", "Z");
                assertEquals(Tag.TAG_INT, encoded.getCompound("pos").getTagType("Y"));
                assertEquals(pos, codec.parse(ops, disk(encoded)).getOrThrow(false, fail -> {}).getPos());
                var json = codec.encodeStart(jsonOps, record).getOrThrow(false, fail -> {});
                assertTrue(json.getAsJsonObject().get("pos").isJsonArray());
                assertEquals(pos, codec.parse(jsonOps, json).getOrThrow(false, fail -> {}).getPos());

                PoiSection section = new PoiSection(() -> {});
                section.add(pos, type);
                CompoundTag sectionTag = (CompoundTag) sectionCodec.encodeStart(ops, section).getOrThrow(false, fail -> {});
                PoiSection loaded = sectionCodec.parse(ops, sectionTag).getOrThrow(false, fail -> {});
                assertEquals(pos, loaded.getRecords(holder -> true, PoiManager.Occupancy.ANY).findFirst().orElseThrow().getPos());
                CompoundTag sections = new CompoundTag();
                sections.put("0", sectionTag);
                CompoundTag column = new CompoundTag();
                column.put("Sections", sections);
                readColumn.invoke(storage, new ChunkPos(pos), ops, column);

                for (String axis : new String[] {"X", "Z"}) {
                    CompoundTag malformed = sectionTag.copy();
                    malformed.getList("Records", Tag.TAG_COMPOUND).getCompound(0).getCompound("pos").putLongArray(axis, new long[16]);
                    assertTrue(sectionCodec.parse(ops, malformed).error().isPresent());
                    sections.put("0", malformed);
                    var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
                            () -> readColumn.invoke(storage, new ChunkPos(pos), ops, column));
                    assertInstanceOf(RuntimeException.class, error.getCause());
                    assertTrue(error.getCause().getMessage().contains("LongArray[17]"));
                }
                CompoundTag flattened = encoded.copy();
                flattened.put("pos", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).getOrThrow(false, fail -> {}));
                assertTrue(codec.parse(ops, flattened).error().isPresent());
                for (String axis : new String[] {"X", "Z"}) {
                    CompoundTag missing = encoded.copy();
                    missing.getCompound("pos").remove(axis);
                    assertTrue(codec.parse(ops, missing).error().isPresent());
                    for (Tag invalid : new Tag[] {LongTag.valueOf(0), new LongArrayTag(new long[2]), new LongArrayTag(new long[18])}) {
                        CompoundTag malformed = encoded.copy();
                        malformed.getCompound("pos").put(axis, invalid);
                        assertTrue(codec.parse(ops, malformed).error().isPresent());
                    }
                }
                CompoundTag wrongY = encoded.copy();
                wrongY.getCompound("pos").putDouble("Y", 7);
                assertTrue(codec.parse(ops, wrongY).error().isPresent());
            }
        }
    }

    private CompoundTag disk(CompoundTag tag) throws Exception {
        Path file = directory.resolve("saved.dat");
        NbtIo.writeCompressed(tag, file.toFile());
        return NbtIo.readCompressed(file.toFile());
    }

    @Test
    void levelSpawnPersistsFullCoordinatesAndRejectsMissingOrApproximateAnchors() throws Exception {
        var registries = RegistryAccess.builtinCopy();
        var settings = new LevelSettings("test", GameType.SURVIVAL, false, Difficulty.NORMAL, true,
                new GameRules(), DataPackConfig.DEFAULT);
        var worldgen = WorldPresets.createNormalWorldFromPreset(registries, 0, true, false);
        for (Int1088 coordinate : coordinates()) {
            PrimaryLevelData data = new PrimaryLevelData(settings, worldgen, Lifecycle.stable());
            BlockPos spawn = new BlockPos(coordinate, 100, coordinate.not());
            data.setSpawn(spawn, 45);
            CompoundTag tag = disk(data.createTag(registries, null));
            assertArrayShape(tag, "SpawnX", "SpawnZ");
            assertEquals(Tag.TAG_INT, tag.getTagType("SpawnY"));
            Consumer<CompoundTag> read = encoded -> {
                Dynamic<Tag> dynamic = new Dynamic<>(NbtOps.INSTANCE, encoded);
                PrimaryLevelData loaded = PrimaryLevelData.parse(dynamic, null, settings, LevelVersion.parse(dynamic), worldgen, Lifecycle.stable());
                CompoundTag saved = loaded.createTag(registries, null);
                assertEquals(coordinate, NbtUtils.readInt1088(saved, "SpawnX"));
                assertEquals(coordinate.not(), NbtUtils.readInt1088(saved, "SpawnZ"));
                assertEquals(100, saved.getInt("SpawnY"));
                assertEquals(45, saved.getFloat("SpawnAngle"));
            };
            read.accept(tag);
            rejectAxes(tag, "SpawnX", "SpawnZ", read);
        }
    }

    private static List<Int1088> coordinates() {
        List<Int1088> values = new ArrayList<>(List.of(Int1088.ZERO, Int1088.ONE, Int1088.fromLong(-1),
                Int1088.MIN, Int1088.MAX, Int1088.ONE.shiftLeftSaturated(127)));
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            Int1088 value = Int1088.ONE.shiftLeftSaturated(limb * 64).addSaturated(Int1088.fromLong(7));
            values.add(value);
            values.add(value.not());
        }
        return values;
    }

    private static void assertArrayShape(CompoundTag tag, String... keys) {
        for (String key : keys) {
            assertEquals(Tag.TAG_LONG_ARRAY, tag.getTagType(key));
            assertEquals(17, tag.getLongArray(key).length);
        }
    }

    private static void rejectAxes(CompoundTag tag, String x, String z, Consumer<CompoundTag> load) {
        for (String axis : new String[] {x, z}) {
            CompoundTag missing = tag.copy();
            missing.remove(axis);
            missing.putLong(axis + "Hi", 0);
            rejectUnchanged(missing, load);
            for (Tag invalid : new Tag[] {LongTag.valueOf(0), new ListTag(),
                    new LongArrayTag(new long[2]), new LongArrayTag(new long[16]), new LongArrayTag(new long[18])}) {
                CompoundTag malformed = tag.copy();
                malformed.put(axis, invalid);
                rejectUnchanged(malformed, load);
            }
        }
    }

    private static void rejectUnchanged(CompoundTag tag, Consumer<CompoundTag> load) {
        CompoundTag before = tag.copy();
        assertThrows(IllegalArgumentException.class, () -> load.accept(tag));
        assertEquals(before, tag);
    }
}
