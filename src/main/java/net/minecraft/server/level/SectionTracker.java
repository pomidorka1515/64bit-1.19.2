package net.minecraft.server.level;

import net.minecraft.core.SectionPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.lighting.DynamicGraphMinFixedPoint;

/** Propagates section status using 3×3×3 neighbouring sections. */
public abstract class SectionTracker extends DynamicGraphMinFixedPoint<SectionPos> {
    protected SectionTracker(int levelCount, int expectedQueueSize, int expectedMapSize) {
        super(levelCount, expectedQueueSize, expectedMapSize);
    }

    protected boolean isSource(SectionPos sectionPos) {
        return sectionPos == null;
    }

    private static boolean isValidSection(SectionPos sectionPos) {
        return WorldBounds.isValidChunk(sectionPos.x(), sectionPos.z());
    }

    protected void checkNeighborsAfterUpdate(SectionPos sectionPos, int level, boolean decreasing) {
        if (!isValidSection(sectionPos))
            return;

        Int1088 negativeZ = WorldBounds.tryAddChunkOffset(sectionPos.z(), -1);
        Int1088 positiveZ = WorldBounds.tryAddChunkOffset(sectionPos.z(), 1);
        for (int x = -1; x <= 1; ++x) {
            Int1088 neighborX = x == 0 ? sectionPos.x() : WorldBounds.tryAddChunkOffset(sectionPos.x(), x);
            if (neighborX == null)
                continue;

            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    if (x == 0 && y == 0 && z == 0)
                        continue;
                    Int1088 neighborZ = z == 0 ? sectionPos.z() : z < 0 ? negativeZ : positiveZ;
                    if (neighborZ == null)
                        continue;

                    SectionPos neighbor = SectionPos.of(neighborX, sectionPos.y() + y, neighborZ);
                    this.checkNeighbor(sectionPos, neighbor, level, decreasing);
                }
            }
        }
    }

    protected int getComputedLevel(SectionPos sectionPos, SectionPos excludedNeighbor, int level) {
        if (!isValidSection(sectionPos))
            return level;

        int computedLevel = level;
        Int1088 negativeZ = WorldBounds.tryAddChunkOffset(sectionPos.z(), -1);
        Int1088 positiveZ = WorldBounds.tryAddChunkOffset(sectionPos.z(), 1);
        for (int x = -1; x <= 1; ++x) {
            Int1088 neighborX = x == 0 ? sectionPos.x() : WorldBounds.tryAddChunkOffset(sectionPos.x(), x);
            if (neighborX == null)
                continue;

            for (int y = -1; y <= 1; ++y) {
                for (int z = -1; z <= 1; ++z) {
                    Int1088 neighborZ = z == 0 ? sectionPos.z() : z < 0 ? negativeZ : positiveZ;
                    if (neighborZ == null)
                        continue;

                    SectionPos neighbor = x == 0 && y == 0 && z == 0 ? null
                            : SectionPos.of(neighborX, sectionPos.y() + y, neighborZ);

                    if (neighbor == null ? excludedNeighbor != null : !neighbor.equals(excludedNeighbor)) {
                        int neighborLevel = this.computeLevelFromNeighbor(neighbor, sectionPos, neighbor == null ? 0 : this.getLevel(neighbor));
                        if (computedLevel > neighborLevel) {
                            computedLevel = neighborLevel;
                        }
                        if (computedLevel == 0) {
                            return computedLevel;
                        }
                    }
                }
            }
        }
        return computedLevel;
    }

    protected int computeLevelFromNeighbor(SectionPos source, SectionPos target, int level) {
        return source == null ? this.getLevelFromSource(target) : level + 1;
    }

    protected abstract int getLevelFromSource(SectionPos sectionPos);

    public void update(SectionPos sectionPos, int level, boolean decreasing) {
        if (isValidSection(sectionPos)) {
            this.checkEdge(null, sectionPos, level, decreasing);
        }
    }
}
