package net.minecraft.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;

/** Shared exact chat formatting and bounded debug-overlay formatting. */
public final class CoordinateFormat {
    private static final Int1088 LIMIT = Int1088.ONE.shiftLeftSaturated(127);
    private static final Int1088 NEGATIVE_LIMIT = LIMIT.negate();
    private static final ThreadLocal<DecimalFormat> SCIENTIFIC = ThreadLocal.withInitial(() -> {
        DecimalFormat format = new DecimalFormat("0.0000000E0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    });

    private CoordinateFormat() {}

    public static boolean abbreviated(Int1088 value) {
        return value.compareTo(LIMIT) > 0 || value.compareTo(NEGATIVE_LIMIT) < 0;
    }

    public static boolean abbreviated(Int1088 block, double fraction) {
        return abbreviated(block) || block.equals(LIMIT) && fraction > 0;
    }

    public static String overlayInteger(Int1088 value) {
        return abbreviated(value) ? scientific(new BigDecimal(value.toDecimalString())) : value.toDecimalString();
    }

    public static String overlayCoordinate(Int1088 block, double fraction) {
        return abbreviated(block, fraction) ? scientific(decimal(block, fraction)) : coordinate(block, fraction);
    }

    private static String scientific(BigDecimal value) {
        return SCIENTIFIC.get().format(value).replace('E', 'e');
    }

    private static BigDecimal decimal(Int1088 block, double fraction) {
        return new BigDecimal(block.toDecimalString()).add(BigDecimal.valueOf(fraction));
    }

    public static String coordinate(Int1088 block, double fraction) {
        return decimal(block, fraction).setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    public static String y(double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fraction(double value) {
        return (Double.doubleToRawLongBits(value) < 0 ? "" : "+")
                + BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP).toPlainString();
    }

    public static String subPosition(SectorVec3 position) {
        return "SubPos [X: " + fraction(position.subX()) + ", Y: " + fraction(position.subY())
                + ", Z: " + fraction(position.subZ()) + "]";
    }
}
