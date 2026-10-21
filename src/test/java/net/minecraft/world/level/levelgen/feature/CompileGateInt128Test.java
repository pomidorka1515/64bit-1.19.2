package net.minecraft.world.level.levelgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.LayerConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CompileGateInt128Test {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void layerWritesStayExactAcrossLowWordCarry() {
        for (BlockPos origin : List.of(new BlockPos(-16, 70, 32),
                new BlockPos(Int128.of(123, -1), 70, Int128.of(-98, -1)))) {
            List<BlockPos> writes = new ArrayList<>();
            WorldGenLevel level = level((method, args) -> switch (method) {
                case "getMinBuildHeight" -> -64;
                case "getBlockState" -> Blocks.AIR.defaultBlockState();
                case "setBlock" -> {
                    writes.add(((BlockPos) args[0]).immutable());
                    yield true;
                }
                default -> throw new AssertionError(method);
            });
            assertTrue(Feature.FILL_LAYER.place(new FeaturePlaceContext<>(Optional.empty(), level, null,
                    RandomSource.create(42), origin, new LayerConfiguration(4, Blocks.STONE.defaultBlockState()))));
            List<BlockPos> expected = new ArrayList<>();
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    expected.add(origin.offset(x, 0, z).atY(-60));
                }
            }
            assertEquals(expected, writes);
        }
    }

    @Test
    void voidPlatformDoesNotRepeatAtAliasedCoordinates() {
        WorldGenLevel level = level((method, args) -> { throw new AssertionError(method); });
        assertTrue(Feature.VOID_START_PLATFORM.place(new FeaturePlaceContext<>(Optional.empty(), level, null,
                RandomSource.create(42), new BlockPos(Int128.of(1, 8), 70, Int128.fromLong(8)),
                NoneFeatureConfiguration.INSTANCE)));
    }

    @Test
    void oreHeightQueriesRetainFullWidthAnchorInEveryMode() {
        BlockPos origin = new BlockPos(Int128.of(123, 100), 70, Int128.of(-98, 200));
        try {
            for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
                FarlandsMode.setMode(mode);
                List<BlockPos> queries = new ArrayList<>();
                WorldGenLevel level = level((method, args) -> switch (method) {
                    case "getHeight" -> {
                        queries.add(new BlockPos((Int128) args[1], 0, (Int128) args[2]));
                        yield -64;
                    }
                    default -> throw new AssertionError(method);
                });
                new OreFeature(OreConfiguration.CODEC).place(new FeaturePlaceContext<>(Optional.empty(), level, null,
                        RandomSource.create(42), origin, new OreConfiguration(
                                new BlockMatchTest(Blocks.STONE), Blocks.IRON_ORE.defaultBlockState(), 8)));
                assertTrue(!queries.isEmpty());
                for (BlockPos query : queries) {
                    assertTrue(query.distSqr(origin.atY(0)) < 100);
                }
            }
        } finally {
            FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
        }
    }

    private static WorldGenLevel level(java.util.function.BiFunction<String, Object[], Object> handler) {
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class}, (proxy, method, args) -> handler.apply(method.getName(), args));
    }
}
