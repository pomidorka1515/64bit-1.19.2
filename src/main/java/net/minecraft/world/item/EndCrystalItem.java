package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public class EndCrystalItem extends Item {
    public EndCrystalItem(Item.Properties pProperties) {
        super(pProperties);
    }

    public InteractionResult useOn(UseOnContext pContext) {
        Level level = pContext.getLevel();
        BlockPos blockpos = pContext.getClickedPos();
        BlockState blockstate = level.getBlockState(blockpos);
        if (!blockstate.is(Blocks.OBSIDIAN) && !blockstate.is(Blocks.BEDROCK)) {
            return InteractionResult.FAIL;
        } else {
            BlockPos blockpos1 = blockpos.above();
            if (!level.isEmptyBlock(blockpos1)) {
                return InteractionResult.FAIL;
            } else {
                List<Entity> list = level.getEntities((Entity) null, new AABB(blockpos1).expandTowards(0.0D, 1.0D, 0.0D));
                if (!list.isEmpty()) {
                    return InteractionResult.FAIL;
                } else {
                    if (level instanceof ServerLevel) {
                        EndCrystal endcrystal = new EndCrystal(net.minecraft.world.entity.EntityType.END_CRYSTAL, level);
                        endcrystal.moveTo(net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(blockpos1.getX(), 0.5D, blockpos1.getY(), blockpos1.getZ(), 0.5D), 0.0F, 0.0F);
                        endcrystal.setShowBottom(false);
                        level.addFreshEntity(endcrystal);
                        level.gameEvent(pContext.getPlayer(), GameEvent.ENTITY_PLACE, blockpos1);
                        EndDragonFight enddragonfight = ((ServerLevel) level).dragonFight();
                        if (enddragonfight != null) {
                            enddragonfight.tryRespawn();
                        }
                    }

                    pContext.getItemInHand().shrink(1);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }
    }

    public boolean isFoil(ItemStack pStack) {
        return true;
    }
}
