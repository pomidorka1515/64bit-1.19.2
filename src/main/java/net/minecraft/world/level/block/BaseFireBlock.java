package net.minecraft.world.level.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseFireBlock extends Block {
    private static final int SECONDS_ON_FIRE = 8;
    private final float fireDamage;
    protected static final float AABB_OFFSET = 1.0F;
    protected static final VoxelShape DOWN_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public BaseFireBlock(BlockBehaviour.Properties pProperties, float pFireDamage) {
        super(pProperties);
        this.fireDamage = pFireDamage;
    }

    public BlockState getStateForPlacement(BlockPlaceContext pContext) {
        return getState(pContext.getLevel(), pContext.getClickedPos());
    }

    public static BlockState getState(BlockGetter pReader, BlockPos pPos) {
        BlockPos blockpos = pPos.below();
        BlockState blockstate = pReader.getBlockState(blockpos);
        return SoulFireBlock.canSurviveOnBlock(blockstate) ? Blocks.SOUL_FIRE.defaultBlockState() : ((FireBlock) Blocks.FIRE).getStateForPlacement(pReader, pPos);
    }

    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return DOWN_AABB;
    }

    public void animateTick(BlockState pState, Level pLevel, BlockPos pPos, RandomSource pRandom) {
        if (pRandom.nextInt(24) == 0) {
            pLevel.playLocalSound((double) pPos.getX() + 0.5D, (double) pPos.getY() + 0.5D, (double) pPos.getZ() + 0.5D, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F + pRandom.nextFloat(), pRandom.nextFloat() * 0.7F + 0.3F, false);
        }

        BlockPos blockpos = pPos.below();
        BlockState blockstate = pLevel.getBlockState(blockpos);
        if (!this.canBurn(blockstate) && !blockstate.isFaceSturdy(pLevel, blockpos, Direction.UP)) {
            if (this.canBurn(pLevel.getBlockState(pPos.west()))) {
                for (int j = 0; j < 2; ++j) {
                    double d3 = pRandom.nextDouble() * (double) 0.1F;
                    double d8 = (double) pPos.getY() + pRandom.nextDouble();
                    double d13 = pRandom.nextDouble();
                    pLevel.addParticle(ParticleTypes.LARGE_SMOKE, SectorVec3.fromBlockAndFraction(pPos.getX(), d3, d8, pPos.getZ(), d13), 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(pLevel.getBlockState(pPos.east()))) {
                for (int k = 0; k < 2; ++k) {
                    double d4 = 1.0D - pRandom.nextDouble() * (double) 0.1F;
                    double d9 = (double) pPos.getY() + pRandom.nextDouble();
                    double d14 = pRandom.nextDouble();
                    pLevel.addParticle(ParticleTypes.LARGE_SMOKE, SectorVec3.fromBlockAndFraction(pPos.getX(), d4, d9, pPos.getZ(), d14), 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(pLevel.getBlockState(pPos.north()))) {
                for (int l = 0; l < 2; ++l) {
                    double d5 = pRandom.nextDouble();
                    double d10 = (double) pPos.getY() + pRandom.nextDouble();
                    double d15 = pRandom.nextDouble() * (double) 0.1F;
                    pLevel.addParticle(ParticleTypes.LARGE_SMOKE, SectorVec3.fromBlockAndFraction(pPos.getX(), d5, d10, pPos.getZ(), d15), 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(pLevel.getBlockState(pPos.south()))) {
                for (int i1 = 0; i1 < 2; ++i1) {
                    double d6 = pRandom.nextDouble();
                    double d11 = (double) pPos.getY() + pRandom.nextDouble();
                    double d16 = 1.0D - pRandom.nextDouble() * (double) 0.1F;
                    pLevel.addParticle(ParticleTypes.LARGE_SMOKE, SectorVec3.fromBlockAndFraction(pPos.getX(), d6, d11, pPos.getZ(), d16), 0.0D, 0.0D, 0.0D);
                }
            }

            if (this.canBurn(pLevel.getBlockState(pPos.above()))) {
                for (int j1 = 0; j1 < 2; ++j1) {
                    double d7 = pRandom.nextDouble();
                    double d12 = (double) (pPos.getY() + 1) - pRandom.nextDouble() * (double) 0.1F;
                    double d17 = pRandom.nextDouble();
                    pLevel.addParticle(ParticleTypes.LARGE_SMOKE, SectorVec3.fromBlockAndFraction(pPos.getX(), d7, d12, pPos.getZ(), d17), 0.0D, 0.0D, 0.0D);
                }
            }
        } else {
            for (int i = 0; i < 3; ++i) {
                double d0 = pRandom.nextDouble();
                double d1 = (double) pPos.getY() + pRandom.nextDouble() * 0.5D + 0.5D;
                double d2 = pRandom.nextDouble();
                pLevel.addParticle(ParticleTypes.LARGE_SMOKE, SectorVec3.fromBlockAndFraction(pPos.getX(), d0, d1, pPos.getZ(), d2), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    protected abstract boolean canBurn(BlockState pState);

    public void entityInside(BlockState pState, Level pLevel, BlockPos pPos, Entity pEntity) {
        if (!pEntity.fireImmune()) {
            pEntity.setRemainingFireTicks(pEntity.getRemainingFireTicks() + 1);
            if (pEntity.getRemainingFireTicks() == 0) {
                pEntity.setSecondsOnFire(8);
            }
        }

        pEntity.hurt(DamageSource.IN_FIRE, this.fireDamage);
        super.entityInside(pState, pLevel, pPos, pEntity);
    }

    public void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
        if (!pOldState.is(pState.getBlock())) {
            if (inPortalDimension(pLevel)) {
                Optional<PortalShape> optional = PortalShape.findEmptyPortalShape(pLevel, pPos, Direction.Axis.X);
                if (optional.isPresent()) {
                    optional.get().createPortalBlocks();
                    return;
                }
            }

            if (!pState.canSurvive(pLevel, pPos)) {
                pLevel.removeBlock(pPos, false);
            }
        }
    }

    private static boolean inPortalDimension(Level pLevel) {
        return pLevel.dimension() == Level.OVERWORLD || pLevel.dimension() == Level.NETHER;
    }

    protected void spawnDestroyParticles(Level pLevel, Player pPlayer, BlockPos pPos, BlockState pState) {
    }

    public void playerWillDestroy(Level pLevel, BlockPos pPos, BlockState pState, Player pPlayer) {
        if (!pLevel.isClientSide()) {
            pLevel.levelEvent((Player) null, 1009, pPos, 0);
        }

        super.playerWillDestroy(pLevel, pPos, pState, pPlayer);
    }

    public static boolean canBePlacedAt(Level pLevel, BlockPos pPos, Direction pDirection) {
        BlockState blockstate = pLevel.getBlockState(pPos);
        if (!blockstate.isAir()) {
            return false;
        } else {
            return getState(pLevel, pPos).canSurvive(pLevel, pPos) || isPortal(pLevel, pPos, pDirection);
        }
    }

    private static boolean isPortal(Level pLevel, BlockPos pPos, Direction pDirection) {
        if (!inPortalDimension(pLevel)) {
            return false;
        } else {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = pPos.mutable();
            boolean flag = false;

            for (Direction direction : Direction.values()) {
                if (pLevel.getBlockState(blockpos$mutableblockpos.set(pPos).move(direction)).is(Blocks.OBSIDIAN)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return false;
            } else {
                Direction.Axis direction$axis = pDirection.getAxis().isHorizontal() ? pDirection.getCounterClockWise().getAxis() : Direction.Plane.HORIZONTAL.getRandomAxis(pLevel.random);
                return PortalShape.findEmptyPortalShape(pLevel, pPos, direction$axis).isPresent();
            }
        }
    }
}
