package net.minecraft.world.level.levelgen.structure.pools;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Int128;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JigsawPlacementPrecisionTest {
    private static final ResourceLocation POOL = new ResourceLocation("test:rooms");
    private static final ResourceLocation EMPTY = new ResourceLocation("minecraft:empty");

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
    void actualPieceAssemblyMatchesNearOriginInEveryMode() {
        for (Rotation rotation : Rotation.values()) {
            List<BoundingBox> expected = assemble(0, rotation);
            assertTrue(expected.size() > 2, "must actually connect rooms");
            for (FarlandsMode.Mode mode : FarlandsMode.Mode.values()) {
                FarlandsMode.setMode(mode);
                for (long origin : new long[] {52999999999999981L, -52999999999999981L,
                        Long.MAX_VALUE - 512L, Long.MIN_VALUE + 512L}) {
                    assertEquals(expected, assemble(origin, rotation), mode + " / " + rotation + " / " + origin);
                }
            }
        }
    }

    private static List<BoundingBox> assemble(long origin, Rotation rotation) {
        Registry<StructureTemplatePool> pools = new MappedRegistry<>(Registry.TEMPLATE_POOL_REGISTRY, Lifecycle.stable(), null);
        Room room = new Room();
        Registry.register(pools, EMPTY, new StructureTemplatePool(EMPTY, EMPTY, List.of()));
        Registry.register(pools, POOL, new StructureTemplatePool(POOL, EMPTY, List.of(Pair.of(room, 1))));
        BlockPos frame = new BlockPos(origin, 64, origin);
        BoundingBox box = room.getBoundingBox(null, frame, rotation);
        PoolElementStructurePiece start = new PoolElementStructurePiece(null, room, frame, 1, rotation, box);
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        pieces.add(start);
        JigsawPlacement.Placer placer = new JigsawPlacement.Placer(pools, 3, null, null, pieces, RandomSource.create(12), frame);
        placer.placing.add(new JigsawPlacement.PieceState(start, new MutableObject<>(Shapes.join(
                Shapes.box(-80, -80, -80, 81, 81, 81), Shapes.create(box.toLocalAABB(frame)), BooleanOp.ONLY_FIRST)), 0));
        while (!placer.placing.isEmpty()) {
            JigsawPlacement.PieceState state = placer.placing.removeFirst();
            placer.tryPlacingChildren(state.piece, state.free, state.depth, false, null, null);
        }
        return pieces.stream().map(piece -> {
            BoundingBox b = piece.getBoundingBox();
            return new BoundingBox(b.minX().subtract(Int128.fromLong(origin)), b.minY(), b.minZ().subtract(Int128.fromLong(origin)),
                    b.maxX().subtract(Int128.fromLong(origin)), b.maxY(), b.maxZ().subtract(Int128.fromLong(origin)));
        }).toList();
    }

    private static final class Room extends StructurePoolElement {
        Room() {
            super(StructureTemplatePool.Projection.RIGID);
        }

        public Vec3i getSize(StructureTemplateManager manager, Rotation rotation) {
            return new Vec3i(7, 7, 7);
        }

        public List<StructureTemplate.StructureBlockInfo> getShuffledJigsawBlocks(StructureTemplateManager manager,
                BlockPos pos, Rotation rotation, RandomSource random) {
            List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos local = new BlockPos(3 + 3 * direction.getStepX(), 1, 3 + 3 * direction.getStepZ());
                CompoundTag nbt = new CompoundTag();
                nbt.putString("pool", POOL.toString());
                nbt.putString("name", "test:door");
                nbt.putString("target", "test:door");
                result.add(new StructureTemplate.StructureBlockInfo(
                        StructureTemplate.transform(local, net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO).offset(pos),
                        Blocks.JIGSAW.defaultBlockState().setValue(JigsawBlock.ORIENTATION,
                                FrontAndTop.fromFrontAndTop(rotation.rotate(direction), Direction.UP)), nbt));
            }
            return result;
        }

        public BoundingBox getBoundingBox(StructureTemplateManager manager, BlockPos pos, Rotation rotation) {
            return BoundingBox.fromCorners(pos, StructureTemplate.transform(new BlockPos(6, 6, 6),
                    net.minecraft.world.level.block.Mirror.NONE, rotation, BlockPos.ZERO).offset(pos));
        }

        public boolean place(StructureTemplateManager manager, WorldGenLevel level, StructureManager structures,
                ChunkGenerator generator, BlockPos pos, BlockPos pivot, Rotation rotation, BoundingBox box,
                RandomSource random, boolean keepJigsaws) {
            return true;
        }

        public StructurePoolElementType<?> getType() {
            return StructurePoolElementType.SINGLE;
        }
    }
}
