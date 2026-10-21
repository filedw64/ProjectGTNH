package moze_intel.projecte.transport.api;

import net.minecraft.item.ItemStack;

/**
 * 物品端点。
 * 契约：网络每次搬运前都会先在 simulate=true 下询问可接收空间 / 可抽取量，
 * 实现方在 simulate=true 时禁止改动任何容器状态，也禁止改动传入的 ItemStack。
 */
public interface IItemEndpoint extends INetworkEndpoint
{
	/**
	 * 向相邻容器插入物品。
	 * @param stack    待插入物品，调用方持有，实现方禁止修改
	 * @param simulate true 时只报告可接收数量
	 * @return 实际（或模拟）接收数量，必须落在 0..stack.stackSize
	 */
	int insertItems(ItemStack stack, boolean simulate);

	/**
	 * 从相邻容器抽取物品。
	 * @param maxAmount 本次最多抽取的件数
	 * @param simulate  true 时只报告可抽取物，不改动容器
	 * @return 抽到的物品副本（stackSize = 本次可抽取件数），无货时返回 null
	 */
	ItemStack extractItems(int maxAmount, boolean simulate);

	/** 投递白名单：返回 false 的物品不会被投递。没有过滤表时必须返回 true（空白名单 = 全通过） */
	boolean matchesFilter(ItemStack stack);
}
