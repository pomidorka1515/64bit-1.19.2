package net.minecraft.client.renderer.debug;

import java.util.Optional;
import java.util.UUID;
import java.math.BigInteger;
import net.minecraft.client.renderer.CameraRelativePosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.PositionSourceType;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugCoordinatesInt1088Test {
    @Test
    void chunkBorderOriginsKeepTheirLocalOffsetsAcrossWideChunkSeams() {
        for (int bit : new int[] {127, 1024}) {
            for (int sign : new int[] {-1, 1}) {
                BigInteger origin = BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign));
                for (int offset : new int[] {-17, -16, -1, 0, 1, 15, 16, 17}) {
                    BigInteger x = origin.add(BigInteger.valueOf(offset));
                    BigInteger z = origin.negate().subtract(BigInteger.valueOf(offset));
                    SectorVec3 camera = SectorVec3.fromBlockAndFraction(Int1088.parseDecimal(x.toString()),
                            0.75D, 80.5D, Int1088.parseDecimal(z.toString()), 0.25D);
                    ChunkPos chunk = new ChunkPos(camera.blockPosition());
                    // ChunkBorderRenderer uses this exact origin before emitting
                    // its local -16..32 line offsets. This does not invoke OpenGL.
                    SectorVec3 chunkOrigin = SectorVec3.fromBlockAndFraction(chunk.getMinBlockX(), 0, 0,
                            chunk.getMinBlockZ(), 0);
                    Vec3 relative = chunkOrigin.relativeTo(camera);
                    assertEquals(x.shiftRight(4).shiftLeft(4).subtract(x).doubleValue() - 0.75D, relative.x);
                    assertEquals(-80.5D, relative.y);
                    assertEquals(z.shiftRight(4).shiftLeft(4).subtract(z).doubleValue() - 0.25D, relative.z);
                    Vec3 center = DebugRenderer.localPosition(CameraRelativePosition.of(camera),
                            camera.blockPosition(), 0.5D, 0.5D, 0.5D);
                    assertEquals(new Vec3(-0.25D, 0, 0.25D), center);
                }
            }
        }
    }

    @Test
    void localOffsetsSurviveHighHalvesAndLowWordCarry() {
        Int1088 x = Int1088.of(-1L, 123L);
        Int1088 z = Int1088.of(Long.MAX_VALUE, -456L);
        SectorVec3 camera = SectorVec3.fromBlockAndFraction(x, 0.75D, 80.5D, z, 0.25D);
        BlockPos block = new BlockPos(x.add(Int1088.ONE), 81, z.add(Int1088.ONE));
        Vec3 local = DebugRenderer.localPosition(CameraRelativePosition.of(camera), block, 0.5D, 0.25D, 0.5D);
        assertEquals(0.75D, local.x);
        assertEquals(0.75D, local.y);
        assertEquals(1.25D, local.z);
    }

    @Test
    void debugSnapshotsRetainExactIdentity() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int1088.of(1L, 100L), 0.5D, 80, Int1088.of(2L, -100L), 0.25D);
        UUID id = new UUID(1L, 2L);
        assertSame(position, new BeeDebugRenderer.BeeInfo(id, 1, position, null, null, null, 0).pos);
        assertSame(position, new BrainDebugRenderer.BrainDump(id, 1, "name", "profession", 0, 1, 1, position, "", null, false, 0).pos);
        assertSame(position, new GameEventListenerRenderer.TrackedGameEvent(0, null, position).position());
    }

    @Test
    void listenerLifetimeUsesExactDistanceAndMissingSourcesExpire() {
        SectorVec3 camera = SectorVec3.fromBlockAndFraction(Int1088.of(0L, 100L), 0, 80, Int1088.ZERO, 0);
        PositionSource source = new PositionSource() {
            public Optional<SectorVec3> exactPosition(Level level) {
                return Optional.of(camera.add(32, 0, 0));
            }
            public PositionSourceType<?> getType() { return null; }
        };
        var listener = new GameEventListenerRenderer.TrackedListener(source, 16);
        assertFalse(listener.isExpired(null, camera));
        assertTrue(listener.isExpired(null, camera.add(-1, 0, 0)));
        assertTrue(listener.isExpired(null, SectorVec3.fromBlockAndFraction(Int1088.ZERO, 0, 80, Int1088.ZERO, 0)));
        PositionSource missing = new PositionSource() {
            public Optional<SectorVec3> exactPosition(Level level) { return Optional.empty(); }
            public PositionSourceType<?> getType() { return null; }
        };
        assertTrue(new GameEventListenerRenderer.TrackedListener(missing, 16).isExpired(null, camera));
    }
}
