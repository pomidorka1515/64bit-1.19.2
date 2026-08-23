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


public abstract class LayerLightEngine<M extends DataLayerStorageMap<M>, S extends LayerLightSectionStorage<M>> extends DynamicGraphMinFixedPoint<BlockPos> implements LightEventListener {
   protected final LightChunkGetter chunkSource;
   protected final S storage;
   protected LayerLightEngine(LightChunkGetter chunkSource, LightLayer layer, S storage) {
      super(16, 256, 8192);
      this.chunkSource = chunkSource;
      this.storage = storage;
   }

   public static int getLightBlockInto(LevelReader level, BlockState state, BlockPos pos, BlockState neighborState, BlockPos neighborPos, Direction direction, int lightBlock) {
      VoxelShape shape = state.getFaceOcclusionShape(level, pos, direction);
      VoxelShape neighborShape = neighborState.getFaceOcclusionShape(level, neighborPos, direction.getOpposite());
      return Shapes.mergedFaceOccludes(shape, neighborShape, direction) ? 15 : lightBlock;
   }

   protected BlockState getStateAndOpacity(BlockPos blockPos, @Nullable MutableInt opacity) {
      BlockGetter level = this.chunkSource.getChunkForLighting(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
      if (level == null) {
         if (opacity != null) opacity.setValue(15);
         return Blocks.AIR.defaultBlockState();
      }

      BlockState state = level.getBlockState(blockPos);
      if (opacity != null) opacity.setValue(state.getLightBlock(level, blockPos));
      return state;
   }

   protected VoxelShape getShape(BlockState state, BlockPos blockPos, Direction direction) {
      if (!state.useShapeForLightOcclusion()) return Shapes.empty();
      BlockGetter level = this.chunkSource.getChunkForLighting(SectionPos.blockToSectionCoord(blockPos.getX()), SectionPos.blockToSectionCoord(blockPos.getZ()));
      return level == null ? Shapes.empty() : state.getFaceOcclusionShape(level, blockPos, direction);
   }

   protected int getLevel(DataLayer dataLayer, BlockPos blockPos) {
      return dataLayer.get(SectionPos.sectionRelative(blockPos.getX()), SectionPos.sectionRelative(blockPos.getY()), SectionPos.sectionRelative(blockPos.getZ()));
   }

   protected int getLevel(BlockPos blockPos) {
      return this.storage.getStoredLevel(blockPos);
   }

   protected void setLevel(BlockPos blockPos, int level) {
      this.storage.setStoredLevel(blockPos, level);
   }

   public void checkBlock(BlockPos blockPos) {
      this.checkNode(blockPos.immutable());
   }

   public void onBlockEmissionIncrease(BlockPos blockPos, int emission) {
      this.checkEdge(null, blockPos, 15 - emission, true);
   }

   public boolean hasLightWork() {
      return this.hasWork() || this.storage.hasInconsistencies();
   }

   public int runUpdates(int maxSteps, boolean updateSkyLight, boolean skipEdgeChecks) {
      this.storage.markNewInconsistencies(this, updateSkyLight, skipEdgeChecks);
      int remaining = this.runUpdates(maxSteps);
      this.storage.swapSectionMap();
      return remaining;
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

   public String getDebugData(BlockPos blockPos) {
      return Integer.toString(this.storage.getLightValue(blockPos));
   }

   protected void checkNeighborsAfterUpdate(BlockPos blockPos, int level, boolean decreasing) {
   }

   protected boolean isSource(BlockPos blockPos) {
      return blockPos == null;
   }
}

