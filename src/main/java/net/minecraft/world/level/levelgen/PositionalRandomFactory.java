package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public interface PositionalRandomFactory {
    default RandomSource at(BlockPos pPos) {
        // Explicit low-64 content/seed projection; the caller retains the exact placement identity.
        return this.at(pPos.getXLo(), pPos.getY(), pPos.getZLo());
    }

    default RandomSource fromHashOf(ResourceLocation pName) {
        return this.fromHashOf(pName.toString());
    }

    RandomSource fromHashOf(String pName);

    /** Legacy content/seed domain: horizontal inputs are explicitly projected low 64 bits. */
    RandomSource at(long pX, int pY, long pZ);

    @VisibleForTesting
    void parityConfigString(StringBuilder pBuilder);
}
