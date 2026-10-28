package net.minecraft.client.particle;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ParticleInt1088Test {
    @Test
    void renderedQuadVerticesStayLocalWhileCameraMovesAtWideCoordinates() {
        for (int bit : new int[] {0, 127, 1024}) {
            for (int sign : new int[] {-1, 1}) {
                Int1088 base = Int1088.ONE.shiftLeftSaturated(bit).multiplySaturated(sign);
                SectorVec3 origin = SectorVec3.fromBlockAndFraction(base, 0.75D, 64.5D, base.negate(), 0.25D);
                Quad particle = Particle.createAt(origin, Quad::new);
                particle.move(0.5D, 0.25D, -0.5D);
                for (float tick : new float[] {0, 0.25F, 0.5F, 0.75F, 1}) {
                    // Move the camera with the interpolated particle: the emitted quad
                    // must remain identical even when either crosses a block boundary.
                    SectorVec3 cameraPosition = origin.add(0.5D * tick - 2, 0.25D * tick - 1, -0.5D * tick + 3);
                    Camera camera = new Camera() {
                        { this.setPosition(cameraPosition); }
                    };
                    CapturedVertices vertices = new CapturedVertices();
                    particle.render(vertices, camera, tick);
                    assertEquals(List.of(new Vec3(1.5, 0.5, -3), new Vec3(1.5, 1.5, -3),
                            new Vec3(2.5, 1.5, -3), new Vec3(2.5, 0.5, -3)), vertices.positions);
                    assertEquals(4, vertices.completed);
                }
            }
        }
    }

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

    private static class Quad extends SingleQuadParticle {
        Quad() {
            super(null, 0, 0, 0);
            this.hasPhysics = false;
            this.quadSize = 0.5F;
        }

        protected float getU0() { return 0; }
        protected float getU1() { return 1; }
        protected float getV0() { return 0; }
        protected float getV1() { return 1; }
        public int getLightColor(float partialTick) { return 0; }
        public ParticleRenderType getRenderType() { return ParticleRenderType.NO_RENDER; }
    }

    private static class CapturedVertices implements VertexConsumer {
        final List<Vec3> positions = new ArrayList<>();
        int completed;

        public VertexConsumer vertex(double x, double y, double z) {
            assertTrue(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z));
            this.positions.add(new Vec3(x, y, z));
            return this;
        }

        public VertexConsumer color(int r, int g, int b, int a) { return this; }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void endVertex() { ++this.completed; }
        public void defaultColor(int r, int g, int b, int a) {}
        public void unsetDefaultColor() {}
    }
}
