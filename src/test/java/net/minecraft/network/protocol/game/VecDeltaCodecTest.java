package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class VecDeltaCodecTest {
    private static final Int1088 HUGE_X = Int1088.of(-1L, 1234L);
    private static final Int1088 HUGE_Z = Int1088.of(1L, -1234L);

    @Test
    void repeatedRelativePacketsAdvanceFromPreviousPacketTarget() {
        VecDeltaCodec codec = new VecDeltaCodec();
        SectorVec3 spawn = SectorVec3.fromBlockAndFraction(HUGE_X, 0.125D, 64.0D,
                HUGE_Z, 0.875D);
        codec.setBase(spawn);

        SectorVec3 firstTarget = codec.decodeExact(1024L, 0L, -512L);
        codec.setBase(firstTarget);
        SectorVec3 secondTarget = codec.decodeExact(1024L, 0L, -512L);

        assertEquals(0.25D, firstTarget.relativeX(spawn), 0.0D);
        assertEquals(-0.125D, firstTarget.relativeZ(spawn), 0.0D);
        assertEquals(0.5D, secondTarget.relativeX(spawn), 0.0D);
        assertEquals(-0.25D, secondTarget.relativeZ(spawn), 0.0D);
        assertEquals(HUGE_X, secondTarget.blockX());
        assertEquals(HUGE_Z, secondTarget.blockZ());
    }

    @Test
    void zeroRelativePacketKeepsExactBaseObject() {
        VecDeltaCodec codec = new VecDeltaCodec();
        SectorVec3 base = SectorVec3.fromBlockAndFraction(HUGE_X, 0.333D, 80.0D,
                HUGE_Z, 0.777D);
        codec.setBase(base);

        assertSame(base, codec.decodeExact(0L, 0L, 0L));
    }

    @Test
    void relativeLimitsAndCrossHighHalfCarryRoundTrip() {
        VecDeltaCodec codec = new VecDeltaCodec();
        SectorVec3 base = SectorVec3.fromBlockAndFraction(HUGE_X, 0.875D, 64.0D, HUGE_Z, 0.125D);
        codec.setBase(base);
        assertSame(base, codec.getSectorBase());
        for (long steps : new long[] {-32768L, -1L, 0L, 1L, 32767L}) {
            double delta = steps / 4096.0D;
            SectorVec3 target = base.add(delta, delta, -delta);
            assertEquals(steps, codec.encodeX(target));
            assertEquals(steps, codec.encodeY(target));
            if (steps != -32768L) {
                assertEquals(-steps, codec.encodeZ(target));
                assertEquals(target, codec.decodeExact(steps, steps, -steps));
            }
        }
        SectorVec3 crossed = codec.decodeExact(1024L, 0L, -1024L);
        assertEquals(Int1088.of(0L, 1235L), crossed.blockX());
        assertEquals(Int1088.of(0L, -1234L), crossed.blockZ());
    }

    @Test
    void oversizedAndOverflowingDeltasRequireAbsolutePosition() {
        VecDeltaCodec codec = new VecDeltaCodec();
        for (Int1088 coordinate : new Int1088[] {Int1088.ZERO, Int1088.MIN, Int1088.MAX}) {
            SectorVec3 base = SectorVec3.fromBlockAndFraction(coordinate, 0.0D, 0.0D, coordinate, 0.0D);
            codec.setBase(base);
            Int1088 opposite = coordinate.equals(Int1088.MIN) ? Int1088.MAX : Int1088.MIN;
            SectorVec3 target = SectorVec3.fromBlockAndFraction(opposite, 0.0D, 0.0D, opposite, 0.0D);
            assertEquals(VecDeltaCodec.ABSOLUTE_POSITION_REQUIRED, codec.encodeX(target));
            assertEquals(VecDeltaCodec.ABSOLUTE_POSITION_REQUIRED, codec.encodeZ(target));
        }
        SectorVec3 base = SectorVec3.fromBlockAndFraction(HUGE_X, 0.0D, 0.0D, HUGE_Z, 0.0D);
        codec.setBase(base);
        for (double delta : new double[] {-8.000244140625D, 8.0D, 0x1.0p100}) {
            SectorVec3 target = base.add(delta, delta, delta);
            assertEquals(VecDeltaCodec.ABSOLUTE_POSITION_REQUIRED, codec.encodeX(target));
            assertEquals(VecDeltaCodec.ABSOLUTE_POSITION_REQUIRED, codec.encodeY(target));
            assertEquals(VecDeltaCodec.ABSOLUTE_POSITION_REQUIRED, codec.encodeZ(target));
        }
        for (long invalid : new long[] {-32769L, 32768L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> codec.decodeExact(invalid, 0L, 0L));
            assertThrows(IllegalArgumentException.class, () -> codec.decodeExact(0L, invalid, 0L));
            assertThrows(IllegalArgumentException.class, () -> codec.decodeExact(0L, 0L, invalid));
        }
    }

    @Test
    void outwardDeltasSaturateInsteadOfWrappingAtWorldEdges() {
        VecDeltaCodec codec = new VecDeltaCodec();
        codec.setBase(SectorVec3.fromBlockAndFraction(Int1088.MAX, 0.75D, 0.0D, Int1088.MIN, 0.0D));
        SectorVec3 target = codec.decodeExact(4096L, 0L, -4096L);
        assertEquals(Int1088.MAX, target.blockX());
        assertEquals(Int1088.MIN, target.blockZ());
        assertEquals(Math.nextDown(1.0D), target.subX());
        assertEquals(0.0D, target.subZ());
    }
}
