package net.minecraft.world.level.levelgen.feature;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FullWidthOreFeatureTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach void restoreMode() { FarlandsMode.setMode(FarlandsMode.Mode.BIT_32); }

    @Test
    void oreCandidatesStayNearTheirExactAnchorInEveryMode() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            FarlandsMode.setMode(mode);
            for (Int128 coordinate : new Int128[] {Int128.of(17L, 37L), Int128.MIN, Int128.MAX}) {
                BlockPos origin = new BlockPos(coordinate, 32, coordinate);
                AtomicInteger candidates = new AtomicInteger();
                WorldGenLevel level = (WorldGenLevel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {WorldGenLevel.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getHeight" -> 64;
                        case "isOutsideBuildHeight" -> false;
                        case "ensureCanWrite" -> {
                            BlockPos pos = (BlockPos) args[0];
                            assertTrue(Math.abs(net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.difference(pos.getXHi(), pos.getXLo(), origin.getXHi(), origin.getXLo())) < 32);
                            assertTrue(Math.abs(net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.difference(pos.getZHi(), pos.getZLo(), origin.getZHi(), origin.getZLo())) < 32);
                            candidates.incrementAndGet();
                            yield false;
                        }
                        default -> throw new UnsupportedOperationException(method.toString());
                    };
                });
                new OreFeature(OreConfiguration.CODEC).place(new FeaturePlaceContext<>(Optional.empty(), level, null,
                        new LegacyRandomSource(1234L), origin, new OreConfiguration(List.of(), 32, 0.0F)));
                assertTrue(candidates.get() > 0, mode.toString());
            }
        }
    }
}
