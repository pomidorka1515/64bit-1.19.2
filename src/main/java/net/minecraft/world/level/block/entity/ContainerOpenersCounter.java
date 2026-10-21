package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.SectorVec3;

public abstract class ContainerOpenersCounter {
    private static final int CHECK_TICK_DELAY = 5;
    private int openCount;

    protected abstract void onOpen(Level pLevel, BlockPos pPos, BlockState pState);

    protected abstract void onClose(Level pLevel, BlockPos pPos, BlockState pState);

    protected abstract void openerCountChanged(Level pLevel, BlockPos pPos, BlockState pState, int pCount, int pOpenCount);

    protected abstract boolean isOwnContainer(Player p_155451_);

    public void incrementOpeners(Player pPlayer, Level pLevel, BlockPos pPos, BlockState pState) {
        int i = this.openCount++;
        if (i == 0) {
            this.onOpen(pLevel, pPos, pState);
            pLevel.gameEvent(pPlayer, GameEvent.CONTAINER_OPEN, pPos);
            scheduleRecheck(pLevel, pPos, pState);
        }

        this.openerCountChanged(pLevel, pPos, pState, i, this.openCount);
    }

    public void decrementOpeners(Player pPlayer, Level pLevel, BlockPos pPos, BlockState pState) {
        int i = this.openCount--;
        if (this.openCount == 0) {
            this.onClose(pLevel, pPos, pState);
            pLevel.gameEvent(pPlayer, GameEvent.CONTAINER_CLOSE, pPos);
        }

        this.openerCountChanged(pLevel, pPos, pState, i, this.openCount);
    }

    private int getOpenCount(Level pLevel, BlockPos pPos) {
        int i = 0;

        for (Player player : pLevel.players()) {
            // The former float-backed AABB query drifted off the chest beyond
            // 2^24, so scheduled rechecks erased legitimate openers.  Filtering
            // the level's player list in split-coordinate space keeps the exact
            // vanilla search volume: [block - 5, block + 6] on every axis.
            if (this.isOwnContainer(player) && isWithinRecheckRange(player, pPos)) {
                ++i;
            }
        }

        return i;
    }

    static boolean isWithinRecheckRange(Player pLevel, BlockPos pPos) {
        return isWithinRecheckRange(pLevel.exactPosition(), pPos);
    }

    static boolean isWithinRecheckRange(SectorVec3 pLevel, BlockPos pPos) {
        SectorVec3 origin = SectorVec3.fromBlockAndFraction(pPos.getX(), 0.0D,
                pPos.getY(), pPos.getZ(), 0.0D);
        double x = pLevel.relativeX(origin);
        double y = pLevel.relativeY(origin);
        double z = pLevel.relativeZ(origin);
        return x >= -5.0D && x <= 6.0D && y >= -5.0D && y <= 6.0D && z >= -5.0D && z <= 6.0D;
    }

    public void recheckOpeners(Level pLevel, BlockPos pPos, BlockState pState) {
        int i = this.getOpenCount(pLevel, pPos);
        int j = this.openCount;
        if (j != i) {
            boolean flag = i != 0;
            boolean flag1 = j != 0;
            if (flag && !flag1) {
                this.onOpen(pLevel, pPos, pState);
                pLevel.gameEvent((Entity) null, GameEvent.CONTAINER_OPEN, pPos);
            } else if (!flag) {
                this.onClose(pLevel, pPos, pState);
                pLevel.gameEvent((Entity) null, GameEvent.CONTAINER_CLOSE, pPos);
            }

            this.openCount = i;
        }

        this.openerCountChanged(pLevel, pPos, pState, j, i);
        if (i > 0) {
            scheduleRecheck(pLevel, pPos, pState);
        }
    }

    public int getOpenerCount() {
        return this.openCount;
    }

    private static void scheduleRecheck(Level pLevel, BlockPos pPos, BlockState pState) {
        pLevel.scheduleTick(pPos, pState.getBlock(), 5);
    }
}
