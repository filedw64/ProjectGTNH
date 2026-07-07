package projecte.playerData;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import projecte.network.PacketHandler;
import projecte.network.packets.SyncBagDataPKT;
import projecte.utils.PELogger;

public final class AlchemicalBags
{
	public static ItemStack[] get(EntityPlayer player, byte color)
	{
		return AlchBagProps.getDataFor(player).getInv(color);
	}

	public static void set(EntityPlayer player, byte color, ItemStack[] inv)
	{
		AlchBagProps.getDataFor(player).setInv(color, inv);
	}

	public static void syncFull(EntityPlayer player)
	{
		PacketHandler.sendTo(new SyncBagDataPKT(AlchBagProps.getDataFor(player).saveForPacket()), (EntityPlayerMP) player);
		PELogger.logDebug("** SENT FULL BAG DATA **");
	}

	public static void syncPartial(EntityPlayer player, int color)
	{
		PacketHandler.sendTo(new SyncBagDataPKT(AlchBagProps.getDataFor(player).saveForPartialPacket(color)), (EntityPlayerMP) player);
		PELogger.logDebug("** SENT PARTIAL BAG DATA **");
	}
}
