package net.minecraft.world.level.levelgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BonusChestFeatureInt1088Test {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void visitsExactChunkFootprintInLegacyOrderWithoutChangingRandomConsumption() {
        for (BlockPos origin : List.of(new BlockPos(-19, 70, 35),
                new BlockPos(Int1088.of(-1L, 123L), 70, Int1088.of(0L, -98L)),
                new BlockPos(Int1088.MIN, 70, Int1088.MAX))) {
            ChunkPos chunk = new ChunkPos(origin);
            RandomSource random = RandomSource.create(42L);
            RandomSource expectedRandom = RandomSource.create(42L);
            // Reference the old long shuffle in a representable chunk; only its offsets matter.
            var xs = Util.toShuffledList(LongStream.rangeClosed(-32L, -17L), expectedRandom);
            var zs = Util.toShuffledList(LongStream.rangeClosed(32L, 47L), expectedRandom);
            List<BlockPos> expected = new ArrayList<>();
            for (long x : xs) {
                for (long z : zs) {
                    expected.add(new BlockPos(chunk.getBlockX((int) (x + 32L)), 0,
                            chunk.getBlockZ((int) (z - 32L))));
                }
            }
            List<BlockPos> visited = new ArrayList<>();
            WorldGenLevel level = level((method, args) -> switch (method) {
                case "getHeightmapPos" -> {
                    BlockPos pos = ((BlockPos) args[1]).immutable();
                    visited.add(pos);
                    yield pos;
                }
                case "isEmptyBlock" -> false;
                case "getBlockState" -> Blocks.STONE.defaultBlockState();
                default -> throw new AssertionError(method);
            });
            assertFalse(place(level, random, origin));
            assertEquals(expected, visited);
            assertEquals(256, visited.stream().distinct().count());
            assertEquals(expectedRandom.nextLong(), random.nextLong());
        }
    }

    @Test
    void chestWriteKeepsExactHeightmapResult() {
        BlockPos origin = new BlockPos(Int1088.of(-1L, 123L), 70, Int1088.of(0L, -98L));
        List<BlockPos> sampled = new ArrayList<>();
        WorldGenLevel level = level((method, args) -> switch (method) {
            case "getHeightmapPos" -> {
                BlockPos pos = ((BlockPos) args[1]).atY(75);
                sampled.add(pos);
                yield pos;
            }
            case "isEmptyBlock" -> true;
            case "setBlock" -> {
                assertEquals(sampled.get(0), args[0]);
                assertEquals(Blocks.CHEST.defaultBlockState(), args[1]);
                assertEquals(new ChunkPos(origin), new ChunkPos((BlockPos) args[0]));
                throw new Placed();
            }
            default -> throw new AssertionError(method);
        });
        assertThrows(Placed.class, () -> place(level, RandomSource.create(42L), origin));
        assertEquals(1, sampled.size());
    }

    private static boolean place(WorldGenLevel level, RandomSource random, BlockPos origin) {
        return Feature.BONUS_CHEST.place(new FeaturePlaceContext<>(Optional.empty(), level, null,
                random, origin, NoneFeatureConfiguration.INSTANCE));
    }

    private static WorldGenLevel level(java.util.function.BiFunction<String, Object[], Object> handler) {
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class}, (proxy, method, args) -> handler.apply(method.getName(), args));
    }

    private static class Placed extends RuntimeException {
    }
}
