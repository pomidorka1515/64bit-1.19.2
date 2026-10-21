package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Int128;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.NumericTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

public class StructureTemplate {
    public static final String PALETTE_TAG = "palette";
    public static final String PALETTE_LIST_TAG = "palettes";
    public static final String ENTITIES_TAG = "entities";
    public static final String BLOCKS_TAG = "blocks";
    public static final String BLOCK_TAG_POS = "pos";
    public static final String BLOCK_TAG_STATE = "state";
    public static final String BLOCK_TAG_NBT = "nbt";
    public static final String ENTITY_TAG_POS = "pos";
    public static final String ENTITY_TAG_BLOCKPOS = "blockPos";
    public static final String ENTITY_TAG_NBT = "nbt";
    public static final String SIZE_TAG = "size";
    static final int CHUNK_SIZE = 16;
    private final List<StructureTemplate.Palette> palettes = Lists.newArrayList();
    private final List<StructureTemplate.StructureEntityInfo> entityInfoList = Lists.newArrayList();
    private Vec3i size = Vec3i.ZERO;
    private String author = "?";

    public Vec3i getSize() {
        return this.size;
    }

    public void setAuthor(String pAuthor) {
        this.author = pAuthor;
    }

    public String getAuthor() {
        return this.author;
    }

    public void fillFromWorld(Level pLevel, BlockPos pPos, Vec3i pSize, boolean pWithEntities, @Nullable Block pToIgnore) {
        if (localInt(pSize.getX()) >= 1 && pSize.getY() >= 1 && localInt(pSize.getZ()) >= 1) {
            BlockPos blockpos = pPos.offset(pSize).offset(-1, -1, -1);
            List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
            List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();
            List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();
            BlockPos blockpos1 = new BlockPos(min(pPos.getX(), blockpos.getX()), Math.min(pPos.getY(), blockpos.getY()), min(pPos.getZ(), blockpos.getZ()));
            BlockPos blockpos2 = new BlockPos(max(pPos.getX(), blockpos.getX()), Math.max(pPos.getY(), blockpos.getY()), max(pPos.getZ(), blockpos.getZ()));
            this.size = pSize;

            for (BlockPos blockpos3 : BlockPos.betweenClosed(blockpos1, blockpos2)) {
                BlockPos blockpos4 = blockpos3.subtract(blockpos1);
                BlockState blockstate = pLevel.getBlockState(blockpos3);
                if (pToIgnore == null || !blockstate.is(pToIgnore)) {
                    BlockEntity blockentity = pLevel.getBlockEntity(blockpos3);
                    StructureTemplate.StructureBlockInfo structuretemplate$structureblockinfo;
                    if (blockentity != null) {
                        structuretemplate$structureblockinfo = new StructureTemplate.StructureBlockInfo(blockpos4, blockstate, blockentity.saveWithId());
                    } else {
                        structuretemplate$structureblockinfo = new StructureTemplate.StructureBlockInfo(blockpos4, blockstate, (CompoundTag) null);
                    }

                    addToLists(structuretemplate$structureblockinfo, list, list1, list2);
                }
            }

            List<StructureTemplate.StructureBlockInfo> list3 = buildInfoList(list, list1, list2);
            this.palettes.clear();
            this.palettes.add(new StructureTemplate.Palette(list3));
            if (pWithEntities) {
                this.fillEntityList(pLevel, blockpos1, blockpos2.offset(1, 1, 1));
            } else {
                this.entityInfoList.clear();
            }
        }
    }

    private static void addToLists(StructureTemplate.StructureBlockInfo pBlockInfo, List<StructureTemplate.StructureBlockInfo> pNormalBlocks, List<StructureTemplate.StructureBlockInfo> pBlocksWithNbt, List<StructureTemplate.StructureBlockInfo> pBlocksWithSpecialShape) {
        if (pBlockInfo.nbt != null) {
            pBlocksWithNbt.add(pBlockInfo);
        } else if (!pBlockInfo.state.getBlock().hasDynamicShape() && pBlockInfo.state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            pNormalBlocks.add(pBlockInfo);
        } else {
            pBlocksWithSpecialShape.add(pBlockInfo);
        }
    }

