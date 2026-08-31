package net.minecraft.world.level.block.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ContainerOpenersCounterTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void recheckRangePreservesNearbyOpenersBeyondFloatPrecision() {
      long huge = (1L << 53) + 37L;
      BlockPos chest = new BlockPos(huge, 64, -huge);
      SectorVec3 nearbyPlayer = SectorVec3.fromBlockAndFraction(huge + 5L, 0.75D, 70.0D, -huge - 5L, 0.25D);
      SectorVec3 distantPlayer = SectorVec3.fromBlockAndFraction(huge + 6L, 0.25D, 64.5D, -huge, 0.5D);

      assertTrue(ContainerOpenersCounter.isWithinRecheckRange(nearbyPlayer, chest));
      assertFalse(ContainerOpenersCounter.isWithinRecheckRange(distantPlayer, chest));
   }
}
