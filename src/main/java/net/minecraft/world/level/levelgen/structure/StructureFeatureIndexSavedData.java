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

   private StructureFeatureIndexSavedData(ObjectSet<ChunkPos> p_163532_, ObjectSet<ChunkPos> p_163533_) {
      this.all = p_163532_;
      this.remaining = p_163533_;
   }

   public StructureFeatureIndexSavedData() {
      this(new ObjectOpenHashSet<>(), new ObjectOpenHashSet<>());
   }

   public static StructureFeatureIndexSavedData load(CompoundTag p_163535_) {
      return new StructureFeatureIndexSavedData(fromTag(p_163535_.getList("All", Tag.TAG_COMPOUND)), fromTag(p_163535_.getList("Remaining", Tag.TAG_COMPOUND)));
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

   public CompoundTag save(CompoundTag p_73372_) {
      p_73372_.put("All", toTag(this.all));
      p_73372_.put("Remaining", toTag(this.remaining));
      return p_73372_;
   }
   
   private ListTag toTag(ObjectSet<ChunkPos> set) {
	   ListTag tag = new ListTag();
	   for (ChunkPos chunkPos : set) {
		   CompoundTag tag2 = new CompoundTag();
		   tag2.putLong("x", chunkPos.x);
		   tag2.putLong("z", chunkPos.z);
	   }
	   return tag;
   }

   public void addIndex(ChunkPos p_73366_) {
      this.all.add(p_73366_);
      this.remaining.add(p_73366_);
   }

   public boolean hasStartIndex(ChunkPos p_73370_) {
      return this.all.contains(p_73370_);
   }

   public boolean hasUnhandledIndex(ChunkPos p_73374_) {
      return this.remaining.contains(p_73374_);
   }

   public void removeIndex(ChunkPos p_73376_) {
      this.remaining.remove(p_73376_);
   }

   public ObjectSet<ChunkPos> getAll() {
      return this.all;
   }
}