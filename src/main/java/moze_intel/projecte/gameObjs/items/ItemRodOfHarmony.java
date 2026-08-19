package moze_intel.projecte.gameObjs.items;

import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class ItemRodOfHarmony extends ItemPE
{
	public ItemRodOfHarmony()
	{
		this.setUnlocalizedName("rod_of_harmony");
		this.setTextureName("projecte:rod_of_harmony");
		this.setMaxStackSize(1);
		this.setCreativeTab(ObjHandler.cTab);
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		player.swingItem();

		if (world.isRemote) return stack; // 传送逻辑仅在服务端处理，防止拉回和数据不同步

		// 获取玩家标准触及范围内的目标
		MovingObjectPosition localMop = this.getMovingObjectPositionFromPlayer(world, player, false);

		Vec3 head = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
		Vec3 look = player.getLookVec();

		// 遁地穿墙
		if (localMop != null && localMop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
		{
			boolean insideWall = false;
			int solidCount = 0;
			int lastX = Integer.MAX_VALUE, lastY = Integer.MAX_VALUE, lastZ = Integer.MAX_VALUE;

			// 顺着视线以 0.2 为步长最多向前扫描 64 格
			for (double d = 0; d <= 64.0; d += 0.2)
			{
				double tx = head.xCoord + look.xCoord * d;
				double ty = head.yCoord + look.yCoord * d;
				double tz = head.zCoord + look.zCoord * d;

				int ix = MathHelper.floor_double(tx);
				int iy = MathHelper.floor_double(ty);
				int iz = MathHelper.floor_double(tz);

				Block block = world.getBlock(ix, iy, iz);
				boolean isSolid = block.getMaterial().blocksMovement();

				// 统计沿途穿过了多少个不同的固体方块
				if (ix != lastX || iy != lastY || iz != lastZ)
				{
					if (isSolid) solidCount++;
					lastX = ix; lastY = iy; lastZ = iz;
				}

				if (isSolid)
				{
					insideWall = true;
				}
				else if (insideWall)
				{
					// 脱离了墙壁，检查上方是否也是空气，确保有 1x1x2 的容纳空间
					Block blockAbove = world.getBlock(ix, iy + 1, iz);
					if (!blockAbove.getMaterial().blocksMovement())
					{
						// 下方是否是直通虚空
						if (isAboveVoid(world, ix, iy, iz))
						{
							player.addChatMessage(new ChatComponentTranslation("pe.harmony.voiddanger"));
							return stack;
						}

						// 计费并传送
						double cost = solidCount * 576.0;
						if (!consumePlayerNetworkEMC(player, cost)) return stack;

						teleportPlayer(player, tx, iy, tz);
						return stack;
					}
				}
			}
			player.addChatMessage(new ChatComponentTranslation("pe.harmony.toothick"));
			return stack;
		}

		// 指哪飞哪
		int viewDistance = MinecraftServer.getServer().getConfigurationManager().getViewDistance();
		double maxDist = viewDistance * 16.0;

		Vec3 target = Vec3.createVectorHelper(
			head.xCoord + look.xCoord * maxDist,
			head.yCoord + look.yCoord * maxDist,
			head.zCoord + look.zCoord * maxDist
		);

		MovingObjectPosition mopFar = world.rayTraceBlocks(head, target, false);
		Vec3 finalPos;

		if (mopFar != null && mopFar.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
		{
			// 视距内击中了方块，根据击中面偏移，防止卡进方块里
			ForgeDirection dir = ForgeDirection.getOrientation(mopFar.sideHit);
			finalPos = Vec3.createVectorHelper(
				mopFar.blockX + 0.5 + dir.offsetX,
				mopFar.blockY + dir.offsetY,
				mopFar.blockZ + 0.5 + dir.offsetZ
			);
			if (mopFar.sideHit == 0) finalPos.yCoord -= 2.0; // 如果打中方块底面，往下偏移防止头卡住
		}
		else
		{
			// 平飞，未击中任何东西，直接传送到区块加载边缘
			finalPos = target;
		}

		// 边界检查：朝上传送，插值到 Y=255
		if (finalPos.yCoord > 255.0)
		{
			double diffY = 255.0 - head.yCoord;
			double ratio = diffY / (finalPos.yCoord - head.yCoord);
			finalPos.xCoord = head.xCoord + (finalPos.xCoord - head.xCoord) * ratio;
			finalPos.zCoord = head.zCoord + (finalPos.zCoord - head.zCoord) * ratio;
			finalPos.yCoord = 255.0;
		}
		// 边界检查：朝下传送越过 Y=0，直接拦截
		else if (finalPos.yCoord < 0.0)
		{
			player.addChatMessage(new ChatComponentTranslation("pe.harmony.voidcancel"));
			return stack;
		}

		// 计费并传送 (直线飞行每格 8 EMC)
		double dist = head.distanceTo(finalPos);
		double cost = dist * 8.0;

		if (!consumePlayerNetworkEMC(player, cost)) return stack;

		teleportPlayer(player, finalPos.xCoord, finalPos.yCoord, finalPos.zCoord);
		return stack;
	}

	/**
	 * 检查目标坐标正下方是否有任何固体方块（防止直接传送到虚空上方）
	 */
	private boolean isAboveVoid(World world, int x, int y, int z)
	{
		for (int i = y - 1; i >= 0; i--)
		{
			if (world.getBlock(x, i, z).getMaterial().blocksMovement())
			{
				return false; // 碰到了固体方块，安全
			}
		}
		return true; // 一路向下全是空气，下面是虚空
	}

	private void teleportPlayer(EntityPlayer player, double x, double y, double z)
	{
		if (player instanceof EntityPlayerMP)
		{
			EntityPlayerMP mp = (EntityPlayerMP) player;
			mp.setPositionAndUpdate(x, y, z);
			mp.fallDistance = 0.0F;
			mp.worldObj.playSoundAtEntity(mp, "mob.endermen.portal", 1.0F, 1.0F); // 传送音效
		}
	}

	private boolean consumePlayerNetworkEMC(EntityPlayer player, double amount)
	{
		if (player.capabilities.isCreativeMode) return true;

		double currentEMC = Transmutation.getEmc(player);
		if (currentEMC >= amount)
		{
			Transmutation.setEmc(player, currentEMC - amount);
			// PacketHandler.sendTo(new SyncEmcPKT(currentEMC - amount), (EntityPlayerMP) player);
			return true;
		}
		else
		{
			player.addChatMessage(new ChatComponentTranslation("pe.harmony.noemc", String.format("%.2f", amount)));
			return false;
		}
	}
}
