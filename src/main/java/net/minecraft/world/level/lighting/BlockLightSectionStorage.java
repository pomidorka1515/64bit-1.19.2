package net.minecraft.world.level.lighting;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public class BlockLightSectionStorage extends LayerLightSectionStorage<BlockLightSectionStorage.BlockDataLayerStorageMap> {
    protected BlockLightSectionStorage(LightChunkGetter pChunkSource) {
        super(LightLayer.BLOCK, pChunkSource, new BlockLightSectionStorage.BlockDataLayerStorageMap(new HashMap<>()));
    }

    protected int getLightValue(BlockPos pLevelPos) {
        DataLayer datalayer = this.getDataLayer(SectionPos.of(pLevelPos), false);
        return datalayer == null ? 0 : datalayer.get(SectionPos.sectionRelative(pLevelPos.getXLo()), SectionPos.sectionRelative(pLevelPos.getY()), SectionPos.sectionRelative(pLevelPos.getZLo()));
    }

    protected static final class BlockDataLayerStorageMap extends DataLayerStorageMap<BlockLightSectionStorage.BlockDataLayerStorageMap> {
        public BlockDataLayerStorageMap(Map<SectionPos, DataLayer> p_75515_) {
            super(p_75515_);
        }

        public BlockLightSectionStorage.BlockDataLayerStorageMap copy() {
            return new BlockLightSectionStorage.BlockDataLayerStorageMap(this.map.copy());
        }
    }
}
