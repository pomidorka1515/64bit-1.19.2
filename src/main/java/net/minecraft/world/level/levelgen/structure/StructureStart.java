package net.minecraft.world.level.levelgen.structure;

import com.mojang.logging.LogUtils;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.slf4j.Logger;

public final class StructureStart {
    public static final String INVALID_START_ID = "INVALID";
    public static final StructureStart INVALID_START = new StructureStart((Structure) null, new ChunkPos(0, 0), 0, new PiecesContainer(List.of()));
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Structure structure;
    private final PiecesContainer pieceContainer;
    private final ChunkPos chunkPos;
    private int references;
    @Nullable
    private volatile BoundingBox cachedBoundingBox;

    public StructureStart(Structure pStructure, ChunkPos pChunkPos, int pReferences, PiecesContainer pPieceContainer) {
        this.structure = pStructure;
        this.chunkPos = pChunkPos;
        this.references = pReferences;
        this.pieceContainer = pPieceContainer;
    }

    @Nullable
    public static StructureStart loadStaticStart(StructurePieceSerializationContext p_226858_, CompoundTag p_226859_, long p_226860_) {
        String s = p_226859_.getString("id");
        if ("INVALID".equals(s)) {
            return INVALID_START;
        } else {
            Registry<Structure> registry = p_226858_.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
            Structure structure = registry.get(new ResourceLocation(s));
            if (structure == null) {
                LOGGER.error("Unknown stucture id: {}", (Object) s);
                return null;
            } else {
                Int1088 chunkX = NbtUtils.readInt1088(p_226859_, "ChunkX");
                Int1088 chunkZ = NbtUtils.readInt1088(p_226859_, "ChunkZ");
                if (chunkX.compareTo(ChunkPos.MIN_CHUNK_COORDINATE) < 0 || chunkX.compareTo(ChunkPos.MAX_CHUNK_COORDINATE) > 0
                        || chunkZ.compareTo(ChunkPos.MIN_CHUNK_COORDINATE) < 0 || chunkZ.compareTo(ChunkPos.MAX_CHUNK_COORDINATE) > 0) {
                    throw new IllegalArgumentException("Structure start is outside the chunk coordinate domain");
                }
                ChunkPos chunkpos = new ChunkPos(chunkX, chunkZ);
                int i = p_226859_.getInt("references");
                ListTag listtag = p_226859_.getList("Children", 10);

                try {
                    PiecesContainer piecescontainer = PiecesContainer.load(listtag, p_226858_);
                    if (structure instanceof OceanMonumentStructure) {
                        piecescontainer = OceanMonumentStructure.regeneratePiecesAfterLoad(chunkpos, p_226860_, piecescontainer);
                    }

                    return new StructureStart(structure, chunkpos, i, piecescontainer);
                } catch (Exception exception) {
                    LOGGER.error("Failed Start with id {}", s, exception);
                    return null;
                }
            }
        }
    }

    public BoundingBox getBoundingBox() {
        BoundingBox boundingbox = this.cachedBoundingBox;
        if (boundingbox == null) {
            boundingbox = this.structure.adjustBoundingBox(this.pieceContainer.calculateBoundingBox());
            this.cachedBoundingBox = boundingbox;
        }

        return boundingbox;
    }

    public void placeInChunk(WorldGenLevel pLevel, StructureManager pStructureManager, ChunkGenerator pGenerator, RandomSource pRandom, BoundingBox pBox, ChunkPos pChunkPos) {
        List<StructurePiece> list = this.pieceContainer.pieces();
        if (!list.isEmpty()) {
            BoundingBox boundingbox = (list.get(0)).boundingBox;
            BlockPos blockpos = boundingbox.getCenter();
            BlockPos blockpos1 = new BlockPos(blockpos.getX(), boundingbox.minY(), blockpos.getZ());

            for (StructurePiece structurepiece : list) {
                if (structurepiece.getBoundingBox().intersects(pBox)) {
                    structurepiece.postProcess(pLevel, pStructureManager, pGenerator, pRandom, pBox, pChunkPos, blockpos1);
                }
            }

            this.structure.afterPlace(pLevel, pStructureManager, pGenerator, pRandom, pBox, pChunkPos, this.pieceContainer);
        }
    }

    public CompoundTag createTag(StructurePieceSerializationContext pContext, ChunkPos pChunkPos) {
        CompoundTag compoundtag = new CompoundTag();
        if (this.isValid()) {
            compoundtag.putString("id", pContext.registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY).getKey(this.structure).toString());
            NbtUtils.writeInt1088(compoundtag, "ChunkX", pChunkPos.x());
            NbtUtils.writeInt1088(compoundtag, "ChunkZ", pChunkPos.z());
            compoundtag.putInt("references", this.references);
            compoundtag.put("Children", this.pieceContainer.save(pContext));
            return compoundtag;
        } else {
            compoundtag.putString("id", "INVALID");
            return compoundtag;
        }
    }

    public boolean isValid() {
        return !this.pieceContainer.isEmpty();
    }

    public ChunkPos getChunkPos() {
        return this.chunkPos;
    }

    public boolean canBeReferenced() {
        return this.references < this.getMaxReferences();
    }

    public void addReference() {
        ++this.references;
    }

    public int getReferences() {
        return this.references;
    }

    protected int getMaxReferences() {
        return 1;
    }

    public Structure getStructure() {
        return this.structure;
    }

    public List<StructurePiece> getPieces() {
        return this.pieceContainer.pieces();
    }
}
