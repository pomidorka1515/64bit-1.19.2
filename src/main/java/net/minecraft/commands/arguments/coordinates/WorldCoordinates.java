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

    public WorldCoordinates(WorldCoordinate pX, WorldCoordinate pY, WorldCoordinate pZ) {
        this.x = pX;
        this.y = pY;
        this.z = pZ;
    }

    public SectorVec3 getExactPosition(CommandSourceStack source) {
        SectorVec3 origin = source.getExactPosition();
        SectorVec3 result = origin;
        if (!this.x.isRelative())
            result = SectorVec3.fromBlockAndFraction(this.x.getAbsoluteBlock(), this.x.getAbsoluteFraction(), result.y(), result.blockZ(), result.subZ());
        else
            result = result.add(this.x.getValue(), 0.0D, 0.0D);
        if (!this.z.isRelative())
            result = SectorVec3.fromBlockAndFraction(result.blockX(), result.subX(), result.y(), this.z.getAbsoluteBlock(), this.z.getAbsoluteFraction());
        else
            result = result.add(0.0D, 0.0D, this.z.getValue());
        double y = this.y.isRelative() ? origin.y() + this.y.getValue() : this.y.getValue();
        return result.withY(y);
    }

    public Vec2 getRotation(CommandSourceStack pSource) {
        Vec2 vec2 = pSource.getRotation();
        return new Vec2((float) this.x.get((double) vec2.x), (float) this.y.get((double) vec2.y));
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

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof WorldCoordinates)) {
            return false;
        } else {
            WorldCoordinates worldcoordinates = (WorldCoordinates) pOther;
            if (!this.x.equals(worldcoordinates.x)) {
                return false;
            } else {
                return !this.y.equals(worldcoordinates.y) ? false : this.z.equals(worldcoordinates.z);
            }
        }
    }

    public static WorldCoordinates parseInt(StringReader pReader) throws CommandSyntaxException {
        int i = pReader.getCursor();
        WorldCoordinate worldcoordinate = WorldCoordinate.parseInt(pReader);
        if (pReader.canRead() && pReader.peek() == ' ') {
            pReader.skip();
            WorldCoordinate worldcoordinate1 = WorldCoordinate.parseInt(pReader);
            if (pReader.canRead() && pReader.peek() == ' ') {
                pReader.skip();
                WorldCoordinate worldcoordinate2 = WorldCoordinate.parseInt(pReader);
                return new WorldCoordinates(worldcoordinate, worldcoordinate1, worldcoordinate2);
            } else {
                pReader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(pReader);
            }
        } else {
            pReader.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(pReader);
        }
    }

    public static WorldCoordinates parseDouble(StringReader pReader, boolean pCenterCorrect) throws CommandSyntaxException {
        int i = pReader.getCursor();
        WorldCoordinate worldcoordinate = WorldCoordinate.parseDouble(pReader, pCenterCorrect);
        if (pReader.canRead() && pReader.peek() == ' ') {
            pReader.skip();
            WorldCoordinate worldcoordinate1 = WorldCoordinate.parseDouble(pReader, false);
            if (pReader.canRead() && pReader.peek() == ' ') {
                pReader.skip();
                WorldCoordinate worldcoordinate2 = WorldCoordinate.parseDouble(pReader, pCenterCorrect);
                return new WorldCoordinates(worldcoordinate, worldcoordinate1, worldcoordinate2);
            } else {
                pReader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(pReader);
            }
        } else {
            pReader.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(pReader);
        }
    }

    public static WorldCoordinates absolute(double pX, double pY, double pZ) {
        return new WorldCoordinates(new WorldCoordinate(false, pX), new WorldCoordinate(false, pY), new WorldCoordinate(false, pZ));
    }

    public static WorldCoordinates absolute(String x, String y, String z) {
        return new WorldCoordinates(WorldCoordinate.absoluteDecimal(x, false), WorldCoordinate.absoluteDecimal(y, false), WorldCoordinate.absoluteDecimal(z, false));
    }

    public static WorldCoordinates absolute(Vec2 pVector) {
        return new WorldCoordinates(new WorldCoordinate(false, (double) pVector.x), new WorldCoordinate(false, (double) pVector.y), new WorldCoordinate(true, 0.0D));
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
