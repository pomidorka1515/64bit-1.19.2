package net.minecraft.network.protocol.game;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Set;
import java.util.BitSet;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WidePositionPacketTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void entityTeleportPlayerAndVehicleRetainWidePositions() {
        for (Int1088 x : new Int1088[] {Int1088.ONE.shiftLeftSaturated(127),
                Int1088.ONE.shiftLeftSaturated(128), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MAX, Int1088.MIN}) {
            SectorVec3 pos = SectorVec3.fromBlockAndFraction(x, 0.375D, 42.5D,
                    x.equals(Int1088.MIN) ? Int1088.MAX : x.negate(), 0.625D);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new ClientboundAddEntityPacket(7, UUID.randomUUID(), pos, 0, 0, EntityType.PIG, 0, Vec3.ZERO, 0).write(buf);
                SectorVec3 decoded = new ClientboundAddEntityPacket(buf).getExactPosition();
                assertEquals(x, decoded.blockX());
                assertEquals(pos.blockZ(), decoded.blockZ());
                assertEquals(0.375D, decoded.subX());
                assertEquals(0.625D, decoded.subZ());
                assertEquals(pos, decoded);
                buf.clear();
                new ClientboundPlayerPositionPacket(pos, 0, 0, Set.of(), 1, false).write(buf);
                assertEquals(pos, new ClientboundPlayerPositionPacket(buf).getExactPosition());
                buf.clear();
                buf.writeVarInt(7);
                buf.writeSectorVec3(pos);
                buf.writeByte(0).writeByte(0).writeBoolean(false);
                ClientboundTeleportEntityPacket teleport = new ClientboundTeleportEntityPacket(buf);
                assertEquals(pos, teleport.getExactPosition());
                buf.clear();
                teleport.write(buf);
                assertEquals(pos, new ClientboundTeleportEntityPacket(buf).getExactPosition());
                buf.clear();
                buf.writeSectorVec3(pos);
                buf.writeFloat(0).writeFloat(0);
                ServerboundMoveVehiclePacket vehicle = new ServerboundMoveVehiclePacket(buf);
                assertEquals(pos, vehicle.getExactPosition());
                buf.clear();
                vehicle.write(buf);
                assertEquals(pos, new ServerboundMoveVehiclePacket(buf).getExactPosition());
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void chunkAndLightKeepWideChunkAndLocalLightMasks() {
        Int1088 x = Int1088.ONE.shiftLeftSaturated(1024);
        ChunkPos chunk = new ChunkPos(x, x.negate());
        BitSet sky = new BitSet();
        sky.set(17);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeChunkPos(chunk);
            writeLight(buf, sky);
            ClientboundLightUpdatePacket light = new ClientboundLightUpdatePacket(buf);
            assertEquals(chunk.x(), light.getX());
            assertEquals(chunk.z(), light.getZ());
            assertEquals(sky, light.getLightData().getSkyYMask());
            buf.clear();
            light.write(buf);
            assertEquals(sky, new ClientboundLightUpdatePacket(buf).getLightData().getSkyYMask());

            buf.clear();
            buf.writeChunkPos(chunk);
            buf.writeNbt(new CompoundTag());
            buf.writeVarInt(0); // section data
            buf.writeVarInt(0); // block entities
            writeLight(buf, sky);
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(buf);
            assertEquals(chunk.x(), packet.getX());
            assertEquals(chunk.z(), packet.getZ());
            assertEquals(sky, packet.getLightData().getSkyYMask());
            buf.clear();
            packet.write(buf);
            assertEquals(chunk.x(), new ClientboundLevelChunkWithLightPacket(buf).getX());
        } finally {
            buf.release();
        }
    }

    private static void writeLight(FriendlyByteBuf buf, BitSet sky) {
        buf.writeBoolean(true);
        buf.writeBitSet(sky);
        buf.writeBitSet(new BitSet());
        buf.writeBitSet(new BitSet());
        buf.writeBitSet(new BitSet());
        buf.writeVarInt(1);
        buf.writeByteArray(new byte[2048]);
        buf.writeVarInt(0);
    }

    @Test
    void movementRejectsExcessiveLimbCountBeforePayload() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(18);
            assertThrows(DecoderException.class, () -> ServerboundMovePlayerPacket.Pos.read(buf));
        } finally {
            buf.release();
        }
    }
}
