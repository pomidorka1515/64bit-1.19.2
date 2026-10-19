package net.minecraft.world.level.lighting;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.SectionTracker;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;

public abstract class LayerLightSectionStorage<M extends DataLayerStorageMap<M>> extends SectionTracker {
    protected static final int LIGHT_AND_DATA = 0;
    protected static final int LIGHT_ONLY = 1;
    protected static final int EMPTY = 2;
    protected static final DataLayer EMPTY_DATA = new DataLayer();
    private static final Direction[] DIRECTIONS = Direction.values();
    private final LightLayer layer;
    private final LightChunkGetter chunkSource;
    protected final Set<SectionPos> dataSectionSet = new HashSet<>();
    protected final Set<SectionPos> toMarkNoData = new HashSet<>();
    protected final Set<SectionPos> toMarkData = new HashSet<>();
    protected volatile M visibleSectionData;
    protected final M updatingSectionData;
    protected final Set<SectionPos> changedSections = new HashSet<>();
    protected final Set<SectionPos> sectionsAffectedByLightUpdates = new HashSet<>();
    protected final Map<SectionPos, DataLayer> queuedSections = Collections.synchronizedMap(new HashMap<>());
    private final Set<SectionPos> untrustedSections = new HashSet<>();
    private final Set<SectionPos> columnsToRetainQueuedDataFor = new HashSet<>();
    private final Set<SectionPos> toRemove = new HashSet<>();
    protected volatile boolean hasToRemove;

    protected LayerLightSectionStorage(LightLayer pLayer, LightChunkGetter pChunkSource, M pUpdatingSectionData) {
        super(3, 16, 256);
        this.layer = pLayer;
        this.chunkSource = pChunkSource;
        this.updatingSectionData = pUpdatingSectionData;
        this.visibleSectionData = pUpdatingSectionData.copy();
        this.visibleSectionData.disableCache();
    }

    protected boolean storingLightForSection(SectionPos pSectionPos) {
        return this.getDataLayer(pSectionPos, true) != null;
    }

    @Nullable
    protected DataLayer getDataLayer(SectionPos pSectionPos, boolean pCached) {
        return this.getDataLayer((M) (pCached ? this.updatingSectionData : this.visibleSectionData), pSectionPos);
    }

    @Nullable
    protected DataLayer getDataLayer(M pMap, SectionPos pSectionPos) {
        return pMap.getLayer(pSectionPos);
    }

    @Nullable
    public DataLayer getDataLayerData(SectionPos pSectionPos) {
        DataLayer datalayer = this.queuedSections.get(pSectionPos);
        return datalayer != null ? datalayer : this.getDataLayer(pSectionPos, false);
    }

    protected abstract int getLightValue(BlockPos pLevelPos);

    protected int getStoredLevel(BlockPos pLevelPos) {
        SectionPos i = SectionPos.of(pLevelPos);
        DataLayer datalayer = this.getDataLayer(i, true);
        // Unloaded edge sections legitimately have no data layer.  Treat them as
        // unlit rather than allowing a render/light query to throw an NPE.
        return datalayer == null ? 0 : datalayer.get(SectionPos.sectionRelative(pLevelPos.getX()), SectionPos.sectionRelative(pLevelPos.getY()), SectionPos.sectionRelative(pLevelPos.getZ()));
    }

    protected void setStoredLevel(BlockPos pLevelPos, int pLightLevel) {
        SectionPos i = SectionPos.of(pLevelPos);
        if (this.changedSections.add(i)) {
            this.updatingSectionData.copyDataLayer(i);
        }

        DataLayer datalayer = this.getDataLayer(i, true);
        datalayer.set(SectionPos.sectionRelative(pLevelPos.getX()), SectionPos.sectionRelative(pLevelPos.getY()), SectionPos.sectionRelative(pLevelPos.getZ()), pLightLevel);
        SectionPos.aroundAndAtBlockPos(pLevelPos, this.sectionsAffectedByLightUpdates::add);
    }

    protected int getLevel(SectionPos pSectionPos) {
        if (pSectionPos == null) {
            return 2;
        } else if (this.dataSectionSet.contains(pSectionPos)) {
            return 0;
        } else {
            return !this.toRemove.contains(pSectionPos) && this.updatingSectionData.hasLayer(pSectionPos) ? 1 : 2;
        }
    }

    protected int getLevelFromSource(SectionPos pPos) {
        if (this.toMarkNoData.contains(pPos)) {
            return 2;
        } else {
            return !this.dataSectionSet.contains(pPos) && !this.toMarkData.contains(pPos) ? 2 : 0;
        }
    }

