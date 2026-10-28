package net.minecraft.client.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class ClientLevelSoundDelayTest {
    @Test
    void missingCameraPlaysWithoutDelay() {
        assertEquals(0, ClientLevel.SoundDelay.ticks(SectorVec3.fromApproximate(20.0D, 0.0D, 0.0D), null));
    }

    @Test
    void delayUsesExactCameraRelativeDistance() {
        Int1088 far = Int1088.ONE.shiftLeftSaturated(1024);
        SectorVec3 camera = SectorVec3.fromBlockAndFraction(far, 0.25D, 64.0D, far, 0.5D);
        assertEquals(0, ClientLevel.SoundDelay.ticks(camera.add(10.0D, 0.0D, 0.0D), camera));
        assertEquals(5, ClientLevel.SoundDelay.ticks(camera.add(11.0D, 0.0D, 0.0D), camera));
        assertEquals(20, ClientLevel.SoundDelay.ticks(camera.add(40.0D, 0.0D, 0.0D), camera));
    }
}
