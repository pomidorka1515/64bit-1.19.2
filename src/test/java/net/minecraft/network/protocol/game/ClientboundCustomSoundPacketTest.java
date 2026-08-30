package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientboundCustomSoundPacketTest {
   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void preservesSplitCustomSoundOriginBeyondIntAndDoublePrecision() {
      SectorVec3 position = SectorVec3.fromBlockAndFraction((1L << 53) + 19L, 0.125D, 64.5D,
            -((1L << 53) + 23L), 0.875D);
      ClientboundCustomSoundPacket sent = new ClientboundCustomSoundPacket(new ResourceLocation("test", "far_sound"),
            SoundSource.AMBIENT, position, 0.5F, 1.5F, 192837465L);

      FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
      sent.write(bytes);
      ClientboundCustomSoundPacket received = new ClientboundCustomSoundPacket(bytes);

      assertEquals(position, received.getExactPosition());
      assertEquals(new ResourceLocation("test", "far_sound"), received.getName());
      assertEquals(0.5F, received.getVolume());
      assertEquals(1.5F, received.getPitch());
      assertEquals(192837465L, received.getSeed());
   }
}