    protected void setLevel(SectionPos pSectionPos, int pLevel) {
        int i = this.getLevel(pSectionPos);
        if (i != 0 && pLevel == 0) {
            this.dataSectionSet.add(pSectionPos);
            this.toMarkData.remove(pSectionPos);
        }

        if (i == 0 && pLevel != 0) {
            this.dataSectionSet.remove(pSectionPos);
            this.toMarkNoData.remove(pSectionPos);
        }

        if (i >= 2 && pLevel != 2) {
            if (this.toRemove.contains(pSectionPos)) {
                this.toRemove.remove(pSectionPos);
            } else {
                this.updatingSectionData.setLayer(pSectionPos, this.createDataLayer(pSectionPos));
                this.changedSections.add(pSectionPos);
                this.onNodeAdded(pSectionPos);
                long j = pSectionPos.x();
                int k = pSectionPos.y();
                long l = pSectionPos.z();

                for (int i1 = -1; i1 <= 1; ++i1) {
                    Long affectedZ = WorldBounds.tryAddChunkOffset(l, i1);
                    if (affectedZ == null)
                        continue;

                    for (int j1 = -1; j1 <= 1; ++j1) {
                        Long affectedX = WorldBounds.tryAddChunkOffset(j, j1);
                        if (affectedX == null)
                            continue;

                        for (int k1 = -1; k1 <= 1; ++k1) {
                            this.sectionsAffectedByLightUpdates.add(SectionPos.of(affectedX, k + k1, affectedZ));
                        }
                    }
                }
            }
        }

        if (i != 2 && pLevel >= 2) {
            this.toRemove.add(pSectionPos);
        }

        this.hasToRemove = !this.toRemove.isEmpty();
    }

    protected DataLayer createDataLayer(SectionPos pSectionPos) {
        DataLayer datalayer = this.queuedSections.get(pSectionPos);
        return datalayer != null ? datalayer : new DataLayer();
    }

    protected void clearQueuedSectionBlocks(LayerLightEngine<?, ?> pEngine, SectionPos pSectionPos) {
        if (pEngine.getQueueSize() != 0) {
            if (pEngine.getQueueSize() < 8192) {
                pEngine.removeIf((p_75753_) -> {
                    return SectionPos.of(p_75753_).equals(pSectionPos);
                });
            } else {
                long i = SectionPos.sectionToBlockCoord(pSectionPos.x());
                int j = SectionPos.sectionToBlockCoord(pSectionPos.y());
                long k = SectionPos.sectionToBlockCoord(pSectionPos.z());

                for (int l = 0; l < 16; ++l) {
                    for (int i1 = 0; i1 < 16; ++i1) {
                        for (int j1 = 0; j1 < 16; ++j1) {
                            BlockPos k1 = new BlockPos(i + l, j + i1, k + j1);
                            pEngine.removeFromQueue(k1);
                        }
                    }
                }
            }
        }
    }

    protected boolean hasInconsistencies() {
        return this.hasToRemove;
    }

