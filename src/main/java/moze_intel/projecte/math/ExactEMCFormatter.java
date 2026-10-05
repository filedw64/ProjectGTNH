package moze_intel.projecte.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/** Two-decimal display only; authoritative EMC remains an exact rational. */
public final class ExactEMCFormatter {
    private ExactEMCFormatter() {}

    private static final BigInteger SCIENTIFIC_THRESHOLD = BigInteger.valueOf(100000);
    private static final MathContext SCIENTIFIC_PRECISION = new MathContext(3, RoundingMode.HALF_UP);

    /** Below 100000: two decimal places. Larger magnitudes: a two-decimal scientific mantissa. */
    public static String compact(ExactEMC value) {
        if (value.getNumerator().abs().compareTo(
                value.getDenominator().multiply(SCIENTIFIC_THRESHOLD)) >= 0) {
            BigDecimal rounded = new BigDecimal(value.getNumerator()).divide(
                new BigDecimal(value.getDenominator()), SCIENTIFIC_PRECISION);
            return scientific(rounded);
        }
        return value.toBigDecimal(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Integer display boundary, without a floating-point intermediate. */
    public static String compact(long value) {
        return compactDecimal(BigDecimal.valueOf(value));
    }

    /** Legacy display boundary; never feed the rounded text back into transactions. */
    public static String compact(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "--";
        return compactDecimal(BigDecimal.valueOf(value));
    }

    private static String compactDecimal(BigDecimal value) {
        if (value.abs().compareTo(new BigDecimal(SCIENTIFIC_THRESHOLD)) >= 0) {
            return scientific(value.round(SCIENTIFIC_PRECISION));
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Derive the exponent after rounding so a carry produces 1.00eN, not 10.00e(N-1). */
    private static String scientific(BigDecimal rounded) {
        int exponent = rounded.precision() - rounded.scale() - 1;
        return rounded.movePointLeft(exponent).setScale(2, RoundingMode.HALF_UP)
            .toPlainString() + "e" + exponent;
    }

    /** Tooltips deliberately expose no more precision than the main display. */
    public static List<String> tooltip(ExactEMC value) {
        return Collections.singletonList(compact(value));
    }

    /** Per-widget cache avoids repeating rational division while the balance is unchanged. */
    public static final class Cache {
        private ExactEMC previous;
        private String text;
        private long previousDoubleBits;
        private String doubleText;
        private long previousInteger;
        private String integerText;

        public String format(long value) {
            if (integerText == null || value != previousInteger) {
                integerText = compact(value);
                previousInteger = value;
            }
            return integerText;
        }

        public String format(double value) {
            long bits = Double.doubleToLongBits(value);
            if (doubleText == null || bits != previousDoubleBits) {
                doubleText = compact(value);
                previousDoubleBits = bits;
            }
            return doubleText;
        }

        public String format(ExactEMC value) {
            if (!value.equals(previous)) {
                text = compact(value);
                previous = value;
            }
            return text;
        }
    }
}
