package net.minecraft.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.PositionSourceType;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class VibrationSignalParticleTest {
    @Test
    void finalLiveTickArrivesWithoutNonFiniteCoordinates() {
        SectorVec3 spawn = SectorVec3.fromBlockAndFraction(Int1088.of(-1, 123), 0.75D,
                64.5D, Int1088.of(Long.MAX_VALUE, -123), 0.25D);
        for (int lifetime : new int[] {1, 2, 5}) {
            for (Vec3 offset : new Vec3[] {Vec3.ZERO, new Vec3(0, 3, 0), new Vec3(4, -2, -6)}) {
                SectorVec3 destination = spawn.add(offset.x, offset.y, offset.z);
                VibrationSignalParticle particle = create(spawn, Optional.of(destination), lifetime);
                for (int tick = 0; tick < lifetime; ++tick) {
                    particle.tick();
                    assertTrue(particle.isAlive());
                    assertDoesNotThrow(() -> particle.interpolatedExactPosition(0.5F));
                }
                Vec3 remaining = destination.relativeTo(particle.exactPosition());
                assertEquals(0.0D, remaining.x, 1.0E-12D);
                assertEquals(0.0D, remaining.y, 1.0E-12D);
                assertEquals(0.0D, remaining.z, 1.0E-12D);
                particle.tick();
                assertFalse(particle.isAlive());
            }
        }
    }

    @Test
    void expiredOrMissingDestinationRemovesParticleWithoutMoving() {
        SectorVec3 spawn = SectorVec3.fromApproximate(0, 64, 0);
        for (int lifetime : new int[] {-1, 0, 1}) {
            VibrationSignalParticle particle = create(spawn, Optional.empty(), lifetime);
            particle.tick();
            assertFalse(particle.isAlive());
            assertEquals(Vec3.ZERO, particle.exactPosition().relativeTo(spawn));
        }
    }

    private static VibrationSignalParticle create(SectorVec3 spawn, Optional<SectorVec3> destination, int lifetime) {
        PositionSource target = new PositionSource() {
            public Optional<SectorVec3> exactPosition(Level level) {
                return destination;
            }

            public PositionSourceType<?> getType() {
                return null;
            }
        };
        return Particle.createAt(spawn, () -> new VibrationSignalParticle(null, 0, 0, 0, target, lifetime));
    }
}
