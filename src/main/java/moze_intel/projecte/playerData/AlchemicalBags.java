package moze_intel.projecte.playerData;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.SyncBagDataPKT;
import moze_intel.projecte.utils.PELogger;

public final class AlchemicalBags
{
	// 原版单页调用
	public static ItemStack[] get(EntityPlayer player, byte color)
	{
		return get(player, color, (byte) 0);
	}

	// 支持分页的数据获取。利用 color + page * 16 映射到 0~79 的虚拟ID
	public static ItemStack[] get(EntityPlayer player, byte color, byte page)
	{
		byte virtualColorId = (byte) (color + (page * 16));
		return AlchBagProps.getDataFor(player).getInv(virtualColorId);
	}

	public static void set(EntityPlayer player, byte color, ItemStack[] inv)
	{
		set(player, color, (byte) 0, inv);
	}

	// 支持分页的数据保存
	public static void set(EntityPlayer player, byte color, byte page, ItemStack[] inv)
	{
		byte virtualColorId = (byte) (color + (page * 16));
		AlchBagProps.getDataFor(player).setInv(virtualColorId, inv);
	}

	public static void syncFull(EntityPlayer player)
	{
		PacketHandler.sendTo(new SyncBagDataPKT(AlchBagProps.getDataFor(player).saveForPacket()), (EntityPlayerMP) player);
		PELogger.logDebug("** SENT FULL BAG DATA **");
	}

	public static void syncPartial(EntityPlayer player, int color)
	{
		syncPartial(player, color, 0);
	}

	// 支持分页的网络同步
	public static void syncPartial(EntityPlayer player, int color, int page)
	{
		int virtualColorId = color + (page * 16);
		PacketHandler.sendTo(new SyncBagDataPKT(AlchBagProps.getDataFor(player).saveForPartialPacket(virtualColorId)), (EntityPlayerMP) player);
		PELogger.logDebug("** SENT PARTIAL BAG DATA **");
	}
}
