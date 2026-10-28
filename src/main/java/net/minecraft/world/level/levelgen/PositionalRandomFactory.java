package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public interface PositionalRandomFactory {
    default RandomSource at(BlockPos pPos) {
        return this.at(pPos.getX(), pPos.getY(), pPos.getZ());
    }

    default RandomSource fromHashOf(ResourceLocation pName) {
        return this.fromHashOf(pName.toString());
    }

    RandomSource fromHashOf(String pName);

    /** Narrow coordinate entry point; wide callers fold every limb before seeding. */
    RandomSource at(long pX, int pY, long pZ);

    default RandomSource at(net.minecraft.core.Int1088 x, int y, net.minecraft.core.Int1088 z) {
        return at(net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(x), y,
                net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(z));
    }

    @VisibleForTesting
    void parityConfigString(StringBuilder pBuilder);
}
