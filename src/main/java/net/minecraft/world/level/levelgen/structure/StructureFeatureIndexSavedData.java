package net.minecraft.world.level.levelgen.structure;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldBounds;
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
        return new StructureFeatureIndexSavedData(readIndexes(pTag, "All"), readIndexes(pTag, "Remaining"));
    }

    private static ObjectSet<ChunkPos> readIndexes(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof ListTag tags) || !tags.isEmpty() && tags.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " must be a list of chunk compounds");
        }
        return fromTag(tags);
    }

    private static ObjectSet<ChunkPos> fromTag(ListTag tags) {
        ObjectSet<ChunkPos> set = new ObjectOpenHashSet<>();
        for (Tag tag : tags) {
            if (tag instanceof CompoundTag tag2) {
                var x = NbtUtils.readInt128(tag2, "x");
                var z = NbtUtils.readInt128(tag2, "z");
                if (!WorldBounds.clampChunk(x).equals(x) || !WorldBounds.clampChunk(z).equals(z)) {
                    throw new IllegalArgumentException("Structure index outside chunk domain");
                }
                ChunkPos pos = new ChunkPos(x, z);
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
            NbtUtils.writeInt128(tag2, "x", chunkPos.x());
            NbtUtils.writeInt128(tag2, "z", chunkPos.z());
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