    private static List<StructureTemplate.StructureBlockInfo> buildInfoList(List<StructureTemplate.StructureBlockInfo> pNormalBlocks, List<StructureTemplate.StructureBlockInfo> pBlocksWithNbt, List<StructureTemplate.StructureBlockInfo> pBlocksWithSpecialShape) {
        Comparator<StructureTemplate.StructureBlockInfo> comparator = Comparator.<StructureTemplate.StructureBlockInfo>comparingInt((p_74641_) -> {
                                                                                    return p_74641_.pos.getY();
                                                                                })
                                                                              .thenComparing((p_74637_) -> {
                                                                                  return p_74637_.pos.getX();
                                                                              })
                                                                              .thenComparing((p_74572_) -> {
                                                                                  return p_74572_.pos.getZ();
                                                                              });
        pNormalBlocks.sort(comparator);
        pBlocksWithSpecialShape.sort(comparator);
        pBlocksWithNbt.sort(comparator);
        List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
        list.addAll(pNormalBlocks);
        list.addAll(pBlocksWithSpecialShape);
        list.addAll(pBlocksWithNbt);
        return list;
    }

    private void fillEntityList(Level pLevel, BlockPos pStartPos, BlockPos pEndPos) {
        List<Entity> list = pLevel.getEntitiesOfClass(Entity.class, new AABB(pStartPos, pEndPos), (p_74499_) -> {
            return !(p_74499_ instanceof Player);
        });
        this.entityInfoList.clear();

        for (Entity entity : list) {
            SectorVec3 origin = SectorVec3.fromBlockAndFraction(pStartPos.getX(), 0.0D, pStartPos.getY(), pStartPos.getZ(), 0.0D);
            if (entity.sectorPosition().localSpanExceeds(origin)) {
                throw new IllegalArgumentException("Template entity exceeds the safe local span");
            }
            Vec3 vec3 = entity.sectorPosition().relativeTo(origin);
            CompoundTag compoundtag = new CompoundTag();
            entity.save(compoundtag);
            BlockPos blockpos;
            if (entity instanceof Painting) {
                blockpos = ((Painting) entity).getPos().subtract(pStartPos);
            } else {
                blockpos = new BlockPos(vec3);
            }

            this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3, blockpos, compoundtag.copy()));
        }
    }

    public List<StructureTemplate.StructureBlockInfo> filterBlocks(BlockPos pPos, StructurePlaceSettings pSettings, Block pBlock) {
        return this.filterBlocks(pPos, pSettings, pBlock, true);
    }

    public ObjectArrayList<StructureTemplate.StructureBlockInfo> filterBlocks(BlockPos pPos, StructurePlaceSettings pSettings, Block pBlock, boolean pRelativePosition) {
        ObjectArrayList<StructureTemplate.StructureBlockInfo> objectarraylist = new ObjectArrayList<>();
        BoundingBox boundingbox = pSettings.getBoundingBox();
        if (this.palettes.isEmpty()) {
            return objectarraylist;
        } else {
            for (StructureTemplate.StructureBlockInfo structuretemplate$structureblockinfo : pSettings.getRandomPalette(this.palettes, pPos).blocks(pBlock)) {
                BlockPos blockpos = pRelativePosition ? calculateRelativePosition(pSettings, structuretemplate$structureblockinfo.pos).offset(pPos) : structuretemplate$structureblockinfo.pos;
                if (boundingbox == null || boundingbox.isInside(blockpos)) {
                    objectarraylist.add(new StructureTemplate.StructureBlockInfo(blockpos, structuretemplate$structureblockinfo.state.rotate(pSettings.getRotation()), structuretemplate$structureblockinfo.nbt));
                }
            }

            return objectarraylist;
        }
    }

    public BlockPos calculateConnectedPosition(StructurePlaceSettings pDecorator, BlockPos pStart, StructurePlaceSettings pSettings, BlockPos pEnd) {
        BlockPos blockpos = calculateRelativePosition(pDecorator, pStart);
        BlockPos blockpos1 = calculateRelativePosition(pSettings, pEnd);
        return blockpos.subtract(blockpos1);
    }

    public static BlockPos calculateRelativePosition(StructurePlaceSettings pDecorator, BlockPos pPos) {
        return transform(pPos, pDecorator.getMirror(), pDecorator.getRotation(), pDecorator.getRotationPivot());
    }

    public boolean placeInWorld(ServerLevelAccessor p_230329_, BlockPos p_230330_, BlockPos p_230331_, StructurePlaceSettings p_230332_, RandomSource p_230333_, int p_230334_) {
        if (this.palettes.isEmpty()) {
            return false;
        } else {
            List<StructureTemplate.StructureBlockInfo> list = p_230332_.getRandomPalette(this.palettes, p_230330_).blocks();
            if ((!list.isEmpty() || !p_230332_.isIgnoreEntities() && !this.entityInfoList.isEmpty()) && localInt(this.size.getX()) >= 1 && this.size.getY() >= 1 && localInt(this.size.getZ()) >= 1) {
                BoundingBox boundingbox = p_230332_.getBoundingBox();
                List<BlockPos> list1 = Lists.newArrayListWithCapacity(p_230332_.shouldKeepLiquids() ? list.size() : 0);
                List<BlockPos> list2 = Lists.newArrayListWithCapacity(p_230332_.shouldKeepLiquids() ? list.size() : 0);
                List<Pair<BlockPos, CompoundTag>> list3 = Lists.newArrayListWithCapacity(list.size());
                Int128 i = Int128.MAX;
                int j = Integer.MAX_VALUE;
                Int128 k = Int128.MAX;
                Int128 l = Int128.MIN;
                int i1 = Integer.MIN_VALUE;
                Int128 j1 = Int128.MIN;

                for (StructureTemplate.StructureBlockInfo structuretemplate$structureblockinfo : processBlockInfos(p_230329_, p_230330_, p_230331_, p_230332_, list)) {
                    BlockPos blockpos = structuretemplate$structureblockinfo.pos;
                    if (boundingbox == null || boundingbox.isInside(blockpos)) {
                        FluidState fluidstate = p_230332_.shouldKeepLiquids() ? p_230329_.getFluidState(blockpos) : null;
                        BlockState blockstate = structuretemplate$structureblockinfo.state.mirror(p_230332_.getMirror()).rotate(p_230332_.getRotation());
                        if (structuretemplate$structureblockinfo.nbt != null) {
                            BlockEntity blockentity = p_230329_.getBlockEntity(blockpos);
                            Clearable.tryClear(blockentity);
                            p_230329_.setBlock(blockpos, Blocks.BARRIER.defaultBlockState(), 20);
                        }

                        if (p_230329_.setBlock(blockpos, blockstate, p_230334_)) {
                            i = min(i, blockpos.getX());
                            j = Math.min(j, blockpos.getY());
                            k = min(k, blockpos.getZ());
                            l = max(l, blockpos.getX());
                            i1 = Math.max(i1, blockpos.getY());
                            j1 = max(j1, blockpos.getZ());
                            list3.add(Pair.of(blockpos, structuretemplate$structureblockinfo.nbt));
                            if (structuretemplate$structureblockinfo.nbt != null) {
                                BlockEntity blockentity1 = p_230329_.getBlockEntity(blockpos);
                                if (blockentity1 != null) {
                                    if (blockentity1 instanceof RandomizableContainerBlockEntity) {
                                        structuretemplate$structureblockinfo.nbt.putLong("LootTableSeed", p_230333_.nextLong());
                                    }

                                    blockentity1.load(structuretemplate$structureblockinfo.nbt);
                                }
                            }

                            if (fluidstate != null) {
                                if (blockstate.getFluidState().isSource()) {
                                    list2.add(blockpos);
                                } else if (blockstate.getBlock() instanceof LiquidBlockContainer) {
                                    ((LiquidBlockContainer) blockstate.getBlock()).placeLiquid(p_230329_, blockpos, blockstate, fluidstate);
                                    if (!fluidstate.isSource()) {
                                        list1.add(blockpos);
                                    }
                                }
                            }
                        }
                    }
                }

                boolean flag = true;
                Direction[] adirection = new Direction[] {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

                while (flag && !list1.isEmpty()) {
                    flag = false;
                    Iterator<BlockPos> iterator = list1.iterator();

                    while (iterator.hasNext()) {
                        BlockPos blockpos3 = iterator.next();
                        FluidState fluidstate2 = p_230329_.getFluidState(blockpos3);

                        for (int i2 = 0; i2 < adirection.length && !fluidstate2.isSource(); ++i2) {
                            BlockPos blockpos1 = blockpos3.relative(adirection[i2]);
                            FluidState fluidstate1 = p_230329_.getFluidState(blockpos1);
                            if (fluidstate1.isSource() && !list2.contains(blockpos1)) {
                                fluidstate2 = fluidstate1;
                            }
                        }

                        if (fluidstate2.isSource()) {
                            BlockState blockstate1 = p_230329_.getBlockState(blockpos3);
                            Block block = blockstate1.getBlock();
                            if (block instanceof LiquidBlockContainer) {
                                ((LiquidBlockContainer) block).placeLiquid(p_230329_, blockpos3, blockstate1, fluidstate2);
                                flag = true;
                                iterator.remove();
                            }
                        }
                    }
                }

                if (!list3.isEmpty()) {
                    if (!p_230332_.getKnownShape()) {
                        DiscreteVoxelShape discretevoxelshape = new BitSetDiscreteVoxelShape(localSpan(i, l), i1 - j + 1, localSpan(k, j1));
                        Int128 k1 = i;
                        int l1 = j;
                        Int128 j2 = k;

                        for (Pair<BlockPos, CompoundTag> pair1 : list3) {
                            BlockPos blockpos2 = pair1.getFirst();
                            discretevoxelshape.fill(localInt(blockpos2.getX().subtract(k1)), blockpos2.getY() - l1, localInt(blockpos2.getZ().subtract(j2)));
                        }

                        updateShapeAtEdge(p_230329_, p_230334_, discretevoxelshape, k1, l1, j2);
                    }

                    for (Pair<BlockPos, CompoundTag> pair : list3) {
                        BlockPos blockpos4 = pair.getFirst();
                        if (!p_230332_.getKnownShape()) {
                            BlockState blockstate2 = p_230329_.getBlockState(blockpos4);
                            BlockState blockstate3 = Block.updateFromNeighbourShapes(blockstate2, p_230329_, blockpos4);
                            if (blockstate2 != blockstate3) {
                                p_230329_.setBlock(blockpos4, blockstate3, p_230334_ & -2 | 16);
                            }

                            p_230329_.blockUpdated(blockpos4, blockstate3.getBlock());
                        }

                        if (pair.getSecond() != null) {
                            BlockEntity blockentity2 = p_230329_.getBlockEntity(blockpos4);
                            if (blockentity2 != null) {
                                blockentity2.setChanged();
                            }
                        }
                    }
                }

                if (!p_230332_.isIgnoreEntities()) {
                    this.placeEntities(p_230329_, p_230330_, p_230332_.getMirror(), p_230332_.getRotation(), p_230332_.getRotationPivot(), boundingbox, p_230332_.shouldFinalizeEntities());
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public static void updateShapeAtEdge(LevelAccessor p_74511_, int p_74512_, DiscreteVoxelShape p_74513_, long p_74514_, int p_74515_, long p_74516_) {
        updateShapeAtEdge(p_74511_, p_74512_, p_74513_, Int128.fromLong(p_74514_), p_74515_, Int128.fromLong(p_74516_));
    }

    public static void updateShapeAtEdge(LevelAccessor p_74511_, int p_74512_, DiscreteVoxelShape p_74513_, Int128 p_74514_, int p_74515_, Int128 p_74516_) {
        p_74513_.forAllFaces((p_74494_, p_74495_, p_74496_, p_74497_) -> {
            BlockPos blockpos = new BlockPos(WorldBounds.addBlockOffset(p_74514_, p_74495_), p_74515_ + p_74496_, WorldBounds.addBlockOffset(p_74516_, p_74497_));
            BlockPos blockpos1 = blockpos.relative(p_74494_);
            BlockState blockstate = p_74511_.getBlockState(blockpos);
            BlockState blockstate1 = p_74511_.getBlockState(blockpos1);
            BlockState blockstate2 = blockstate.updateShape(p_74494_, blockstate1, p_74511_, blockpos, blockpos1);
            if (blockstate != blockstate2) {
                p_74511_.setBlock(blockpos, blockstate2, p_74512_ & -2);
            }

            BlockState blockstate3 = blockstate1.updateShape(p_74494_.getOpposite(), blockstate2, p_74511_, blockpos1, blockpos);
            if (blockstate1 != blockstate3) {
                p_74511_.setBlock(blockpos1, blockstate3, p_74512_ & -2);
            }
        });
    }

    public static List<StructureTemplate.StructureBlockInfo> processBlockInfos(LevelAccessor p_74518_, BlockPos p_74519_, BlockPos p_74520_, StructurePlaceSettings p_74521_, List<StructureTemplate.StructureBlockInfo> p_74522_) {
        List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();

        for (StructureTemplate.StructureBlockInfo structuretemplate$structureblockinfo : p_74522_) {
            BlockPos blockpos = calculateRelativePosition(p_74521_, structuretemplate$structureblockinfo.pos).offset(p_74519_);
            StructureTemplate.StructureBlockInfo structuretemplate$structureblockinfo1 = new StructureTemplate.StructureBlockInfo(blockpos, structuretemplate$structureblockinfo.state, structuretemplate$structureblockinfo.nbt != null ? structuretemplate$structureblockinfo.nbt.copy() : null);

            for (Iterator<StructureProcessor> iterator = p_74521_.getProcessors().iterator(); structuretemplate$structureblockinfo1 != null && iterator.hasNext(); structuretemplate$structureblockinfo1 = iterator.next().processBlock(p_74518_, p_74519_, p_74520_, structuretemplate$structureblockinfo, structuretemplate$structureblockinfo1, p_74521_)) {
            }

            if (structuretemplate$structureblockinfo1 != null) {
                list.add(structuretemplate$structureblockinfo1);
            }
        }

        return list;
    }

    private void placeEntities(ServerLevelAccessor p_74524_, BlockPos p_74525_, Mirror p_74526_, Rotation p_74527_, BlockPos p_74528_, @Nullable BoundingBox p_74529_, boolean p_74530_) {
        for (StructureTemplate.StructureEntityInfo structuretemplate$structureentityinfo : this.entityInfoList) {
            BlockPos blockpos = transform(structuretemplate$structureentityinfo.blockPos, p_74526_, p_74527_, p_74528_).offset(p_74525_);
            if (p_74529_ == null || p_74529_.isInside(blockpos)) {
                CompoundTag compoundtag = structuretemplate$structureentityinfo.nbt.copy();
                Vec3 vec3 = transform(structuretemplate$structureentityinfo.pos, p_74526_, p_74527_, p_74528_);
                SectorVec3 entityPosition = SectorVec3.fromBlockAndFraction(p_74525_.getX(), vec3.x,
                        (double) p_74525_.getY() + vec3.y, p_74525_.getZ(), vec3.z);
                Vec3 vec31 = entityPosition.toApproximateVec3();
                ListTag listtag = new ListTag();
                listtag.add(DoubleTag.valueOf(vec31.x));
                listtag.add(DoubleTag.valueOf(vec31.y));
                listtag.add(DoubleTag.valueOf(vec31.z));
                compoundtag.put("Pos", listtag);
                setEntityPosition(compoundtag, entityPosition);
                compoundtag.remove("UUID");
                createEntityIgnoreException(p_74524_, compoundtag).ifPresent((p_205061_) -> {
                    float f = p_205061_.rotate(p_74527_);
                    f += p_205061_.mirror(p_74526_) - p_205061_.getYRot();
                    p_205061_.moveTo(entityPosition, f, p_205061_.getXRot());
                    p_205061_.getSelfAndPassengers().filter((entity) -> entity != p_205061_)
                            .forEach((entity) -> entity.setExactBlockPosition(entityPosition.blockPosition()));
                    if (p_74530_ && p_205061_ instanceof Mob) {
                        ((Mob) p_205061_).finalizeSpawn(p_74524_, p_74524_.getCurrentDifficultyAt(entityPosition.blockPosition()), MobSpawnType.STRUCTURE, (SpawnGroupData) null, compoundtag);
                    }

                    p_74524_.addFreshEntityWithPassengers(p_205061_);
                });
            }
        }
    }

    private static Optional<Entity> createEntityIgnoreException(ServerLevelAccessor pLevel, CompoundTag pTag) {
        try {
            return EntityType.create(pTag, pLevel.getLevel());
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private static void setEntityPosition(CompoundTag tag, SectorVec3 position) {
        CompoundTag exactTag = new CompoundTag();
        NbtUtils.writeInt128(exactTag, "BlockX", position.blockX());
        NbtUtils.writeInt128(exactTag, "BlockZ", position.blockZ());
        exactTag.putDouble("SubX", position.subX());
        exactTag.putDouble("SubZ", position.subZ());
        exactTag.putDouble("Y", position.y());
        tag.put("ExactPos", exactTag);
        for (net.minecraft.nbt.Tag passenger : tag.getList("Passengers", 10)) {
            setEntityPosition((CompoundTag) passenger, position);
        }
    }

    public Vec3i getSize(Rotation pRotation) {
        switch (pRotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                return new Vec3i(this.size.getZ(), this.size.getY(), this.size.getX());
            default:
                return this.size;
        }
    }

    public static BlockPos transform(BlockPos pTargetPos, Mirror pMirror, Rotation pRotation, BlockPos pOffset) {
        Int128 i = pTargetPos.getX();
        int j = pTargetPos.getY();
        Int128 k = pTargetPos.getZ();
        boolean flag = true;
        switch (pMirror) {
            case LEFT_RIGHT:
                k = Int128.ZERO.subtractSaturated(k);
                break;
            case FRONT_BACK:
                i = Int128.ZERO.subtractSaturated(i);
                break;
            default:
                flag = false;
        }

        Int128 l = pOffset.getX();
        Int128 i1 = pOffset.getZ();
        switch (pRotation) {
            case COUNTERCLOCKWISE_90:
                return new BlockPos(WorldBounds.addSubtract(l, k, i1), j, WorldBounds.addSubtract(l, i1, i));
            case CLOCKWISE_90:
                return new BlockPos(WorldBounds.addSubtract(l, i1, k), j, WorldBounds.addSubtract(i1, i, l));
            case CLOCKWISE_180:
                return new BlockPos(WorldBounds.addSubtract(l, l, i), j, WorldBounds.addSubtract(i1, i1, k));
            default:
                return flag ? new BlockPos(i, j, k) : pTargetPos;
        }
    }

    public static Vec3 transform(Vec3 pTarget, Mirror pMirror, Rotation pRotation, BlockPos pCenterOffset) {
        double d0 = pTarget.x;
        double d1 = pTarget.y;
        double d2 = pTarget.z;
        boolean flag = true;
        switch (pMirror) {
            case LEFT_RIGHT:
                d2 = 1.0D - d2;
                break;
            case FRONT_BACK:
                d0 = 1.0D - d0;
                break;
            default:
                flag = false;
        }

        long i = localInt(pCenterOffset.getX());
        long j = localInt(pCenterOffset.getZ());
        switch (pRotation) {
            case COUNTERCLOCKWISE_90:
                return new Vec3((double) (i - j) + d2, d1, (double) (i + j + 1) - d0);
            case CLOCKWISE_90:
                return new Vec3((double) (i + j + 1) - d2, d1, (double) (j - i) + d0);
            case CLOCKWISE_180:
                return new Vec3((double) (i + i + 1) - d0, d1, (double) (j + j + 1) - d2);
            default:
                return flag ? new Vec3(d0, d1, d2) : pTarget;
        }
    }

    public BlockPos getZeroPositionWithTransform(BlockPos pTargetPos, Mirror pMirror, Rotation pRotation) {
        return getZeroPositionWithTransform(pTargetPos, pMirror, pRotation, localInt(this.getSize().getX()), localInt(this.getSize().getZ()));
    }

    public static BlockPos getZeroPositionWithTransform(BlockPos pPos, Mirror pMirror, Rotation pRotation, long pSizeX, long pSizeZ) {
        --pSizeX;
        --pSizeZ;
        long i = pMirror == Mirror.FRONT_BACK ? pSizeX : 0;
        long j = pMirror == Mirror.LEFT_RIGHT ? pSizeZ : 0;
        BlockPos blockpos = pPos;
        switch (pRotation) {
            case COUNTERCLOCKWISE_90:
                blockpos = pPos.offset(j, 0, pSizeX - i);
                break;
            case CLOCKWISE_90:
                blockpos = pPos.offset(pSizeZ - j, 0, i);
                break;
            case CLOCKWISE_180:
                blockpos = pPos.offset(pSizeX - i, 0, pSizeZ - j);
                break;
            case NONE:
                blockpos = pPos.offset(i, 0, j);
        }

        return blockpos;
    }

    public BoundingBox getBoundingBox(StructurePlaceSettings pSettings, BlockPos pStartPos) {
        return this.getBoundingBox(pStartPos, pSettings.getRotation(), pSettings.getRotationPivot(), pSettings.getMirror());
    }

    public BoundingBox getBoundingBox(BlockPos pStartPos, Rotation pRotation, BlockPos pPivotPos, Mirror pMirror) {
        return getBoundingBox(pStartPos, pRotation, pPivotPos, pMirror, this.size);
    }

    @VisibleForTesting
    protected static BoundingBox getBoundingBox(BlockPos pStartPos, Rotation pRotation, BlockPos pPivotPos, Mirror pMirror, Vec3i pSize) {
        Vec3i vec3i = pSize.offset(-1, -1, -1);
        BlockPos blockpos = transform(BlockPos.ZERO, pMirror, pRotation, pPivotPos);
        BlockPos blockpos1 = transform(BlockPos.ZERO.offset(vec3i), pMirror, pRotation, pPivotPos);
        return BoundingBox.fromCorners(blockpos, blockpos1).move(pStartPos);
    }

    public CompoundTag save(CompoundTag pTag) {
        if (this.palettes.isEmpty()) {
            pTag.put("blocks", new ListTag());
            pTag.put("palette", new ListTag());
        } else {
            List<StructureTemplate.SimplePalette> list = Lists.newArrayList();
            StructureTemplate.SimplePalette structuretemplate$simplepalette = new StructureTemplate.SimplePalette();
            list.add(structuretemplate$simplepalette);

            for (int i = 1; i < this.palettes.size(); ++i) {
                list.add(new StructureTemplate.SimplePalette());
            }

            ListTag listtag1 = new ListTag();
            List<StructureTemplate.StructureBlockInfo> list1 = this.palettes.get(0).blocks();

            for (int j = 0; j < list1.size(); ++j) {
                StructureTemplate.StructureBlockInfo structuretemplate$structureblockinfo = list1.get(j);
                CompoundTag compoundtag = new CompoundTag();
                compoundtag.put("pos", this.newIntegerList(localInt(structuretemplate$structureblockinfo.pos.getX()), structuretemplate$structureblockinfo.pos.getY(), localInt(structuretemplate$structureblockinfo.pos.getZ())));
                int k = structuretemplate$simplepalette.idFor(structuretemplate$structureblockinfo.state);
                compoundtag.putInt("state", k);
                if (structuretemplate$structureblockinfo.nbt != null) {
                    compoundtag.put("nbt", structuretemplate$structureblockinfo.nbt);
                }

                listtag1.add(compoundtag);

                for (int l = 1; l < this.palettes.size(); ++l) {
                    StructureTemplate.SimplePalette structuretemplate$simplepalette1 = list.get(l);
                    structuretemplate$simplepalette1.addMapping((this.palettes.get(l).blocks().get(j)).state, k);
                }
            }

            pTag.put("blocks", listtag1);
            if (list.size() == 1) {
                ListTag listtag2 = new ListTag();

                for (BlockState blockstate : structuretemplate$simplepalette) {
                    listtag2.add(NbtUtils.writeBlockState(blockstate));
                }

                pTag.put("palette", listtag2);
            } else {
                ListTag listtag3 = new ListTag();

                for (StructureTemplate.SimplePalette structuretemplate$simplepalette2 : list) {
                    ListTag listtag4 = new ListTag();

                    for (BlockState blockstate1 : structuretemplate$simplepalette2) {
                        listtag4.add(NbtUtils.writeBlockState(blockstate1));
                    }

                    listtag3.add(listtag4);
                }

                pTag.put("palettes", listtag3);
            }
        }

        ListTag listtag = new ListTag();

        for (StructureTemplate.StructureEntityInfo structuretemplate$structureentityinfo : this.entityInfoList) {
            CompoundTag compoundtag1 = new CompoundTag();
            compoundtag1.put("pos", this.newDoubleList(structuretemplate$structureentityinfo.pos.x, structuretemplate$structureentityinfo.pos.y, structuretemplate$structureentityinfo.pos.z));
            compoundtag1.put("blockPos", this.newIntegerList(localInt(structuretemplate$structureentityinfo.blockPos.getX()), structuretemplate$structureentityinfo.blockPos.getY(), localInt(structuretemplate$structureentityinfo.blockPos.getZ())));
            if (structuretemplate$structureentityinfo.nbt != null) {
                compoundtag1.put("nbt", structuretemplate$structureentityinfo.nbt);
            }

            listtag.add(compoundtag1);
        }

        pTag.put("entities", listtag);
        pTag.put("size", this.newIntegerList(localInt(this.size.getX()), this.size.getY(), localInt(this.size.getZ())));
        pTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        return pTag;
    }

    public void load(CompoundTag pTag) {
        this.palettes.clear();
        this.entityInfoList.clear();
        ListTag listtag = getLongList(pTag, "size");
        this.size = new Vec3i(readLocalInt(listtag, 0), readLocalInt(listtag, 1), readLocalInt(listtag, 2));
        ListTag listtag1 = pTag.getList("blocks", 10);
        if (pTag.contains("palettes", 9)) {
            ListTag listtag2 = pTag.getList("palettes", 9);

            for (int i = 0; i < listtag2.size(); ++i) {
                this.loadPalette(listtag2.getList(i), listtag1);
            }
        } else {
            this.loadPalette(pTag.getList("palette", 10), listtag1);
        }

        ListTag listtag5 = pTag.getList("entities", 10);

        for (int j = 0; j < listtag5.size(); ++j) {
            CompoundTag compoundtag = listtag5.getCompound(j);
            ListTag listtag3 = compoundtag.getList("pos", 6);
            Vec3 vec3 = new Vec3(listtag3.getDouble(0), listtag3.getDouble(1), listtag3.getDouble(2));
            ListTag listtag4 = getLongList(compoundtag, "blockPos");
            BlockPos blockpos = new BlockPos(readLocalInt(listtag4, 0), readLocalInt(listtag4, 1), readLocalInt(listtag4, 2));
            if (compoundtag.contains("nbt")) {
                CompoundTag compoundtag1 = compoundtag.getCompound("nbt");
                this.entityInfoList.add(new StructureTemplate.StructureEntityInfo(vec3, blockpos, compoundtag1));
            }
        }
    }

    private static ListTag getLongList(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, 4);
        return list.isEmpty() ? tag.getList(key, 3) : list;
    }

    private static int readLocalInt(ListTag list, int index) {
        return Math.toIntExact(((NumericTag) list.get(index)).getAsLong());
    }

    private void loadPalette(ListTag pPaletteTag, ListTag pBlocksTag) {
        StructureTemplate.SimplePalette structuretemplate$simplepalette = new StructureTemplate.SimplePalette();

        for (int i = 0; i < pPaletteTag.size(); ++i) {
            structuretemplate$simplepalette.addMapping(NbtUtils.readBlockState(pPaletteTag.getCompound(i)), i);
        }

        List<StructureTemplate.StructureBlockInfo> list2 = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> list = Lists.newArrayList();
        List<StructureTemplate.StructureBlockInfo> list1 = Lists.newArrayList();

        for (int j = 0; j < pBlocksTag.size(); ++j) {
            CompoundTag compoundtag = pBlocksTag.getCompound(j);
            ListTag listtag = getLongList(compoundtag, "pos");
            BlockPos blockpos = new BlockPos(readLocalInt(listtag, 0), readLocalInt(listtag, 1), readLocalInt(listtag, 2));
            BlockState blockstate = structuretemplate$simplepalette.stateFor(compoundtag.getInt("state"));
            CompoundTag compoundtag1;
            if (compoundtag.contains("nbt")) {
                compoundtag1 = compoundtag.getCompound("nbt");
            } else {
                compoundtag1 = null;
            }

            StructureTemplate.StructureBlockInfo structuretemplate$structureblockinfo = new StructureTemplate.StructureBlockInfo(blockpos, blockstate, compoundtag1);
            addToLists(structuretemplate$structureblockinfo, list2, list, list1);
        }

        List<StructureTemplate.StructureBlockInfo> list3 = buildInfoList(list2, list, list1);
        this.palettes.add(new StructureTemplate.Palette(list3));
    }

    private ListTag newIntegerList(int... pValues) {
        ListTag listtag = new ListTag();

        for (int i : pValues) {
            listtag.add(IntTag.valueOf(i));
        }

        return listtag;
    }

    private static int localInt(Int128 value) {
        if (value.compareTo(Int128.fromLong(Integer.MIN_VALUE)) < 0 || value.compareTo(Int128.fromLong(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Template-local coordinate does not fit an int");
        }
        return (int) value.lo();
    }

    private static Int128 min(Int128 first, Int128 second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static Int128 max(Int128 first, Int128 second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static int localSpan(Int128 min, Int128 max) {
        if (WorldBounds.localSpanExceeds(max, min)) {
            throw new IllegalArgumentException("Template shape exceeds the safe local span");
        }
        return Math.addExact(localInt(max.subtract(min)), 1);
    }

    private ListTag newDoubleList(double... pValues) {
        ListTag listtag = new ListTag();

        for (double d0 : pValues) {
            listtag.add(DoubleTag.valueOf(d0));
        }

        return listtag;
    }

    public static final class Palette {
        private final List<StructureTemplate.StructureBlockInfo> blocks;
        private final Map<Block, List<StructureTemplate.StructureBlockInfo>> cache = Maps.newHashMap();

        Palette(List<StructureTemplate.StructureBlockInfo> pBlocks) {
            this.blocks = pBlocks;
        }

        public List<StructureTemplate.StructureBlockInfo> blocks() {
            return this.blocks;
        }

        public List<StructureTemplate.StructureBlockInfo> blocks(Block pBlock) {
            return this.cache.computeIfAbsent(pBlock, (p_74659_) -> {
                return this.blocks.stream().filter((p_163818_) -> {
                                               return p_163818_.state.is(p_74659_);
                                           })
                        .collect(Collectors.toList());
            });
        }
    }

    static class SimplePalette implements Iterable<BlockState> {
        public static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
        private final IdMapper<BlockState> ids = new IdMapper<>(16);
        private int lastId;

        public int idFor(BlockState pState) {
            int i = this.ids.getId(pState);
            if (i == -1) {
                i = this.lastId++;
                this.ids.addMapping(pState, i);
            }

            return i;
        }

        @Nullable
        public BlockState stateFor(int pId) {
            BlockState blockstate = this.ids.byId(pId);
            return blockstate == null ? DEFAULT_BLOCK_STATE : blockstate;
        }

        public Iterator<BlockState> iterator() {
            return this.ids.iterator();
        }

        public void addMapping(BlockState pState, int pId) {
            this.ids.addMapping(pState, pId);
        }
    }

    public static class StructureBlockInfo {
        public final BlockPos pos;
        public final BlockState state;
        public final CompoundTag nbt;

        public StructureBlockInfo(BlockPos pPos, BlockState pState, @Nullable CompoundTag pNbt) {
            this.pos = pPos;
            this.state = pState;
            this.nbt = pNbt;
        }

        public String toString() {
            return String.format(Locale.ROOT, "<StructureBlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
        }
    }

    public static class StructureEntityInfo {
        public final Vec3 pos;
        public final BlockPos blockPos;
        public final CompoundTag nbt;

        public StructureEntityInfo(Vec3 pPos, BlockPos pBlockPos, CompoundTag pNbt) {
            this.pos = pPos;
            this.blockPos = pBlockPos;
            this.nbt = pNbt;
        }
    }
}
