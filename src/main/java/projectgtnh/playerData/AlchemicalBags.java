package projectgtnh.playerData;

import com.google.common.collect.Maps;
import projectgtnh.network.PacketHandler;
import projectgtnh.network.packets.SyncBagDataPKT;
import projectgtnh.utils.PELogger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.Map;
import java.util.Map.Entry;

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
