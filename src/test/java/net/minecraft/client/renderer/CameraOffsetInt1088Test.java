package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class CameraOffsetInt1088Test {
    @Test
    void nearbyOffsetsRemainExactAcrossAllLimbsAndDomainEdges() {
        for (Int1088 base : new Int1088[] {Int1088.ZERO, Int1088.ONE.shiftLeftSaturated(127),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN.addSaturated(Int1088.fromLong(2)),
                Int1088.MAX.subtractSaturated(Int1088.fromLong(2))}) {
            CameraRelativePosition camera = CameraRelativePosition.of(
                    SectorVec3.fromBlockAndFraction(base, 0.25D, 64.5D, base, 0.75D));
            assertEquals(0.75D, camera.relativeX(base.addSaturated(Int1088.ONE)));
            assertEquals(-1.75D, camera.relativeZ(base.subtractSaturated(Int1088.ONE)));
            assertEquals(-0.5D, camera.relativeY(64));
        }
    }
}
