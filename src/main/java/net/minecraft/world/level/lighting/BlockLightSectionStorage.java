package net.minecraft.world.level.lighting;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class BlockLightSectionStorage extends LayerLightSectionStorage<BlockLightSectionStorage.BlockDataLayerStorageMap> {
   protected BlockLightSectionStorage(LightChunkGetter p_75511_) {
      super(LightLayer.BLOCK, p_75511_, new BlockLightSectionStorage.BlockDataLayerStorageMap(new HashMap<>()));
   }

   protected int getLightValue(BlockPos p_75513_) {
      DataLayer datalayer = this.getDataLayer(SectionPos.of(p_75513_), false);
      return datalayer == null ? 0 : datalayer.get(SectionPos.sectionRelative(p_75513_.getX()), SectionPos.sectionRelative(p_75513_.getY()), SectionPos.sectionRelative(p_75513_.getZ()));
   }

   protected static final class BlockDataLayerStorageMap extends DataLayerStorageMap<BlockLightSectionStorage.BlockDataLayerStorageMap> {
      public BlockDataLayerStorageMap(Map<SectionPos, DataLayer> p_75515_) {
         super(p_75515_);
      }

      public BlockLightSectionStorage.BlockDataLayerStorageMap copy() {
         return new BlockLightSectionStorage.BlockDataLayerStorageMap(new HashMap<>(this.map));
      }
   }
}