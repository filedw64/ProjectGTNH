package moze_intel.projecte.transport.api;

import net.minecraft.tileentity.TileEntity;

/**
 * 端口对象到「所属方块实体」的解析契约。
 *
 * 网络里的端点不再总是方块实体：一个方块实体可以对外暴露多个端口对象
 * （例如物质接口的六个面各自是一个 {@link IItemEndpoint} / {@link IFluidEndpoint}）。
 * 端口脱离方块实体后，网络就拿不到世界坐标，也就无法做兜底处理
 * （投递失败的物品退回原容器也失败时，必须定位到坐标掉落到世界里而不是删除）。
 *
 * 实现本接口的端口即可让网络把端口映射回它在世界里的那个方块实体。
 */
public interface IPortOwner
{
	/** 端口所属的方块实体；端口已脱离世界时应返回 null，调用方需自行退化处理。 */
	TileEntity getPortOwner();
}
