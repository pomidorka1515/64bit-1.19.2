package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableInt;

public final class SkyLightEngine extends LayerLightEngine<SkyLightSectionStorage.SkyDataLayerStorageMap, SkyLightSectionStorage> {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] HORIZONTALS = new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    public SkyLightEngine(LightChunkGetter chunkSource) {
        super(chunkSource, LightLayer.SKY, new SkyLightSectionStorage(chunkSource));
    }

    protected int computeLevelFromNeighbor(BlockPos source, BlockPos target, int level) {
        if (source == null || target == null)
            return 15;
        if (level >= 15)
            return level;

        MutableInt opacity = new MutableInt();
        BlockState targetState = this.getStateAndOpacity(target, opacity);
        if (opacity.getValue() >= 15)
            return 15;

        Direction direction = Direction.fromNormal(target.getX().compareTo(source.getX()), Integer.compare(target.getY(), source.getY()), target.getZ().compareTo(source.getZ()));
        if (direction == null)
            return level;

        return this.computeLevelFromNeighbor(source, target, level, targetState, opacity.intValue(), direction);
    }

    private int computeLevelFromNeighbor(BlockPos source, BlockPos target, int level, BlockState targetState, int opacity, Direction direction) {
        BlockState sourceState = this.getStateAndOpacity(source, null);
        VoxelShape sourceShape = this.getShape(sourceState, source, direction);
        VoxelShape targetShape = this.getShape(targetState, target, direction.getOpposite());
        if (Shapes.faceShapeOccludes(sourceShape, targetShape))
            return 15;

        return direction == Direction.DOWN && level == 0 && opacity == 0 ? 0 : level + Math.max(1, opacity);
    }

    protected void checkNeighborsAfterUpdate(BlockPos blockPos, int level, boolean decreasing) {
        SectionPos sectionPos = this.storage.updatingSectionData.sectionForBlock(blockPos);
        int relativeY = SectionPos.sectionRelative(blockPos.getY());
        int sectionY = SectionPos.blockToSectionCoord(blockPos.getY());
        int emptySectionsBelow = 0;
        if (relativeY == 0) {
            while (!this.storage.storingLightForSection(sectionPos.offset(0, -emptySectionsBelow - 1, 0)) && this.storage.hasSectionsBelow(sectionY - emptySectionsBelow - 1)) {
                ++emptySectionsBelow;
            }
        }

        BlockPos below = blockPos.below(1 + emptySectionsBelow * 16);
        if (!below.equals(blockPos) && (sectionPos.contains(below) || this.storage.storingLightForSection(SectionPos.of(below))))
            this.checkNeighbor(blockPos, below, level, decreasing);

        BlockPos above = blockPos.above();
        if (!above.equals(blockPos) && (sectionPos.contains(above) || this.storage.storingLightForSection(SectionPos.of(above))))
            this.checkNeighbor(blockPos, above, level, decreasing);

        for (Direction direction : HORIZONTALS) {
            for (int distance = 0; distance <= emptySectionsBelow * 16 + 1;) {
                BlockPos neighbor = blockPos.offset(direction.getStepX(), -distance, direction.getStepZ());
                if (sectionPos.contains(neighbor)) {
                    if (!neighbor.equals(blockPos))
                        this.checkNeighbor(blockPos, neighbor, level, decreasing);
                    break;
                }
                // A whole 16-block span has the same section identity. In sparse
                // sky columns, do not allocate/query every absent block in it.
                int end = Math.min(emptySectionsBelow * 16 + 1, distance + (neighbor.getY() & 15));
                if (this.storage.storingLightForSection(this.storage.updatingSectionData.sectionForBlock(neighbor))) {
                    for (; distance <= end; ++distance) {
                        BlockPos source = blockPos.below(distance);
                        BlockPos target = source.relative(direction);
                        if (!target.equals(source))
                            this.checkNeighbor(source, target, level, decreasing);
                    }
                } else {
                    distance = end + 1;
                }
            }
        }
    }

    protected int getComputedLevel(BlockPos blockPos, BlockPos excludedNeighbor, int level) {
        int computedLevel = level;
        SectionPos sectionPos = this.storage.updatingSectionData.sectionForBlock(blockPos);
        DataLayer layer = this.storage.getDataLayer(sectionPos, true);
        BlockState targetState = null;
        MutableInt opacity = new MutableInt();
        for (Direction direction : DIRECTIONS) {
            BlockPos neighbor = blockPos.relative(direction);
            if (!neighbor.equals(blockPos) && !neighbor.equals(excludedNeighbor)) {
                DataLayer neighborLayer = sectionPos.contains(neighbor) ? layer : this.storage.getDataLayer(SectionPos.of(neighbor), true);
                int neighborLevel;
                if (neighborLayer != null) {
                    neighborLevel = this.getLevel(neighborLayer, neighbor);
                } else {
                    if (direction == Direction.DOWN)
                        continue;
                    neighborLevel = 15 - this.storage.getLightValue(neighbor, true);
                }
                if (neighborLevel >= 15)
                    continue;
                if (targetState == null) {
                    targetState = this.getStateAndOpacity(blockPos, opacity);
                    if (opacity.intValue() >= 15)
                        return Math.min(computedLevel, 15);
                }
                Direction incoming = direction.getOpposite();
                // Y is an int: retain the actual direction even at wraparound.
                if (direction.getAxis() == Direction.Axis.Y)
                    incoming = blockPos.getY() > neighbor.getY() ? Direction.UP : Direction.DOWN;
                int minimum = incoming == Direction.DOWN && neighborLevel == 0 && opacity.intValue() == 0
                        ? 0 : neighborLevel + Math.max(1, opacity.intValue());
                // Occlusion can only increase this bound. Avoid source state and
                // shape queries for edges that cannot improve the current result.
                if (Math.min(15, minimum) >= computedLevel)
                    continue;
                int candidate = this.computeLevelFromNeighbor(neighbor, blockPos, neighborLevel, targetState, opacity.intValue(), incoming);
                if (computedLevel > candidate)
                    computedLevel = candidate;
                if (computedLevel == 0)
                    return computedLevel;
            }
        }
        return computedLevel;
    }

    protected void checkNode(BlockPos blockPos) {
        this.storage.runAllUpdates();
        SectionPos sectionPos = SectionPos.of(blockPos);
        if (this.storage.storingLightForSection(sectionPos)) {
            super.checkNode(blockPos);
            return;
        }

        BlockPos searchPos = new BlockPos(blockPos.getX(), blockPos.getY() & ~15, blockPos.getZ());
        while (!this.storage.storingLightForSection(sectionPos) && !this.storage.isAboveData(sectionPos)) {
            searchPos = searchPos.above(16);
            sectionPos = sectionPos.offset(Direction.UP);
        }
        if (this.storage.storingLightForSection(sectionPos))
            super.checkNode(searchPos);
    }

    public String getDebugData(BlockPos blockPos) {
        return super.getDebugData(blockPos) + (this.storage.isAboveData(SectionPos.of(blockPos)) ? "*" : "");
    }

    public String getDebugData(SectionPos sectionPos) {
        return super.getDebugData(sectionPos) + (this.storage.isAboveData(sectionPos) ? "*" : "");
    }
}
