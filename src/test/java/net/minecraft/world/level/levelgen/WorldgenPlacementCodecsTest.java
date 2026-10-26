package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Dynamic;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SpikeConfiguration;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class WorldgenPlacementCodecsTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void optionalWorldAnchorsRoundTripWithoutProjectingIdentity() {
        for (Int128 x : new Int128[] {Int128.MIN, Int128.MAX, Int128.of(17, Long.MIN_VALUE), Int128.fromLong(-1)}) {
            BlockPos anchor = new BlockPos(x, 64, Int128.of(-9, Long.MAX_VALUE));
            var gateway = EndGatewayConfiguration.knownExit(anchor, true);
            var encoded = EndGatewayConfiguration.CODEC.encodeStart(NbtOps.INSTANCE, gateway).result().orElseThrow();
            assertEquals(anchor, EndGatewayConfiguration.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow().getExit().orElseThrow());
            var spike = new SpikeConfiguration(false, List.of(), anchor);
            var spikeTag = SpikeConfiguration.CODEC.encodeStart(NbtOps.INSTANCE, spike).result().orElseThrow();
            assertEquals(anchor, SpikeConfiguration.CODEC.parse(NbtOps.INSTANCE, spikeTag).result().orElseThrow().getCrystalBeamTarget());
            var junction = new JigsawJunction(x, 64, anchor.getZ(), 3, StructureTemplatePool.Projection.RIGID);
            var tag = (CompoundTag) junction.serialize(NbtOps.INSTANCE).getValue();
            assertEquals(x.lo(), tag.getLong("source_x"));
            assertEquals(x.hi(), tag.getLong("source_xHi"));
            assertEquals(junction, JigsawJunction.deserialize(new Dynamic<>(NbtOps.INSTANCE, tag)));
        }
    }

    @Test
    void absentAnchorsRemainOptionalButPresentLegacyOrMalformedOnesFail() {
        assertTrue(EndGatewayConfiguration.CODEC.parse(JsonOps.INSTANCE, json("{\"exact\":false}")).result().orElseThrow().getExit().isEmpty());
        assertNull(SpikeConfiguration.CODEC.parse(JsonOps.INSTANCE, json("{\"spikes\":[]}")).result().orElseThrow().getCrystalBeamTarget());
        for (String invalid : new String[] {"[1,64,2]", "[0,1,64,0]", "[0,1,64,0,2,3]", "\"foreign\"", "null"}) {
            assertTrue(EndGatewayConfiguration.CODEC.parse(JsonOps.INSTANCE, json("{\"exact\":true,\"exit\":" + invalid + "}")).error().isPresent(), invalid);
            assertTrue(SpikeConfiguration.CODEC.parse(JsonOps.INSTANCE, json("{\"spikes\":[],\"crystal_beam_target\":" + invalid + "}")).error().isPresent(), invalid);
        }
    }

    @Test
    void placementOffsetIsStrictAndLocateRetainsExactChunkAnchor() {
        JsonObject placement = json("{\"spacing\":32,\"separation\":8,\"salt\":1234}");
        var decoded = RandomSpreadStructurePlacement.CODEC.parse(JsonOps.INSTANCE, placement).result().orElseThrow();
        assertEquals(new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 1234).getPotentialStructureChunk(42, -100, 200), decoded.getPotentialStructureChunk(42, -100, 200));
        Vec3i offset = new Vec3i(3, 4, -5);
        placement.add("locate_offset", Vec3i.CODEC.encodeStart(JsonOps.INSTANCE, offset).result().orElseThrow());
        decoded = RandomSpreadStructurePlacement.CODEC.parse(JsonOps.INSTANCE, placement).result().orElseThrow();
        ChunkPos chunk = new ChunkPos(Int128.of(17, 42), Int128.of(-9, -42));
        assertEquals(new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ()).offset(offset), decoded.getLocatePos(chunk));
        var roundTrip = RandomSpreadStructurePlacement.CODEC.encodeStart(JsonOps.INSTANCE, decoded).result().orElseThrow();
        assertEquals(decoded.getLocatePos(chunk), RandomSpreadStructurePlacement.CODEC.parse(JsonOps.INSTANCE, roundTrip).result().orElseThrow().getLocatePos(chunk));
        for (String invalid : new String[] {"[3,4,-5]", "[0,16,0,0,0]", "[1,3,4,0,-5]", "null"}) {
            placement.add("locate_offset", JsonParser.parseString(invalid));
            assertTrue(RandomSpreadStructurePlacement.CODEC.parse(JsonOps.INSTANCE, placement).error().isPresent(), invalid);
        }
    }

    @Test
    void predicatePlacementOffsetsRejectLegacyDataInsteadOfDefaulting() {
        var codec = net.minecraft.world.level.levelgen.blockpredicates.InsideWorldBoundsPredicate.CODEC;
        assertTrue(codec.parse(JsonOps.INSTANCE, json("{}")).result().isPresent());
        assertTrue(codec.parse(JsonOps.INSTANCE, json("{\"offset\":[0,1,2,0,3]}")).result().isPresent());
        for (String invalid : new String[] {"[1,2,3]", "[0,16,0,0,0]", "null", "\"foreign\""}) {
            assertTrue(codec.parse(JsonOps.INSTANCE, json("{\"offset\":" + invalid + "}")).error().isPresent(), invalid);
        }
    }

    @Test
    void foreignJigsawCoordinateHalvesCannotBeTruncatedOrDefaulted() {
        JigsawJunction junction = new JigsawJunction(Int128.of(17, Long.MIN_VALUE), 64, Int128.of(-9, Long.MAX_VALUE), 3, StructureTemplatePool.Projection.RIGID);
        JsonObject encoded = junction.serialize(JsonOps.INSTANCE).getValue().getAsJsonObject();
        assertEquals(junction, JigsawJunction.deserialize(new Dynamic<>(JsonOps.INSTANCE, encoded)));
        for (String invalid : new String[] {"1.5", "9223372036854775808", "-9223372036854775809", "\"0\"", "null"}) {
            JsonObject malformed = encoded.deepCopy();
            malformed.add("source_xHi", JsonParser.parseString(invalid));
            assertThrows(IllegalArgumentException.class, () -> JigsawJunction.deserialize(new Dynamic<>(JsonOps.INSTANCE, malformed)), invalid);
        }
        encoded.remove("source_xHi");
        assertThrows(IllegalArgumentException.class, () -> JigsawJunction.deserialize(new Dynamic<>(JsonOps.INSTANCE, encoded)));
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
