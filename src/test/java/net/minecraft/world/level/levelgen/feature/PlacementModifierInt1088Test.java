package net.minecraft.world.level.levelgen.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.RandomOffsetPlacement;
import org.junit.jupiter.api.Test;

class PlacementModifierInt1088Test {
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger MAX = BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE);

    @Test
    void localPlacementOffsetsRetainAllLimbsAndRandomOrder() {
        List<BigInteger> origins = new ArrayList<>(List.of(BigInteger.ZERO, MIN, MAX));
        for (int bit = 64; bit <= 1024; bit += 64) {
            BigInteger seam = BigInteger.ONE.shiftLeft(bit);
            origins.add(seam.subtract(BigInteger.ONE));
            origins.add(seam.negate());
        }
        var offset = RandomOffsetPlacement.of(UniformInt.of(-16, 16), UniformInt.of(-4, 4));
        for (BigInteger x : origins) {
            BigInteger z = x.negate().max(MIN).min(MAX);
            BlockPos origin = pos(x, 70, z);
            RandomSource random = RandomSource.create(42);
            RandomSource oracle = RandomSource.create(42);
            for (int i = 0; i < 32; i++) {
                int dx = oracle.nextInt(16);
                int dz = oracle.nextInt(16);
                assertEquals(pos(x.add(BigInteger.valueOf(dx)), 70, z.add(BigInteger.valueOf(dz))),
                        InSquarePlacement.spread().getPositions(null, random, origin).findFirst().orElseThrow());
                dx = oracle.nextInt(33) - 16;
                int dy = oracle.nextInt(9) - 4;
                dz = oracle.nextInt(33) - 16;
                assertEquals(pos(x.add(BigInteger.valueOf(dx)), 70 + dy, z.add(BigInteger.valueOf(dz))),
                        offset.getPositions(null, random, origin).findFirst().orElseThrow());
            }
            assertEquals(oracle.nextLong(), random.nextLong());
        }
    }

    private static BlockPos pos(BigInteger x, int y, BigInteger z) {
        return new BlockPos(Int1088.parseDecimal(x.max(MIN).min(MAX).toString()), y,
                Int1088.parseDecimal(z.max(MIN).min(MAX).toString()));
    }
}
