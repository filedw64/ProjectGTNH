package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import moze_intel.projecte.gameObjs.gui.GUIMercurialShape;
import moze_intel.projecte.gameObjs.items.MercurialEye;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.KeyPressPKT;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.PEKeybind;

import java.util.Map;

@SideOnly(Side.CLIENT)
public class KeyPressEvent
{
	@SubscribeEvent
	public void keyPress(KeyInputEvent event) {
		// 避免高频事件下的重复哈希查找开销
		for (Map.Entry<KeyBinding, PEKeybind> entry : ClientKeyHelper.mcToPe.entrySet()) {
			if (entry.getKey().isPressed()) {
				// MercurialEye 的菜单仅在客户端打开，无需额外发送一个空操作包。
				if (entry.getValue() != PEKeybind.EXTRA_FUNCTION || !openMercurialGui()) {
					PacketHandler.sendToServer(new KeyPressPKT(entry.getValue()));
				}
			}
		}
	}

	/**
	 * 手持墨丘利之眼按下 EXTRA_FUNCTION 时打开形状/目标方块轮盘 GUI。
	 * 形状取自物品的 getBuildShape（与建造逻辑同源，无 NBT 时为 CUBE），目标方块从 NBT 还原，缺失时为 null。
	 */
	private boolean openMercurialGui() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || mc.currentScreen != null) return false;
		// 潜行是重置选区，交给服务端处理
		if (mc.thePlayer.isSneaking()) return false;

		ItemStack held = mc.thePlayer.getHeldItem();
		if (held == null || !(held.getItem() instanceof MercurialEye)) return false;

		MercurialEye eye = (MercurialEye) held.getItem();
		// 形状以物品自身的判定为准：无 NBT / 越界时 getBuildShape 返回 CUBE，
		// 直读 NBT 会得到 0(LINE)，并在 GUIMercurialShape 回发 MercurialShapePKT 时把形状静默改掉
		byte shapeOrdinal = (byte) eye.getBuildShape(held).ordinal();

		ItemStack targetItem = null;
		NBTTagCompound nbt = held.getTagCompound();
		if (nbt != null && nbt.hasKey("TargetItem"))
			targetItem = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("TargetItem"));

		mc.displayGuiScreen(new GUIMercurialShape(shapeOrdinal, targetItem));
		return true;
	}
}
