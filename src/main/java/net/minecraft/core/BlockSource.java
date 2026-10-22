package net.minecraft.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockSource extends Position {
    @Override
    default net.minecraft.world.phys.SectorVec3 exactPosition() {
        BlockPos pos = this.getPos();
        return net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(pos.getX(), 0.5D,
                this.y(), pos.getZ(), 0.5D);
    }

    double x();

    double y();

    double z();

    BlockPos getPos();

    BlockState getBlockState();

    <T extends BlockEntity> T getEntity();

    ServerLevel getLevel();
}
