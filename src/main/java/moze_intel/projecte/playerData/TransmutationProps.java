package moze_intel.projecte.playerData;

import moze_intel.projecte.math.ExactEMCCodec;

import moze_intel.projecte.math.ExactEMC;

import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;

public class TransmutationProps implements IExtendedEntityProperties {
	private final EntityPlayer player;

	private ExactEMC transmutationEmc = ExactEMC.ZERO;
	private final List<ItemStack> knowledge = new ArrayList<>();
	private ItemStack[] inputLocks = new ItemStack[9];
	public static final String PROP_NAME = "ProjectETransmutation";

	public static void register(EntityPlayer player) {
		player.registerExtendedProperties(PROP_NAME, new TransmutationProps(player));
	}

	public static TransmutationProps getDataFor(EntityPlayer player) {
		return (TransmutationProps) player.getExtendedProperties(PROP_NAME);
	}

	public TransmutationProps(EntityPlayer player) {
		this.player = player;
	}

	public ItemStack[] getInputLocks() {
		return inputLocks;
	}

	public void setInputLocks(ItemStack[] inputLocks) {
		this.inputLocks = inputLocks;
	}

	protected ExactEMC getTransmutationEmc() {
		return transmutationEmc;
	}

	protected void setTransmutationEmc(ExactEMC transmutationEmc) {
		this.transmutationEmc = ExactEMCCodec.validateBalance(transmutationEmc);
	}

	protected List<ItemStack> getKnowledge() {
		pruneStaleKnowledge();
		return knowledge;
	}

	private void pruneDuplicateKnowledge() {
		ItemHelper.compactItemListIgnoreStacksize(knowledge);
		for (ItemStack s : knowledge) {
			if (s.stackSize > 1)
				s.stackSize = 1;
		}
	}

	private void pruneStaleKnowledge() {
        knowledge.removeIf(itemStack -> !EMCHelper.doesItemHaveEmc(itemStack));
	}

	protected NBTTagCompound saveForPacket() {
		NBTTagCompound nbt = new NBTTagCompound();
		ExactEMCCodec.writeBalance(nbt, transmutationEmc);

		pruneStaleKnowledge();
		NBTTagList knowledgeList = new NBTTagList();
		for (ItemStack is : knowledge) {
			knowledgeList.appendTag(is.writeToNBT(new NBTTagCompound()));
		}

		NBTTagList inputLockList = ItemHelper.toIndexedNBTList(inputLocks);
		nbt.setTag("knowledge", knowledgeList);
		nbt.setTag("inputlocks", inputLockList);
		return nbt;
	}

    public void applyBalancePacket(ExactEMC balance) {
        if (!player.worldObj.isRemote) throw new IllegalStateException("Client balance update on server");
        setTransmutationEmc(balance);
        refreshClientInventory();
    }

    private void refreshClientInventory() {
        if (player.openContainer instanceof moze_intel.projecte.gameObjs.container.TransmutationContainer) {
            moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory inv =
                ((moze_intel.projecte.gameObjs.container.TransmutationContainer) player.openContainer).transmutationInventory;
            inv.invalidateSearchCache();
            inv.updateOutputs();
        }
    }

	public void readFromPacket(NBTTagCompound nbt) {
		transmutationEmc = ExactEMCCodec.readBalance(nbt);

		NBTTagList knowledgeList = nbt.getTagList("knowledge", Constants.NBT.TAG_COMPOUND);
		int length = knowledgeList.tagCount();
		knowledge.clear();
		for (int i = 0; i < length; i++) {
			ItemStack is = ItemStack.loadItemStackFromNBT(knowledgeList.getCompoundTagAt(i));
			if (is == null) continue;
			knowledge.add(is);
		}
		pruneDuplicateKnowledge();

		NBTTagList inputLockList = nbt.getTagList("inputlocks", Constants.NBT.TAG_COMPOUND);
		inputLocks = ItemHelper.copyIndexedNBTToArray(inputLockList, new ItemStack[9]);
        if (player.worldObj.isRemote) refreshClientInventory();
	}

	@Override
	public void saveNBTData(NBTTagCompound playerData) {
		NBTTagCompound data = new NBTTagCompound();
		ExactEMCCodec.writeBalance(data, transmutationEmc);

		pruneStaleKnowledge();
		NBTTagList knowledgeList = new NBTTagList();
		for (ItemStack is : knowledge) {
			knowledgeList.appendTag(is.writeToNBT(new NBTTagCompound()));
		}

		NBTTagList inputLockList = ItemHelper.toIndexedNBTList(inputLocks);
		data.setTag("knowledge", knowledgeList);
		data.setTag("inputlock", inputLockList);
		playerData.setTag(PROP_NAME, data);
	}

	@Override
	public void loadNBTData(NBTTagCompound playerData) {
		NBTTagCompound data = playerData.getCompoundTag(PROP_NAME);
		transmutationEmc = ExactEMCCodec.readBalance(data);

		NBTTagList knowledgeList = data.getTagList("knowledge", Constants.NBT.TAG_COMPOUND);
		int length = knowledgeList.tagCount();
		for (int i = 0; i < length; i++) {
			ItemStack is = ItemStack.loadItemStackFromNBT(knowledgeList.getCompoundTagAt(i));
			if (is == null) continue;
			knowledge.add(is);
		}
		pruneDuplicateKnowledge();

		NBTTagList inputLockList = data.getTagList("inputlock", Constants.NBT.TAG_COMPOUND);
		inputLocks = ItemHelper.copyIndexedNBTToArray(inputLockList, new ItemStack[9]);
        if (player.worldObj.isRemote) refreshClientInventory();
	}

	@Override
	public void init(Entity entity, World world) {}
}
