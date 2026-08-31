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

   public BlockEntity(BlockEntityType<?> p_155228_, BlockPos p_155229_, BlockState p_155230_) {
      this.type = p_155228_;
      this.worldPosition = p_155229_.immutable();
      this.blockState = p_155230_;
   }

   public static BlockPos getPosFromTag(CompoundTag p_187473_) {
      // Block entities in protochunks are keyed by this position.  In particular,
      // WorldGenRegion writes a DUMMY block-entity tag before the real entity is
      // created.  Reading x/z as ints here truncated positions outside the signed
      // 32-bit range, so the pending entity was stored under a different key than
      // the placed block and could never be retrieved during feature generation.
      // getLong also accepts legacy IntTag coordinates, preserving old saves.
      return new BlockPos(p_187473_.getLong("x"), p_187473_.getInt("y"), p_187473_.getLong("z"));
   }

   @Nullable
   public Level getLevel() {
      return this.level;
   }

   public void setLevel(Level p_155231_) {
      this.level = p_155231_;
   }

   public boolean hasLevel() {
      return this.level != null;
   }

   public void load(CompoundTag p_155245_) {
   }

   protected void saveAdditional(CompoundTag p_187471_) {
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

   private void saveId(CompoundTag p_187475_) {
      ResourceLocation resourcelocation = BlockEntityType.getKey(this.getType());
      if (resourcelocation == null) {
         throw new RuntimeException(this.getClass() + " is missing a mapping! This is a bug!");
      } else {
         p_187475_.putString("id", resourcelocation.toString());
      }
   }

   public static void addEntityType(CompoundTag p_187469_, BlockEntityType<?> p_187470_) {
      p_187469_.putString("id", BlockEntityType.getKey(p_187470_).toString());
   }

   public void saveToItem(ItemStack p_187477_) {
      BlockItem.setBlockEntityData(p_187477_, this.getType(), this.saveWithoutMetadata());
   }

   private void saveMetadata(CompoundTag p_187479_) {
      this.saveId(p_187479_);
      p_187479_.putInt("x", this.worldPosition.getX());
      p_187479_.putInt("y", this.worldPosition.getY());
      p_187479_.putInt("z", this.worldPosition.getZ());
   }

   @Nullable
   public static BlockEntity loadStatic(BlockPos p_155242_, BlockState p_155243_, CompoundTag p_155244_) {
      String s = p_155244_.getString("id");
      ResourceLocation resourcelocation = ResourceLocation.tryParse(s);
      if (resourcelocation == null) {
         LOGGER.error("Block entity has invalid type: {}", (Object)s);
         return null;
      } else {
         return Registry.BLOCK_ENTITY_TYPE.getOptional(resourcelocation).map((p_155240_) -> {
            try {
               return p_155240_.create(p_155242_, p_155243_);
            } catch (Throwable throwable) {
               LOGGER.error("Failed to create block entity {}", s, throwable);
               return null;
            }
         }).map((p_155249_) -> {
            try {
               p_155249_.load(p_155244_);
               return p_155249_;
            } catch (Throwable throwable) {
               LOGGER.error("Failed to load data for block entity {}", s, throwable);
               return null;
            }
         }).orElseGet(() -> {
            LOGGER.warn("Skipping BlockEntity with id {}", (Object)s);
            return null;
         });
      }
   }

   public void setChanged() {
      if (this.level != null) {
         setChanged(this.level, this.worldPosition, this.blockState);
      }

   }

   protected static void setChanged(Level p_155233_, BlockPos p_155234_, BlockState p_155235_) {
      p_155233_.blockEntityChanged(p_155234_);
      if (!p_155235_.isAir()) {
         p_155233_.updateNeighbourForOutputSignal(p_155234_, p_155235_.getBlock());
      }

   }

   public BlockPos getBlockPos() {
      return this.worldPosition;
   }

   /**
    * Tests the distance from a player to this block's center without rebuilding
    * its long X/Z coordinates as an absolute double.
    */
   protected final boolean isWithinUsableDistance(Player p_155252_, double p_155253_) {
      SectorVec3 position = p_155252_.exactPosition();
      return position != null ? isWithinUsableDistance(position, this.worldPosition, p_155253_)
            : p_155252_.distanceToSqr((double)this.worldPosition.getX() + 0.5D,
                  (double)this.worldPosition.getY() + 0.5D, (double)this.worldPosition.getZ() + 0.5D) <= p_155253_ * p_155253_;
   }

   /** Exact variant shared by block-entity menu validation and regression tests. */
   public static boolean isWithinUsableDistance(SectorVec3 p_155254_, BlockPos p_155255_, double p_155256_) {
      SectorVec3 center = SectorVec3.fromBlockAndFraction(p_155255_.getX(), 0.5D,
            (double)p_155255_.getY() + 0.5D, p_155255_.getZ(), 0.5D);
      return p_155254_.relativeTo(center).lengthSqr() <= p_155256_ * p_155256_;
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

   public boolean triggerEvent(int p_58889_, int p_58890_) {
      return false;
   }

   public void fillCrashReportCategory(CrashReportCategory p_58887_) {
      p_58887_.setDetail("Name", () -> {
         return Registry.BLOCK_ENTITY_TYPE.getKey(this.getType()) + " // " + this.getClass().getCanonicalName();
      });
      if (this.level != null) {
         CrashReportCategory.populateBlockDetails(p_58887_, this.level, this.worldPosition, this.getBlockState());
         CrashReportCategory.populateBlockDetails(p_58887_, this.level, this.worldPosition, this.level.getBlockState(this.worldPosition));
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
   public void setBlockState(BlockState p_155251_) {
      this.blockState = p_155251_;
   }
}