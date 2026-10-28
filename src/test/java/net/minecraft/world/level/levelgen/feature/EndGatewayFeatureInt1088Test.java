package net.minecraft.world.level.levelgen.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndGatewayFeatureInt1088Test {
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger MAX = BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void gatewayAndFrameUseCoordinateValuesAcrossSeamsAndDomainEdges() {
        for (BigInteger value : List.of(BigInteger.ZERO, BigInteger.ONE.shiftLeft(53).add(BigInteger.ONE),
                BigInteger.ONE.shiftLeft(127), BigInteger.ONE.shiftLeft(1024), MIN, MAX)) {
            for (int sign : new int[] {1, -1}) {
                BigInteger x = value.multiply(BigInteger.valueOf(sign)).max(MIN).min(MAX);
                BigInteger z = x.negate().max(MIN).min(MAX);
                BlockPos origin = pos(x, 70, z);
                Map<BlockPos, BlockState> actual = new HashMap<>();
                WorldGenLevel level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                        new Class<?>[] {WorldGenLevel.class}, (proxy, method, args) -> {
                            if (method.getName().equals("setBlock")) {
                                assertNull(actual.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]));
                                return true;
                            }
                            throw new AssertionError(method.getName());
                        });
                assertTrue(Feature.END_GATEWAY.place(new FeaturePlaceContext<>(Optional.empty(), level, null,
                        RandomSource.create(42), origin, EndGatewayConfiguration.delayedExitSearch())));

                Map<BlockPos, BlockState> expected = new HashMap<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BigInteger px = x.add(BigInteger.valueOf(dx));
                        BigInteger pz = z.add(BigInteger.valueOf(dz));
                        if (px.compareTo(MIN) < 0 || px.compareTo(MAX) > 0
                                || pz.compareTo(MIN) < 0 || pz.compareTo(MAX) > 0) continue;
                        for (int dy = -2; dy <= 2; dy++) {
                            boolean column = dx == 0 && dz == 0;
                            BlockState state = column && dy == 0 ? Blocks.END_GATEWAY.defaultBlockState()
                                    : dy != 0 && (Math.abs(dy) == 2 ? column : dx == 0 || dz == 0)
                                            ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                            expected.put(pos(px, 70 + dy, pz), state);
                        }
                    }
                }
                assertEquals(expected, actual, origin.toString());
                assertEquals(Blocks.END_GATEWAY.defaultBlockState(), actual.get(origin));
            }
        }
    }

    private static BlockPos pos(BigInteger x, int y, BigInteger z) {
        return new BlockPos(Int1088.parseDecimal(x.toString()), y, Int1088.parseDecimal(z.toString()));
    }
}
