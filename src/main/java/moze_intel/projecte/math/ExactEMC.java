package moze_intel.projecte.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Immutable, exact rational value for EMC arithmetic.
 *
 * <p>Every value is reduced to coprime terms with a positive denominator. Negative
 * values are supported for intermediate calculations; player balance and transaction
 * entry points must separately reject negative balances and invalid amounts.
 * No arithmetic operation implicitly rounds or passes through floating point.
 */
public final class ExactEMC implements Comparable<ExactEMC> {
    public static final ExactEMC ZERO = new ExactEMC(BigInteger.ZERO, BigInteger.ONE);
    public static final ExactEMC ONE = new ExactEMC(BigInteger.ONE, BigInteger.ONE);

    /** Bounds decimal input expansion, not the range of rational arithmetic. */
    public static final int MAX_DECIMAL_SCALE = 10000;
    public static final int MAX_DECIMAL_INPUT_LENGTH = 10000;

    private final BigInteger numerator;
    private final BigInteger denominator;

    /** Only used internally with already normalized terms. */
    private ExactEMC(BigInteger numerator, BigInteger denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static ExactEMC of(long value) {
        return of(BigInteger.valueOf(value));
    }

    public static ExactEMC of(BigInteger value) {
        if (value == null) throw new NullPointerException("value");
        if (value.signum() == 0) return ZERO;
        if (value.equals(BigInteger.ONE)) return ONE;
        return new ExactEMC(value, BigInteger.ONE);
    }

    public static ExactEMC of(BigInteger numerator, BigInteger denominator) {
        if (numerator == null || denominator == null) throw new NullPointerException("fraction terms");
        if (denominator.signum() == 0) throw new ArithmeticException("Division by zero");
        if (numerator.signum() == 0) return ZERO;
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd);
        denominator = denominator.divide(gcd);
        if (denominator.equals(BigInteger.ONE)) return of(numerator);
        return new ExactEMC(numerator, denominator);
    }

    /** Parses a decimal or scientific literal directly, without a double intermediate. */
    public static ExactEMC parse(String text) {
        if (text == null) throw new NumberFormatException("Missing EMC value");
        if (text.length() > MAX_DECIMAL_INPUT_LENGTH) {
            throw new NumberFormatException("EMC literal is too long");
        }
        return of(new BigDecimal(text.trim()));
    }

    public static ExactEMC of(BigDecimal value) {
        if (value == null) throw new NullPointerException("value");
        if (value.signum() == 0) return ZERO;
        value = value.stripTrailingZeros();
        int scale = value.scale();
        if (scale > MAX_DECIMAL_SCALE || scale < -MAX_DECIMAL_SCALE) {
            throw new ArithmeticException("Decimal scale exceeds the EMC input limit");
        }
        BigInteger unscaled = value.unscaledValue();
        if (scale < 0) return of(unscaled.multiply(BigInteger.TEN.pow(-scale)));
        if (scale == 0) return of(unscaled);
        return of(unscaled, BigInteger.TEN.pow(scale));
    }

