package moze_intel.projecte.playerData;

import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.KnowledgeSyncPKT;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PELogger;
import moze_intel.projecte.PECore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class Transmutation
{
	private static final List<ItemStack> CACHED_TOME_KNOWLEDGE = new ArrayList<>();

	public static void clearCache() {
		CACHED_TOME_KNOWLEDGE.clear();
	}

	public static void cacheFullKnowledge()
	{
		// 优化
		Set<SimpleStack> seenStacks = new HashSet<>();

		for (SimpleStack stack : EMCMapper.emc.keySet())
		{
			if (!stack.isValid()) continue;
			ItemStack s = stack.toItemStack();
			if (s == null) continue;
			s.stackSize = 1;

			if (EMCHelper.doesItemHaveEmc(s) && EMCHelper.getEmcValue(s) > 0)
			{
				SimpleStack dedupeStack = new SimpleStack(s);
				if (seenStacks.add(dedupeStack)) // 不重复
				{
					CACHED_TOME_KNOWLEDGE.add(s);
				}
			}
		}
	}

	public static List<ItemStack> getKnowledge(EntityPlayer player)
	{
		TransmutationProps data = TransmutationProps.getDataFor(player);
		return data.getKnowledge();
	}

	public static void addKnowledge(ItemStack stack, EntityPlayer player)
	{
		TransmutationProps data = TransmutationProps.getDataFor(player);
		if (!hasKnowledgeForStack(stack, player))
		{
			data.getKnowledge().add(stack);
			data.rebuildIndex(); // 快速索引
			if (!player.worldObj.isRemote)
			{
				MinecraftForge.EVENT_BUS.post(new PlayerKnowledgeChangeEvent(player));
			}
		}
	}

	public static void removeKnowledge(ItemStack stack, EntityPlayer player)
	{
		TransmutationProps data = TransmutationProps.getDataFor(player);
		if (hasKnowledgeForStack(stack, player))
		{
			Iterator<ItemStack> iter = data.getKnowledge().iterator();

			while (iter.hasNext())
			{
				if (ItemStack.areItemStacksEqual(stack, iter.next()))
				{
					iter.remove();
					data.rebuildIndex(); // 同步更新快速索引
					if (!player.worldObj.isRemote)
					{
						MinecraftForge.EVENT_BUS.post(new PlayerKnowledgeChangeEvent(player));
					}
					break;
				}
			}
		}
	}

	public static void setInputsAndLocks(ItemStack[] stacks, EntityPlayer player)
	{
		TransmutationProps data = TransmutationProps.getDataFor(player);
		data.setInputLocks(stacks);
	}

	public static ItemStack[] getInputsAndLock(EntityPlayer player)
	{
		ItemStack[] locks = TransmutationProps.getDataFor(player).getInputLocks();
		return Arrays.copyOf(locks, locks.length);
	}

	public static boolean hasKnowledgeForStack(ItemStack stack, EntityPlayer player)
	{
		if (stack == null || stack.getItem() == null) {
			return false;
		}

		TransmutationProps data = TransmutationProps.getDataFor(player);

		// 拦截
		if (!data.hasItemType(stack.getItem())) {
			return false;
		}

		for (ItemStack s : data.getKnowledge())
		{
			if (EMCMapper.enableNBTprocess) {
				if (ItemHelper.areItemStacksEqual(s, stack)) {
					return true;
				}
			}
			else {
				if (ItemHelper.basicAreStacksEqual(s, stack)) {
					return true;
				}
			}
		}
		return false;
	}

	public static void setFullKnowledge(EntityPlayer player)
	{
		TransmutationProps data = TransmutationProps.getDataFor(player);
		data.getKnowledge().clear();
		data.getKnowledge().addAll(CACHED_TOME_KNOWLEDGE);
		data.rebuildIndex(); // 快速索引

		if (!player.worldObj.isRemote)
		{
			MinecraftForge.EVENT_BUS.post(new PlayerKnowledgeChangeEvent(player));
		}
	}

	public static void clearKnowledge(EntityPlayer player)
	{
		TransmutationProps data = TransmutationProps.getDataFor(player);
		data.getKnowledge().clear();
		data.rebuildIndex(); // 快速索引

		if (!player.worldObj.isRemote)
		{
			MinecraftForge.EVENT_BUS.post(new PlayerKnowledgeChangeEvent(player));
		}
	}

	public static double getEmc(EntityPlayer player)
	{
		return TransmutationProps.getDataFor(player).getTransmutationEmc();
	}

	public static void setEmc(EntityPlayer player, double emc)
	{
		TransmutationProps.getDataFor(player).setTransmutationEmc(emc);
	}

	public static void sync(EntityPlayer player)
	{
		PacketHandler.sendTo(new KnowledgeSyncPKT(TransmutationProps.getDataFor(player).saveForPacket()), (EntityPlayerMP) player);
		PELogger.logDebug("** SENT TRANSMUTATION DATA **");
	}

	public static void syncIncremental(EntityPlayer player, ItemStack stack, boolean isRemove)
	{
		PacketHandler.sendTo(new KnowledgeSyncPKT(stack, isRemove), (EntityPlayerMP) player);
		PELogger.logDebug("** SENT INCREMENTAL TRANSMUTATION DATA **");
	}

	// 客户端调用此方法更新内存
	public static void updateKnowledgeClient(ItemStack stack, boolean isRemove)
	{
		TransmutationProps props = PECore.proxy.getClientTransmutationProps();
		if (isRemove) {
			Iterator<ItemStack> iter = props.getKnowledge().iterator();
			while (iter.hasNext()) {
				ItemStack s = iter.next();
				boolean match = EMCMapper.enableNBTprocess ? ItemHelper.areItemStacksEqual(s, stack) : ItemHelper.basicAreStacksEqual(s, stack);
				if (match) {
					iter.remove();
					break;
				}
			}
		} else {
			boolean exists = false;
			for (ItemStack s : props.getKnowledge()) {
				boolean match = EMCMapper.enableNBTprocess ? ItemHelper.areItemStacksEqual(s, stack) : ItemHelper.basicAreStacksEqual(s, stack);
				if (match) {
					exists = true;
					break;
				}
			}
			if (!exists) {
				props.getKnowledge().add(stack);
			}
		}
		props.rebuildIndex();
	}
}
