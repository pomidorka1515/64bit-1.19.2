package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class WorldCoordinates implements Coordinates {
   private final WorldCoordinate x;
   private final WorldCoordinate y;
   private final WorldCoordinate z;

   public WorldCoordinates(WorldCoordinate p_120883_, WorldCoordinate p_120884_, WorldCoordinate p_120885_) {
      this.x = p_120883_;
      this.y = p_120884_;
      this.z = p_120885_;
   }

   public SectorVec3 getExactPosition(CommandSourceStack source) {
      SectorVec3 origin = source.getExactPosition();
      SectorVec3 result = origin;
      if (!this.x.isRelative()) result = result.withXDecimal(this.x.getLiteral());
      else result = result.add(this.x.getValue(), 0.0D, 0.0D);
      if (!this.z.isRelative()) result = result.withZDecimal(this.z.getLiteral());
      else result = result.add(0.0D, 0.0D, this.z.getValue());
      double y = this.y.isRelative() ? origin.y() + this.y.getValue() : this.y.getValue();
      return result.withY(y);
   }

   public Vec3 getPosition(CommandSourceStack source) {
      return this.getExactPosition(source).toApproximateVec3();
   }

   public Vec2 getRotation(CommandSourceStack p_120896_) {
      Vec2 vec2 = p_120896_.getRotation();
      return new Vec2((float)this.x.get((double)vec2.x), (float)this.y.get((double)vec2.y));
   }

   public boolean isXRelative() {
      return this.x.isRelative();
   }

   public boolean isYRelative() {
      return this.y.isRelative();
   }

   public boolean isZRelative() {
      return this.z.isRelative();
   }

   public boolean equals(Object p_120900_) {
      if (this == p_120900_) {
         return true;
      } else if (!(p_120900_ instanceof WorldCoordinates)) {
         return false;
      } else {
         WorldCoordinates worldcoordinates = (WorldCoordinates)p_120900_;
         if (!this.x.equals(worldcoordinates.x)) {
            return false;
         } else {
            return !this.y.equals(worldcoordinates.y) ? false : this.z.equals(worldcoordinates.z);
         }
      }
   }

   public static WorldCoordinates parseInt(StringReader p_120888_) throws CommandSyntaxException {
      int i = p_120888_.getCursor();
      WorldCoordinate worldcoordinate = WorldCoordinate.parseInt(p_120888_);
      if (p_120888_.canRead() && p_120888_.peek() == ' ') {
         p_120888_.skip();
         WorldCoordinate worldcoordinate1 = WorldCoordinate.parseInt(p_120888_);
         if (p_120888_.canRead() && p_120888_.peek() == ' ') {
            p_120888_.skip();
            WorldCoordinate worldcoordinate2 = WorldCoordinate.parseInt(p_120888_);
            return new WorldCoordinates(worldcoordinate, worldcoordinate1, worldcoordinate2);
         } else {
            p_120888_.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(p_120888_);
         }
      } else {
         p_120888_.setCursor(i);
         throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(p_120888_);
      }
   }

   public static WorldCoordinates parseDouble(StringReader p_120890_, boolean p_120891_) throws CommandSyntaxException {
      int i = p_120890_.getCursor();
      WorldCoordinate worldcoordinate = WorldCoordinate.parseDouble(p_120890_, p_120891_);
      if (p_120890_.canRead() && p_120890_.peek() == ' ') {
         p_120890_.skip();
         WorldCoordinate worldcoordinate1 = WorldCoordinate.parseDouble(p_120890_, false);
         if (p_120890_.canRead() && p_120890_.peek() == ' ') {
            p_120890_.skip();
            WorldCoordinate worldcoordinate2 = WorldCoordinate.parseDouble(p_120890_, p_120891_);
            return new WorldCoordinates(worldcoordinate, worldcoordinate1, worldcoordinate2);
         } else {
            p_120890_.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(p_120890_);
         }
      } else {
         p_120890_.setCursor(i);
         throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(p_120890_);
      }
   }

   public static WorldCoordinates absolute(double p_175086_, double p_175087_, double p_175088_) {
      return new WorldCoordinates(new WorldCoordinate(false, p_175086_), new WorldCoordinate(false, p_175087_), new WorldCoordinate(false, p_175088_));
   }

   public static WorldCoordinates absolute(String x, String y, String z) {
      return new WorldCoordinates(WorldCoordinate.absoluteDecimal(x, false), WorldCoordinate.absoluteDecimal(y, false), WorldCoordinate.absoluteDecimal(z, false));
   }

   public static WorldCoordinates absolute(Vec2 p_175090_) {
      return new WorldCoordinates(new WorldCoordinate(false, (double)p_175090_.x), new WorldCoordinate(false, (double)p_175090_.y), new WorldCoordinate(true, 0.0D));
   }

   public static WorldCoordinates current() {
      return new WorldCoordinates(new WorldCoordinate(true, 0.0D), new WorldCoordinate(true, 0.0D), new WorldCoordinate(true, 0.0D));
   }

   public int hashCode() {
      int i = this.x.hashCode();
      i = 31 * i + this.y.hashCode();
      return 31 * i + this.z.hashCode();
   }
}