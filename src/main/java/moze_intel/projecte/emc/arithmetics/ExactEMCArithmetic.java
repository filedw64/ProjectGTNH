package moze_intel.projecte.emc.arithmetics;

import moze_intel.projecte.math.ExactEMC;
import moze_intel.projecte.math.ExactEMCCodec;

/** Exact mapping arithmetic. FREE is a mapping-only identity sentinel, not a balance. */
public final class ExactEMCArithmetic implements IValueArithmetic<ExactEMC> {
    public static final ExactEMCArithmetic INSTANCE = new ExactEMCArithmetic();
    // Identity-tested so a numerical negative intermediate can never accidentally be free.
    public static final ExactEMC FREE = ExactEMC.of(-1);
    private ExactEMCArithmetic() {}
    public boolean isZero(ExactEMC value) { return value != null && !isFree(value) && value.isZero(); }
    public ExactEMC getZero() { return ExactEMC.ZERO; }
    public ExactEMC getFree() { return FREE; }
    public boolean isFree(ExactEMC value) { return value == FREE; }
    public ExactEMC add(ExactEMC a, ExactEMC b) {
        if (isZero(a) || isFree(a)) return b;
        if (isZero(b) || isFree(b)) return a;
        return ExactEMCCodec.validate(a.add(b));
    }
    public ExactEMC mul(int a, ExactEMC b) {
        if (a == 0 || isZero(b)) return ExactEMC.ZERO;
        if (isFree(b)) return FREE;
        return ExactEMCCodec.validate(b.multiply(a));
    }
    public ExactEMC div(ExactEMC a, int b) {
        if (b == 0) throw new ArithmeticException("Zero mapping output count");
        if (isZero(a) || isFree(a)) return a;
        return ExactEMCCodec.validate(a.divide(b));
    }
}
