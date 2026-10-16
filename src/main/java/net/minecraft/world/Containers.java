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
    public static void dropContents(Level pLevel, BlockPos pPos, Container pInventory) {
        dropContents(pLevel, SectorVec3.fromBlockAndFraction(pPos.getX(), 0.0D, (double) pPos.getY(), pPos.getZ(), 0.0D), pInventory);
    }

    public static void dropContents(Level pLevel, Entity pEntityAt, Container pInventory) {
        SectorVec3 position = pEntityAt.exactPosition();
        if (position != null) {
            dropContents(pLevel, position, pInventory);
        } else {
            dropContents(pLevel, pEntityAt.getX(), pEntityAt.getY(), pEntityAt.getZ(), pInventory);
        }
    }

    private static void dropContents(Level level, double x, double y, double z, Container container) {
        dropContents(level, SectorVec3.fromApproximate(x, y, z), container);
    }

    private static void dropContents(Level pLevel, SectorVec3 pX, Container pInventory) {
        for (int i = 0; i < pInventory.getContainerSize(); ++i) {
            dropItemStack(pLevel, pX, pInventory.getItem(i));
        }
    }

    public static void dropContents(Level pLevel, BlockPos pPos, NonNullList<ItemStack> pStackList) {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(pPos.getX(), 0.0D, (double) pPos.getY(),
                pPos.getZ(), 0.0D);
        pStackList.forEach((p_19009_) -> dropItemStack(pLevel, position, p_19009_));
    }

    /** Creates a randomized item split around an exact world position. */
    public static void dropItemStack(Level pLevel, SectorVec3 pX, ItemStack pStack) {
        double d0 = (double) EntityType.ITEM.getWidth();
        double d1 = 1.0D - d0;
        double d2 = d0 / 2.0D;
        SectorVec3 position = SectorVec3.fromBlockAndFraction(pX.blockX(), 0.0D,
                                                Math.floor(pX.y()) + pLevel.random.nextDouble() * d1, pX.blockZ(), 0.0D)
                                      .add(pLevel.random.nextDouble() * d1 + d2, 0.0D, pLevel.random.nextDouble() * d1 + d2);

        while (!pStack.isEmpty()) {
            ItemEntity itementity = createItemEntity(pLevel, position,
                    pStack.split(pLevel.random.nextInt(21) + 10));
            itementity.setDeltaMovement(pLevel.random.triangle(0.0D, 0.11485000171139836D), pLevel.random.triangle(0.2D, 0.11485000171139836D), pLevel.random.triangle(0.0D, 0.11485000171139836D));
            pLevel.addFreshEntity(itementity);
        }
    }

    /**
    * Compatibility entry point for legacy double callers. Block/entity callers
    * use the exact overload above so their X/Z fraction never reaches an
    * absolute double first.
    */
    public static void dropItemStack(Level pLevel, double pX, double pY, double pZ, ItemStack pStack) {
        dropItemStack(pLevel, SectorVec3.fromApproximate(pX, pY, pZ), pStack);
    }

    public static ItemEntity createItemEntity(Level level, SectorVec3 position, ItemStack stack) {
        return new ItemEntity(level, position, stack);
    }
}
