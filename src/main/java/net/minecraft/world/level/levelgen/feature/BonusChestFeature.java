package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.stream.IntStream;
import java.util.stream.LongStream;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class BonusChestFeature extends Feature<NoneFeatureConfiguration> {
   public BonusChestFeature(Codec<NoneFeatureConfiguration> p_65299_) {
      super(p_65299_);
   }

   public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> p_159477_) {
      RandomSource randomsource = p_159477_.random();
      WorldGenLevel worldgenlevel = p_159477_.level();
      ChunkPos chunkpos = new ChunkPos(p_159477_.origin());
      LongArrayList intarraylist = Util.toShuffledList(LongStream.rangeClosed(chunkpos.getMinBlockX(), chunkpos.getMaxBlockX()), randomsource);
      LongArrayList intarraylist1 = Util.toShuffledList(LongStream.rangeClosed(chunkpos.getMinBlockZ(), chunkpos.getMaxBlockZ()), randomsource);
      BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

      for(long integer : intarraylist) {
         for(long integer1 : intarraylist1) {
            blockpos$mutableblockpos.set(integer, 0, integer1);
            BlockPos blockpos = worldgenlevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos$mutableblockpos);
            if (worldgenlevel.isEmptyBlock(blockpos) || worldgenlevel.getBlockState(blockpos).getCollisionShape(worldgenlevel, blockpos).isEmpty()) {
               worldgenlevel.setBlock(blockpos, Blocks.CHEST.defaultBlockState(), 2);
               RandomizableContainerBlockEntity.setLootTable(worldgenlevel, randomsource, blockpos, BuiltInLootTables.SPAWN_BONUS_CHEST);
               BlockState blockstate = Blocks.TORCH.defaultBlockState();

               for(Direction direction : Direction.Plane.HORIZONTAL) {
                  BlockPos blockpos1 = blockpos.relative(direction);
                  if (blockstate.canSurvive(worldgenlevel, blockpos1)) {
                     worldgenlevel.setBlock(blockpos1, blockstate, 2);
                  }
               }

               return true;
            }
         }
      }

      return false;
   }
}