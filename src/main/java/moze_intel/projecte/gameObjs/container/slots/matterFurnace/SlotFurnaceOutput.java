package moze_intel.projecte.gameObjs.container.slots.matterFurnace;

import cpw.mods.fml.common.FMLCommonHandler;
import moze_intel.projecte.gameObjs.tiles.RMFurnaceTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.AchievementList;

public class SlotFurnaceOutput extends Slot {
	private final RMFurnaceTile furnace;

	public SlotFurnaceOutput(IInventory inv, int slotId, int xPos, int yPos) {
		super(inv, slotId, xPos, yPos);
		if (inv instanceof RMFurnaceTile tile)
			furnace = tile;
		else furnace = null;
	}

	@Override
	public boolean isItemValid(ItemStack stack) {
		return false;
	}

	@Override
	public void onPickupFromSlot(EntityPlayer player, ItemStack stack) {
		stack.onCrafting(player.worldObj, player, stack.stackSize);
		FMLCommonHandler.instance().firePlayerSmeltedEvent(player, stack);
		if (stack.getItem() == Items.iron_ingot) player.addStat(AchievementList.acquireIron, 1);
		if (stack.getItem() == Items.cooked_fished) player.addStat(AchievementList.cookFish, 1);
		if (furnace != null)
			furnace.spawnXPOrbs(player);
		super.onPickupFromSlot(player, stack);
	}
}
