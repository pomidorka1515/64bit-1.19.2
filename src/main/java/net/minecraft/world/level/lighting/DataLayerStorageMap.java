package net.minecraft.world.level.lighting;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;

public abstract class DataLayerStorageMap<M extends DataLayerStorageMap<M>> {
    private static final int CACHE_SIZE = 2;
    private final SectionPos[] lastSectionKeys = new SectionPos[2];
    private final DataLayer[] lastSections = new DataLayer[2];
    private boolean cacheEnabled;
    protected final LightStorageMap<SectionPos, DataLayer> map;

    protected DataLayerStorageMap(Map<SectionPos, DataLayer> pMap) {
        this.map = pMap instanceof LightStorageMap ? (LightStorageMap<SectionPos, DataLayer>) pMap : new LightStorageMap<>(pMap);
        this.clearCache();
        this.cacheEnabled = true;
    }

    public abstract M copy();

    public void copyDataLayer(SectionPos pSectionPos) {
        this.map.put(pSectionPos, this.map.get(pSectionPos).copy());
        this.clearCache();
    }

    public boolean hasLayer(SectionPos pSectionPos) {
        return this.map.containsKey(pSectionPos);
    }

    /** Reuse cached immutable keys for the overwhelmingly section-local propagation path. */
    public SectionPos sectionForBlock(BlockPos pos) {
        if (this.cacheEnabled) {
            for (SectionPos key : this.lastSectionKeys) {
                if (key != null && key.contains(pos)) return key;
            }
        }
        return SectionPos.of(pos);
    }

    @Nullable
    public DataLayer getLayer(SectionPos pSectionPos) {
        if (this.cacheEnabled) {
            for (int i = 0; i < 2; ++i) {
                if (pSectionPos.equals(this.lastSectionKeys[i])) {
                    return this.lastSections[i];
                }
            }
        }

        DataLayer datalayer = this.map.get(pSectionPos);
        if (datalayer == null) {
            return null;
        } else {
            if (this.cacheEnabled) {
                for (int j = 1; j > 0; --j) {
                    this.lastSectionKeys[j] = this.lastSectionKeys[j - 1];
                    this.lastSections[j] = this.lastSections[j - 1];
                }

                this.lastSectionKeys[0] = pSectionPos;
                this.lastSections[0] = datalayer;
            }

            return datalayer;
        }
    }

    @Nullable
    public DataLayer removeLayer(SectionPos pSectionPos) {
        return this.map.remove(pSectionPos);
    }

    public void setLayer(SectionPos pSectionPos, DataLayer pArray) {
        this.map.put(pSectionPos, pArray);
    }

    public void clearCache() {
        for (int i = 0; i < 2; ++i) {
            this.lastSectionKeys[i] = null;
            this.lastSections[i] = null;
        }
    }

    public void disableCache() {
        this.cacheEnabled = false;
    }
}
