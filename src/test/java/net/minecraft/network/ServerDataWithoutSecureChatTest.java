package net.minecraft.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.netty.buffer.Unpooled;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import org.junit.jupiter.api.Test;

class ServerDataWithoutSecureChatTest {
    @Test
    void packetContainsNoSecureChatEnforcementFlag() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientboundServerDataPacket(Component.literal("Server"), null, false).write(buffer);
            assertEquals("Server", buffer.readOptional(FriendlyByteBuf::readComponent).orElseThrow().getString());
            assertFalse(buffer.readBoolean()); // No icon.
            assertFalse(buffer.readBoolean()); // No chat preview.
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
