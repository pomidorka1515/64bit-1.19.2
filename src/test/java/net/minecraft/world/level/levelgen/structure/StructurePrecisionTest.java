package net.minecraft.world.level.levelgen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructurePrecisionTest {
    private static final long[] ORIGINS = {0L, 52999999999999981L, -52999999999999981L,
            Long.MAX_VALUE - 512L, Long.MIN_VALUE + 512L};

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreMode() {
        FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
    }

    @Test
    void jigsawCollisionGeometryIsTranslationInvariantInEveryMode() {
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            FarlandsMode.setMode(mode);
            for (long origin : ORIGINS) {
                BlockPos frame = new BlockPos(origin, 64, origin);
                BoundingBox first = new BoundingBox(origin, 64, origin, origin + 6, 70, origin + 6);
                AABB local = first.toLocalAABB(frame);
                assertEquals(new AABB(0, 0, 0, 7, 7, 7), local);
                VoxelShape free = Shapes.join(Shapes.box(-80, -80, -80, 81, 81, 81), Shapes.create(local), BooleanOp.ONLY_FIRST);
                BoundingBox adjacent = new BoundingBox(origin + 7, 64, origin, origin + 13, 70, origin + 6);
                BoundingBox overlap = new BoundingBox(origin + 6, 64, origin, origin + 12, 70, origin + 6);
                assertFalse(Shapes.joinIsNotEmpty(free, Shapes.create(adjacent.toLocalAABB(frame).deflate(0.25D)), BooleanOp.ONLY_SECOND));
                assertTrue(Shapes.joinIsNotEmpty(free, Shapes.create(overlap.toLocalAABB(frame).deflate(0.25D)), BooleanOp.ONLY_SECOND));
            }
        }
    }

    @Test
    void upperHalfSphereHasTheSameBlocksAtEveryOriginAndMode() {
        Set<BlockPos> expected = new SpherePiece().generate(0L);
        assertFalse(expected.isEmpty());
        for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
            FarlandsMode.setMode(mode);
            for (long origin : ORIGINS) {
                assertEquals(expected, new SpherePiece().generate(origin), mode + " at " + origin);
            }
        }
    }

    @Test
    void infiniteBoundingBoxIncludesTheLongCoordinateWorld() {
        assertTrue(BoundingBox.infinite().isInside(new BlockPos(Long.MIN_VALUE, 0, Long.MAX_VALUE)));
    }

    @Test
    void savedJigsawJunctionsRetainLongCoordinates() {
        for (long origin : ORIGINS) {
            JigsawJunction junction = new JigsawJunction(origin, 64, origin, 0, StructureTemplatePool.Projection.RIGID);
            assertEquals(junction, JigsawJunction.deserialize(junction.serialize(NbtOps.INSTANCE)));
        }
    }

    private static class SpherePiece extends StructurePiece {
        private final Set<BlockPos> blocks = new HashSet<>();
        private long origin;

        SpherePiece() {
            super(StructurePieceType.MINE_SHAFT_ROOM, 0, new BoundingBox(BlockPos.ZERO));
        }

        Set<BlockPos> generate(long origin) {
            this.origin = origin;
            generateUpperHalfSphere(null, null, origin, 0, origin, origin + 10, 6, origin + 10, null, false);
            return blocks;
        }

        @Override
        protected void placeBlock(WorldGenLevel level, BlockState state, long x, int y, long z, BoundingBox box) {
            blocks.add(new BlockPos(x - origin, y, z - origin));
        }

        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

        public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pos) {}
    }
}
