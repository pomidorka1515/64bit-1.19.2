package net.minecraft.world.level.levelgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructureIdentityInt1088Test {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void startsPiecesAndReferenceCacheKeepEveryChunkLimb() {
        var registries = RegistryAccess.builtinCopy();
        Structure structure = registries.registryOrThrow(Registry.STRUCTURE_REGISTRY)
                .get(new ResourceLocation("minecraft:mineshaft"));
        assertNotNull(structure);
        var context = new StructurePieceSerializationContext(ResourceManager.Empty.INSTANCE, registries, null);
        // Loaded-result paths require no generator or storage service. A cache miss fails the test.
        var check = new StructureCheck(null, registries, null, null, null, null, null, 0);
        List<ChunkPos> chunks = new ArrayList<>();
        chunks.add(new ChunkPos(0, 0));
        for (int bit = 64; bit <= 1024; bit += 64) {
            BigInteger coordinate = BigInteger.ONE.shiftLeft(bit);
            for (int sign : new int[] {1, -1}) {
                Int1088 x = Int1088.parseDecimal(coordinate.multiply(BigInteger.valueOf(sign)).toString());
                chunks.add(new ChunkPos(x, x.not()));
            }
        }
        chunks.add(new ChunkPos(ChunkPos.MIN_CHUNK_COORDINATE, ChunkPos.MAX_CHUNK_COORDINATE));
        chunks.add(new ChunkPos(ChunkPos.MAX_CHUNK_COORDINATE, ChunkPos.MIN_CHUNK_COORDINATE));

        for (int i = 0; i < chunks.size(); i++) {
            ChunkPos chunk = chunks.get(i);
            var room = new MineshaftPieces.MineShaftRoom(0, RandomSource.create(42),
                    chunk.getMinBlockX(), chunk.getMinBlockZ(), MineshaftStructure.Type.NORMAL);
            var start = new StructureStart(structure, chunk, i, new PiecesContainer(List.of(room)));
            var encoded = start.createTag(context, chunk);
            assertEquals(17, encoded.getLongArray("ChunkX").length);
            assertEquals(17, encoded.getLongArray("ChunkZ").length);
            assertEquals(chunk.x(), NbtUtils.readInt1088(encoded, "ChunkX"));
            assertEquals(chunk.z(), NbtUtils.readInt1088(encoded, "ChunkZ"));
            StructureStart restored = StructureStart.loadStaticStart(context, encoded, 0);
            assertNotNull(restored);
            assertEquals(chunk, restored.getChunkPos());
            assertEquals(start.getBoundingBox(), restored.getBoundingBox());
            assertEquals(i, restored.getReferences());
            assertEquals(1, restored.getPieces().size());
            assertEquals(room.createTag(context), restored.getPieces().get(0).createTag(context));
            check.onStructureLoad(chunk, Map.of(structure, restored));
        }
        for (int i = 0; i < chunks.size(); i++) {
            ChunkPos chunk = chunks.get(i);
            // Reconstruct values to exclude reference identity as a cache key.
            ChunkPos probe = new ChunkPos(Int1088.parseDecimal(chunk.x().toDecimalString()),
                    Int1088.parseDecimal(chunk.z().toDecimalString()));
            assertEquals(StructureCheckResult.START_PRESENT, check.checkStart(probe, structure, false));
            assertEquals(i == 0 ? StructureCheckResult.START_PRESENT : StructureCheckResult.START_NOT_PRESENT,
                    check.checkStart(probe, structure, true));
        }
        check.incrementReference(chunks.get(1), structure);
        assertEquals(StructureCheckResult.START_PRESENT, check.checkStart(chunks.get(0), structure, true));
        check.onStructureLoad(chunks.get(1), Map.of());
        assertEquals(StructureCheckResult.START_NOT_PRESENT, check.checkStart(chunks.get(1), structure, false));
        assertEquals(StructureCheckResult.START_PRESENT, check.checkStart(chunks.get(2), structure, false));
    }
}
