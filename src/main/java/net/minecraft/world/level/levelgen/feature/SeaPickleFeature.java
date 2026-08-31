package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration;

public class SeaPickleFeature extends Feature<CountConfiguration> {
   public SeaPickleFeature(Codec<CountConfiguration> p_66754_) {
      super(p_66754_);
   }

   public boolean place(FeaturePlaceContext<CountConfiguration> p_160316_) {
      int i = 0;
      RandomSource randomsource = p_160316_.random();
      WorldGenLevel worldgenlevel = p_160316_.level();
      BlockPos blockpos = p_160316_.origin();
      int j = p_160316_.config().count().sample(randomsource);

      for(int k = 0; k < j; ++k) {
         int l = randomsource.nextInt(8) - randomsource.nextInt(8);
         int i1 = randomsource.nextInt(8) - randomsource.nextInt(8);
         long j1 = WorldBounds.addBlockOffset(blockpos.getX(), l);
         long k1 = WorldBounds.addBlockOffset(blockpos.getZ(), i1);
         int l1 = worldgenlevel.getHeight(Heightmap.Types.OCEAN_FLOOR, j1, k1);
         BlockPos blockpos1 = new BlockPos(j1, l1, k1);
         BlockState blockstate = Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, Integer.valueOf(randomsource.nextInt(4) + 1));
         if (worldgenlevel.getBlockState(blockpos1).is(Blocks.WATER) && blockstate.canSurvive(worldgenlevel, blockpos1)) {
            worldgenlevel.setBlock(blockpos1, blockstate, 2);
            ++i;
         }
      }

      return i > 0;
   }
}