package net.minecraft.world.level.lighting;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableInt;


public abstract class LayerLightEngine<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>> extends DynamicGraphMinFixedPoint<BlockPos> implements LayerLightEventListener {
   private static final Direction[] DIRECTIONS = Direction.values();
   protected final LightChunkGetter chunkSource;
   protected final S storage;
   private boolean runningLightUpdates;

   protected LayerLightEngine(LightChunkGetter chunkSource, LightLayer layer, S storage) {
      super(16, 256, 8192);
      this.chunkSource = chunkSource;
      this.storage = storage;
   }

   public static int getLightBlockInto(LevelReader level, BlockState state, BlockPos pos, BlockState neighborState, BlockPos neighborPos, Direction direction, int lightBlock) {
      boolean stateOccludes = state.canOcclude() && state.useShapeForLightOcclusion();
      boolean neighborOccludes = neighborState.canOcclude() && neighborState.useShapeForLightOcclusion();
      if (!stateOccludes && !neighborOccludes) {
         return lightBlock;
      }

      VoxelShape shape = stateOccludes ? state.getOcclusionShape(level, pos) : Shapes.empty();
      VoxelShape neighborShape = neighborOccludes ? neighborState.getOcclusionShape(level, neighborPos) : Shapes.empty();
      return Shapes.mergedFaceOccludes(shape, neighborShape, direction) ? 16 : lightBlock;
   }

   protected BlockState getStateAndOpacity(BlockPos blockPos, @Nullable MutableInt opacity) {
      BlockGetter level = this.chunkSource.getChunkForLighting(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
      if (level == null) {
         if (opacity != null) {
            opacity.setValue(16);
         }

         return Blocks.BEDROCK.defaultBlockState();
      }

      BlockState state = level.getBlockState(blockPos);
      boolean useShape = state.canOcclude() && state.useShapeForLightOcclusion();
      if (opacity != null) {
         opacity.setValue(state.getLightBlock(this.chunkSource.getLevel(), blockPos));
      }

      return useShape ? state : Blocks.AIR.defaultBlockState();
   }

   protected VoxelShape getShape(BlockState state, BlockPos blockPos, Direction direction) {
      return state.canOcclude() ? state.getFaceOcclusionShape(this.chunkSource.getLevel(), blockPos, direction) : Shapes.empty();
   }

   protected int getLevel(DataLayer dataLayer, BlockPos blockPos) {
      return 15 - dataLayer.get(SectionPos.sectionRelative(blockPos.getX()), SectionPos.sectionRelative(blockPos.getY()), SectionPos.sectionRelative(blockPos.getZ()));
   }

   protected int getLevel(BlockPos blockPos) {
      return blockPos == null ? 0 : 15 - this.storage.getStoredLevel(blockPos);
   }

   protected void setLevel(BlockPos blockPos, int level) {
      this.storage.setStoredLevel(blockPos, Math.min(15, 15 - level));
   }

   protected void checkNode(BlockPos blockPos) {
      this.storage.runAllUpdates();
      if (this.storage.storingLightForSection(SectionPos.of(blockPos))) {
         super.checkNode(blockPos);
      }
   }

   public void checkBlock(BlockPos blockPos) {
      BlockPos immutablePos = blockPos.immutable();
      this.checkNode(immutablePos);

      for (Direction direction : DIRECTIONS) {
         this.checkNode(immutablePos.relative(direction));
      }
   }

   public void onBlockEmissionIncrease(BlockPos blockPos, int emission) {
   }

   public boolean hasLightWork() {
      return this.hasWork() || this.storage.hasWork() || this.storage.hasInconsistencies();
   }

   public int runUpdates(int maxSteps, boolean updateSkyLight, boolean skipEdgeChecks) {
      if (!this.runningLightUpdates) {
         if (this.storage.hasWork()) {
            maxSteps = this.storage.runUpdates(maxSteps);
            if (maxSteps == 0) {
               return maxSteps;
            }
         }

         this.storage.markNewInconsistencies(this, updateSkyLight, skipEdgeChecks);
      }

      this.runningLightUpdates = true;
      if (this.hasWork()) {
         maxSteps = this.runUpdates(maxSteps);
         if (maxSteps == 0) {
            return maxSteps;
         }
      }

      this.runningLightUpdates = false;
      this.storage.swapSectionMap();
      return maxSteps;
   }

   public void updateSectionStatus(SectionPos sectionPos, boolean isEmpty) {
      this.storage.updateSectionStatus(sectionPos, isEmpty);
   }

   public void enableLightSources(ChunkPos chunkPos, boolean enabled) {
      this.storage.enableLightSources(SectionPos.of(chunkPos.x, 0, chunkPos.z), enabled);
   }

   public void queueSectionData(SectionPos sectionPos, @Nullable DataLayer dataLayer, boolean trustEdges) {
      this.storage.queueSectionData(sectionPos, dataLayer, trustEdges);
   }

   public void retainData(ChunkPos chunkPos, boolean retain) {
      this.storage.retainData(SectionPos.of(chunkPos.x, 0, chunkPos.z), retain);
   }

   @Nullable
   public DataLayer getDataLayerData(SectionPos sectionPos) {
      return this.storage.getDataLayerData(sectionPos);
   }

   public int getLightValue(BlockPos blockPos) {
      return this.storage.getLightValue(blockPos);
   }

   public String getDebugData(BlockPos blockPos) {
      return Integer.toString(this.storage.getLightValue(blockPos));
   }

   public String getDebugData(SectionPos sectionPos) {
      return Integer.toString(this.storage.getLevel(sectionPos));
   }

   protected void checkNeighborsAfterUpdate(BlockPos blockPos, int level, boolean decreasing) {
   }

   protected boolean isSource(BlockPos blockPos) {
      return blockPos == null;
   }
}

