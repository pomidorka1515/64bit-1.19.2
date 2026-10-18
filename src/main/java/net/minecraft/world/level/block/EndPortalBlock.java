package net.minecraft.world.level.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EndPortalBlock extends BaseEntityBlock {
    protected static final VoxelShape SHAPE = Block.box(0.0D, 6.0D, 0.0D, 16.0D, 12.0D, 16.0D);

    protected EndPortalBlock(BlockBehaviour.Properties pProperties) {
        super(pProperties);
    }

    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TheEndPortalBlockEntity(pPos, pState);
    }

    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    public void entityInside(BlockState pState, Level pLevel, BlockPos pPos, Entity pEntity) {
        if (pLevel instanceof ServerLevel && !pEntity.isPassenger() && !pEntity.isVehicle() && pEntity.canChangeDimensions() && Shapes.joinIsNotEmpty(Shapes.create(pEntity.getSectorBoundingBox().toLocalAABB(new net.minecraft.world.phys.SectorPhysicsOrigin(pPos.getX(), pPos.getY(), pPos.getZ()))), pState.getShape(pLevel, pPos), BooleanOp.AND)) {
            ResourceKey<Level> resourcekey = pLevel.dimension() == Level.END ? Level.OVERWORLD : Level.END;
            ServerLevel serverlevel = ((ServerLevel) pLevel).getServer().getLevel(resourcekey);
            if (serverlevel == null) {
                return;
            }

            pEntity.changeDimension(serverlevel);
        }
    }

    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        pLevel.addParticle(ParticleTypes.SMOKE, SectorVec3.fromBlockAndFraction(pPos.getX(), pRandom.nextDouble(), (double) pPos.getY() + 0.8D, pPos.getZ(), pRandom.nextDouble()), 0.0D, 0.0D, 0.0D);
    }

    public ItemStack getCloneItemStack(BlockGetter pLevel, BlockPos pPos, BlockState pState) {
        return ItemStack.EMPTY;
    }

    public boolean canBeReplaced(BlockState pState, Fluid pFluid) {
        return false;
    }
}
