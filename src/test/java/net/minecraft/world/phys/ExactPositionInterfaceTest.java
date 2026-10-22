package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSourceImpl;
import net.minecraft.core.Int128;
import net.minecraft.core.Position;
import org.junit.jupiter.api.Test;

class ExactPositionInterfaceTest {
    @Test
    void blockSourcesPreserveFullWidthCoordinatesThroughPositionInterface() {
        Int128 x = Int128.of(12345L, -7L);
        Int128 z = Int128.of(-54321L, 19L);
        Position source = new BlockSourceImpl(null, new BlockPos(x, 64, z));
        assertEquals(SectorVec3.fromBlockAndFraction(x, 0.5D, 64.5D, z, 0.5D), source.exactPosition());
    }

    @Test
    void exactVectorRetainsFractionsThroughPositionInterface() {
        SectorVec3 exact = SectorVec3.fromBlockAndFraction(Int128.of(9L, 3L), 0.125D,
                64.0D, Int128.of(-9L, 7L), 0.875D);
        Position position = exact.toExactVec3();
        assertEquals(exact, position.exactPosition());
        assertEquals(new Vec3(0.25D, 1.0D, -0.5D),
                position.exactPosition().add(0.25D, 1.0D, -0.5D).relativeTo(exact));
    }
}
