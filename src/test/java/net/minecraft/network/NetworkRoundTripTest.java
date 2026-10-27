package net.minecraft.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real packet registries, framing and codecs; no game loop, authentication or rendering. */
class NetworkRoundTripTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void framedHandshakeAcceptsMatchingBuildAndRejectsOldBuild() {
        for (int protocol : new int[] {SharedConstants.getProtocolVersion(), 0x4C534302}) {
            EmbeddedChannel client = endpoint(PacketFlow.CLIENTBOUND, ConnectionProtocol.HANDSHAKING);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel server = endpoint(PacketFlow.SERVERBOUND, ConnectionProtocol.HANDSHAKING, connection);
            ServerHandshakePacketListenerImpl handshake = new ServerHandshakePacketListenerImpl(null, connection);
            connection.setListener(handshake);
            try {
                FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.buffer());
                ClientIntentionPacket intention;
                try {
                    payload.writeVarInt(protocol).writeUtf("localhost").writeShort(25565);
                    payload.writeVarInt(ConnectionProtocol.LOGIN.getId());
                    intention = new ClientIntentionPacket(payload);
                } finally {
                    payload.release();
                }
                assertTrue(client.writeOutbound(intention));
                transfer(client, server);
                assertEquals(ConnectionProtocol.LOGIN, server.attr(Connection.ATTRIBUTE_PROTOCOL).get());
                assertNull(server.readInbound());
                if (protocol == SharedConstants.getProtocolVersion()) {
                    assertInstanceOf(ServerLoginPacketListenerImpl.class, connection.getPacketListener());
                    assertTrue(server.isActive());
                    assertNull(server.readOutbound());
                } else {
                    client.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.LOGIN);
                    transfer(server, client);
                    ClientboundLoginDisconnectPacket rejection = receive(client, ClientboundLoginDisconnectPacket.class);
                    assertTrue(rejection.getReason().getString().contains("matching 1088-bit coordinate fork jar"));
                    assertSame(handshake, connection.getPacketListener());
                    assertFalse(server.isActive());
                }
            } finally {
                client.finishAndReleaseAll();
                server.finishAndReleaseAll();
            }
        }
    }

    @Test
    void playConversationRetainsWideIdentityAcrossFragmentedAndCoalescedFrames() {
        EmbeddedChannel client = endpoint(PacketFlow.CLIENTBOUND, ConnectionProtocol.PLAY);
        EmbeddedChannel server = endpoint(PacketFlow.SERVERBOUND, ConnectionProtocol.PLAY);
        try {
            int teleportId = 0;
            for (Int1088 anchor : anchors()) {
                SectorVec3 origin = SectorVec3.fromBlockAndFraction(anchor, 0.375D, 100.25D, anchor, 0.625D);
                ChunkPos chunk = new ChunkPos(origin.blockPosition());
                server.writeOutbound(new ClientboundSetChunkCacheCenterPacket(chunk.x(), chunk.z()));
                server.writeOutbound(chunkPacket(chunk));
                server.writeOutbound(new ClientboundAddEntityPacket(7, new UUID(1, 2), origin,
                        0, 0, EntityType.PIG, 0, Vec3.ZERO, 0));
                server.writeOutbound(new ClientboundPlayerPositionPacket(origin, 90, -15, Set.of(), ++teleportId, true));
                transfer(server, client);

                ClientboundSetChunkCacheCenterPacket center = receive(client, ClientboundSetChunkCacheCenterPacket.class);
                assertEquals(chunk.x(), center.getX());
                assertEquals(chunk.z(), center.getZ());
                ClientboundLevelChunkWithLightPacket streamed = receive(client, ClientboundLevelChunkWithLightPacket.class);
                assertEquals(chunk.x(), streamed.getX());
                assertEquals(chunk.z(), streamed.getZ());
                assertEquals(42, streamed.getChunkData().getHeightmaps().getInt("marker"));
                FriendlyByteBuf sections = streamed.getChunkData().getReadBuffer();
                try {
                    byte[] bytes = new byte[sections.readableBytes()];
                    sections.readBytes(bytes);
                    assertArrayEquals(new byte[] {0, 1, -1, 42}, bytes);
                } finally {
                    sections.release();
                }
                assertEquals(BitSet.valueOf(new long[] {1L << 17}), streamed.getLightData().getSkyYMask());
                assertArrayEquals(new byte[2048], streamed.getLightData().getSkyUpdates().get(0));
                assertEquals(origin, receive(client, ClientboundAddEntityPacket.class).getExactPosition());
                ClientboundPlayerPositionPacket teleport = receive(client, ClientboundPlayerPositionPacket.class);
                assertEquals(origin, teleport.getExactPosition());
                assertEquals(teleportId, teleport.getId());
                assertTrue(teleport.getRelativeArguments().isEmpty());
                assertTrue(teleport.requestDismountVehicle());

                SectorVec3 moved = origin.add(-0.5D, 0.5D, -0.25D);
                client.writeOutbound(new ServerboundAcceptTeleportationPacket(teleport.getId()));
                client.writeOutbound(new ServerboundMovePlayerPacket.PosRot(moved, 90, -15, true));
                client.writeOutbound(new ServerboundMovePlayerPacket.Rot(45, 10, false));
                transfer(client, server);
                assertEquals(teleportId, receive(server, ServerboundAcceptTeleportationPacket.class).getId());
                ServerboundMovePlayerPacket.PosRot movement = receive(server, ServerboundMovePlayerPacket.PosRot.class);
                assertEquals(moved, movement.getExactPosition(origin));
                assertTrue(movement.isOnGround());
                assertEquals(90, movement.getYRot(0));
                assertEquals(moved, receive(server, ServerboundMovePlayerPacket.Rot.class).getExactPosition(moved));

                List<BlockPos> affected = List.of(moved.blockPosition());
                server.writeOutbound(new ClientboundSoundPacket(SoundEvents.NOTE_BLOCK_HARP, SoundSource.BLOCKS,
                        moved, 1.25F, 0.75F, 123L));
                server.writeOutbound(new ClientboundLevelParticlesPacket(ParticleTypes.FLAME, true,
                        moved, 1, 2, 3, 0.5F, 14));
                server.writeOutbound(new ClientboundExplodePacket(moved, 5, affected, new Vec3(0.25, -0.5, 0.75)));
                server.writeOutbound(new ClientboundForgetLevelChunkPacket(chunk.x(), chunk.z()));
                transfer(server, client);
                ClientboundSoundPacket sound = receive(client, ClientboundSoundPacket.class);
                assertEquals(moved, sound.getExactPosition());
                assertSame(SoundEvents.NOTE_BLOCK_HARP, sound.getSound());
                assertEquals(123L, sound.getSeed());
                ClientboundLevelParticlesPacket particle = receive(client, ClientboundLevelParticlesPacket.class);
                assertEquals(moved, particle.getExactPosition());
                assertSame(ParticleTypes.FLAME, particle.getParticle());
                assertEquals(14, particle.getCount());
                ClientboundExplodePacket explosion = receive(client, ClientboundExplodePacket.class);
                assertEquals(moved, explosion.getExactPosition());
                assertEquals(affected, explosion.getToBlow());
                assertEquals(0.25F, explosion.getKnockbackX());
                ClientboundForgetLevelChunkPacket forget = receive(client, ClientboundForgetLevelChunkPacket.class);
                assertEquals(chunk.x(), forget.getX());
                assertEquals(chunk.z(), forget.getZ());
                assertNull(client.readInbound());
                assertNull(server.readInbound());
            }
        } finally {
            client.finishAndReleaseAll();
            server.finishAndReleaseAll();
        }
    }

    @Test
    void smallCoordinatesRemainTwoBytesEach() {
        for (long value : new long[] {0, 1, -1}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeInt1088(Int1088.fromLong(value));
                assertEquals(2, buf.readableBytes());
                assertEquals(1, buf.readUnsignedByte());
                assertEquals(value < 0 ? 1 : value * 2, buf.readUnsignedByte());
                buf.readerIndex(0);
                assertEquals(Int1088.fromLong(value), buf.readInt1088());
                assertFalse(buf.isReadable());
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void excessiveLimbCountClosesRealConnectionBeforeDispatch() {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel server = endpoint(PacketFlow.SERVERBOUND, ConnectionProtocol.PLAY, connection);
        FriendlyByteBuf frame = new FriendlyByteBuf(Unpooled.buffer());
        try {
            int id = ConnectionProtocol.PLAY.getPacketId(PacketFlow.SERVERBOUND,
                    new ServerboundMovePlayerPacket.Pos(SectorVec3.fromBlockAndFraction(
                            Int1088.ZERO, 0, 100, Int1088.ZERO, 0), true));
            frame.writeVarInt(FriendlyByteBuf.getVarIntSize(id) + 1);
            frame.writeVarInt(id).writeVarInt(18); // no coordinate payload is needed
            server.writeInbound(frame.retain());
            server.runPendingTasks();
            assertFalse(server.isActive());
            assertNotNull(connection.getDisconnectedReason());
            assertTrue(connection.getDisconnectedReason().getString().contains("Invalid Int1088 limb count"));
            assertNull(server.readInbound());
        } finally {
            frame.release();
            server.finishAndReleaseAll();
        }
    }

    private static List<Int1088> anchors() {
        List<Int1088> values = new ArrayList<>(List.of(Int1088.ZERO, Int1088.ONE, Int1088.ONE.negate(),
                Int1088.ONE.shiftLeftSaturated(127), Int1088.ONE.shiftLeftSaturated(127).negate(),
                Int1088.ONE.shiftLeftSaturated(1024), Int1088.ONE.shiftLeftSaturated(1024).negate(),
                Int1088.MAX, Int1088.MIN));
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            Int1088 high = Int1088.ONE.shiftLeftSaturated(limb * 64);
            values.add(high.add(Int1088.fromLong(7)));
            values.add(high.negate().add(Int1088.fromLong(7)));
        }
        return values;
    }

    private static ClientboundLevelChunkWithLightPacket chunkPacket(ChunkPos chunk) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeChunkPos(chunk);
            CompoundTag heightmaps = new CompoundTag();
            heightmaps.putInt("marker", 42);
            buf.writeNbt(heightmaps);
            buf.writeByteArray(new byte[] {0, 1, -1, 42});
            buf.writeVarInt(0); // no block entities; section bytes are an opaque wire fixture
            buf.writeBoolean(true);
            buf.writeBitSet(BitSet.valueOf(new long[] {1L << 17}));
            for (int i = 0; i < 3; ++i) {
                buf.writeBitSet(new BitSet());
            }
            buf.writeVarInt(1);
            buf.writeByteArray(new byte[2048]);
            buf.writeVarInt(0);
            return new ClientboundLevelChunkWithLightPacket(buf);
        } finally {
            buf.release();
        }
    }

    private static EmbeddedChannel endpoint(PacketFlow receiving, ConnectionProtocol protocol) {
        return endpoint(receiving, protocol, null);
    }

    private static EmbeddedChannel endpoint(PacketFlow receiving, ConnectionProtocol protocol, Connection connection) {
        EmbeddedChannel channel = new EmbeddedChannel(new Varint21FrameDecoder(), new PacketDecoder(receiving),
                new Varint21LengthFieldPrepender(), new PacketEncoder(receiving.getOpposite()), connection);
        channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(protocol);
        return channel;
    }

    /** Coalesce consecutive packets, then split headers and payloads across arbitrary reads. */
    private static void transfer(EmbeddedChannel from, EmbeddedChannel to) {
        ByteBuf wire = Unpooled.buffer();
        try {
            ByteBuf part;
            while ((part = from.readOutbound()) != null) {
                try {
                    wire.writeBytes(part);
                } finally {
                    part.release();
                }
            }
            assertTrue(wire.isReadable());
            int[] fragments = {1, 2, 7, 31, 4096};
            int i = 0;
            while (wire.isReadable()) {
                to.writeInbound(wire.readRetainedSlice(Math.min(wire.readableBytes(), fragments[i++ % fragments.length])));
            }
            from.checkException();
            to.checkException();
        } finally {
            wire.release();
        }
    }

    private static <T extends Packet<?>> T receive(EmbeddedChannel channel, Class<T> type) {
        return assertInstanceOf(type, channel.readInbound());
    }
}
