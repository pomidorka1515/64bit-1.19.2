package net.minecraft.world.level.levelgen.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TreeFeatureInt1088Test {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void leafUpdatesUseExactPositionsAndLocalVoxelIndices() throws Exception {
        for (Int1088 x : List.of(Int1088.of(-1L, 123L), Int1088.MIN, Int1088.MAX.add(Int1088.fromLong(-2L)))) {
            BlockPos root = new BlockPos(x, 70, Int1088.of(-1L, -98L));
            BlockPos leaf = root.east();
            Map<BlockPos, BlockState> states = new HashMap<>();
            states.put(root, Blocks.OAK_LOG.defaultBlockState());
            states.put(leaf, Blocks.OAK_LEAVES.defaultBlockState());
            LevelAccessor level = (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                    new Class<?>[]{LevelAccessor.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getBlockState" -> states.getOrDefault(args[0], Blocks.AIR.defaultBlockState());
                        case "setBlock" -> {
                            states.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                            yield true;
                        }
                        default -> throw new AssertionError(method.getName());
                    });
            BoundingBox box = new BoundingBox(root.getX(), 70, root.getZ(), root.getX().add(Int1088.fromLong(2L)), 70, root.getZ());
            DiscreteVoxelShape shape = updateLeaves(level, box, Set.of(root), Set.of(), Set.of(leaf));
            assertNotNull(shape);
            assertTrue(shape.isFull(0, 0, 0));
            assertTrue(shape.isFull(1, 0, 0));
            assertEquals(1, states.get(leaf).getValue(BlockStateProperties.DISTANCE));
        }
    }

    @Test
    void oversizedLeafBoxesRejectBeforeAccessingWorld() throws Exception {
        BoundingBox box = new BoundingBox(Int1088.MIN, 0, Int1088.ZERO, Int1088.MAX, 0, Int1088.ZERO);
        assertNull(updateLeaves(null, box, Set.of(), Set.of(), Set.of()));
    }

    @Test
    void trunkPlacementIsTranslationInvariantAcrossHighHalves() {
        BlockPos far = new BlockPos(Int1088.of(-1L, 123L), 70, Int1088.of(-1L, -98L));
        BlockPos near = new BlockPos(31, 70, 31);
        for (TrunkPlacer placer : List.of(new FancyTrunkPlacer(3, 0, 0), new ForkingTrunkPlacer(3, 0, 0),
                new DarkOakTrunkPlacer(3, 0, 0), new UpwardsBranchingTrunkPlacer(3, 0, 0,
                        ConstantInt.of(3), 1.0F, ConstantInt.of(3), HolderSet.direct()))) {
            assertEquals(placeTrunk(placer, near), placeTrunk(placer, far), placer.getClass().getSimpleName());
        }
    }

    private static Map<BlockPos, BlockState> placeTrunk(TrunkPlacer placer, BlockPos origin) {
        TreeConfiguration config = new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(Blocks.OAK_LOG), placer, BlockStateProvider.simple(Blocks.OAK_LEAVES),
                new FancyFoliagePlacer(ConstantInt.of(2), ConstantInt.of(4), 4), new TwoLayersFeatureSize(0, 0, 0))
                .ignoreVines().build();
        LevelSimulatedReader level = (LevelSimulatedReader) Proxy.newProxyInstance(LevelSimulatedReader.class.getClassLoader(),
                new Class<?>[]{LevelSimulatedReader.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "isStateAtPosition" -> ((java.util.function.Predicate<BlockState>) args[1]).test(Blocks.AIR.defaultBlockState());
                    default -> throw new AssertionError(method.getName());
                });
        Map<BlockPos, BlockState> local = new HashMap<>();
        placer.placeTrunk(level, (pos, state) -> local.put(pos.subtract(origin), state),
                RandomSource.create(42L), 8, origin, config);
        assertFalse(local.isEmpty());
        return local;
    }

    private static DiscreteVoxelShape updateLeaves(LevelAccessor level, BoundingBox box,
            Set<BlockPos> roots, Set<BlockPos> trunks, Set<BlockPos> foliage) throws Exception {
        Method method = TreeFeature.class.getDeclaredMethod("updateLeaves", LevelAccessor.class,
                BoundingBox.class, Set.class, Set.class, Set.class);
        method.setAccessible(true);
        return (DiscreteVoxelShape) method.invoke(null, level, box, roots, trunks, foliage);
    }
}
