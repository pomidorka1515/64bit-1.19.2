package net.minecraft.world.level;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

public class ForcedChunksSavedData extends SavedData {
    public static final String FILE_ID = "chunks";
    private static final String TAG_FORCED = "Forced";
    private final ObjectSet<ChunkPos> chunks;

    private ForcedChunksSavedData(ObjectSet<ChunkPos> pChunks) {
        this.chunks = pChunks;
    }

    public ForcedChunksSavedData() {
        this(new ObjectOpenHashSet<>());
    }

    public static ForcedChunksSavedData load(CompoundTag pTag) {
        if (!(pTag.get(TAG_FORCED) instanceof ListTag tags) || !tags.isEmpty() && tags.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Forced must be a list of chunk compounds");
        }
        return new ForcedChunksSavedData(fromTag(tags));
    }

    private static ObjectSet<ChunkPos> fromTag(ListTag tags) {
        ObjectSet<ChunkPos> set = new ObjectOpenHashSet<>();
        for (Tag tag : tags) {
            if (tag instanceof CompoundTag tag2) {
                var x = NbtUtils.readInt128(tag2, "x");
                var z = NbtUtils.readInt128(tag2, "z");
                if (!WorldBounds.clampChunk(x).equals(x) || !WorldBounds.clampChunk(z).equals(z)) {
                    throw new IllegalArgumentException("Forced chunk outside chunk domain");
                }
                ChunkPos pos = new ChunkPos(x, z);
                set.add(pos);
            }
        }
        return set;
    }

    public CompoundTag save(CompoundTag pCompound) {
        pCompound.put("Forced", toTag(this.chunks));
        return pCompound;
    }

    private ListTag toTag(ObjectSet<ChunkPos> chunks) {
        ListTag tag = new ListTag();
        for (ChunkPos chunkPos : chunks) {
            CompoundTag tag2 = new CompoundTag();
            NbtUtils.writeInt128(tag2, "x", chunkPos.x());
            NbtUtils.writeInt128(tag2, "z", chunkPos.z());
            tag.add(tag2);
        }
        return tag;
    }

    public ObjectSet<ChunkPos> getChunks() {
        return this.chunks;
    }
}
