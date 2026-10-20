package net.minecraft.world.level.levelgen.structure;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Int128;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class LegacyStructureDataHandler {
    private static final Map<String, String> CURRENT_TO_LEGACY_MAP = Util.make(Maps.newHashMap(), (p_71337_) -> {
        p_71337_.put("Village", "Village");
        p_71337_.put("Mineshaft", "Mineshaft");
        p_71337_.put("Mansion", "Mansion");
        p_71337_.put("Igloo", "Temple");
        p_71337_.put("Desert_Pyramid", "Temple");
        p_71337_.put("Jungle_Pyramid", "Temple");
        p_71337_.put("Swamp_Hut", "Temple");
        p_71337_.put("Stronghold", "Stronghold");
        p_71337_.put("Monument", "Monument");
        p_71337_.put("Fortress", "Fortress");
        p_71337_.put("EndCity", "EndCity");
    });
    private static final Map<String, String> LEGACY_TO_CURRENT_MAP = Util.make(Maps.newHashMap(), (p_71325_) -> {
        p_71325_.put("Iglu", "Igloo");
        p_71325_.put("TeDP", "Desert_Pyramid");
        p_71325_.put("TeJP", "Jungle_Pyramid");
        p_71325_.put("TeSH", "Swamp_Hut");
    });
    private static final Set<String> OLD_STRUCTURE_REGISTRY_KEYS = Set.of("pillager_outpost", "mineshaft", "mansion", "jungle_pyramid", "desert_pyramid", "igloo", "ruined_portal", "shipwreck", "swamp_hut", "stronghold", "monument", "ocean_ruin", "fortress", "endcity", "buried_treasure", "village", "nether_fossil", "bastion_remnant");
    private final boolean hasLegacyData;
    private final Map<String, Object2ObjectMap<ChunkPos, CompoundTag>> dataMap = Maps.newHashMap();
    private final Map<String, StructureFeatureIndexSavedData> indexMap = Maps.newHashMap();
    private final List<String> legacyKeys;
    private final List<String> currentKeys;

    public LegacyStructureDataHandler(@Nullable DimensionDataStorage pStorage, List<String> pLegacyKeys, List<String> pCurrentKeys) {
        this.legacyKeys = pLegacyKeys;
        this.currentKeys = pCurrentKeys;
        this.populateCaches(pStorage);
        boolean flag = false;

        for (String s : this.currentKeys) {
            flag |= this.dataMap.get(s) != null;
        }

        this.hasLegacyData = flag;
    }

    public void removeIndex(ChunkPos pPackedChunkPos) {
        for (String s : this.legacyKeys) {
            StructureFeatureIndexSavedData structurefeatureindexsaveddata = this.indexMap.get(s);
            if (structurefeatureindexsaveddata != null && structurefeatureindexsaveddata.hasUnhandledIndex(pPackedChunkPos)) {
                structurefeatureindexsaveddata.removeIndex(pPackedChunkPos);
                structurefeatureindexsaveddata.setDirty();
            }
        }
    }

    public CompoundTag updateFromLegacy(CompoundTag pTag) {
        CompoundTag compoundtag = pTag.getCompound("Level");
        ChunkPos chunkpos = readChunkPos(compoundtag, "xPos", "zPos");
        if (this.isUnhandledStructureStart(chunkpos.x(), chunkpos.z())) {
            pTag = this.updateStructureStart(pTag, chunkpos);
        }

        CompoundTag compoundtag1 = compoundtag.getCompound("Structures");
        CompoundTag compoundtag2 = compoundtag1.getCompound("References");

        for (String s : this.currentKeys) {
            boolean flag = OLD_STRUCTURE_REGISTRY_KEYS.contains(s.toLowerCase(Locale.ROOT));
            if (!compoundtag2.contains(s) && flag) {
                int i = 8;
                ObjectList<ChunkPos> longlist = new ObjectArrayList<>();

                Int128 j = WorldBounds.addChunkOffset(chunkpos.x(), -8L);
                Int128 k = WorldBounds.addChunkOffset(chunkpos.x(), 8L);
                Int128 l = WorldBounds.addChunkOffset(chunkpos.z(), -8L);
                Int128 i1 = WorldBounds.addChunkOffset(chunkpos.z(), 8L);

                for (Int128 j1 = j;; j1 = WorldBounds.addChunkOffset(j1, 1L)) {
                    for (Int128 k1 = l;; k1 = WorldBounds.addChunkOffset(k1, 1L)) {
                        if (this.hasLegacyStart(j1, k1, s)) {
                            longlist.add(new ChunkPos(j1, k1));
                        }

                        if (k1.equals(i1)) {
                            break;
                        }
                    }

                    if (j1.equals(k)) {
                        break;
                    }
                }

                compoundtag2.put(s, toTag(longlist));
            }
        }

        compoundtag1.put("References", compoundtag2);
        compoundtag.put("Structures", compoundtag1);
        pTag.put("Level", compoundtag);
        return pTag;
    }

    private ListTag toTag(ObjectList<ChunkPos> longlist) {
        ListTag tag = new ListTag();
        for (ChunkPos chunkPos : longlist) {
            CompoundTag tag2 = new CompoundTag();
            NbtUtils.writeInt128(tag2, "x", chunkPos.x());
            NbtUtils.writeInt128(tag2, "z", chunkPos.z());
            tag.add(tag2);
        }
        return tag;
    }

    private boolean hasLegacyStart(Int128 pChunkX, Int128 pChunkZ, String pKey) {
        if (!this.hasLegacyData) {
            return false;
        } else {
            return this.dataMap.get(pKey) != null && this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(pKey)).hasStartIndex(new ChunkPos(pChunkX, pChunkZ));
        }
    }

    private boolean isUnhandledStructureStart(Int128 pChunkX, Int128 pChunkZ) {
        if (!this.hasLegacyData) {
            return false;
        } else {
            for (String s : this.currentKeys) {
                if (this.dataMap.get(s) != null && this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(s)).hasUnhandledIndex(new ChunkPos(pChunkX, pChunkZ))) {
                    return true;
                }
            }

            return false;
        }
    }

    private CompoundTag updateStructureStart(CompoundTag pTag, ChunkPos pChunkPos) {
        CompoundTag compoundtag = pTag.getCompound("Level");
        CompoundTag compoundtag1 = compoundtag.getCompound("Structures");
        CompoundTag compoundtag2 = compoundtag1.getCompound("Starts");

        for (String s : this.currentKeys) {
            Object2ObjectMap<ChunkPos, CompoundTag> long2objectmap = this.dataMap.get(s);
            if (long2objectmap != null) {
                if (this.indexMap.get(CURRENT_TO_LEGACY_MAP.get(s)).hasUnhandledIndex(pChunkPos)) {
                    CompoundTag compoundtag3 = long2objectmap.get(pChunkPos);
                    if (compoundtag3 != null) {
                        compoundtag2.put(s, compoundtag3);
                    }
                }
            }
        }

        compoundtag1.put("Starts", compoundtag2);
        compoundtag.put("Structures", compoundtag1);
        pTag.put("Level", compoundtag);
        return pTag;
    }

    private void populateCaches(@Nullable DimensionDataStorage pStorage) {
        if (pStorage != null) {
            for (String s : this.legacyKeys) {
                CompoundTag compoundtag = new CompoundTag();

                try {
                    compoundtag = pStorage.readTagFromDisk(s, 1493).getCompound("data").getCompound("Features");
                    if (compoundtag.isEmpty()) {
                        continue;
                    }
                } catch (IOException ioexception) {
                }

                for (String s1 : compoundtag.getAllKeys()) {
                    CompoundTag compoundtag1 = compoundtag.getCompound(s1);
                    ChunkPos i = readChunkPos(compoundtag1, "ChunkX", "ChunkZ");
                    ListTag listtag = compoundtag1.getList("Children", 10);
                    if (!listtag.isEmpty()) {
                        String s3 = listtag.getCompound(0).getString("id");
                        String s4 = LEGACY_TO_CURRENT_MAP.get(s3);
                        if (s4 != null) {
                            compoundtag1.putString("id", s4);
                        }
                    }

                    String s6 = compoundtag1.getString("id");
                    this.dataMap.computeIfAbsent(s6, (p_71335_) -> {
                                    return new Object2ObjectOpenHashMap();
                                }).put(i, compoundtag1);
                }

                String s5 = s + "_index";
                StructureFeatureIndexSavedData structurefeatureindexsaveddata = pStorage.computeIfAbsent(StructureFeatureIndexSavedData::load, StructureFeatureIndexSavedData::new, s5);
                if (!structurefeatureindexsaveddata.getAll().isEmpty()) {
                    this.indexMap.put(s, structurefeatureindexsaveddata);
                } else {
                    StructureFeatureIndexSavedData structurefeatureindexsaveddata1 = new StructureFeatureIndexSavedData();
                    this.indexMap.put(s, structurefeatureindexsaveddata1);

                    for (String s2 : compoundtag.getAllKeys()) {
                        CompoundTag compoundtag2 = compoundtag.getCompound(s2);
                        structurefeatureindexsaveddata1.addIndex(readChunkPos(compoundtag2, "ChunkX", "ChunkZ"));
                    }

                    structurefeatureindexsaveddata1.setDirty();
                }
            }
        }
    }

    private static ChunkPos readChunkPos(CompoundTag tag, String xKey, String zKey) {
        Int128 x = NbtUtils.readInt128(tag, xKey);
        Int128 z = NbtUtils.readInt128(tag, zKey);
        if (!WorldBounds.clampChunk(x).equals(x) || !WorldBounds.clampChunk(z).equals(z)) {
            throw new IllegalArgumentException("Structure chunk outside chunk domain");
        }
        return new ChunkPos(x, z);
    }

    public static LegacyStructureDataHandler getLegacyStructureHandler(ResourceKey<Level> pLevel, @Nullable DimensionDataStorage pStorage) {
        if (pLevel == Level.OVERWORLD) {
            return new LegacyStructureDataHandler(pStorage, ImmutableList.of("Monument", "Stronghold", "Village", "Mineshaft", "Temple", "Mansion"), ImmutableList.of("Village", "Mineshaft", "Mansion", "Igloo", "Desert_Pyramid", "Jungle_Pyramid", "Swamp_Hut", "Stronghold", "Monument"));
        } else if (pLevel == Level.NETHER) {
            List<String> list1 = ImmutableList.of("Fortress");
            return new LegacyStructureDataHandler(pStorage, list1, list1);
        } else if (pLevel == Level.END) {
            List<String> list = ImmutableList.of("EndCity");
            return new LegacyStructureDataHandler(pStorage, list, list);
        } else {
            throw new RuntimeException(String.format(Locale.ROOT, "Unknown dimension type : %s", pLevel));
        }
    }
}
