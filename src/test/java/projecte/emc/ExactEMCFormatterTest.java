package projecte.emc;

import static org.junit.Assert.*;
import java.math.BigInteger;
import java.util.Collections;
import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.math.ExactEMCFormatter;
import org.junit.Test;

public class ExactEMCFormatterTest {
    @Test
    public void alwaysUsesTwoDecimalPlaces() {
        assertEquals("0.00", ExactEMCFormatter.compact(ExactEMC.ZERO));
        assertEquals("12.00", ExactEMCFormatter.compact(ExactEMC.of(12)));
        assertEquals("0.33", ExactEMCFormatter.compact(ExactEMC.ONE.divide(3)));
        assertEquals("0.13", ExactEMCFormatter.compact(ExactEMC.ONE.divide(8)));
        assertEquals("123.46", ExactEMCFormatter.compact(ExactEMC.parse("123.456")));
        assertEquals("0.00", ExactEMCFormatter.compact(ExactEMC.parse("0.00001")));
        assertEquals("-0.13", ExactEMCFormatter.compact(ExactEMC.ONE.divide(8).negate()));
    }

    @Test
    public void hugeValuesUseScientificNotation() {
        BigInteger huge = BigInteger.TEN.pow(100);
        assertEquals("1.00e100", ExactEMCFormatter.compact(ExactEMC.of(huge)));
        assertEquals("-1.00e100", ExactEMCFormatter.compact(ExactEMC.of(huge.negate())));
    }

    @Test
    public void scientificNotationUsesTwoDecimalMantissa() {
        assertEquals("99999.00", ExactEMCFormatter.compact(ExactEMC.of(99999)));
        assertEquals("1.00e5", ExactEMCFormatter.compact(ExactEMC.of(100000)));
        assertEquals("1.23e5", ExactEMCFormatter.compact(ExactEMC.of(123456)));
        assertEquals("1.24e5", ExactEMCFormatter.compact(ExactEMC.of(123500)));
        assertEquals("1.00e6", ExactEMCFormatter.compact(ExactEMC.of(999999)));
        assertEquals("-1.23e5", ExactEMCFormatter.compact(ExactEMC.of(-123456)));
        assertEquals("1.23e5", ExactEMCFormatter.compact(ExactEMC.of(370368).divide(3)));
        assertEquals(Collections.singletonList("1.23e5"),
            ExactEMCFormatter.tooltip(ExactEMC.of(123456)));
    }

    @Test
    public void tooltipDoesNotRevealHiddenPrecision() {
        assertEquals(Collections.singletonList("0.33"),
            ExactEMCFormatter.tooltip(ExactEMC.ONE.divide(3)));
    }

    @Test
    public void displayDoesNotChangeArithmetic() {
        ExactEMC third = ExactEMC.ONE.divide(3);
        ExactEMCFormatter.compact(third);
        assertEquals(ExactEMC.ONE, third.multiply(3));
        assertEquals("1/3", third.toString());
    }

    @Test
    public void cacheReusesTextAndInvalidatesOnBalanceChange() {
        ExactEMCFormatter.Cache cache = new ExactEMCFormatter.Cache();
        String first = cache.format(ExactEMC.ONE.divide(3));
        assertSame(first, cache.format(ExactEMC.ONE.divide(3)));
        assertEquals("0.67", cache.format(ExactEMC.of(2).divide(3)));
    }

    @Test
    public void legacyDisplayUsesSameRule() {
        assertEquals("12.00", ExactEMCFormatter.compact(12.0));
        assertEquals("1.23e5", ExactEMCFormatter.compact(123456.0));
        assertEquals("0.13", ExactEMCFormatter.compact(0.125));
        long[] values = {0, 12, 99999, 100000, 123456, 123500, 999999, -123456};
        for (long value : values) {
            String expected = ExactEMCFormatter.compact(ExactEMC.of(value));
            assertEquals(expected, ExactEMCFormatter.compact(value));
            assertEquals(expected, ExactEMCFormatter.compact((double) value));
        }
        assertEquals("--", ExactEMCFormatter.compact(Double.NaN));
        assertEquals("--", ExactEMCFormatter.compact(Double.POSITIVE_INFINITY));
    }

    @Test
    public void legacyCachesReuseTextAndFollowScientificRule() {
        ExactEMCFormatter.Cache cache = new ExactEMCFormatter.Cache();
        String integerText = cache.format(123456L);
        assertEquals("1.23e5", integerText);
        assertSame(integerText, cache.format(123456L));
        String doubleText = cache.format(123456.0);
        assertEquals("1.23e5", doubleText);
        assertSame(doubleText, cache.format(123456.0));
        assertSame(integerText, cache.format(123456L));
        assertEquals("12.00", cache.format(12L));
        assertEquals("0.13", cache.format(0.125));
    }
}
