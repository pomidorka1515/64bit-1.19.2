package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundExplodePacketTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void preservesSplitExplosionOriginBeyondDoublePrecision() {
      long hugeX = (1L << 53) + 37L;
      long hugeZ = -((1L << 53) + 41L);
      SectorVec3 position = SectorVec3.fromBlockAndFraction(hugeX, 0.375D, 71.25D, hugeZ, 0.625D);
      List<BlockPos> affected = List.of(
            new BlockPos(hugeX - 3L, 70, hugeZ + 5L),
            new BlockPos(hugeX + 4L, 73, hugeZ - 2L));
      ClientboundExplodePacket sent = new ClientboundExplodePacket(position, 5.0F, affected,
            new Vec3(0.25D, -0.5D, 0.75D));

      FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
      sent.write(bytes);
      ClientboundExplodePacket received = new ClientboundExplodePacket(bytes);

      assertEquals(position, received.getExactPosition());
      assertEquals(affected, received.getToBlow());
      assertEquals(5.0F, received.getPower());
      assertEquals(0.25F, received.getKnockbackX());
      assertEquals(-0.5F, received.getKnockbackY());
      assertEquals(0.75F, received.getKnockbackZ());
   }
}
