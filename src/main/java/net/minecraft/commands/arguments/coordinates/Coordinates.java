package net.minecraft.commands.arguments.coordinates;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public interface Coordinates {
   /** Legacy view. Exact command code must use {@link #getExactPosition}. */
   Vec3 getPosition(CommandSourceStack p_119566_);

   SectorVec3 getExactPosition(CommandSourceStack source);

   Vec2 getRotation(CommandSourceStack p_119567_);

   default BlockPos getBlockPos(CommandSourceStack source) {
      return this.getExactPosition(source).blockPosition();
   }

   boolean isXRelative();

   boolean isYRelative();

   boolean isZRelative();
}