package net.minecraft.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CoordinateHandshakeTest {
    @BeforeAll
    static void initializeVersion() {
        SharedConstants.tryDetectVersion();
    }

    @Test
    void detectedAndBuiltInVersionsAdvertiseTheNewProtocol() {
        assertEquals(0x4C534303, SharedConstants.getProtocolVersion());
        assertEquals(0x4C534303, SharedConstants.getCurrentVersion().getProtocolVersion());
        assertEquals(0x4C534303, DetectedVersion.BUILT_IN.getProtocolVersion());
        assertTrue(SharedConstants.getCurrentVersion().getName().contains("1088-bit"));
    }

    @Test
    void matchingHandshakeRoundTripsAndInstallsLoginListener() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientIntentionPacket("localhost", 25565, ConnectionProtocol.LOGIN).write(buffer);
            ClientIntentionPacket decoded = new ClientIntentionPacket(buffer);
            assertEquals(0x4C534303, decoded.getProtocolVersion());
            assertEquals("localhost", decoded.getHostName());
            assertEquals(25565, decoded.getPort());
            assertEquals(0, buffer.readableBytes());
            RecordingConnection connection = new RecordingConnection();
            new ServerHandshakePacketListenerImpl(null, connection).handleIntention(decoded);
            assertEquals(ConnectionProtocol.LOGIN, connection.protocol);
            assertInstanceOf(ServerLoginPacketListenerImpl.class, connection.listener);
            assertTrue(connection.sent.isEmpty());
            assertNull(connection.reason);
        } finally {
            buffer.release();
        }
    }

    @Test
    void foreignProtocolsDisconnectBeforeInstallingLoginListener() {
        for (int protocol : new int[] {0, 760, 0x4C534301, 0x4C534302, 0x4C534304, Integer.MAX_VALUE}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(protocol);
                buffer.writeUtf("localhost");
                buffer.writeShort(25565);
                buffer.writeVarInt(ConnectionProtocol.LOGIN.getId());
                RecordingConnection connection = new RecordingConnection();
                new ServerHandshakePacketListenerImpl(null, connection)
                        .handleIntention(new ClientIntentionPacket(buffer));
                assertEquals(ConnectionProtocol.LOGIN, connection.protocol);
                assertNull(connection.listener);
                assertEquals(1, connection.sent.size());
                ClientboundLoginDisconnectPacket disconnect = assertInstanceOf(
                        ClientboundLoginDisconnectPacket.class, connection.sent.get(0));
                assertSame(connection.reason, disconnect.getReason());
                assertTrue(connection.reason.getString().contains("matching 1088-bit coordinate fork jar"));
            } finally {
                buffer.release();
            }
        }
    }

    private static final class RecordingConnection extends Connection {
        private ConnectionProtocol protocol;
        private PacketListener listener;
        private Component reason;
        private final List<Packet<?>> sent = new ArrayList<>();

        private RecordingConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void setProtocol(ConnectionProtocol protocol) {
            this.protocol = protocol;
        }

        @Override
        public void setListener(PacketListener listener) {
            this.listener = listener;
        }

        @Override
        public void send(Packet<?> packet) {
            this.sent.add(packet);
        }

        @Override
        public void disconnect(Component reason) {
            this.reason = reason;
        }
    }
}
