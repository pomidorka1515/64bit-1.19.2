package net.minecraft.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.SectorVec3;

public class BlockSourceImpl implements BlockSource {
    private final ServerLevel level;
    private final BlockPos pos;

    public BlockSourceImpl(ServerLevel pLevel, BlockPos pPos) {
        this.level = pLevel;
        this.pos = pPos;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public SectorVec3 exactPosition() {
        return SectorVec3.fromBlockAndFraction(this.pos.getX(), 0.5D, this.y(), this.pos.getZ(), 0.5D);
    }

    public double x() {
        return SectorVec3.fromBlockAndFraction(this.pos.getX(), 0.5D, this.y(), this.pos.getZ(), 0.5D).toApproximateVec3().x;
    }

    public double y() {
        return (double) this.pos.getY() + 0.5D;
    }

    public double z() {
        return SectorVec3.fromBlockAndFraction(this.pos.getX(), 0.5D, this.y(), this.pos.getZ(), 0.5D).toApproximateVec3().z;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public BlockState getBlockState() {
        return this.level.getBlockState(this.pos);
    }

    public <T extends BlockEntity> T getEntity() {
        return (T) this.level.getBlockEntity(this.pos);
    }
}
