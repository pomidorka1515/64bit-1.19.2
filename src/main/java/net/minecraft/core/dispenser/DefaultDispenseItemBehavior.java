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
    public final ItemStack dispense(BlockSource pSource, ItemStack pStack) {
        ItemStack itemstack = this.execute(pSource, pStack);
        this.playSound(pSource);
        this.playAnimation(pSource, pSource.getBlockState().getValue(DispenserBlock.FACING));
        return itemstack;
    }

    protected ItemStack execute(BlockSource pSource, ItemStack pStack) {
        Direction direction = pSource.getBlockState().getValue(DispenserBlock.FACING);
        ItemStack itemstack = pStack.split(1);
        spawnItem(pSource.getLevel(), itemstack, 6, direction, pSource.getPos());
        return pStack;
    }

    /** Legacy entry point for custom behaviors that only expose a double Position. */
    public static void spawnItem(Level pLevel, ItemStack pStack, int pSpeed, Direction pFacing, Position pPosition) {
        spawnItem(pLevel, pStack, pSpeed, pFacing,
                SectorVec3.fromApproximate(pPosition.x(), pPosition.y(), pPosition.z()));
    }

    /** Exact dispenser path, anchored by the source block's long coordinates. */
    public static void spawnItem(Level pLevel, ItemStack pStack, int pSpeed, Direction pFacing, BlockPos source) {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(source.getX(), 0.5D, (double) source.getY() + 0.5D,
                                                source.getZ(), 0.5D)
                                      .add(0.7D * (double) pFacing.getStepX(),
                                              0.7D * (double) pFacing.getStepY(), 0.7D * (double) pFacing.getStepZ());
        spawnItem(pLevel, pStack, pSpeed, pFacing, position);
    }

    private static void spawnItem(Level pLevel, ItemStack pStack, int pSpeed, Direction pFacing, SectorVec3 position) {
        position = position.add(0.0D, pFacing.getAxis() == Direction.Axis.Y ? -0.125D : -0.15625D, 0.0D);
        ItemEntity itementity = new ItemEntity(pLevel, position, pStack);
        double d3 = pLevel.random.nextDouble() * 0.1D + 0.2D;
        itementity.setDeltaMovement(pLevel.random.triangle((double) pFacing.getStepX() * d3, 0.0172275D * (double) pSpeed), pLevel.random.triangle(0.2D, 0.0172275D * (double) pSpeed), pLevel.random.triangle((double) pFacing.getStepZ() * d3, 0.0172275D * (double) pSpeed));
        pLevel.addFreshEntity(itementity);
    }

    protected void playSound(BlockSource pSource) {
        pSource.getLevel().levelEvent(1000, pSource.getPos(), 0);
    }

    protected void playAnimation(BlockSource pSource, Direction pFacing) {
        pSource.getLevel().levelEvent(2000, pSource.getPos(), pFacing.get3DDataValue());
    }
}
