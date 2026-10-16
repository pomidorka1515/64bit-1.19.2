package net.minecraft.world.level.levelgen.structure;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public class StructureFeatureIndexSavedData extends SavedData {
    private static final String TAG_REMAINING_INDEXES = "Remaining";
    private static final String TAG_All_INDEXES = "All";
    private final ObjectSet<ChunkPos> all;
    private final ObjectSet<ChunkPos> remaining;

    private StructureFeatureIndexSavedData(ObjectSet<ChunkPos> pAll, ObjectSet<ChunkPos> pRemaining) {
        this.all = pAll;
        this.remaining = pRemaining;
    }

    public StructureFeatureIndexSavedData() {
        this(new ObjectOpenHashSet<>(), new ObjectOpenHashSet<>());
    }

    public static StructureFeatureIndexSavedData load(CompoundTag pTag) {
        return new StructureFeatureIndexSavedData(fromTag(pTag.getList("All", Tag.TAG_COMPOUND)), fromTag(pTag.getList("Remaining", Tag.TAG_COMPOUND)));
    }

    private static ObjectSet<ChunkPos> fromTag(ListTag tags) {
        ObjectSet<ChunkPos> set = new ObjectOpenHashSet<>();
        for (Tag tag : tags) {
            if (tag instanceof CompoundTag tag2) {
                ChunkPos pos = new ChunkPos(tag2.getLong("x"), tag2.getLong("z"));
                set.add(pos);
            }
        }
        return set;
    }

    public CompoundTag save(CompoundTag pCompoundTag) {
        pCompoundTag.put("All", toTag(this.all));
        pCompoundTag.put("Remaining", toTag(this.remaining));
        return pCompoundTag;
    }

    private ListTag toTag(ObjectSet<ChunkPos> set) {
        ListTag tag = new ListTag();
        for (ChunkPos chunkPos : set) {
            CompoundTag tag2 = new CompoundTag();
            tag2.putLong("x", chunkPos.x);
            tag2.putLong("z", chunkPos.z);
            tag.add(tag2);
        }
        return tag;
    }

    public void addIndex(ChunkPos pIndex) {
        this.all.add(pIndex);
        this.remaining.add(pIndex);
    }

    public boolean hasStartIndex(ChunkPos pIndex) {
        return this.all.contains(pIndex);
    }

    public boolean hasUnhandledIndex(ChunkPos pIndex) {
        return this.remaining.contains(pIndex);
    }

    public void removeIndex(ChunkPos pIndex) {
        this.remaining.remove(pIndex);
    }

    public ObjectSet<ChunkPos> getAll() {
        return this.all;
    }
}
