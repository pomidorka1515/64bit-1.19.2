package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public interface PositionalRandomFactory {
    default RandomSource at(BlockPos pPos) {
        return this.at(pPos.getX().hi(), pPos.getXLo(), pPos.getY(), pPos.getZ().hi(), pPos.getZLo());
    }

    default RandomSource fromHashOf(ResourceLocation pName) {
        return this.fromHashOf(pName.toString());
    }

    RandomSource fromHashOf(String pName);

    /** Legacy content/seed domain: horizontal inputs are explicitly projected low 64 bits. */
    RandomSource at(long pX, int pY, long pZ);

    default RandomSource at(long xHi, long xLo, int y, long zHi, long zLo) {
        return at(net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(xHi, xLo), y,
                net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.seedCoordinate(zHi, zLo));
    }

    @VisibleForTesting
    void parityConfigString(StringBuilder pBuilder);
}
