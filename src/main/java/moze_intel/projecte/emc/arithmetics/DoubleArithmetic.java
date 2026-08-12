package moze_intel.projecte.emc.arithmetics;

public class DoubleArithmetic implements IValueArithmetic<Double> {

	// 提供单例实例
	public static final DoubleArithmetic INSTANCE = new DoubleArithmetic();

	// 缓存常用的 Double 对象
	private static final Double ZERO = 0.0;
	private static final Double FREE = -Double.MAX_VALUE;

	@Override
	public boolean isZero(Double value) {
		return value != null && value.doubleValue() == 0.0;
	}

	@Override
	public Double getZero() {
		// 返回缓存的常量
		return ZERO;
	}

	@Override
	public Double add(Double a, Double b) {
		if (isFree(a)) return b;
		if (isFree(b)) return a;
		if (isZero(a)) return b;
		if (isZero(b)) return a;
		return a + b;
	}

	@Override
	public Double mul(int a, Double b) {
		if (a == 0 || isZero(b)) return ZERO;
		if (isFree(b)) return FREE;
		return a * b;
	}

	@Override
	public Double div(Double a, int b) {
		if (isZero(a)) return ZERO;
		if (isFree(a)) return FREE;
		// 防止除以 0 导致返回 Infinity
		if (b == 0) return ZERO;
		return a / b;
	}

	@Override
	public Double getFree() {
		// 返回缓存的常量
		return FREE;
	}

	@Override
	public boolean isFree(Double value) {
		return value != null && value.doubleValue() == -Double.MAX_VALUE;
	}
}
