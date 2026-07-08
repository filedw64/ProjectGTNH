package projecte.emc.arithmetics;

import java.math.BigInteger;

public class BigintArithmetic implements IValueArithmetic<BigInteger>{
    public static final BigInteger free = BigInteger.valueOf(-1);
	@Override
	public boolean isZero(BigInteger value) {
		return value.equals(BigInteger.ZERO);
	}

	@Override
	public BigInteger getZero() {
		return BigInteger.ZERO;
	}

	@Override
	public BigInteger add(BigInteger a, BigInteger b) {
		return a.add(b);
	}

	@Override
	public BigInteger mul(int a, BigInteger b) {
		if (isFree(b)) return getFree();
		return b.multiply(BigInteger.valueOf(a));
	}

	@Override
	public BigInteger div(BigInteger a, int b) {
		if (isFree(a)) return getFree();
		return a.divide(BigInteger.valueOf(b));
	}

	@Override
	public BigInteger getFree() {
		return free;
	}

	@Override
	public boolean isFree(BigInteger value) {
		return value.equals(free);
	}
}
