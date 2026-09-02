package net.minecraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;

public class Containers {
   public static void dropContents(Level p_19003_, BlockPos p_19004_, Container p_19005_) {
      dropContents(p_19003_, SectorVec3.fromBlockAndFraction(p_19004_.getX(), 0.0D,
            (double)p_19004_.getY(), p_19004_.getZ(), 0.0D), p_19005_);
   }

   public static void dropContents(Level p_18999_, Entity p_19000_, Container p_19001_) {
      SectorVec3 position = p_19000_.exactPosition();
      if (position != null) {
         dropContents(p_18999_, position, p_19001_);
      } else {
         dropContents(p_18999_, p_19000_.getX(), p_19000_.getY(), p_19000_.getZ(), p_19001_);
      }
   }

   private static void dropContents(Level level, double x, double y, double z, Container container) {
      dropContents(level, SectorVec3.fromApproximate(x, y, z), container);
   }

   private static void dropContents(Level p_18987_, SectorVec3 p_18988_, Container p_18991_) {
      for(int i = 0; i < p_18991_.getContainerSize(); ++i) {
         dropItemStack(p_18987_, p_18988_, p_18991_.getItem(i));
      }

   }

   public static void dropContents(Level p_19011_, BlockPos p_19012_, NonNullList<ItemStack> p_19013_) {
      SectorVec3 position = SectorVec3.fromBlockAndFraction(p_19012_.getX(), 0.0D, (double)p_19012_.getY(),
            p_19012_.getZ(), 0.0D);
      p_19013_.forEach((p_19009_) -> dropItemStack(p_19011_, position, p_19009_));
   }

   /** Creates a randomized item split around an exact world position. */
   public static void dropItemStack(Level p_18993_, SectorVec3 p_18994_, ItemStack p_18997_) {
      double d0 = (double)EntityType.ITEM.getWidth();
      double d1 = 1.0D - d0;
      double d2 = d0 / 2.0D;
      SectorVec3 position = SectorVec3.fromBlockAndFraction(p_18994_.blockX(), 0.0D,
            Math.floor(p_18994_.y()) + p_18993_.random.nextDouble() * d1, p_18994_.blockZ(), 0.0D)
            .add(p_18993_.random.nextDouble() * d1 + d2, 0.0D, p_18993_.random.nextDouble() * d1 + d2);

      while(!p_18997_.isEmpty()) {
         ItemEntity itementity = createItemEntity(p_18993_, position,
               p_18997_.split(p_18993_.random.nextInt(21) + 10));
         itementity.setDeltaMovement(p_18993_.random.triangle(0.0D, 0.11485000171139836D), p_18993_.random.triangle(0.2D, 0.11485000171139836D), p_18993_.random.triangle(0.0D, 0.11485000171139836D));
         p_18993_.addFreshEntity(itementity);
      }

   }

   /**
    * Compatibility entry point for legacy double callers. Block/entity callers
    * use the exact overload above so their X/Z fraction never reaches an
    * absolute double first.
    */
   public static void dropItemStack(Level p_18993_, double p_18994_, double p_18995_, double p_18996_, ItemStack p_18997_) {
      dropItemStack(p_18993_, SectorVec3.fromApproximate(p_18994_, p_18995_, p_18996_), p_18997_);
   }

   public static ItemEntity createItemEntity(Level level, SectorVec3 position, ItemStack stack) {
      return new ItemEntity(level, position, stack);
   }
}
