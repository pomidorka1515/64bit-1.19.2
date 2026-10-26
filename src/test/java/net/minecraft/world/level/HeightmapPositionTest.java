package net.minecraft.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HeightmapPositionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void heightmapPositionUsesGenerationHeightLookupWithoutRequestingLaterChunkStatus() {
        for (BlockPos pos : List.of(new BlockPos(160, 0, 128),
                new BlockPos(Int128.of(123, -1), 17, Int128.of(-98, 0)))) {
            WorldGenLevel level = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getHeightmapPos")) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        if (method.getName().equals("getHeight")) {
                            assertEquals(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, args[0]);
                            assertEquals(pos.getX(), args[1]);
                            assertEquals(pos.getZ(), args[2]);
                            return 73;
                        }
                        throw new AssertionError("Heightmap position bypassed the generation height lookup: " + method);
                    });

            assertEquals(new BlockPos(pos.getX(), 73, pos.getZ()),
                    level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos));
        }
    }
}
