package moze_intel.projecte.gameObjs.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import moze_intel.projecte.gameObjs.items.AlchemicalBag;
import moze_intel.projecte.PECore;
import moze_intel.projecte.utils.Constants;

public class AlchBagPageContainer extends Container {
	private EntityPlayer player;
	private ItemStack bag;

	public AlchBagPageContainer(EntityPlayer player, ItemStack bag) {
		this.player = player;
		this.bag = bag;
	}

	@Override
	public boolean canInteractWith(EntityPlayer player) {
		return true;
	}

	// 利用原版附魔台的底层通信机制，实现 Client 到 Server 的按钮点击同步
	@Override
	public boolean enchantItem(EntityPlayer player, int button) {
		if (button >= 0) {
			AlchemicalBag.setPage(bag, (byte) button);
			// 选完页数后，服务器直接指令玩家打开对应的炼金袋 GUI！
			player.openGui(PECore.instance, Constants.ALCH_BAG_GUI, player.worldObj, (int) player.posX, (int) player.posY, (int) player.posZ);
			return true;
		}
		return false;
	}
}
