package moze_intel.projecte.events;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.GuiSlotPickPKT;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.MouseEvent;

/**
 * 客户端：在容器/背包 GUI 中对着格子按中键时把 windowId + 槽位号发给服务端。
 * 不取消 MouseEvent，也不在客户端做任何 EMC/知识/转化桌判断。
 */
@SideOnly(Side.CLIENT)
public class GuiSlotPickEvents
{
	private static boolean disabled;

	@SubscribeEvent
	public void onMouseInput(MouseEvent event)
	{
		// 中键按下（button == 2）；不 cancel，保持原版与其它模组的可见行为
		if (event.button != 2 || !event.buttonstate) return;

		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer == null || !(mc.currentScreen instanceof GuiContainer)) return;

		GuiContainer gui = (GuiContainer) mc.currentScreen;
		Slot slot = getHoveredSlot(gui);
		if (slot == null || !slot.getHasStack()) return;

		PacketHandler.sendToServer(new GuiSlotPickPKT(gui.inventorySlots.windowId, slot.slotNumber));
	}

	/**
	 * ObfuscationReflectionHelper 会把 MCP 名 "theSlot" 在运行期翻成 SRG 名，开发与线上都能取到悬停槽。
	 */
	private static Slot getHoveredSlot(GuiContainer gui)
	{
		if (disabled) return null;

		try
		{
			return ObfuscationReflectionHelper.getPrivateValue(GuiContainer.class, gui, "theSlot");
		}
		catch (Throwable t)
		{
			// 取不到就永久失效，绝不把客户端搞崩
			disabled = true;
			PELogger.logDebug("GuiSlotPickEvents: cannot read GuiContainer#theSlot, slot copy disabled");
			return null;
		}
	}
}
