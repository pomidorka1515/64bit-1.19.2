package net.minecraft.world.level.lighting;

import java.util.Locale;
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
   private static final Direction[] HORIZONTALS = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

   public SkyLightEngine(LightChunkGetter chunkSource) {
      super(chunkSource, LightLayer.SKY, new SkyLightSectionStorage(chunkSource));
   }

   protected int computeLevelFromNeighbor(BlockPos source, BlockPos target, int level) {
      if (source == null || target == null) return 15;
      if (level >= 15) return level;

      MutableInt opacity = new MutableInt();
      BlockState targetState = this.getStateAndOpacity(target, opacity);
      if (opacity.getValue() >= 15) return 15;

      Direction direction = Direction.fromNormal(Long.signum(target.getX() - source.getX()), Integer.signum(target.getY() - source.getY()), Long.signum(target.getZ() - source.getZ()));
      if (direction == null) {
         throw new IllegalStateException(String.format(Locale.ROOT, "Light was spread in illegal direction %d, %d, %d", target.getX() - source.getX(), target.getY() - source.getY(), target.getZ() - source.getZ()));
      }

      BlockState sourceState = this.getStateAndOpacity(source, null);
      VoxelShape sourceShape = this.getShape(sourceState, source, direction);
      VoxelShape targetShape = this.getShape(targetState, target, direction.getOpposite());
      if (Shapes.faceShapeOccludes(sourceShape, targetShape)) return 15;

      boolean vertical = source.getX() == target.getX() && source.getZ() == target.getZ();
      return vertical && source.getY() > target.getY() && level == 0 && opacity.getValue() == 0 ? 0 : level + Math.max(1, opacity.getValue());
   }

   protected void checkNeighborsAfterUpdate(BlockPos blockPos, int level, boolean decreasing) {
      SectionPos sectionPos = SectionPos.of(blockPos);
      int relativeY = SectionPos.sectionRelative(blockPos.getY());
      int sectionY = SectionPos.blockToSectionCoord(blockPos.getY());
      int emptySectionsBelow = 0;
      if (relativeY == 0) {
         while (!this.storage.storingLightForSection(sectionPos.offset(0, -emptySectionsBelow - 1, 0)) && this.storage.hasSectionsBelow(sectionY - emptySectionsBelow - 1)) {
            ++emptySectionsBelow;
         }
      }

      BlockPos below = blockPos.below(1 + emptySectionsBelow * 16);
      SectionPos belowSection = SectionPos.of(below);
      if (sectionPos.equals(belowSection) || this.storage.storingLightForSection(belowSection)) this.checkNeighbor(blockPos, below, level, decreasing);

      BlockPos above = blockPos.above();
      SectionPos aboveSection = SectionPos.of(above);
      if (sectionPos.equals(aboveSection) || this.storage.storingLightForSection(aboveSection)) this.checkNeighbor(blockPos, above, level, decreasing);

      for (Direction direction : HORIZONTALS) {
         for (int distance = 0; ; ++distance) {
            BlockPos neighbor = blockPos.offset(direction.getStepX(), -distance, direction.getStepZ());
            SectionPos neighborSection = SectionPos.of(neighbor);
            if (sectionPos.equals(neighborSection)) {
               this.checkNeighbor(blockPos, neighbor, level, decreasing);
               break;
            }
            if (this.storage.storingLightForSection(neighborSection)) {
               this.checkNeighbor(blockPos.below(distance), neighbor, level, decreasing);
            }
            if (distance > emptySectionsBelow * 16) break;
         }
      }
   }

   protected int getComputedLevel(BlockPos blockPos, BlockPos excludedNeighbor, int level) {
      int computedLevel = level;
      SectionPos sectionPos = SectionPos.of(blockPos);
      DataLayer layer = this.storage.getDataLayer(sectionPos, true);
      for (Direction direction : DIRECTIONS) {
         BlockPos neighbor = blockPos.relative(direction);
         if (!neighbor.equals(excludedNeighbor)) {
            SectionPos neighborSection = SectionPos.of(neighbor);
            DataLayer neighborLayer = sectionPos.equals(neighborSection) ? layer : this.storage.getDataLayer(neighborSection, true);
            int neighborLevel;
            if (neighborLayer != null) {
               neighborLevel = this.getLevel(neighborLayer, neighbor);
            } else {
               if (direction == Direction.DOWN) continue;
               neighborLevel = 15 - this.storage.getLightValue(neighbor, true);
            }
            int candidate = this.computeLevelFromNeighbor(neighbor, blockPos, neighborLevel);
            if (computedLevel > candidate) computedLevel = candidate;
            if (computedLevel == 0) return computedLevel;
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
      if (this.storage.storingLightForSection(sectionPos)) super.checkNode(searchPos);
   }

   public String getDebugData(BlockPos blockPos) {
      return super.getDebugData(blockPos) + (this.storage.isAboveData(SectionPos.of(blockPos)) ? "*" : "");
   }
}
