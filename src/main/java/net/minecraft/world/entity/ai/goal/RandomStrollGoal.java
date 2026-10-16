package net.minecraft.world.entity.ai.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.SectorVec3;

public class RandomStrollGoal extends Goal {
    public static final int DEFAULT_INTERVAL = 120;
    protected final PathfinderMob mob;
    @Nullable
    protected SectorVec3 wantedPosition;
    protected final double speedModifier;
    protected int interval;
    protected boolean forceTrigger;
    private final boolean checkNoActionTime;

    public RandomStrollGoal(PathfinderMob pMob, double pSpeedModifier) {
        this(pMob, pSpeedModifier, 120);
    }

    public RandomStrollGoal(PathfinderMob pMob, double pSpeedModifier, int pInterval) {
        this(pMob, pSpeedModifier, pInterval, true);
    }

    public RandomStrollGoal(PathfinderMob pMob, double pSpeedModifier, int pInterval, boolean pCheckNoActionTime) {
        this.mob = pMob;
        this.speedModifier = pSpeedModifier;
        this.interval = pInterval;
        this.checkNoActionTime = pCheckNoActionTime;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean canUse() {
        if (this.mob.isVehicle()) {
            return false;
        } else {
            if (!this.forceTrigger) {
                if (this.checkNoActionTime && this.mob.getNoActionTime() >= 100) {
                    return false;
                }

                if (this.mob.getRandom().nextInt(reducedTickDelay(this.interval)) != 0) {
                    return false;
                }
            }

            this.wantedPosition = this.getPosition();
            if (this.wantedPosition == null)
                return false;
            this.forceTrigger = false;
            return true;
        }
    }

    @Nullable
    protected SectorVec3 getPosition() {
        return DefaultRandomPos.getSectorPos(this.mob, 10, 7);
    }

    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone() && !this.mob.isVehicle();
    }

    public void start() {
        if (this.wantedPosition != null) {
            this.mob.getNavigation().moveTo(this.wantedPosition, this.speedModifier);
        }
    }

    public void stop() {
        this.mob.getNavigation().stop();
        super.stop();
    }

    public void trigger() {
        this.forceTrigger = true;
    }

    public void setInterval(int pNewchance) {
        this.interval = pNewchance;
    }
}
