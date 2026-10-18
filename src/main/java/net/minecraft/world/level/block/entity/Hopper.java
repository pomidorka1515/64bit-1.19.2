package net.minecraft.world.level.block.entity;

import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface Hopper extends Container {
    VoxelShape INSIDE = Block.box(2.0D, 11.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    VoxelShape ABOVE = Block.box(0.0D, 16.0D, 0.0D, 16.0D, 32.0D, 16.0D);
    VoxelShape SUCK = Shapes.or(INSIDE, ABOVE);

    default VoxelShape getSuckShape() {
        return SUCK;
    }

    double getLevelX();

    double getLevelY();

    double getLevelZ();

    default net.minecraft.world.phys.SectorVec3 getExactHopperPosition() {
        if (this instanceof net.minecraft.world.entity.Entity entity) {
            return entity.sectorPosition().add(0.0D, 0.5D, 0.0D);
        }
        if (this instanceof BlockEntity blockEntity) {
            net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
            return net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(pos.getX(), 0.5D, pos.getY() + 0.5D, pos.getZ(), 0.5D);
        }
        return net.minecraft.world.phys.SectorVec3.fromApproximate(this.getLevelX(), this.getLevelY(), this.getLevelZ());
    }
}
