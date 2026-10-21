package moze_intel.projecte.transport.api;

import java.util.List;

/**
 * 多端口方块实体的契约：一个方块实体可以对外暴露多个网络端点，
 * 例如物质接口的六个面各自是一个独立端点（各有自己的输入 / 输出 / 关闭状态）。
 *
 * 网络重建时，若邻居方块实体实现本接口，就逐个取端口按类型 / 颜色筛入网络，
 * 不再把方块实体本身当成端点。
 *
 * 契约：返回的端口实例必须长期复用（同一个面每次都返回同一个对象）——
 * 网络用对象身份（endpoints.contains）去重，每次 new 会让同一个面被重复收进网络、重复投递。
 */
public interface IPortProvider
{
	/**
	 * 当前对外暴露的端口列表。只应包含可以真正干活的端口：
	 * 关闭的面、相邻方块不是有效容器 / 流体罐的面都不出现在这里。
	 * 调用方只当只读快照使用，不得保留引用。
	 */
	List<INetworkEndpoint> getNetworkPorts();
}
