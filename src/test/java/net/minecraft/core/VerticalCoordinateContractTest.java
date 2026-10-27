package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

/** Horizontal widening must not change vertical types or arithmetic. */
class VerticalCoordinateContractTest {
    @Test
    void verticalApisRemainPrimitive() throws Exception {
        assertEquals(int.class, Vec3i.class.getMethod("getY").getReturnType());
        assertEquals(int.class, BlockPos.class.getMethod("getY").getReturnType());
        assertEquals(int.class, SectionPos.class.getMethod("y").getReturnType());
        assertEquals(int.class, BoundingBox.class.getMethod("minY").getReturnType());
        assertEquals(int.class, BoundingBox.class.getMethod("maxY").getReturnType());
        assertEquals(int.class, DensityFunction.FunctionContext.class.getMethod("blockY").getReturnType());
        assertEquals(double.class, SectorVec3.class.getMethod("y").getReturnType());
    }

    @Test
    void verticalSaturationHelpersKeepIntEdges() {
        assertEquals(Integer.MAX_VALUE, WorldBounds.addSaturated(Integer.MAX_VALUE, 1));
        assertEquals(Integer.MIN_VALUE, WorldBounds.addSaturated(Integer.MIN_VALUE, -1));
        assertEquals(Integer.MAX_VALUE, WorldBounds.multiplySaturated(Integer.MAX_VALUE, 2));
        assertEquals(Integer.MIN_VALUE, WorldBounds.multiplySaturated(Integer.MIN_VALUE, 2));
        assertEquals(100, new BlockPos(3L, 99, -7L).above().getY());
        assertEquals(-65, new BlockPos(3L, -64, -7L).below().getY());
    }

    @Test
    void buildHeightHelpersKeepConfiguredVerticalRange() {
        assertEquals(-64, WorldBounds.clampBuildHeight(Integer.MIN_VALUE, -64, 320));
        assertEquals(319, WorldBounds.clampBuildHeight(Integer.MAX_VALUE, -64, 320));
        assertEquals(100, WorldBounds.clampBuildHeight(100, -64, 320));
    }
}
