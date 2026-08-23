package net.minecraft.world.level.lighting;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;

public abstract class DataLayerStorageMap<M extends DataLayerStorageMap<M>> {
   private static final int CACHE_SIZE = 2;
   private final SectionPos[] lastSectionKeys = new SectionPos[2];
   private final DataLayer[] lastSections = new DataLayer[2];
   private boolean cacheEnabled;
   protected final Map<SectionPos, DataLayer> map;

   protected DataLayerStorageMap(Map<SectionPos, DataLayer> p_75523_) {
      this.map = p_75523_;
      this.clearCache();
      this.cacheEnabled = true;
   }

   public abstract M copy();

   public void copyDataLayer(SectionPos p_75525_) {
      this.map.put(p_75525_, this.map.get(p_75525_).copy());
      this.clearCache();
   }

   public boolean hasLayer(SectionPos p_75530_) {
      return this.map.containsKey(p_75530_);
   }

   @Nullable
   public DataLayer getLayer(SectionPos p_75533_) {
      if (this.cacheEnabled) {
         for(int i = 0; i < 2; ++i) {
            if (p_75533_.equals(this.lastSectionKeys[i])) {
               return this.lastSections[i];
            }
         }
      }

      DataLayer datalayer = this.map.get(p_75533_);
      if (datalayer == null) {
         return null;
      } else {
         if (this.cacheEnabled) {
            for(int j = 1; j > 0; --j) {
               this.lastSectionKeys[j] = this.lastSectionKeys[j - 1];
               this.lastSections[j] = this.lastSections[j - 1];
            }

            this.lastSectionKeys[0] = p_75533_;
            this.lastSections[0] = datalayer;
         }

         return datalayer;
      }
   }

   @Nullable
   public DataLayer removeLayer(SectionPos p_75536_) {
      return this.map.remove(p_75536_);
   }

   public void setLayer(SectionPos p_75527_, DataLayer p_75528_) {
      this.map.put(p_75527_, p_75528_);
   }

   public void clearCache() {
      for(int i = 0; i < 2; ++i) {
         this.lastSectionKeys[i] = null;
         this.lastSections[i] = null;
      }

   }

   public void disableCache() {
      this.cacheEnabled = false;
   }
}