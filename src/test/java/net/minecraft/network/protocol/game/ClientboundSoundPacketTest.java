package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundSoundPacketTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void preservesSplitSoundOriginBeyondIntAndDoublePrecision() {
      SectorVec3 position = SectorVec3.fromBlockAndFraction((1L << 53) + 37L, 0.375D, 72.25D,
            -((1L << 53) + 41L), 0.625D);
      ClientboundSoundPacket sent = new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_HARP, SoundSource.BLOCKS,
            position, 1.25F, 0.75F, 918273645L);

      FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
      sent.write(bytes);
      ClientboundSoundPacket received = new ClientboundSoundPacket(bytes);

      assertEquals(position, received.getExactPosition());
      assertEquals(1.25F, received.getVolume());
      assertEquals(0.75F, received.getPitch());
      assertEquals(918273645L, received.getSeed());
   }
}