    /**
     * Compatibility/migration only: preserves the actual finite binary double value.
     * This cannot recover precision already lost by the caller or an old save.
     * New constants and user input must use integer or decimal factories instead.
     */
    public static ExactEMC fromLegacyDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("EMC must be finite");
        }
        return of(new BigDecimal(value));
    }

    /** Lossy compatibility view only. Never use this result for player transactions. */
    public double toLegacyDouble() {
        double result = new BigDecimal(numerator).divide(new BigDecimal(denominator),
            java.math.MathContext.DECIMAL64).doubleValue();
        if (Double.isInfinite(result)) return signum() < 0 ? -Double.MAX_VALUE : Double.MAX_VALUE;
        return result;
    }

    public boolean isExactlyRepresentableAsDouble() {
        return fromLegacyDouble(toLegacyDouble()).equals(this);
    }

    public BigInteger getNumerator() {
        return numerator;
    }

    public BigInteger getDenominator() {
        return denominator;
    }

    public int signum() {
        return numerator.signum();
    }

    public boolean isZero() {
        return signum() == 0;
    }

    public boolean isInteger() {
        return denominator.equals(BigInteger.ONE);
    }

    public ExactEMC add(ExactEMC other) {
        if (other == null) throw new NullPointerException("other");
        if (other.isZero()) return this;
        if (isZero()) return other;
        // Use the least common denominator to avoid needlessly large intermediates.
        BigInteger gcd = denominator.gcd(other.denominator);
        BigInteger leftFactor = other.denominator.divide(gcd);
        BigInteger rightFactor = denominator.divide(gcd);
        return of(
            numerator.multiply(leftFactor).add(other.numerator.multiply(rightFactor)),
            denominator.multiply(leftFactor));
    }

    public ExactEMC add(long other) {
        return add(of(other));
    }

    public ExactEMC subtract(ExactEMC other) {
        if (other == null) throw new NullPointerException("other");
        return add(other.negate());
    }

    public ExactEMC subtract(long other) {
        return subtract(of(other));
    }

    public ExactEMC multiply(ExactEMC other) {
        if (other == null) throw new NullPointerException("other");
        if (isZero() || other.isZero()) return ZERO;
        // Cancel across the product before allocating the multiplied terms.
        BigInteger leftGcd = numerator.gcd(other.denominator);
        BigInteger rightGcd = other.numerator.gcd(denominator);
        return of(
            numerator.divide(leftGcd).multiply(other.numerator.divide(rightGcd)),
            denominator.divide(rightGcd).multiply(other.denominator.divide(leftGcd)));
    }

    public ExactEMC multiply(long other) {
        return multiply(of(other));
    }

    public ExactEMC multiply(BigInteger other) {
        return multiply(of(other));
    }

    public ExactEMC divide(ExactEMC other) {
        if (other == null) throw new NullPointerException("other");
        return multiply(other.reciprocal());
    }

    public ExactEMC divide(long other) {
        return divide(of(other));
    }

    public ExactEMC divide(BigInteger other) {
        return divide(of(other));
    }

    public ExactEMC reciprocal() {
        if (isZero()) throw new ArithmeticException("Division by zero");
        return of(denominator, numerator);
    }

    public ExactEMC negate() {
        return isZero() ? ZERO : new ExactEMC(numerator.negate(), denominator);
    }

    public ExactEMC abs() {
        return signum() < 0 ? negate() : this;
    }

    /** Mathematical floor, including negative intermediate values. */
    public BigInteger floor() {
        BigInteger[] parts = numerator.divideAndRemainder(denominator);
        return signum() < 0 && parts[1].signum() != 0 ? parts[0].subtract(BigInteger.ONE) : parts[0];
    }

    /** Maximum whole units purchasable; the caller must separately apply stack limits. */
    public BigInteger affordableUnits(ExactEMC unitCost) {
        if (unitCost == null) throw new NullPointerException("unitCost");
        if (signum() < 0 || unitCost.signum() <= 0) {
            throw new IllegalArgumentException("Balance must be nonnegative and unit cost positive");
        }
        return divide(unitCost).floor();
    }

    public BigInteger toBigIntegerExact() {
        if (!isInteger()) throw new ArithmeticException("EMC value is not an integer");
        return numerator;
    }

    public long longValueExact() {
        BigInteger value = toBigIntegerExact();
        if (value.bitLength() > 63) throw new ArithmeticException("EMC value exceeds long range");
        return value.longValue();
    }

    public int intValueExact() {
        BigInteger value = toBigIntegerExact();
        if (value.bitLength() > 31) throw new ArithmeticException("EMC value exceeds int range");
        return value.intValue();
    }

    /** Exact for terminating decimals; throws instead of rounding repeating decimals. */
    public BigDecimal toBigDecimalExact() {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator));
    }

    /** Explicitly rounded representation for display only, never for balance updates. */
    public BigDecimal toBigDecimal(int scale, RoundingMode roundingMode) {
        if (roundingMode == null) throw new NullPointerException("roundingMode");
        if (scale > MAX_DECIMAL_SCALE || scale < -MAX_DECIMAL_SCALE) {
            throw new IllegalArgumentException("Display scale exceeds limit");
        }
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), scale, roundingMode);
    }

    @Override
    public int compareTo(ExactEMC other) {
        if (other == null) throw new NullPointerException("other");
        if (this == other) return 0;
        int signComparison = Integer.compare(signum(), other.signum());
        if (signComparison != 0) return signComparison;
        if (denominator.equals(other.denominator)) return numerator.compareTo(other.numerator);
        BigInteger gcd = denominator.gcd(other.denominator);
        return numerator.multiply(other.denominator.divide(gcd))
            .compareTo(other.numerator.multiply(denominator.divide(gcd)));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof ExactEMC)) return false;
        ExactEMC other = (ExactEMC) object;
        return numerator.equals(other.numerator) && denominator.equals(other.denominator);
    }

    @Override
    public int hashCode() {
        return 31 * numerator.hashCode() + denominator.hashCode();
    }

    /** Lossless diagnostic representation; not the compact GUI formatter. */
    @Override
    public String toString() {
        return isInteger() ? numerator.toString() : numerator.toString() + "/" + denominator.toString();
    }
}
