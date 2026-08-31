package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldBounds;

public class InSquarePlacement extends PlacementModifier {
   private static final InSquarePlacement INSTANCE = new InSquarePlacement();
   public static final Codec<InSquarePlacement> CODEC = Codec.unit(() -> {
      return INSTANCE;
   });

   public static InSquarePlacement spread() {
      return INSTANCE;
   }

   public Stream<BlockPos> getPositions(PlacementContext p_226348_, RandomSource p_226349_, BlockPos p_226350_) {
      long i = WorldBounds.addBlockOffset(p_226350_.getX(), p_226349_.nextInt(16));
      long j = WorldBounds.addBlockOffset(p_226350_.getZ(), p_226349_.nextInt(16));
      return Stream.of(new BlockPos(i, p_226350_.getY(), j));
   }

   public PlacementModifierType<?> type() {
      return PlacementModifierType.IN_SQUARE;
   }
}