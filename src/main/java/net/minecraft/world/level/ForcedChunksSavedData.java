package net.minecraft.world.level;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

public class ForcedChunksSavedData extends SavedData {
   public static final String FILE_ID = "chunks";
   private static final String TAG_FORCED = "Forced";
   private final ObjectSet<ChunkPos> chunks;

   private ForcedChunksSavedData(ObjectSet<ChunkPos> p_151482_) {
      this.chunks = p_151482_;
   }

   public ForcedChunksSavedData() {
      this(new ObjectOpenHashSet<>());
   }

   public static ForcedChunksSavedData load(CompoundTag p_151484_) {
      return new ForcedChunksSavedData(new ObjectOpenHashSet<>(fromTag(p_151484_.getList("Forced", Tag.TAG_COMPOUND))));
   }
   
   private static ObjectSet<ChunkPos> fromTag(ListTag tags) {
	   ObjectSet<ChunkPos> set = new ObjectOpenHashSet<>();
	   for (Tag tag : tags) {
		   if(tag instanceof CompoundTag tag2) {
			   ChunkPos pos = new ChunkPos(tag2.getLong("x"), tag2.getLong("z"));
			   set.add(pos);
		   }
		}
	   return set;
   }

   public CompoundTag save(CompoundTag p_46120_) {
      p_46120_.put("Forced", toTag(this.chunks));
      return p_46120_;
   }
   
   private ListTag toTag(ObjectSet<ChunkPos> chunks) {
	   ListTag tag = new ListTag();
	   for (ChunkPos chunkPos : chunks) {
		   CompoundTag tag2 = new CompoundTag();
		   tag2.putLong("x", chunkPos.x);
		   tag2.putLong("z", chunkPos.z);
	   }
	   return tag;
   }

   public ObjectSet<ChunkPos> getChunks() {
      return this.chunks;
   }
}