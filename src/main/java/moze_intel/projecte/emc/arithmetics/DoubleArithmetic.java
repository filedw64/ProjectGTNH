package moze_intel.projecte.emc.arithmetics;

public class DoubleArithmetic implements IValueArithmetic<Double>{
    @Override
    public boolean isZero(Double value) {
        return value == 0.0;
    }

    @Override
    public Double getZero() {
        return 0.0;
    }

    @Override
    public Double add(Double a, Double b) {
        return a + b;
    }

    @Override
    public Double mul(int a, Double b) {
        if (isFree(b)) return getFree();
        return a * b;
    }

    @Override
    public Double div(Double a, int b) {
        if (isFree(a)) return getFree();
        return a / b;
    }

    @Override
    public Double getFree() {
        return -Double.MAX_VALUE;
    }

    @Override
    public boolean isFree(Double value) {
        return value == -Double.MAX_VALUE;
    }
}
