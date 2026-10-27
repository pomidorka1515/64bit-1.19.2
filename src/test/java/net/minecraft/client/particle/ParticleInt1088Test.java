package net.minecraft.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ParticleInt1088Test {
    @Test
    void localMotionInterpolationAndBlockQueriesRetainFullWidthAnchors() {
        for (Int1088 base : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 123),
                Int1088.of(Long.MAX_VALUE, -123), Int1088.MIN.add(Int1088.fromLong(8)),
                Int1088.MAX.subtract(Int1088.fromLong(8))}) {
            SectorVec3 spawn = SectorVec3.fromBlockAndFraction(base, 0.75D, 64.5D, base, 0.25D);
            Probe particle = Particle.createAt(spawn, Probe::new);
            particle.move(0.5D, 0.25D, -0.5D);
            Vec3 delta = particle.exactPosition().relativeTo(spawn);
            assertEquals(0.5D, delta.x);
            assertEquals(0.25D, delta.y);
            assertEquals(-0.5D, delta.z);
            assertEquals(base.add(Int1088.ONE), particle.blockPosition().getX());
            assertEquals(base.subtract(Int1088.ONE), particle.blockPosition().getZ());
            Vec3 interpolated = particle.interpolatedExactPosition(0.5F).relativeTo(spawn);
            assertEquals(0.25D, interpolated.x);
            assertEquals(0.125D, interpolated.y);
            assertEquals(-0.25D, interpolated.z);
        }
    }

    @Test
    void nestedProviderContextsRestoreOnSuccessAndFailure() {
        SectorVec3 outer = SectorVec3.fromBlockAndFraction(Int1088.of(-1, 8), 0.25D, 64, Int1088.ZERO, 0);
        SectorVec3 inner = outer.add(2, 1, -2);
        Particle.createAt(outer, () -> {
            assertEquals(inner.blockPosition(), Particle.createAt(inner, Probe::new).blockPosition());
            assertThrows(IllegalStateException.class, () -> Particle.createAt(inner, () -> {
                throw new IllegalStateException();
            }));
            assertEquals(outer.blockPosition(), new Probe().blockPosition());
            return new Probe();
        });
        assertEquals(Int1088.ZERO, new Probe().blockPosition().getX());
    }

    private static class Probe extends Particle {
        Probe() {
            super(null, 0, 0, 0);
            this.hasPhysics = false;
        }

        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        }

        public ParticleRenderType getRenderType() {
            return ParticleRenderType.NO_RENDER;
        }
    }
}
