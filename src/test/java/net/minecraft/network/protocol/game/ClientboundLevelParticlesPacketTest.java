package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundLevelParticlesPacketTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void preservesSplitParticleOriginBeyondDoublePrecision() {
      long hugeX = (1L << 53) + 17L;
      long hugeZ = -((1L << 53) + 29L);
      SectorVec3 position = SectorVec3.fromBlockAndFraction(hugeX, 0.375D, 72.25D, hugeZ, 0.625D);
      ClientboundLevelParticlesPacket sent = new ClientboundLevelParticlesPacket(ParticleTypes.FLAME, true,
            position, 1.25F, 2.5F, 3.75F, 0.5F, 14);

      FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
      sent.write(bytes);
      ClientboundLevelParticlesPacket received = new ClientboundLevelParticlesPacket(bytes);

      assertEquals(position, received.getExactPosition());
      assertEquals(1.25F, received.getXDist());
      assertEquals(2.5F, received.getYDist());
      assertEquals(3.75F, received.getZDist());
      assertEquals(0.5F, received.getMaxSpeed());
      assertEquals(14, received.getCount());
      assertEquals(true, received.isOverrideLimiter());
   }
}
