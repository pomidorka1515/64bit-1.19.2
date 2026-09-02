package net.minecraft.core.dispenser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.level.block.DispenserBlock;

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {
   public final ItemStack dispense(BlockSource p_123391_, ItemStack p_123392_) {
      ItemStack itemstack = this.execute(p_123391_, p_123392_);
      this.playSound(p_123391_);
      this.playAnimation(p_123391_, p_123391_.getBlockState().getValue(DispenserBlock.FACING));
      return itemstack;
   }

   protected ItemStack execute(BlockSource p_123385_, ItemStack p_123386_) {
      Direction direction = p_123385_.getBlockState().getValue(DispenserBlock.FACING);
      ItemStack itemstack = p_123386_.split(1);
      spawnItem(p_123385_.getLevel(), itemstack, 6, direction, p_123385_.getPos());
      return p_123386_;
   }

   /** Legacy entry point for custom behaviors that only expose a double Position. */
   public static void spawnItem(Level p_123379_, ItemStack p_123380_, int p_123381_, Direction p_123382_, Position p_123383_) {
      spawnItem(p_123379_, p_123380_, p_123381_, p_123382_,
            SectorVec3.fromApproximate(p_123383_.x(), p_123383_.y(), p_123383_.z()));
   }

   /** Exact dispenser path, anchored by the source block's long coordinates. */
   public static void spawnItem(Level p_123379_, ItemStack p_123380_, int p_123381_, Direction p_123382_, BlockPos source) {
      SectorVec3 position = SectorVec3.fromBlockAndFraction(source.getX(), 0.5D, (double)source.getY() + 0.5D,
            source.getZ(), 0.5D).add(0.7D * (double)p_123382_.getStepX(),
                  0.7D * (double)p_123382_.getStepY(), 0.7D * (double)p_123382_.getStepZ());
      spawnItem(p_123379_, p_123380_, p_123381_, p_123382_, position);
   }

   private static void spawnItem(Level p_123379_, ItemStack p_123380_, int p_123381_, Direction p_123382_, SectorVec3 position) {
      position = position.add(0.0D, p_123382_.getAxis() == Direction.Axis.Y ? -0.125D : -0.15625D, 0.0D);
      ItemEntity itementity = new ItemEntity(p_123379_, position, p_123380_);
      double d3 = p_123379_.random.nextDouble() * 0.1D + 0.2D;
      itementity.setDeltaMovement(p_123379_.random.triangle((double)p_123382_.getStepX() * d3, 0.0172275D * (double)p_123381_), p_123379_.random.triangle(0.2D, 0.0172275D * (double)p_123381_), p_123379_.random.triangle((double)p_123382_.getStepZ() * d3, 0.0172275D * (double)p_123381_));
      p_123379_.addFreshEntity(itementity);
   }

   protected void playSound(BlockSource p_123384_) {
      p_123384_.getLevel().levelEvent(1000, p_123384_.getPos(), 0);
   }

   protected void playAnimation(BlockSource p_123388_, Direction p_123389_) {
      p_123388_.getLevel().levelEvent(2000, p_123388_.getPos(), p_123389_.get3DDataValue());
   }
}
