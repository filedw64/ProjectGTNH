package projecte.emc.arithmetics;

public interface IValueArithmetic<T extends Comparable<T>> {
	boolean isZero(T value);
	T getZero();
	T add(T a, T b);
	T mul(int a, T b);
	T div(T a, int b);
	T getFree();
	boolean isFree(T value);
}
