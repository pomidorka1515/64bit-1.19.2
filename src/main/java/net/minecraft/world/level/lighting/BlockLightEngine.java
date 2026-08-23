package net.minecraft.world.level.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableInt;

public final class BlockLightEngine extends LayerLightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {
   private static final Direction[] DIRECTIONS = Direction.values();
   private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

   public BlockLightEngine(LightChunkGetter chunkSource) {
      super(chunkSource, LightLayer.BLOCK, new BlockLightSectionStorage(chunkSource));
   }

   private int getLightEmission(BlockPos blockPos) {
      BlockGetter level = this.chunkSource.getChunkForLighting(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
      return level != null ? level.getLightEmission(this.pos.set(blockPos)) : 0;
   }

   protected int computeLevelFromNeighbor(BlockPos source, BlockPos target, int level) {
      if (target == null) return 15;
      if (source == null) return level + 15 - this.getLightEmission(target);
      if (level >= 15) return level;

      Direction direction = Direction.fromNormal(Long.signum(target.getX() - source.getX()), Integer.signum(target.getY() - source.getY()), Long.signum(target.getZ() - source.getZ()));
      if (direction == null) return 15;

      MutableInt opacity = new MutableInt();
      BlockState targetState = this.getStateAndOpacity(target, opacity);
      if (opacity.getValue() >= 15) return 15;
      BlockState sourceState = this.getStateAndOpacity(source, null);
      VoxelShape sourceShape = this.getShape(sourceState, source, direction);
      VoxelShape targetShape = this.getShape(targetState, target, direction.getOpposite());
      return Shapes.faceShapeOccludes(sourceShape, targetShape) ? 15 : level + Math.max(1, opacity.getValue());
   }

   protected void checkNeighborsAfterUpdate(BlockPos blockPos, int level, boolean decreasing) {
      SectionPos sectionPos = SectionPos.of(blockPos);
      for (Direction direction : DIRECTIONS) {
         BlockPos neighbor = blockPos.relative(direction);
         SectionPos neighborSection = SectionPos.of(neighbor);
         if (sectionPos.equals(neighborSection) || this.storage.storingLightForSection(neighborSection)) {
            this.checkNeighbor(blockPos, neighbor, level, decreasing);
         }
      }
   }

   protected int getComputedLevel(BlockPos blockPos, BlockPos excludedNeighbor, int level) {
      int computedLevel = level;
      if (excludedNeighbor != null) {
         int emissionLevel = this.computeLevelFromNeighbor(null, blockPos, 0);
         if (computedLevel > emissionLevel) computedLevel = emissionLevel;
         if (computedLevel == 0) return computedLevel;
      }

      SectionPos sectionPos = SectionPos.of(blockPos);
      DataLayer layer = this.storage.getDataLayer(sectionPos, true);
      for (Direction direction : DIRECTIONS) {
         BlockPos neighbor = blockPos.relative(direction);
         if (!neighbor.equals(excludedNeighbor)) {
            DataLayer neighborLayer = sectionPos.equals(SectionPos.of(neighbor)) ? layer : this.storage.getDataLayer(SectionPos.of(neighbor), true);
            if (neighborLayer != null) {
               int neighborLevel = this.computeLevelFromNeighbor(neighbor, blockPos, this.getLevel(neighborLayer, neighbor));
               if (computedLevel > neighborLevel) computedLevel = neighborLevel;
               if (computedLevel == 0) return computedLevel;
            }
         }
      }
      return computedLevel;
   }
}
