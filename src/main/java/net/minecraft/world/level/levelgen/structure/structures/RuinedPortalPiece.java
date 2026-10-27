package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.AlwaysTrueTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlackstoneReplaceProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockAgeProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.LavaSubmergedBlockProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProcessorRule;
import net.minecraft.world.level.levelgen.structure.templatesystem.ProtectedBlockProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.RandomBlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public class RuinedPortalPiece extends TemplateStructurePiece {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float PROBABILITY_OF_GOLD_GONE = 0.3F;
    private static final float PROBABILITY_OF_MAGMA_INSTEAD_OF_NETHERRACK = 0.07F;
    private static final float PROBABILITY_OF_MAGMA_INSTEAD_OF_LAVA = 0.2F;
    private final RuinedPortalPiece.VerticalPlacement verticalPlacement;
    private final RuinedPortalPiece.Properties properties;

    public RuinedPortalPiece(StructureTemplateManager pStructureTemplateManager, BlockPos pTemplatePosition, RuinedPortalPiece.VerticalPlacement pVerticalPlacement, RuinedPortalPiece.Properties pProperties, ResourceLocation pLocation, StructureTemplate pTemplate, Rotation pRotation, Mirror pMirror, BlockPos pPivotPos) {
        super(StructurePieceType.RUINED_PORTAL, 0, pStructureTemplateManager, pLocation, pLocation.toString(), makeSettings(pMirror, pRotation, pVerticalPlacement, pPivotPos, pProperties), pTemplatePosition);
        this.verticalPlacement = pVerticalPlacement;
        this.properties = pProperties;
    }

    public RuinedPortalPiece(StructureTemplateManager pStructureTemplateManager, CompoundTag pTag) {
        super(StructurePieceType.RUINED_PORTAL, pTag, pStructureTemplateManager, (p_229188_) -> {
            return makeSettings(pStructureTemplateManager, pTag, p_229188_);
        });
        this.verticalPlacement = RuinedPortalPiece.VerticalPlacement.byName(pTag.getString("VerticalPlacement"));
        this.properties = RuinedPortalPiece.Properties.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, pTag.get("Properties"))).getOrThrow(true, LOGGER::error);
    }

    protected void addAdditionalSaveData(StructurePieceSerializationContext pContext, CompoundTag pTag) {
        super.addAdditionalSaveData(pContext, pTag);
        pTag.putString("Rotation", this.placeSettings.getRotation().name());
        pTag.putString("Mirror", this.placeSettings.getMirror().name());
        pTag.putString("VerticalPlacement", this.verticalPlacement.getName());
        RuinedPortalPiece.Properties.CODEC.encodeStart(NbtOps.INSTANCE, this.properties).resultOrPartial(LOGGER::error).ifPresent((p_229177_) -> {
            pTag.put("Properties", p_229177_);
        });
    }

    private static StructurePlaceSettings makeSettings(StructureTemplateManager pStructureTemplateManager, CompoundTag pTag, ResourceLocation pLocation) {
        StructureTemplate structuretemplate = pStructureTemplateManager.getOrCreate(pLocation);
        BlockPos blockpos = new BlockPos(structuretemplate.getSize().getX().shiftRight(1), 0, structuretemplate.getSize().getZ().shiftRight(1));
        return makeSettings(Mirror.valueOf(pTag.getString("Mirror")), Rotation.valueOf(pTag.getString("Rotation")), RuinedPortalPiece.VerticalPlacement.byName(pTag.getString("VerticalPlacement")), blockpos, RuinedPortalPiece.Properties.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, pTag.get("Properties"))).getOrThrow(true, LOGGER::error));
    }

    private static StructurePlaceSettings makeSettings(Mirror pMirror, Rotation pRotation, RuinedPortalPiece.VerticalPlacement pVerticalPlacement, BlockPos pPos, RuinedPortalPiece.Properties pProperties) {
        BlockIgnoreProcessor blockignoreprocessor = pProperties.airPocket ? BlockIgnoreProcessor.STRUCTURE_BLOCK : BlockIgnoreProcessor.STRUCTURE_AND_AIR;
        List<ProcessorRule> list = Lists.newArrayList();
        list.add(getBlockReplaceRule(Blocks.GOLD_BLOCK, 0.3F, Blocks.AIR));
        list.add(getLavaProcessorRule(pVerticalPlacement, pProperties));
        if (!pProperties.cold) {
            list.add(getBlockReplaceRule(Blocks.NETHERRACK, 0.07F, Blocks.MAGMA_BLOCK));
        }

        StructurePlaceSettings structureplacesettings = (new StructurePlaceSettings()).setRotation(pRotation).setMirror(pMirror).setRotationPivot(pPos).addProcessor(blockignoreprocessor).addProcessor(new RuleProcessor(list)).addProcessor(new BlockAgeProcessor(pProperties.mossiness)).addProcessor(new ProtectedBlockProcessor(BlockTags.FEATURES_CANNOT_REPLACE)).addProcessor(new LavaSubmergedBlockProcessor());
        if (pProperties.replaceWithBlackstone) {
            structureplacesettings.addProcessor(BlackstoneReplaceProcessor.INSTANCE);
        }

        return structureplacesettings;
    }

    private static ProcessorRule getLavaProcessorRule(RuinedPortalPiece.VerticalPlacement pVerticalPlacement, RuinedPortalPiece.Properties pProperties) {
        if (pVerticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR) {
            return getBlockReplaceRule(Blocks.LAVA, Blocks.MAGMA_BLOCK);
        } else {
            return pProperties.cold ? getBlockReplaceRule(Blocks.LAVA, Blocks.NETHERRACK) : getBlockReplaceRule(Blocks.LAVA, 0.2F, Blocks.MAGMA_BLOCK);
        }
    }

    public void postProcess(WorldGenLevel pLevel, StructureManager pStructureManager, ChunkGenerator pGenerator, RandomSource pRandom, BoundingBox pBox, ChunkPos pChunkPos, BlockPos pPos) {
        BoundingBox boundingbox = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
        if (pBox.isInside(boundingbox.getCenter())) {
            pBox.encapsulate(boundingbox);
            super.postProcess(pLevel, pStructureManager, pGenerator, pRandom, pBox, pChunkPos, pPos);
            this.spreadNetherrack(pRandom, pLevel);
            this.addNetherrackDripColumnsBelowPortal(pRandom, pLevel);
            if (this.properties.vines || this.properties.overgrown) {
                BlockPos.betweenClosedStream(this.getBoundingBox()).forEach((p_229127_) -> {
                    if (this.properties.vines) {
                        this.maybeAddVines(pRandom, pLevel, p_229127_);
                    }

                    if (this.properties.overgrown) {
                        this.maybeAddLeavesAbove(pRandom, pLevel, p_229127_);
                    }
                });
            }
        }
    }

    protected void handleDataMarker(String pName, BlockPos pPos, ServerLevelAccessor pLevel, RandomSource pRandom, BoundingBox pBox) {
    }

    private void maybeAddVines(RandomSource pRandom, LevelAccessor pLevel, BlockPos pPos) {
        BlockState blockstate = pLevel.getBlockState(pPos);
        if (!blockstate.isAir() && !blockstate.is(Blocks.VINE)) {
            Direction direction = getRandomHorizontalDirection(pRandom);
            BlockPos blockpos = pPos.relative(direction);
            BlockState blockstate1 = pLevel.getBlockState(blockpos);
            if (blockstate1.isAir()) {
                if (Block.isFaceFull(blockstate.getCollisionShape(pLevel, pPos), direction)) {
                    BooleanProperty booleanproperty = VineBlock.getPropertyForFace(direction.getOpposite());
                    pLevel.setBlock(blockpos, Blocks.VINE.defaultBlockState().setValue(booleanproperty, Boolean.valueOf(true)), 3);
                }
            }
        }
    }

    private void maybeAddLeavesAbove(RandomSource pRandom, LevelAccessor pLevel, BlockPos pPos) {
        if (pRandom.nextFloat() < 0.5F && pLevel.getBlockState(pPos).is(Blocks.NETHERRACK) && pLevel.getBlockState(pPos.above()).isAir()) {
            pLevel.setBlock(pPos.above(), Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, Boolean.valueOf(true)), 3);
        }
    }

    private void addNetherrackDripColumnsBelowPortal(RandomSource pRandom, LevelAccessor pLevel) {
        Int1088 i = WorldBounds.addBlockOffset(this.boundingBox.minX(), 1L);
        Int1088 j = WorldBounds.subtractBlockOffset(this.boundingBox.maxX(), 1L);
        Int1088 k = WorldBounds.addBlockOffset(this.boundingBox.minZ(), 1L);
        Int1088 l = WorldBounds.subtractBlockOffset(this.boundingBox.maxZ(), 1L);
        if (i.compareTo(j) > 0 || k.compareTo(l) > 0) {
            return;
        }

        if (WorldBounds.localSpanExceeds(j, i) || WorldBounds.localSpanExceeds(l, k)) {
            throw new IllegalArgumentException("Portal footprint exceeds safe local span");
        }
        for (Int1088 i1 = i;; i1 = WorldBounds.addBlockOffset(i1, 1L)) {
            for (Int1088 j1 = k;; j1 = WorldBounds.addBlockOffset(j1, 1L)) {
                BlockPos blockpos = new BlockPos(i1, this.boundingBox.minY(), j1);
                if (pLevel.getBlockState(blockpos).is(Blocks.NETHERRACK)) {
                    this.addNetherrackDripColumn(pRandom, pLevel, blockpos.below());
                }

                if (j1.equals(l)) {
                    break;
                }
            }

            if (i1.equals(j)) {
                break;
            }
        }
    }

    private void addNetherrackDripColumn(RandomSource pRandom, LevelAccessor pLevel, BlockPos pPos) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = pPos.mutable();
        this.placeNetherrackOrMagma(pRandom, pLevel, blockpos$mutableblockpos);
        int i = 8;

        while (i > 0 && pRandom.nextFloat() < 0.5F) {
            blockpos$mutableblockpos.move(Direction.DOWN);
            --i;
            this.placeNetherrackOrMagma(pRandom, pLevel, blockpos$mutableblockpos);
        }
    }

    private void spreadNetherrack(RandomSource pRandom, LevelAccessor pLevel) {
        boolean flag = this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_LAND_SURFACE || this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR;
        BlockPos blockpos = this.boundingBox.getCenter();
        Int1088 i = blockpos.getX();
        Int1088 j = blockpos.getZ();
        float[] afloat = new float[] {1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F};
        int k = afloat.length;
        if (this.boundingBox.getXSpan().compareTo(Int1088.fromLong(Integer.MAX_VALUE)) > 0
                || this.boundingBox.getZSpan().compareTo(Int1088.fromLong(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Portal dimensions exceed local int range");
        }
        int l = Math.toIntExact((this.boundingBox.getXSpan().lowLimb() + this.boundingBox.getZSpan().lowLimb()) / 2);
        int i1 = pRandom.nextInt(Math.max(1, 8 - l / 2));
        int j1 = 3;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = BlockPos.ZERO.mutable();

        Int1088 k1 = WorldBounds.subtractBlockOffset(i, k);
        Int1088 l1 = WorldBounds.addBlockOffset(i, k);
        Int1088 i2 = WorldBounds.subtractBlockOffset(j, k);
        Int1088 j2 = WorldBounds.addBlockOffset(j, k);

        for (Int1088 k2 = k1;; k2 = WorldBounds.addBlockOffset(k2, 1L)) {
            for (Int1088 l2 = i2;; l2 = WorldBounds.addBlockOffset(l2, 1L)) {
                double i3 = WorldBounds.distance(k2, i) + WorldBounds.distance(l2, j);
                double j3 = Math.max(0.0D, i3 + (double) i1);
                if (j3 < (double) k) {
                    float f = afloat[(int) j3];
                    if (pRandom.nextDouble() < (double) f) {
                        int k3 = getSurfaceY(pLevel, k2, l2, this.verticalPlacement);
                        int l3 = flag ? k3 : Math.min(this.boundingBox.minY(), k3);
                        blockpos$mutableblockpos.set(k2, l3, l2);
                        if (Math.abs(l3 - this.boundingBox.minY()) <= 3 && this.canBlockBeReplacedByNetherrackOrMagma(pLevel, blockpos$mutableblockpos)) {
                            this.placeNetherrackOrMagma(pRandom, pLevel, blockpos$mutableblockpos);
                            if (this.properties.overgrown) {
                                this.maybeAddLeavesAbove(pRandom, pLevel, blockpos$mutableblockpos);
                            }

                            this.addNetherrackDripColumn(pRandom, pLevel, blockpos$mutableblockpos.below());
                        }
                    }
                }

                if (l2.equals(j2)) {
                    break;
                }
            }

            if (k2.equals(l1)) {
                break;
            }
        }
    }

    private boolean canBlockBeReplacedByNetherrackOrMagma(LevelAccessor pLevel, BlockPos pPos) {
        BlockState blockstate = pLevel.getBlockState(pPos);
        return !blockstate.is(Blocks.AIR) && !blockstate.is(Blocks.OBSIDIAN) && !blockstate.is(BlockTags.FEATURES_CANNOT_REPLACE) && (this.verticalPlacement == RuinedPortalPiece.VerticalPlacement.IN_NETHER || !blockstate.is(Blocks.LAVA));
    }

    private void placeNetherrackOrMagma(RandomSource p_229194_, LevelAccessor p_229195_, BlockPos p_229196_) {
        if (!this.properties.cold && p_229194_.nextFloat() < 0.07F) {
            p_229195_.setBlock(p_229196_, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        } else {
            p_229195_.setBlock(p_229196_, Blocks.NETHERRACK.defaultBlockState(), 3);
        }
    }

    private static int getSurfaceY(LevelAccessor pLevel, Int1088 pX, Int1088 pZ, RuinedPortalPiece.VerticalPlacement pVerticalPlacement) {
        return pLevel.getHeight(getHeightMapType(pVerticalPlacement), pX, pZ) - 1;
    }

    public static Heightmap.Types getHeightMapType(RuinedPortalPiece.VerticalPlacement pVerticalPlacement) {
        return pVerticalPlacement == RuinedPortalPiece.VerticalPlacement.ON_OCEAN_FLOOR ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.WORLD_SURFACE_WG;
    }

    private static ProcessorRule getBlockReplaceRule(Block pBlock, float pProbability, Block pReplaceBlock) {
        return new ProcessorRule(new RandomBlockMatchTest(pBlock, pProbability), AlwaysTrueTest.INSTANCE, pReplaceBlock.defaultBlockState());
    }

    private static ProcessorRule getBlockReplaceRule(Block pBlock, Block pReplaceBlock) {
        return new ProcessorRule(new BlockMatchTest(pBlock), AlwaysTrueTest.INSTANCE, pReplaceBlock.defaultBlockState());
    }

    public static class Properties {
        public static final Codec<RuinedPortalPiece.Properties> CODEC = RecordCodecBuilder.create((p_229214_) -> {
            return p_229214_.group(Codec.BOOL.fieldOf("cold").forGetter((p_229226_) -> {
                                return p_229226_.cold;
                            }),
                                    Codec.FLOAT.fieldOf("mossiness").forGetter((p_229224_) -> {
                                        return p_229224_.mossiness;
                                    }),
                                    Codec.BOOL.fieldOf("air_pocket").forGetter((p_229222_) -> {
                                        return p_229222_.airPocket;
                                    }),
                                    Codec.BOOL.fieldOf("overgrown").forGetter((p_229220_) -> {
                                        return p_229220_.overgrown;
                                    }),
                                    Codec.BOOL.fieldOf("vines").forGetter((p_229218_) -> {
                                        return p_229218_.vines;
                                    }),
                                    Codec.BOOL.fieldOf("replace_with_blackstone").forGetter((p_229216_) -> {
                                        return p_229216_.replaceWithBlackstone;
                                    }))
                    .apply(p_229214_, RuinedPortalPiece.Properties::new);
        });
        public boolean cold;
        public float mossiness;
        public boolean airPocket;
        public boolean overgrown;
        public boolean vines;
        public boolean replaceWithBlackstone;

        public Properties() {
        }

        public Properties(boolean p_229207_, float p_229208_, boolean p_229209_, boolean p_229210_, boolean p_229211_, boolean p_229212_) {
            this.cold = p_229207_;
            this.mossiness = p_229208_;
            this.airPocket = p_229209_;
            this.overgrown = p_229210_;
            this.vines = p_229211_;
            this.replaceWithBlackstone = p_229212_;
        }
    }

    public static enum VerticalPlacement implements StringRepresentable {
        ON_LAND_SURFACE("on_land_surface"),
        PARTLY_BURIED("partly_buried"),
        ON_OCEAN_FLOOR("on_ocean_floor"),
        IN_MOUNTAIN("in_mountain"),
        UNDERGROUND("underground"),
        IN_NETHER("in_nether");

        public static final StringRepresentable.EnumCodec<RuinedPortalPiece.VerticalPlacement> CODEC = StringRepresentable.fromEnum(RuinedPortalPiece.VerticalPlacement::values);
        private final String name;

        private VerticalPlacement(String p_229240_) {
            this.name = p_229240_;
        }

        public String getName() {
            return this.name;
        }

        public static RuinedPortalPiece.VerticalPlacement byName(String pName) {
            return CODEC.byName(pName);
        }

        public String getSerializedName() {
            return this.name;
        }
    }
}
