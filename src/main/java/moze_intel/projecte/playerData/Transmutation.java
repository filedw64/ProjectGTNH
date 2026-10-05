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
        DEFERRED_EMC_SYNC.clear(); WARNED_EMC_SYNC.clear();
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


    // AppliedE core hardening v3
    private static final java.util.Set<java.util.UUID> DEFERRED_EMC_SYNC = new java.util.HashSet<>();
    private static final java.util.Set<java.util.UUID> WARNED_EMC_SYNC = new java.util.HashSet<>();
    /** Caller holds UUID-ordered player locks. No networking or event dispatch during commit. */
    public static void commitEmcBalances(List<EntityPlayer> players, List<ExactEMC> before, List<ExactEMC> after) {
        moze_intel.projecte.events.TickEvents.requireServerThread();
        if (players.size() != before.size() || players.size() != after.size())
            throw new IllegalArgumentException("Mismatched EMC transaction");
        List<TransmutationProps> props = new ArrayList<>();
        java.util.Set<java.util.UUID> unique = new java.util.HashSet<>();
        for (int i = 0; i < players.size(); i++) {
            EntityPlayer player = players.get(i); requireServer(player);
            if (!unique.add(player.getUniqueID())) throw new IllegalArgumentException("Duplicate EMC owner");
            TransmutationProps data = TransmutationProps.getDataFor(player);
            if (data == null || !data.getTransmutationEmc().equals(before.get(i)))
                throw new IllegalStateException("EMC balance changed during transaction");
            moze_intel.projecte.math.ExactEMCCodec.validateBalance(after.get(i));
            props.add(data);
        }
        int committed = 0;
        try {
            for (; committed < props.size(); committed++)
                if (!before.get(committed).equals(after.get(committed)))
                    props.get(committed).setTransmutationEmc(after.get(committed));
        } catch (RuntimeException failure) {
            for (int i = committed - 1; i >= 0; i--) props.get(i).setTransmutationEmc(before.get(i));
            throw failure;
        }
        for (int i = 0; i < players.size(); i++)
            if (!before.get(i).equals(after.get(i))) DEFERRED_EMC_SYNC.add(players.get(i).getUniqueID());
    }
    /** Run outside player locks; retry notification failures, never reapply committed balances. */
    public static void drainDeferredEmcSync() {
        moze_intel.projecte.events.TickEvents.requireServerThread();
        net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
        if (server == null || server.getConfigurationManager() == null) return;
        for (java.util.UUID uuid : new java.util.HashSet<>(DEFERRED_EMC_SYNC)) {
            EntityPlayer found = null;
            for (Object raw : server.getConfigurationManager().playerEntityList)
                if (raw instanceof EntityPlayer && uuid.equals(((EntityPlayer) raw).getUniqueID())) {
                    found = (EntityPlayer) raw; break;
                }
            if (found == null) { DEFERRED_EMC_SYNC.remove(uuid); WARNED_EMC_SYNC.remove(uuid); continue; }
            try {
                syncEmc(found);
                DEFERRED_EMC_SYNC.remove(uuid); WARNED_EMC_SYNC.remove(uuid);
            } catch (RuntimeException failure) {
                if (WARNED_EMC_SYNC.add(uuid))
                    PELogger.logWarn("Deferred EMC sync failed; balance is committed, notification will retry: " + uuid);
            }
        }
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
