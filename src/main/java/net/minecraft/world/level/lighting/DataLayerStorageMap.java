package net.minecraft.world.level.lighting;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;

public abstract class DataLayerStorageMap<M extends DataLayerStorageMap<M>> {
    private static final int CACHE_SIZE = 16;
    private final SectionPos[] lastSectionKeys = new SectionPos[CACHE_SIZE];
    private final DataLayer[] lastSections = new DataLayer[CACHE_SIZE];
    private boolean cacheEnabled;
    protected final LightStorageMap<SectionPos, DataLayer> map;

    protected DataLayerStorageMap(Map<SectionPos, DataLayer> pMap) {
        this.map = pMap instanceof LightStorageMap ? (LightStorageMap<SectionPos, DataLayer>) pMap : new LightStorageMap<>(pMap);
        this.clearCache();
        this.cacheEnabled = true;
    }

    public abstract M copy();

    public void copyDataLayer(SectionPos pSectionPos) {
        this.setLayer(pSectionPos, this.getLayer(pSectionPos).copy());
    }

    public boolean hasLayer(SectionPos pSectionPos) {
        return this.getLayer(pSectionPos) != null;
    }

    /** Reuse cached immutable keys for the overwhelmingly section-local propagation path. */
    public SectionPos sectionForBlock(BlockPos pos) {
        if (this.cacheEnabled) {
            int slot = (int) ((pos.xLimb(0) >>> 4) + 3 * (pos.zLimb(0) >>> 4) + 5 * (pos.getY() >> 4)) & (CACHE_SIZE - 1);
            SectionPos key = this.lastSectionKeys[slot];
            if (key != null && key.contains(pos)) return key;
        }
        return SectionPos.of(pos);
    }

    @Nullable
    public DataLayer getLayer(SectionPos pSectionPos) {
        int slot = cacheSlot(pSectionPos);
        if (this.cacheEnabled) {
            if (pSectionPos.equals(this.lastSectionKeys[slot])) return this.lastSections[slot];
        }

        DataLayer datalayer = this.map.get(pSectionPos);
        if (this.cacheEnabled) {
            // Cache misses too: skylight repeatedly probes unloaded sections.
            this.lastSectionKeys[slot] = pSectionPos;
            this.lastSections[slot] = datalayer;
        }
        return datalayer;
    }

    private static int cacheSlot(SectionPos section) {
        return (int) (section.xLimb(0) + 3 * section.zLimb(0) + 5 * section.y()) & (CACHE_SIZE - 1);
    }

    @Nullable
    public DataLayer removeLayer(SectionPos pSectionPos) {
        DataLayer previous = this.map.remove(pSectionPos);
        this.lastSectionKeys[cacheSlot(pSectionPos)] = null;
        return previous;
    }

    public void setLayer(SectionPos pSectionPos, DataLayer pArray) {
        this.map.put(pSectionPos, pArray);
        if (this.cacheEnabled) {
            int slot = cacheSlot(pSectionPos);
            this.lastSectionKeys[slot] = pSectionPos;
            this.lastSections[slot] = pArray;
        }
    }

    public void clearCache() {
        for (int i = 0; i < CACHE_SIZE; ++i) {
            this.lastSectionKeys[i] = null;
            this.lastSections[i] = null;
        }
    }

    public void disableCache() {
        this.cacheEnabled = false;
    }
}
