package net.minecraft.commands.arguments.coordinates;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;
import net.minecraft.core.Int1088;

/** Command-only exact arithmetic. Never use the fixed-width world type for intermediates. */
public final class CoordinateExpression {
    private static final int MAX_LENGTH = 4096;
    private static final int MAX_BITS = 16384;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_OPERATIONS = 1024;
    private static final Pattern SCIENTIFIC = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)[eE][+-]?[0-9]+");
    private static final BigInteger MIN = BigInteger.ONE.shiftLeft(1087).negate();
    private static final BigInteger LIMIT = MIN.negate();
    public static final Value ZERO = new Value(BigInteger.ZERO, BigInteger.ONE);
    public static final Value ONE = new Value(BigInteger.ONE, BigInteger.ONE);

    private CoordinateExpression() {}

    public static Value parse(String text) {
        if (text.isEmpty() || text.length() > MAX_LENGTH) throw invalid("Coordinate expression is empty or too long");
        for (int i = 0; i < text.length(); i++)
            if (Character.isWhitespace(text.charAt(i))) throw invalid("Whitespace inside coordinate");
        if (text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
            if (!SCIENTIFIC.matcher(text).matches()) throw invalid("Scientific notation cannot be combined with arithmetic");
            try {
                return decimal(new BigDecimal(text));
            } catch (ArithmeticException exception) {
                throw invalid("Scientific exponent is too large");
            }
        }
        Parser parser = new Parser(text);
        Value result = parser.expression();
        if (parser.cursor != text.length()) throw invalid("Unexpected character at " + parser.cursor);
        return result;
    }

    private static Value decimal(BigDecimal decimal) {
        if (decimal.signum() == 0) return ZERO;
        int scale = decimal.scale();
        if (Math.abs((long) scale) * 4 > MAX_BITS) throw invalid("Coordinate expression is too complex");
        return scale >= 0 ? new Value(decimal.unscaledValue(), BigInteger.TEN.pow(scale))
                : new Value(decimal.unscaledValue().multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
    }

    private static NumberFormatException invalid(String message) {
        return new NumberFormatException(message);
    }

    /** Reduced rational; rounding is deferred until the final block/fraction conversion. */
    public record Value(BigInteger numerator, BigInteger denominator) {
        public Value {
            if (denominator.signum() == 0) throw invalid("Division by zero");
            if (numerator.abs().bitLength() > MAX_BITS || denominator.abs().bitLength() > MAX_BITS)
                throw invalid("Coordinate expression is too complex");
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }

        public Value add(Value other) {
            return new Value(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)), denominator.multiply(other.denominator));
        }

        public Value negate() { return new Value(numerator.negate(), denominator); }

        private Value multiply(Value other) {
            return new Value(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
        }

        private Value divide(Value other) {
            return new Value(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
        }

        public BigInteger floor() {
            BigInteger[] parts = numerator.divideAndRemainder(denominator);
            return parts[1].signum() < 0 ? parts[0].subtract(BigInteger.ONE) : parts[0];
        }

        public boolean isInteger() { return denominator.equals(BigInteger.ONE); }

        /** Exact decimal formatting for terminating values, e.g. centered integral inputs. */
        public String toDecimalString() {
            return new BigDecimal(numerator).divide(new BigDecimal(denominator)).toPlainString();
        }

        public void checkBounds() {
            if (numerator.compareTo(MIN.multiply(denominator)) < 0 || numerator.compareTo(LIMIT.multiply(denominator)) >= 0)
                throw invalid("Coordinate outside horizontal world bounds");
        }

        public Split split() {
            checkBounds();
            BigInteger integral = floor();
            BigInteger remainder = numerator.subtract(integral.multiply(denominator));
            double fraction = ratioToDouble(remainder, denominator);
            if (fraction >= 1.0D) {
                if (numerator.signum() < 0 || integral.equals(LIMIT.subtract(BigInteger.ONE))) fraction = Math.nextDown(1.0D);
                else {
                    integral = integral.add(BigInteger.ONE);
                    fraction = 0.0D;
                }
            }
            return new Split(Int1088.parseDecimal(integral.toString()), fraction);
        }

        /** Derived selector box endpoints retain the existing world-edge saturation policy. */
        public Split splitSaturated() {
            if (numerator.compareTo(MIN.multiply(denominator)) < 0) return new Split(Int1088.MIN, 0.0D);
            if (numerator.compareTo(LIMIT.multiply(denominator)) >= 0) return new Split(Int1088.MAX, Math.nextDown(1.0D));
            return split();
        }

        public double doubleValue() {
            double value = ratioToDouble(numerator, denominator);
            return Double.isFinite(value) ? value : Math.copySign(Double.MAX_VALUE, value);
        }

        public static Value fromSplit(Int1088 block, double fraction) {
            return new Value(new BigInteger(block.toDecimalString()), BigInteger.ONE).add(decimal(new BigDecimal(fraction)));
        }

        /** Correctly rounded binary conversion, including halfway ties and subnormals. */
        private static double ratioToDouble(BigInteger numerator, BigInteger denominator) {
            if (numerator.signum() == 0) return 0.0D;
            boolean negative = numerator.signum() < 0;
            BigInteger magnitude = numerator.abs();
            int exponent = magnitude.bitLength() - denominator.bitLength();
            int comparison = exponent >= 0 ? magnitude.compareTo(denominator.shiftLeft(exponent))
                    : magnitude.shiftLeft(-exponent).compareTo(denominator);
            if (comparison < 0) exponent--;
            if (exponent > 1023) return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            int unit = Math.max(exponent - 52, -1074);
            BigInteger scaledNumerator = unit < 0 ? magnitude.shiftLeft(-unit) : magnitude;
            BigInteger scaledDenominator = unit > 0 ? denominator.shiftLeft(unit) : denominator;
            BigInteger[] parts = scaledNumerator.divideAndRemainder(scaledDenominator);
            int halfway = parts[1].shiftLeft(1).compareTo(scaledDenominator);
            if (halfway > 0 || halfway == 0 && parts[0].testBit(0)) parts[0] = parts[0].add(BigInteger.ONE);
            double result = Math.scalb(parts[0].doubleValue(), unit);
            return negative ? -result : result;
        }

        private Value power(Value exponent) {
            if (!exponent.isInteger()) throw invalid("Exponent must be an integer");
            BigInteger e = exponent.numerator;
            if (e.signum() == 0) return ONE;
            if (numerator.signum() == 0) {
                if (e.signum() < 0) throw invalid("Division by zero");
                return ZERO;
            }
            if (numerator.abs().equals(BigInteger.ONE) && denominator.equals(BigInteger.ONE))
                return numerator.signum() < 0 && e.testBit(0) ? ONE.negate() : ONE;
            if (e.abs().compareTo(BigInteger.valueOf(MAX_BITS)) > 0) throw invalid("Coordinate expression is too complex");
            int count = e.abs().intValue();
            if ((long) Math.max(numerator.abs().bitLength(), denominator.bitLength()) * count > MAX_BITS)
                throw invalid("Coordinate expression is too complex");
            BigInteger n = numerator.pow(count), d = denominator.pow(count);
            return e.signum() < 0 ? new Value(d, n) : new Value(n, d);
        }

        private Value tetrate(Value height) {
            if (!height.isInteger() || height.numerator.signum() < 0) throw invalid("Tetration height must be a nonnegative integer");
            if (height.numerator.signum() == 0) return ONE;
            if (equals(ONE)) return ONE;
            if (equals(ZERO)) return height.numerator.testBit(0) ? ZERO : ONE;
            if (height.numerator.compareTo(BigInteger.valueOf(MAX_DEPTH)) > 0) throw invalid("Coordinate expression is too complex");
            Value result = ONE;
            for (int i = 0; i < height.numerator.intValue(); i++) result = power(result);
            return result;
        }
    }

    public record Split(Int1088 block, double fraction) {}

    private static final class Parser {
        private final String text;
        private int cursor;
        private int depth;
        private int operations;

        Parser(String text) { this.text = text; }

        private boolean take(String token) {
            if (!text.startsWith(token, cursor)) return false;
            cursor += token.length();
            if (++operations > MAX_OPERATIONS) throw invalid("Coordinate expression is too complex");
            return true;
        }

        private void enter() {
            if (++depth > MAX_DEPTH) throw invalid("Coordinate expression is nested too deeply");
        }

        private Value expression() {
            Value value = product();
            while (true) {
                if (take("+")) value = value.add(product());
                else if (take("-")) value = value.add(product().negate());
                else return value;
            }
        }

        private Value product() {
            Value value = unary();
            while (true) {
                if (take("*")) value = value.multiply(unary());
                else if (take("//")) value = new Value(value.divide(unary()).floor(), BigInteger.ONE);
                else if (take("/")) value = value.divide(unary());
                else return value;
            }
        }

        private Value unary() {
            enter();
            try {
                if (take("+")) return unary();
                if (take("-")) return unary().negate();
                return power();
            } finally { depth--; }
        }

        private Value power() {
            Value value = tower();
            return take("^") ? value.power(unary()) : value;
        }

        private Value tower() {
            enter();
            try {
                Value value = atom();
                return take("^^") ? value.tetrate(towerHeight()) : value;
            } finally { depth--; }
        }

        private Value towerHeight() {
            enter();
            try {
                if (take("+")) return towerHeight();
                if (take("-")) return towerHeight().negate();
                return tower();
            } finally { depth--; }
        }

        private Value atom() {
            if (take("(")) {
                Value value = expression();
                if (!take(")")) throw invalid("Expected closing parenthesis");
                return value;
            }
            int start = cursor;
            while (cursor < text.length() && digit(text.charAt(cursor))) cursor++;
            if (cursor < text.length() && text.charAt(cursor) == '.') {
                cursor++;
                while (cursor < text.length() && digit(text.charAt(cursor))) cursor++;
            }
            if (start == cursor || text.substring(start, cursor).equals(".")) throw invalid("Expected number at " + start);
            return decimal(new BigDecimal(text.substring(start, cursor)));
        }

        private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    }
}
