package moze_intel.projecte.transport.api;

import moze_intel.projecte.transport.TransportType;

/**
 * 物质物流网络的端点契约。
 * 任何贴着管道的方块实体只要实现本接口，网络重建时就会被自动收进相邻颜色的网络。
 *
 * 契约：所有搬运/查询方法都必须在 simulate=true 时不改变任何容器状态。
 */
public interface INetworkEndpoint
{
	/** 端点类型，必须与所在管道的类型一致才会被收进网络 */
	TransportType getTransportType();

	/** 端点所在信道颜色 0..15；返回 -1 表示接受任意颜色的网络 */
	int getNetworkColor();

	EndpointMode getMode();

	/** 数值越大越优先（OUTPUT 越先被投递，INPUT 越先被抽取），同优先级按网络中收集顺序 */
	int getPriority();
}
