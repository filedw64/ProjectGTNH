package moze_intel.projecte.transport.api;

import net.minecraftforge.fluids.FluidStack;

/**
 * 流体端点，量单位统一为 mB。
 * 契约：网络每次搬运前都会先在 simulate=true 下询问可接收量 / 可抽取量，
 * 实现方在 simulate=true 时禁止改动任何容器状态，也禁止改动传入的 FluidStack。
 */
public interface IFluidEndpoint extends INetworkEndpoint
{
	/**
	 * 向相邻容器插入流体。
	 * @param stack    待插入流体，调用方持有，实现方禁止修改
	 * @param simulate true 时只报告可接收量
	 * @return 实际（或模拟）接收量（mB），必须落在 0..stack.amount
	 */
	int insertFluid(FluidStack stack, boolean simulate);

	/**
	 * 从相邻容器抽取流体。
	 * @param maxAmount 本次最多抽取的 mB
	 * @param simulate  true 时只报告可抽取量，不改动容器
	 * @return 抽到的流体副本（amount = 本次可抽取量），无货时返回 null
	 */
	FluidStack drainFluid(int maxAmount, boolean simulate);

	/** 投递白名单：返回 false 的流体会被跳过。没有过滤表时必须返回 true（空白名单 = 全通过） */
	boolean matchesFilter(FluidStack stack);
}
