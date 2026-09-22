package net.minecraft.world.level.levelgen.feature.trunkplacers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.util.valueproviders.ConstantInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FancyTrunkPlacerPrecisionTest {
   private static final long HUGE_X = 52999999999999981L;
   private static final long HUGE_Z = -75L;
   private static final int ORIGIN_Y = 115;

   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @AfterEach
   void restoreDefaultMode() {
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
   }

   @Test
   void preciseModeKeepsVerticalLogsOnAxisYAtHugeCoordinates() {
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      Map<BlockPos, BlockState> placed = placeFancyTrunk(HUGE_X, ORIGIN_Y, HUGE_Z, 12L);
      assertFalse(placed.isEmpty(), "the trunk placer must emit logs");
      assertTrue(hasVerticalColumn(placed, HUGE_X, HUGE_Z), "the main trunk must stay on the origin column");
      assertEquals(0, countSidewaysColumnLogs(placed, HUGE_X, HUGE_Z),
         "vertical column logs must keep axis=y instead of rotating onto x/z");
   }

   @Test
   void otherModesStillRoundHugeCoordinatesThroughDouble() {
      FarlandsMode.setMode(FarlandsMode.Mode.OFF);
      Map<BlockPos, BlockState> placed = placeFancyTrunk(HUGE_X, ORIGIN_Y, HUGE_Z, 12L);
      assertFalse(placed.isEmpty(), "the trunk placer must emit logs");
      assertTrue(countSidewaysColumnLogs(placed, HUGE_X, HUGE_Z) > 0 || !hasVerticalColumn(placed, HUGE_X, HUGE_Z),
         "legacy double offsets at this coordinate still smear the trunk off axis y");
   }

   @Test
   void preciseModeMatchesLegacyPlacementAtSmallCoordinates() {
      long x = 1203L;
      long z = -88L;
      FarlandsMode.setMode(FarlandsMode.Mode.OFF);
      Map<BlockPos, BlockState> legacy = placeFancyTrunk(x, ORIGIN_Y, z, 4L);
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      Map<BlockPos, BlockState> precise = placeFancyTrunk(x, ORIGIN_Y, z, 4L);
      assertEquals(legacy.keySet(), precise.keySet());
      for (BlockPos pos : legacy.keySet()) {
         assertEquals(legacy.get(pos), precise.get(pos), pos.toString());
      }
   }

   private static Map<BlockPos, BlockState> placeFancyTrunk(long x, int y, long z, long seed) {
      TreeConfiguration configuration = new TreeConfiguration.TreeConfigurationBuilder(
         BlockStateProvider.simple(Blocks.OAK_LOG),
         new FancyTrunkPlacer(3, 11, 0),
         BlockStateProvider.simple(Blocks.OAK_LEAVES),
         new FancyFoliagePlacer(ConstantInt.of(2), ConstantInt.of(4), 4),
         new TwoLayersFeatureSize(0, 0, 0, java.util.OptionalInt.of(4))
      ).ignoreVines().build();
      Map<BlockPos, BlockState> placed = new HashMap<>();
      configuration.trunkPlacer.placeTrunk(new OpenAirReader(), placed::put, RandomSource.create(seed), 8, new BlockPos(x, y, z), configuration);
      return placed;
   }

   private static boolean hasVerticalColumn(Map<BlockPos, BlockState> placed, long x, long z) {
      int yLogs = 0;
      for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
         if (entry.getKey().getX() == x && entry.getKey().getZ() == z && entry.getValue().hasProperty(RotatedPillarBlock.AXIS)) {
            ++yLogs;
         }
      }
      return yLogs >= 2;
   }

   private static int countSidewaysColumnLogs(Map<BlockPos, BlockState> placed, long x, long z) {
      int sideways = 0;
      for (Map.Entry<BlockPos, BlockState> entry : placed.entrySet()) {
         if (entry.getKey().getX() == x && entry.getKey().getZ() == z && entry.getValue().hasProperty(RotatedPillarBlock.AXIS)
               && entry.getValue().getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) {
            ++sideways;
         }
      }
      return sideways;
   }

   private static final class OpenAirReader implements LevelSimulatedReader {
      public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
         return predicate.test(Blocks.AIR.defaultBlockState());
      }

      public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> predicate) {
         return predicate.test(Fluids.EMPTY.defaultFluidState());
      }

      public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
         return Optional.empty();
      }

      public BlockPos getHeightmapPos(Heightmap.Types type, BlockPos pos) {
         return pos;
      }
   }
}
