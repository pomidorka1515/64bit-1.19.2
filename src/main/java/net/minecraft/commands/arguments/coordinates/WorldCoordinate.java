package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.core.Int128;
import net.minecraft.network.chat.Component;

public class WorldCoordinate {
    private static final char PREFIX_RELATIVE = '~';
    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.double"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.int"));
    private final boolean relative;
    private final double value;
    private final String literal;
    private final Int128 absoluteBlock;

    public WorldCoordinate(boolean relative, double value) {
        this(relative, value, Double.toString(value));
    }

    private WorldCoordinate(boolean relative, double value, String literal) {
        this.relative = relative;
        this.value = value;
        this.literal = literal;
        this.absoluteBlock = relative ? null : Int128.parseDecimal(new BigDecimal(literal).setScale(0, RoundingMode.FLOOR).toPlainString());
    }

    public double get(double pCoord) {
        return this.relative ? this.value + pCoord : this.value;
    }

    public double getValue() {
        return this.value;
    }
    public String getLiteral() {
        return this.literal;
    }

    public Int128 getAbsoluteBlock() {
        return this.absoluteBlock;
    }

    public static WorldCoordinate parseDouble(StringReader pReader, boolean pCenterCorrect) throws CommandSyntaxException {
        if (pReader.canRead() && pReader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(pReader);
        } else if (!pReader.canRead()) {
            throw ERROR_EXPECTED_DOUBLE.createWithContext(pReader);
        } else {
            boolean flag = isRelative(pReader);
            int i = pReader.getCursor();
            String s = pReader.canRead() && pReader.peek() != ' ' ? readCoordinateToken(pReader) : "";
            if (flag && s.isEmpty()) {
                return new WorldCoordinate(true, 0.0D, "0");
            } else {
                try {
                    String literal = s.isEmpty() ? "0" : s;
                    if (!isDecimal(literal))
                        throw new NumberFormatException(literal);
                    double d0 = Double.parseDouble(literal);
                    if (!Double.isFinite(d0))
                        throw new NumberFormatException(literal);
                    if (!s.contains(".") && !flag && pCenterCorrect) {
                        d0 += 0.5D;
                        literal = new BigDecimal(literal).add(BigDecimal.valueOf(0.5D)).toPlainString();
                    }
                    return new WorldCoordinate(flag, d0, literal);
                } catch (NumberFormatException exception) {
                    pReader.setCursor(i);
                    throw ERROR_EXPECTED_DOUBLE.createWithContext(pReader);
                }
            }
        }
    }

    public static WorldCoordinate parseInt(StringReader pReader) throws CommandSyntaxException {
        if (pReader.canRead() && pReader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(pReader);
        } else if (!pReader.canRead()) {
            throw ERROR_EXPECTED_INT.createWithContext(pReader);
        } else {
            boolean flag = isRelative(pReader);
            int valueStart = pReader.getCursor();
            String token = pReader.canRead() && pReader.peek() != ' ' ? readCoordinateToken(pReader) : "";
            try {
                String literal = token.isEmpty() ? "0" : token;
                double d0 = flag ? Double.parseDouble(literal) : Int128.parseDecimal(literal).toDoubleSaturated();
                if (!Double.isFinite(d0))
                    throw new NumberFormatException(literal);
                return new WorldCoordinate(flag, d0, literal);
            } catch (NumberFormatException exception) {
                pReader.setCursor(valueStart);
                throw ERROR_EXPECTED_INT.createWithContext(pReader);
            }
        }
    }

    private static String readCoordinateToken(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    private static boolean isDecimal(String token) {
        try {
            new BigDecimal(token);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    public static boolean isRelative(StringReader pReader) {
        boolean flag;
        if (pReader.peek() == '~') {
            flag = true;
            pReader.skip();
        } else {
            flag = false;
        }

        return flag;
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (!(pOther instanceof WorldCoordinate)) {
            return false;
        } else {
            WorldCoordinate worldcoordinate = (WorldCoordinate) pOther;
            if (this.relative != worldcoordinate.relative) {
                return false;
            } else {
                return this.relative ? Double.compare(worldcoordinate.value, this.value) == 0
                        : new BigDecimal(this.literal).compareTo(new BigDecimal(worldcoordinate.literal)) == 0;
            }
        }
    }

    public int hashCode() {
        if (!this.relative)
            return new BigDecimal(this.literal).stripTrailingZeros().hashCode();
        int i = this.relative ? 1 : 0;
        long j = Double.doubleToLongBits(this.value);
        return 31 * i + (int) (j ^ j >>> 32);
    }

    public boolean isRelative() {
        return this.relative;
    }

    public static WorldCoordinate absoluteDecimal(String value, boolean centerCorrect) {
        if (!isDecimal(value))
            throw new IllegalArgumentException("Invalid coordinate: " + value);
        BigDecimal decimal = new BigDecimal(value);
        if (centerCorrect && value.indexOf('.') < 0)
            decimal = decimal.add(BigDecimal.valueOf(0.5D));
        return new WorldCoordinate(false, decimal.doubleValue(), decimal.toPlainString());
    }
}
