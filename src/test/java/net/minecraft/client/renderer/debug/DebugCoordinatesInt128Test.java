package net.minecraft.client.renderer.debug;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.renderer.CameraRelativePosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.PositionSourceType;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugCoordinatesInt128Test {
    @Test
    void localOffsetsSurviveHighHalvesAndLowWordCarry() {
        Int128 x = Int128.of(123L, -1L);
        Int128 z = Int128.of(-456L, Long.MAX_VALUE);
        SectorVec3 camera = SectorVec3.fromBlockAndFraction(x, 0.75D, 80.5D, z, 0.25D);
        BlockPos block = new BlockPos(x.add(Int128.ONE), 81, z.add(Int128.ONE));
        Vec3 local = DebugRenderer.localPosition(CameraRelativePosition.of(camera), block, 0.5D, 0.25D, 0.5D);
        assertEquals(0.75D, local.x);
        assertEquals(0.75D, local.y);
        assertEquals(1.25D, local.z);
    }

    @Test
    void debugSnapshotsRetainExactIdentity() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int128.of(100L, 1L), 0.5D, 80, Int128.of(-100L, 2L), 0.25D);
        UUID id = new UUID(1L, 2L);
        assertSame(position, new BeeDebugRenderer.BeeInfo(id, 1, position, null, null, null, 0).pos);
        assertSame(position, new BrainDebugRenderer.BrainDump(id, 1, "name", "profession", 0, 1, 1, position, "", null, false, 0).pos);
        assertSame(position, new GameEventListenerRenderer.TrackedGameEvent(0, null, position).position());
    }

    @Test
    void listenerLifetimeUsesExactDistanceAndMissingSourcesExpire() {
        SectorVec3 camera = SectorVec3.fromBlockAndFraction(Int128.of(100L, 0L), 0, 80, Int128.ZERO, 0);
        PositionSource source = new PositionSource() {
            public Optional<SectorVec3> exactPosition(Level level) {
                return Optional.of(camera.add(32, 0, 0));
            }
            public PositionSourceType<?> getType() { return null; }
        };
        var listener = new GameEventListenerRenderer.TrackedListener(source, 16);
        assertFalse(listener.isExpired(null, camera));
        assertTrue(listener.isExpired(null, camera.add(-1, 0, 0)));
        assertTrue(listener.isExpired(null, SectorVec3.fromBlockAndFraction(Int128.ZERO, 0, 80, Int128.ZERO, 0)));
        PositionSource missing = new PositionSource() {
            public Optional<SectorVec3> exactPosition(Level level) { return Optional.empty(); }
            public PositionSourceType<?> getType() { return null; }
        };
        assertTrue(new GameEventListenerRenderer.TrackedListener(missing, 16).isExpired(null, camera));
    }
}
