package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class VecDeltaCodecTest {
   private static final long HUGE_X = 53_905_378_846_979_123L;
   private static final long HUGE_Z = -53_905_378_846_979_121L;

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
}
