package moze_intel.projecte.gameObjs.tiles;

import moze_intel.projecte.api.tile.IEmcAcceptor;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterPipe;
import moze_intel.projecte.transport.MatterNetworkManager;
import moze_intel.projecte.transport.TransportType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AEGUTile extends TileEntity
{
	/** 绑定的无线注入目标：凝聚器 / 物质熔炉等直连受体，或 EMC 类型的物质管道（最多 8 个） */
	private final List<BoundTarget> boundTargets = new ArrayList<>(8);
	private int ticksExisted = 0;
	private boolean isActive = false;
	/** 效率缓存要等 AEGU 自身坐标就绪才能算，readFromNBT 时留脏，第一次 updateEntity 补算一次 */
	private boolean efficienciesDirty = true;

	private static final double AEGU_EMC_GEN = 3920.0;
	/** 8 格以内不衰减，与旧绑定规则的 8 格限制保持一致 */
	private static final double NO_DECAY_DISTANCE = 8.0;
	private static final double DECAY_EXPONENT = 0.8;

	/**
	 * 绑定目标 + 它相对 AEGU 的距离效率缓存。
	 * efficiency 只由两端坐标决定，属于静态值：只在绑定 / 自检 / 读档后重算，
	 * 不在每 tick 的注入循环里重复算 sqrt 与 pow。
	 */
	private static final class BoundTarget
	{
		private final ChunkCoordinates coord;
		private double efficiency = 1.0D;

		private BoundTarget(int x, int y, int z)
		{
			this.coord = new ChunkCoordinates(x, y, z);
		}
	}

	@Override
	public void updateEntity()
	{
		if (worldObj.isRemote) return;
		ticksExisted++;

		// 读档时 AEGU 自身坐标可能还没从 NBT 里出来，效率缓存在这里补算
		if (efficienciesDirty)
		{
			efficienciesDirty = false;
			refreshEfficiencies();
		}

		// 每 20 Tick (1秒) 进行一次自检
		if (ticksExisted % 20 == 0)
		{
			validateEnvironment();
		}

		// 仅当状态激活，且绑定了目标时，进行 EMC 注入
		if (isActive && !boundTargets.isEmpty())
		{
			// 第一趟只数本次真正能接收的目标：让被拆掉 / 被换掉的失效绑定不再参与分摊，
			// 否则失效目标那份会被摊到其他目标上，等于凭空多注 EMC
			int targets = 0;

			for (BoundTarget target : boundTargets)
			{
				if (isValidTarget(worldObj.getTileEntity(target.coord.posX, target.coord.posY, target.coord.posZ)))
					targets++;
			}

			if (targets <= 0) return; // 本次没有任何受体：不注入，等 20 tick 自检剔除失效绑定

			double emcPerTarget = AEGU_EMC_GEN / targets;

			// 第二趟按有效目标总数分摊后逐个注入。衰减只减少目标自己拿到的那一份，
			// 衰减掉的部分直接不产出，不摊给别的目标
			for (BoundTarget target : boundTargets)
			{
				TileEntity te = worldObj.getTileEntity(target.coord.posX, target.coord.posY, target.coord.posZ);

				if (te instanceof IEmcAcceptor)
				{
					// 凝聚器 / 物质熔炉等直连受体：绑定本身就限制在 8 格内，不衰减
					((IEmcAcceptor) te).acceptEMC(ForgeDirection.UNKNOWN, emcPerTarget);
				}
				else if (isEmcPipe(te))
				{
					double emc = emcPerTarget * target.efficiency;

					// 用 !(x > 0) 而不是 x <= 0：同时挡住 0、负数与 NaN
					if (!(emc > 0.0D)) continue;

					// EMC 管道：注入的是网络缓冲里真实拥有的 EMC，注入多少算多少，返回值不用
					MatterNetworkManager.INSTANCE.injectEmc(worldObj, target.coord.posX, target.coord.posY, target.coord.posZ, emc);
				}
			}
		}
	}

	/** 重算所有目标的距离效率缓存；只在绑定 / 自检 / 读档后调用，注入循环直接用结果 */
	private void refreshEfficiencies()
	{
		for (BoundTarget target : boundTargets)
		{
			target.efficiency = calcEfficiency(target.coord);
		}
	}

	/**
	 * 管道目标的距离衰减（凝聚器不衰减，注入时按受体类型分流）：
	 * d = AEGU 与目标管道的欧氏距离，不足 1 格按 1 格计算
	 * efficiency = d <= 8 ? 1.0 : Math.pow(8.0 / d, 0.8)
	 * 采样（单目标、3920 基准）：d=1 → 100% / 3920，d=8 → 100% / 3920，
	 * d=64 → 18.95% / 742.7，d=256 → 6.25% / 245.0，d=1000 → 2.10% / 82.4
	 */
	private double calcEfficiency(ChunkCoordinates coord)
	{
		double dx = xCoord - coord.posX;
		double dy = yCoord - coord.posY;
		double dz = zCoord - coord.posZ;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

		if (dist < 1.0D) dist = 1.0D; // 同一格 / 极端近距按 1 格算，避免效率爆表

		return dist <= NO_DECAY_DISTANCE ? 1.0D : Math.pow(NO_DECAY_DISTANCE / dist, DECAY_EXPONENT);
	}

	/**
	 * 8 格内直连的受体：凝聚器与物质熔炉。
	 * DMFurnaceTile 继承 RMFurnaceTile，按父类型判断即可同时覆盖 RM / DM 熔炉（两者都实现 IEmcAcceptor）。
	 */
	private static boolean isDirectAcceptor(TileEntity te)
	{
		return te instanceof CondenserTile || te instanceof RMFurnaceTile;
	}

	/** 绑定目标是否仍是有效受体：IEmcAcceptor（凝聚器 / 物质熔炉等）或 EMC 类型的物质管道 */
	private static boolean isValidTarget(TileEntity te)
	{
		return te instanceof IEmcAcceptor || isEmcPipe(te);
	}

	/** 只有 EMC 类型的物质管道接受无线注入；物品 / 流体管道不是受体 */
	private static boolean isEmcPipe(TileEntity te)
	{
		return te instanceof TileMatterPipe && ((TileMatterPipe) te).getTransportType() == TransportType.EMC;
	}

	public void validateEnvironment()
	{
		isActive = hasCollectorBelow();

		if (isActive)
		{
			// 校验绑定的目标是否合法或被破坏：凝聚器（含 CondenserMK2Tile）、物质熔炉（RM / DM）或 EMC 管道，其余一律剔除
			Iterator<BoundTarget> iterator = boundTargets.iterator();
			while (iterator.hasNext())
			{
				ChunkCoordinates coord = iterator.next().coord;
				TileEntity te = worldObj.getTileEntity(coord.posX, coord.posY, coord.posZ);
				if (!isDirectAcceptor(te) && !isEmcPipe(te)) // 凝聚器 / 物质熔炉 / EMC 管道之外的一律视为失效
				{
					iterator.remove();
					this.markDirty();
				}
			}
		}

		// 目标集合变过（可能刚被剔除），顺手把效率缓存同步到当前坐标
		refreshEfficiencies();

		// 刷新贴图状态
		int currentState = worldObj.getBlockMetadata(xCoord, yCoord, zCoord);
		int newState = 0;

		if (isActive)
		{
			if (boundTargets.isEmpty()) {
				newState = 1; // 有收集器但没绑定机器
			} else {
				newState = 2; // 有收集器且已绑定机器，正在工作
			}
		}

		// 如果状态发生变化，通知世界更新 Meta，标识 3 会同步给客户端并触发区块重绘
		if (currentState != newState)
		{
			worldObj.setBlockMetadataWithNotify(xCoord, yCoord, zCoord, newState, 3);
		}
	}

	public boolean hasCollectorBelow()
	{
		TileEntity tileDown = worldObj.getTileEntity(xCoord, yCoord - 1, zCoord);
		return tileDown instanceof CollectorMK1Tile;
	}

	public boolean bindCondenser(int cx, int cy, int cz)
	{
		if (boundTargets.size() >= 8) return false; // 上限 8 个

		for (BoundTarget bound : boundTargets)
		{
			if (bound.coord.posX == cx && bound.coord.posY == cy && bound.coord.posZ == cz)
				return false; // 防止重复绑定
		}

		boundTargets.add(new BoundTarget(cx, cy, cz));
		this.markDirty();
		validateEnvironment(); // 绑定成功后立刻校验一次：剔除失效项、重算效率缓存、并让待机贴图瞬间变成开机贴图
		return true;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		boundTargets.clear();
		NBTTagList list = nbt.getTagList("BoundCondensers", 10);
		for (int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound subNBT = list.getCompoundTagAt(i);
			// NBT 只存坐标（保持向后兼容），效率不落盘，读档后重算
			boundTargets.add(new BoundTarget(subNBT.getInteger("X"), subNBT.getInteger("Y"), subNBT.getInteger("Z")));
		}

		efficienciesDirty = true; // AEGU 自身坐标此刻未必就绪，交给第一次 updateEntity 补算
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		NBTTagList list = new NBTTagList();
		for (BoundTarget target : boundTargets)
		{
			NBTTagCompound subNBT = new NBTTagCompound();
			subNBT.setInteger("X", target.coord.posX);
			subNBT.setInteger("Y", target.coord.posY);
			subNBT.setInteger("Z", target.coord.posZ);
			list.appendTag(subNBT);
		}
		nbt.setTag("BoundCondensers", list);
	}
}
