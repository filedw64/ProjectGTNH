package moze_intel.projecte.playerData;

import moze_intel.projecte.math.ExactEMC;

import moze_intel.projecte.api.event.PlayerKnowledgeChangeEvent;
import moze_intel.projecte.emc.EMCMapper;
import moze_intel.projecte.emc.SimpleStack;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.KnowledgeChangePKT;
import moze_intel.projecte.network.packets.KnowledgeSyncPKT;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public final class Transmutation {
	private static final List<ItemStack> CACHED_TOME_KNOWLEDGE = new ArrayList<>();

	public static void clearCache() {
		CACHED_TOME_KNOWLEDGE.clear();
	}

	public static void cacheFullKnowledge() {
		CACHED_TOME_KNOWLEDGE.clear();
		for (SimpleStack stack : EMCMapper.emc.keySet()) {
			if (!stack.isValid()) continue;
			ItemStack is = stack.toItemStack();
			if (is == null) continue;
			is.stackSize = 1;

			//Apparently items can still not have EMC if they are in the EMC map.
			if (EMCHelper.doesItemHaveEmc(is) && EMCHelper.getEmcValue(is) > 0)
				CACHED_TOME_KNOWLEDGE.add(is);
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
		TransmutationProps data = TransmutationProps.getDataFor(player);
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

	public static void setFullKnowledge(EntityPlayer player) {
		List<ItemStack> knowledge = TransmutationProps.getDataFor(player).getKnowledge();
		knowledge.clear();
		knowledge.addAll(CACHED_TOME_KNOWLEDGE);
		if (!player.worldObj.isRemote) {
			MinecraftForge.EVENT_BUS.post(new PlayerKnowledgeChangeEvent(player));
		}
	}

	public static void clearKnowledge(EntityPlayer player)
	{
		TransmutationProps data = TransmutationProps.getDataFor(player);
		data.getKnowledge().clear();
		if (!player.worldObj.isRemote)
		{
			MinecraftForge.EVENT_BUS.post(new PlayerKnowledgeChangeEvent(player));
		}
	}

    public static ExactEMC getEmcExact(EntityPlayer player) {
        return TransmutationProps.getDataFor(player).getTransmutationEmc();
    }

    /** Deprecated, lossy external compatibility view only. */
    @Deprecated
    public static double getEmc(EntityPlayer player) {
        return getEmcExact(player).toLegacyDouble();
    }

    /** Refuses unsafe double read/modify/write on a nonrepresentable balance. */
    @Deprecated
    public static void setEmc(EntityPlayer player, double emc) {
        requireServer(player);
        if (!getEmcExact(player).isExactlyRepresentableAsDouble())
            throw new IllegalStateException("Use exact EMC transaction API for this balance");
        setEmcExact(player, ExactEMC.fromLegacyDouble(emc));
    }

    public static void requireServer(EntityPlayer player) {
        if (player == null || player.worldObj.isRemote)
            throw new IllegalStateException("Player EMC transactions are server-only");
        moze_intel.projecte.events.TickEvents.requireServerThread();
    }

    public static void setEmcExact(EntityPlayer player, ExactEMC emc) {
        requireServer(player);
        TransmutationProps.getDataFor(player).setTransmutationEmc(emc);
        syncEmc(player);
    }

    public static void addEmcExact(EntityPlayer player, ExactEMC amount) {
        requireServer(player);
        moze_intel.projecte.math.ExactEMCCodec.validateBalance(amount);
        if (amount.isZero()) return;
        setEmcExact(player, getEmcExact(player).add(amount));
    }

    public static boolean tryRemoveEmcExact(EntityPlayer player, ExactEMC amount) {
        requireServer(player);
        moze_intel.projecte.math.ExactEMCCodec.validateBalance(amount);
        ExactEMC current = getEmcExact(player);
        if (current.compareTo(amount) < 0) return false;
        if (!amount.isZero()) setEmcExact(player, current.subtract(amount));
        return true;
    }

    public static void syncEmc(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && !player.worldObj.isRemote)
            PacketHandler.sendTo(new moze_intel.projecte.network.packets.PlayerEMCSyncPKT(getEmcExact(player)),
                (EntityPlayerMP) player);
    }

	/**
	 * Send Knowledge Sync Packet to player.<br>
	 * Call at Server side.
	 * @param player The player sync Knowledge for
	 */
	public static void sync(EntityPlayer player)
	{
		PacketHandler.sendTo(new KnowledgeSyncPKT(TransmutationProps.getDataFor(player).saveForPacket()), (EntityPlayerMP) player);
		PELogger.logDebug("** SENT TRANSMUTATION DATA **");
	}

	public static void syncIncremental(EntityPlayer player, ItemStack stack, boolean isRemove)
	{
		PacketHandler.sendTo(new KnowledgeChangePKT(stack, isRemove), (EntityPlayerMP) player);
		PELogger.logDebug("** SENT INCREMENTAL TRANSMUTATION DATA **");
	}
}
