package moze_intel.projecte.emc.arithmetics;

public class DoubleArithmetic implements IValueArithmetic<Double> {
	// 提供单例实例
	public static final DoubleArithmetic INSTANCE = new DoubleArithmetic();

	// 缓存常用的 Double 对象
	private static final Double ZERO = 0.0;
	private static final Double FREE = -Double.MAX_VALUE;

	@Override
	public boolean isZero(Double value) {
		return value != null && value == 0.0;
	}

	@Override
	public Double getZero() {
		return ZERO; // 返回缓存的常量
	}

	@Override
	public Double add(Double a, Double b) {
		if (isZero(a) || isFree(a)) return b;
		if (isZero(b) || isFree(b)) return a;
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
		if (isZero(a) || isFree(a)) return a;
		if (b == 0) return ZERO; // 防止除以 0 导致返回 Infinity
		return a / b;
	}

	@Override
	public Double getFree() {
		return FREE; // 返回缓存的常量
	}

	@Override
	public boolean isFree(Double value) {
		return value != null && value == -Double.MAX_VALUE;
	}
}
