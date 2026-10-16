package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.SectorVec3;
import org.slf4j.Logger;

public abstract class BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final BlockEntityType<?> type;
    @Nullable
    protected Level level;
    protected final BlockPos worldPosition;
    protected boolean remove;
    private BlockState blockState;

    public BlockEntity(BlockEntityType<?> pType, BlockPos pPos, BlockState pBlockState) {
        this.type = pType;
        this.worldPosition = pPos.immutable();
        this.blockState = pBlockState;
    }

    public static BlockPos getPosFromTag(CompoundTag pTag) {
        // Block entities in protochunks are keyed by this position.  In particular,
        // WorldGenRegion writes a DUMMY block-entity tag before the real entity is
        // created.  Reading x/z as ints here truncated positions outside the signed
        // 32-bit range, so the pending entity was stored under a different key than
        // the placed block and could never be retrieved during feature generation.
        // getLong also accepts legacy IntTag coordinates, preserving old saves.
        return new BlockPos(pTag.getLong("x"), pTag.getInt("y"), pTag.getLong("z"));
    }

    @Nullable
    public Level getLevel() {
        return this.level;
    }

    public void setLevel(Level pLevel) {
        this.level = pLevel;
    }

    public boolean hasLevel() {
        return this.level != null;
    }

    public void load(CompoundTag pTag) {
    }

    protected void saveAdditional(CompoundTag pTag) {
    }

    public final CompoundTag saveWithFullMetadata() {
        CompoundTag compoundtag = this.saveWithoutMetadata();
        this.saveMetadata(compoundtag);
        return compoundtag;
    }

    public final CompoundTag saveWithId() {
        CompoundTag compoundtag = this.saveWithoutMetadata();
        this.saveId(compoundtag);
        return compoundtag;
    }

    public final CompoundTag saveWithoutMetadata() {
        CompoundTag compoundtag = new CompoundTag();
        this.saveAdditional(compoundtag);
        return compoundtag;
    }

    private void saveId(CompoundTag pTag) {
        ResourceLocation resourcelocation = BlockEntityType.getKey(this.getType());
        if (resourcelocation == null) {
            throw new RuntimeException(this.getClass() + " is missing a mapping! This is a bug!");
        } else {
            pTag.putString("id", resourcelocation.toString());
        }
    }

    public static void addEntityType(CompoundTag pTag, BlockEntityType<?> pEntityType) {
        pTag.putString("id", BlockEntityType.getKey(pEntityType).toString());
    }

    public void saveToItem(ItemStack pStack) {
        BlockItem.setBlockEntityData(pStack, this.getType(), this.saveWithoutMetadata());
    }

    private void saveMetadata(CompoundTag pTag) {
        this.saveId(pTag);
        pTag.putInt("x", this.worldPosition.getX());
        pTag.putInt("y", this.worldPosition.getY());
        pTag.putInt("z", this.worldPosition.getZ());
    }

    @Nullable
    public static BlockEntity loadStatic(BlockPos pPos, BlockState pState, CompoundTag pTag) {
        String s = pTag.getString("id");
        ResourceLocation resourcelocation = ResourceLocation.tryParse(s);
        if (resourcelocation == null) {
            LOGGER.error("Block entity has invalid type: {}", (Object) s);
            return null;
        } else {
            return Registry.BLOCK_ENTITY_TYPE.getOptional(resourcelocation).map((p_155240_) -> {
                                                                               try {
                                                                                   return p_155240_.create(pPos, pState);
                                                                               } catch (Throwable throwable) {
                                                                                   LOGGER.error("Failed to create block entity {}", s, throwable);
                                                                                   return null;
                                                                               }
                                                                           })
                    .map((p_155249_) -> {
                        try {
                            p_155249_.load(pTag);
                            return p_155249_;
                        } catch (Throwable throwable) {
                            LOGGER.error("Failed to load data for block entity {}", s, throwable);
                            return null;
                        }
                    })
                    .orElseGet(() -> {
                        LOGGER.warn("Skipping BlockEntity with id {}", (Object) s);
                        return null;
                    });
        }
    }

    public void setChanged() {
        if (this.level != null) {
            setChanged(this.level, this.worldPosition, this.blockState);
        }
    }

    protected static void setChanged(Level pLevel, BlockPos pPos, BlockState pState) {
        pLevel.blockEntityChanged(pPos);
        if (!pState.isAir()) {
            pLevel.updateNeighbourForOutputSignal(pPos, pState.getBlock());
        }
    }

    public BlockPos getBlockPos() {
        return this.worldPosition;
    }

    /**
    * Tests the distance from a player to this block's center without rebuilding
    * its long X/Z coordinates as an absolute double.
    */
    protected final boolean isWithinUsableDistance(Player p_155252_, double pLevel) {
        SectorVec3 position = p_155252_.exactPosition();
        return position != null ? isWithinUsableDistance(position, this.worldPosition, pLevel)
                                : p_155252_.distanceToSqr((double) this.worldPosition.getX() + 0.5D,
                                          (double) this.worldPosition.getY() + 0.5D, (double) this.worldPosition.getZ() + 0.5D)
                        <= pLevel * pLevel;
    }

    /** Exact variant shared by block-entity menu validation and regression tests. */
    public static boolean isWithinUsableDistance(SectorVec3 pPos, BlockPos pState, double pBlockEntity) {
        SectorVec3 center = SectorVec3.fromBlockAndFraction(pState.getX(), 0.5D,
                (double) pState.getY() + 0.5D, pState.getZ(), 0.5D);
        return pPos.relativeTo(center).lengthSqr() <= pBlockEntity * pBlockEntity;
    }

    public BlockState getBlockState() {
        return this.blockState;
    }

    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return null;
    }

    public CompoundTag getUpdateTag() {
        return new CompoundTag();
    }

    public boolean isRemoved() {
        return this.remove;
    }

    public void setRemoved() {
        this.remove = true;
    }

    public void clearRemoved() {
        this.remove = false;
    }

    public boolean triggerEvent(int pId, int pType) {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory pReportCategory) {
        pReportCategory.setDetail("Name", () -> {
            return Registry.BLOCK_ENTITY_TYPE.getKey(this.getType()) + " // " + this.getClass().getCanonicalName();
        });
        if (this.level != null) {
            CrashReportCategory.populateBlockDetails(pReportCategory, this.level, this.worldPosition, this.getBlockState());
            CrashReportCategory.populateBlockDetails(pReportCategory, this.level, this.worldPosition, this.level.getBlockState(this.worldPosition));
        }
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    public BlockEntityType<?> getType() {
        return this.type;
    }

    /** @deprecated */
    @Deprecated
    public void setBlockState(BlockState pBlockState) {
        this.blockState = pBlockState;
    }
}