    protected void markNewInconsistencies(LayerLightEngine<M, ?> engine, boolean updateSkyLight, boolean skipEdgeChecks) {
        if (this.hasInconsistencies() || !this.queuedSections.isEmpty()) {
            for (SectionPos sectionPos : this.toRemove) {
                this.clearQueuedSectionBlocks(engine, sectionPos);
                DataLayer queuedData = this.queuedSections.remove(sectionPos);
                DataLayer removedData = this.updatingSectionData.removeLayer(sectionPos);
                if (this.columnsToRetainQueuedDataFor.contains(SectionPos.getZeroNode(sectionPos))) {
                    if (queuedData != null) {
                        this.queuedSections.put(sectionPos, queuedData);
                    } else if (removedData != null) {
                        this.queuedSections.put(sectionPos, removedData);
                    }
                }
            }

            this.updatingSectionData.clearCache();
            for (SectionPos sectionPos : this.toRemove) {
                this.onNodeRemoved(sectionPos);
            }
            this.toRemove.clear();
            this.hasToRemove = false;

            for (Map.Entry<SectionPos, DataLayer> entry : this.queuedSections.entrySet()) {
                SectionPos sectionPos = entry.getKey();
                if (this.storingLightForSection(sectionPos)) {
                    DataLayer dataLayer = entry.getValue();
                    if (this.updatingSectionData.getLayer(sectionPos) != dataLayer) {
                        this.clearQueuedSectionBlocks(engine, sectionPos);
                        this.updatingSectionData.setLayer(sectionPos, dataLayer);
                        this.changedSections.add(sectionPos);
                    }
                }
            }

            this.updatingSectionData.clearCache();
            if (!skipEdgeChecks) {
                for (SectionPos sectionPos : this.queuedSections.keySet()) {
                    this.checkEdgesForSection(engine, sectionPos);
                }
            } else {
                for (SectionPos sectionPos : this.untrustedSections) {
                    this.checkEdgesForSection(engine, sectionPos);
                }
            }

            this.untrustedSections.clear();
            Iterator<Map.Entry<SectionPos, DataLayer>> iterator = this.queuedSections.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<SectionPos, DataLayer> entry = iterator.next();
                if (this.storingLightForSection(entry.getKey())) {
                    iterator.remove();
                }
            }
        }
    }

    private void checkEdgesForSection(LayerLightEngine<M, ?> engine, SectionPos sectionPos) {
        if (!this.storingLightForSection(sectionPos))
            return;

        long x = SectionPos.sectionToBlockCoord(sectionPos.x());
        int y = SectionPos.sectionToBlockCoord(sectionPos.y());
        long z = SectionPos.sectionToBlockCoord(sectionPos.z());
        for (Direction direction : DIRECTIONS) {
            SectionPos neighborSection = sectionPos.offset(direction);
            // Section offsets saturate at the world edge. There is no adjacent
            // section there; constructing its block face would wrap long X/Z.
            if (neighborSection.equals(sectionPos) || this.queuedSections.containsKey(neighborSection) || !this.storingLightForSection(neighborSection))
                continue;

            for (int first = 0; first < 16; ++first) {
                for (int second = 0; second < 16; ++second) {
                    BlockPos from;
                    BlockPos to;
                    switch (direction) {
                        case DOWN:
                            from = new BlockPos(x + second, y, z + first);
                            to = new BlockPos(x + second, y - 1, z + first);
                            break;
                        case UP:
                            from = new BlockPos(x + second, y + 15, z + first);
                            to = new BlockPos(x + second, y + 16, z + first);
                            break;
                        case NORTH:
                            from = new BlockPos(x + first, y + second, z);
                            to = new BlockPos(x + first, y + second, z - 1);
                            break;
                        case SOUTH:
                            from = new BlockPos(x + first, y + second, z + 15);
                            to = new BlockPos(x + first, y + second, z + 16);
                            break;
                        case WEST:
                            from = new BlockPos(x, y + first, z + second);
                            to = new BlockPos(x - 1, y + first, z + second);
                            break;
                        default:
                            from = new BlockPos(x + 15, y + first, z + second);
                            to = new BlockPos(x + 16, y + first, z + second);
                    }
                    engine.checkEdge(from, to, engine.computeLevelFromNeighbor(from, to, engine.getLevel(from)), false);
                    engine.checkEdge(to, from, engine.computeLevelFromNeighbor(to, from, engine.getLevel(to)), false);
                }
            }
        }
    }

    protected void onNodeAdded(SectionPos pSectionPos) {
    }

    protected void onNodeRemoved(SectionPos pSectionPos) {
    }

    protected void enableLightSources(SectionPos pSectionPos, boolean pIsQueueEmpty) {
    }

    public void retainData(SectionPos pSectionColumnPos, boolean pRetain) {
        if (pRetain) {
            this.columnsToRetainQueuedDataFor.add(pSectionColumnPos);
        } else {
            this.columnsToRetainQueuedDataFor.remove(pSectionColumnPos);
        }
    }

    protected void queueSectionData(SectionPos pSectionPos, @Nullable DataLayer pArray, boolean pIsTrusted) {
        if (pArray != null) {
            this.queuedSections.put(pSectionPos, pArray);
            if (!pIsTrusted) {
                this.untrustedSections.add(pSectionPos);
            }
        } else {
            this.queuedSections.remove(pSectionPos);
        }
    }

    protected void updateSectionStatus(SectionPos pSectionPos, boolean pIsEmpty) {
        boolean flag = this.dataSectionSet.contains(pSectionPos);
        if (!flag && !pIsEmpty) {
            this.toMarkData.add(pSectionPos);
            this.checkEdge(null, pSectionPos, 0, true);
        }

        if (flag && pIsEmpty) {
            this.toMarkNoData.add(pSectionPos);
            this.checkEdge(null, pSectionPos, 2, false);
        }
    }

    protected void runAllUpdates() {
        if (this.hasWork()) {
            this.runUpdates(Integer.MAX_VALUE);
        }
    }

    protected void swapSectionMap() {
        if (!this.changedSections.isEmpty()) {
            M m = this.updatingSectionData.copy();
            m.disableCache();
            this.visibleSectionData = m;
            this.changedSections.clear();
        }

        if (!this.sectionsAffectedByLightUpdates.isEmpty()) {
            for (SectionPos sectionPos : this.sectionsAffectedByLightUpdates) {
                this.chunkSource.onLightUpdate(this.layer, sectionPos);
            }

            this.sectionsAffectedByLightUpdates.clear();
        }
    }
}
