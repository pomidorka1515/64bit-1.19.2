package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSourceImpl;
import net.minecraft.core.Int1088;
import net.minecraft.core.Position;
import org.junit.jupiter.api.Test;

class ExactPositionInterfaceTest {
    @Test
    void blockSourcesPreserveFullWidthCoordinatesThroughPositionInterface() {
        Int1088 x = Int1088.of(-7L, 12345L);
        Int1088 z = Int1088.of(19L, -54321L);
        Position source = new BlockSourceImpl(null, new BlockPos(x, 64, z));
        assertEquals(SectorVec3.fromBlockAndFraction(x, 0.5D, 64.5D, z, 0.5D), source.exactPosition());
    }

    @Test
    void exactVectorRetainsFractionsThroughPositionInterface() {
        SectorVec3 exact = SectorVec3.fromBlockAndFraction(Int1088.of(3L, 9L), 0.125D,
                64.0D, Int1088.of(7L, -9L), 0.875D);
        Position position = exact.toExactVec3();
        assertEquals(exact, position.exactPosition());
        assertEquals(new Vec3(0.25D, 1.0D, -0.5D),
                position.exactPosition().add(0.25D, 1.0D, -0.5D).relativeTo(exact));
    }
}
