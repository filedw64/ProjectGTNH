package moze_intel.projecte.gameObjs.container.slots.transmutation;

import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;

/**
 * 奥术转化桌的合成输出槽：合成被拾取后统一走知识学习。
 */
public class ArcaneCraftingSlot extends SlotCrafting {
	private final TransmutationInventory inv;

	public ArcaneCraftingSlot(TransmutationInventory inv, EntityPlayer player, IInventory craftMatrix, IInventory craftResult, int index, int x, int y) {
		super(player, craftMatrix, craftResult, index, x, y);
		this.inv = inv;
	}

	@Override
	public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
		// super 内部已经触发 onCrafting(-> onCreated) 并扣除材料，这里只管学习
		super.onPickupFromSlot(player, stack);

		if (stack == null || stack.getItem() == null)
			return;

		ItemStack learned = stack.copy();
		learned.stackSize = 1;

		if (EMCHelper.doesItemHaveEmc(learned))
			inv.handleKnowledge(learned);
	}
}
