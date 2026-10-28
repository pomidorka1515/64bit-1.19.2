package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.core.Int1088;
import net.minecraft.network.chat.Component;

public class WorldCoordinate {
    private static final char PREFIX_RELATIVE = '~';
    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.double"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.int"));
    private final boolean relative;
    private final double value;
    private final String literal;
    private final Int1088 absoluteBlock;
    private final double fraction;
    private final CoordinateExpression.Value exactValue;

    public WorldCoordinate(boolean relative, double value) {
        this(relative, value, relative ? Double.toString(value) : plainDouble(value));
    }

    private static String plainDouble(double value) {
        if (!Double.isFinite(value)) throw new NumberFormatException("Non-finite coordinate");
        String text = Double.toString(value);
        int e = text.indexOf('E');
        if (e < 0) return text;
        int exponent = Integer.parseInt(text.substring(e + 1));
        String mantissa = text.substring(0, e);
        boolean negative = mantissa.startsWith("-");
        if (negative) mantissa = mantissa.substring(1);
        int point = mantissa.indexOf('.');
        String digits = mantissa.replace(".", "");
        int index = point + exponent;
        String plain = index <= 0 ? "0." + "0".repeat(-index) + digits
                : index >= digits.length() ? digits + "0".repeat(index - digits.length())
                : digits.substring(0, index) + "." + digits.substring(index);
        return negative ? "-" + plain : plain;
    }

    private WorldCoordinate(boolean relative, double value, String literal) {
        this.exactValue = null;
        this.relative = relative;
        this.value = value;
        this.literal = literal;
        if (relative) {
            if (!Double.isFinite(value)) throw new NumberFormatException("Non-finite coordinate");
            this.absoluteBlock = null;
            this.fraction = 0;
        } else {
            if (!isDecimal(literal)) throw new NumberFormatException(literal);
            int dot = literal.indexOf('.');
            String integer = dot < 0 ? literal : literal.substring(0, dot);
            if (integer.isEmpty() || integer.equals("-") || integer.equals("+")) integer += "0";
            Int1088 block = Int1088.parseDecimal(integer);
            double part = dot < 0 ? 0.0D : Double.parseDouble("0" + literal.substring(dot));
            boolean negative = literal.startsWith("-");
            boolean nonzero = dot >= 0 && literal.substring(dot + 1).chars().anyMatch(c -> c != '0');
            if (negative && block.equals(Int1088.MIN) && nonzero) throw new NumberFormatException(literal);
            if (negative && nonzero) {
                block = block.subtractSaturated(Int1088.ONE);
                // Round the floor-relative fraction itself, not 1 minus an
                // already rounded magnitude (which can erase a tiny remainder).
                part = complementedFraction(literal.substring(dot + 1));
                if (part == 1.0D) part = Math.nextDown(1.0D);
            } else if (part == 1.0D) {
                if (block.equals(Int1088.MAX)) part = Math.nextDown(1.0D);
                else {
                    block = block.addSaturated(Int1088.ONE);
                    part = 0.0D;
                }
            }
            this.absoluteBlock = block;
            this.fraction = part;
        }
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

    public Int1088 getAbsoluteBlock() {
        return this.absoluteBlock;
    }

    public double getAbsoluteFraction() {
        return this.fraction;
    }

    public static WorldCoordinate parseLegacyDouble(StringReader pReader, boolean pCenterCorrect) throws CommandSyntaxException {
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
                    String literal = s;
                    if (!isDecimal(literal))
                        throw new NumberFormatException(literal);
                    double d0 = Double.parseDouble(literal);
                    if (flag && !Double.isFinite(d0))
                        throw new NumberFormatException(literal);
                    if (!flag && !Double.isFinite(d0)) d0 = Math.copySign(Double.MAX_VALUE, d0);
                    if (!s.contains(".") && !flag && pCenterCorrect) {
                        d0 += 0.5D;
                        Int1088 integer = Int1088.parseDecimal(literal);
                        literal = integer.signum() < 0 ? (integer.equals(Int1088.fromLong(-1)) ? "-0" : integer.addSaturated(Int1088.ONE).toString()) + ".5" : integer + ".5";
                    }
                    return new WorldCoordinate(flag, d0, literal);
                } catch (NumberFormatException exception) {
                    pReader.setCursor(i);
                    throw ERROR_EXPECTED_DOUBLE.createWithContext(pReader);
                }
            }
        }
    }

    public static WorldCoordinate parseLegacyInt(StringReader pReader) throws CommandSyntaxException {
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
                if ((!flag && token.isEmpty()) || (flag && !isDecimal(literal)))
                    throw new NumberFormatException(literal);
                double d0 = flag ? Double.parseDouble(literal) : Int1088.parseDecimal(literal).toDoubleSaturated();
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

    private WorldCoordinate(boolean relative, CoordinateExpression.Value exact, String literal) {
        this.relative = relative;
        this.exactValue = exact;
        this.value = exact.doubleValue();
        this.literal = literal;
        if (relative) {
            // Offsets are not absolute positions: a valid cross-world displacement
            // can exceed the signed coordinate domain. Validate after resolution.
            this.absoluteBlock = null;
            this.fraction = 0.0D;
        } else {
            CoordinateExpression.Split split = exact.split();
            this.absoluteBlock = split.block();
            this.fraction = split.fraction();
        }
    }

    public static WorldCoordinate parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        return parseHorizontal(reader, centerCorrect, false);
    }

    public static WorldCoordinate parseInt(StringReader reader) throws CommandSyntaxException {
        return parseHorizontal(reader, false, true);
    }

    private static WorldCoordinate parseHorizontal(StringReader reader, boolean centerCorrect, boolean integer) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        boolean relative = reader.canRead() && isRelative(reader);
        int start = reader.getCursor();
        String token = readCoordinateToken(reader);
        try {
            CoordinateExpression.Value exact = CoordinateExpression.parse(relative && token.isEmpty() ? "0" : token);
            if (integer && !relative && !exact.isInteger()) throw new NumberFormatException("Expected integer coordinate");
            if (centerCorrect && !relative && exact.isInteger() && !token.contains(".")) {
                exact = exact.add(CoordinateExpression.parse("0.5"));
                token = exact.toDecimalString();
            }
            return new WorldCoordinate(relative, exact, token.isEmpty() ? "0" : token);
        } catch (NumberFormatException exception) {
            reader.setCursor(start);
            throw new SimpleCommandExceptionType(Component.literal(exception.getMessage())).createWithContext(reader);
        }
    }

    /** Resolve a horizontal offset before converting to the world's block/fraction storage. */
    public CoordinateExpression.Split resolve(Int1088 block, double fraction) {
        if (!this.relative) return new CoordinateExpression.Split(this.absoluteBlock, this.fraction);
        CoordinateExpression.Value offset = this.exactValue != null ? this.exactValue
                : CoordinateExpression.Value.fromSplit(Int1088.ZERO, this.value);
        try {
            return CoordinateExpression.Value.fromSplit(block, fraction).add(offset).split();
        } catch (NumberFormatException exception) {
            throw new CommandRuntimeException(Component.literal(exception.getMessage()));
        }
    }

    private static boolean isDecimal(String token) {
        return token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    }

    private static double complementedFraction(String digits) {
        char[] complement = new char[digits.length()];
        int carry = 1;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = 9 - (digits.charAt(i) - '0') + carry;
            complement[i] = (char)('0' + digit % 10);
            carry = digit / 10;
        }
        return Double.parseDouble("0." + new String(complement));
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
                return this.relative ? this.relativeValue().equals(worldcoordinate.relativeValue())
                        : this.absoluteBlock.equals(worldcoordinate.absoluteBlock) && Double.compare(this.fraction, worldcoordinate.fraction) == 0;
            }
        }
    }

    public int hashCode() {
        if (!this.relative)
            return 31 * this.absoluteBlock.hashCode() + Double.hashCode(this.fraction);
        return 31 + this.relativeValue().hashCode();
    }

    private CoordinateExpression.Value relativeValue() {
        return this.exactValue != null ? this.exactValue : CoordinateExpression.parse(Double.toString(this.value));
    }

    public boolean isRelative() {
        return this.relative;
    }

    @Override
    public String toString() {
        if (this.relative) return "~" + (this.relativeValue().numerator().signum() == 0 ? "" : this.literal);
        return this.fraction == 0.0D ? this.absoluteBlock.toDecimalString() : this.literal;
    }

    public static WorldCoordinate absoluteDecimal(String value, boolean centerCorrect) {
        try {
            StringReader reader = new StringReader(value);
            WorldCoordinate coordinate = parseDouble(reader, centerCorrect);
            if (reader.canRead() || coordinate.isRelative()) throw new IllegalArgumentException("Invalid absolute coordinate: " + value);
            return coordinate;
        } catch (CommandSyntaxException exception) {
            throw new IllegalArgumentException("Invalid coordinate: " + value, exception);
        }
    }
}
