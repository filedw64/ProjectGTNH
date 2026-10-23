package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.GuiSlotPickPKT;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.client.event.MouseEvent;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端：在容器/背包 GUI 中对着格子按中键时把 windowId + 槽位号发给服务端。
 * 主触发器是 ClientTickEvent 轮询 LWJGL 中键状态：GuiContainer 的 allowUserInput 为 false，
 * Minecraft.runTick 里 Mouse.next() 连同 Forge MouseEvent 整段被跳过，只有直接读 LWJGL 才拿得到 GUI 内的按键。
 * 不取消 MouseEvent，也不在客户端做任何 EMC/知识/转化桌判断。
 */
@SideOnly(Side.CLIENT)
public class GuiSlotPickEvents
{
	// GuiContainer.theSlot 的 SRG 名，依据 mcp_stable 12 / forge 1.7.10 unpacked conf/fields.csv: field_147006_u,theSlot
	private static final String[] SRG_FIELD_CANDIDATES = { "field_147006_u" };

	// 悬停槽字段的解析结果静态缓存：只扫描一次，失败也不重试
	private static Field hoveredSlotField;
	private static String resolvedTag;
	private static boolean resolutionDone;
	private static boolean disabled;

	// 两条触发路径（tick 轮询 / MouseEvent）共用的上升沿与去抖状态
	private static boolean wasMiddleDown;
	private static long lastFireMs;

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event)
	{
		if (event.phase != TickEvent.Phase.END) return;

		// LWJGL 的按钮状态由 Display 的消息循环更新，与 MC 的 Mouse.next() 无关，GUI 打开时依然可读
		boolean down = Mouse.isButtonDown(2);
		boolean rising = down && !wasMiddleDown;
		wasMiddleDown = down;
		if (!rising) return;

		fire();
	}

	@SubscribeEvent
	public void onMouseInput(MouseEvent event)
	{
		// 中键按下（button == 2）；不 cancel，保持原版与其它模组的可见行为
		if (event.button != 2 || !event.buttonstate) return;

		fire();
	}

	/**
	 * 两条触发路径的唯一收口：共用去抖、共用诊断、共用发包。
	 */
	private static void fire()
	{
		// 同一个物理按键可能被两条路径先后看到：150ms 内只处理第一次
		long now = System.currentTimeMillis();
		if (now - lastFireMs < 150) return;

		Minecraft mc = Minecraft.getMinecraft();

		// 世界里一律不管：方块路径由 PickBlockEvents 负责，绝不能双发
		if (mc.thePlayer == null || !(mc.currentScreen instanceof GuiContainer)) return;

		GuiContainer gui = (GuiContainer) mc.currentScreen;
		Slot slot = getHoveredSlot(gui);

		if (slot == null || !slot.getHasStack()) return;

		PacketHandler.sendToServer(new GuiSlotPickPKT(gui.inventorySlots.windowId, slot.slotNumber));
		lastFireMs = System.currentTimeMillis();
	}

	/**
	 * 读取当前悬停的 Slot。三条策略按顺序试，命中后静态缓存，之后每次只是一次 field.get。
	 */
	private static Slot getHoveredSlot(GuiContainer gui)
	{
		if (disabled) return null;

		Field field = resolveField();
		if (field == null) return null;

		try
		{
			return (Slot) field.get(gui);
		}
		catch (Throwable t)
		{
			// 取不到就当没有悬停槽，绝不把客户端搞崩
			return null;
		}
	}

	private static Field resolveField()
	{
		if (resolutionDone) return hoveredSlotField;
		resolutionDone = true;

		// 策略1：MCP 名，开发环境命中
		hoveredSlotField = tryDeclaredField("theSlot");
		if (hoveredSlotField != null)
		{
			resolvedTag = "mcp(theSlot)";
			return hoveredSlotField;
		}

		// 策略2：SRG 名，正式 GTNH 客户端命中
		for (String srg : SRG_FIELD_CANDIDATES)
		{
			hoveredSlotField = tryDeclaredField(srg);
			if (hoveredSlotField != null)
			{
				resolvedTag = "srg(" + srg + ")";
				return hoveredSlotField;
			}
		}

		// 策略3：结构化兜底，只取声明顺序里的第一个 Slot 类型字段。
		// 1.7.10 声明顺序为 theSlot, clickedSlot, returningStackDestSlot, currentDragTargetSlot, lastClickSlot, slot1，第一个正是 theSlot。
		// 不能用“当前值非空”这类启发式：clickedSlot / lastClickSlot 在指针不在槽位上时也可能非空，会发出错误槽位号。
		List<Field> slotFields = new ArrayList<Field>();
		for (Field f : GuiContainer.class.getDeclaredFields())
		{
			if (f.getType() == Slot.class) slotFields.add(f);
		}

		if (!slotFields.isEmpty())
		{
			Field first = slotFields.get(0);
			try
			{
				first.setAccessible(true);
				hoveredSlotField = first;
				resolvedTag = "structural";
				return hoveredSlotField;
			}
			catch (Throwable t)
			{
				// 落到下面的统一失败分支
			}
		}

		// 三者全失败：只警告一次，然后静默关闭，绝不刷屏
		resolvedTag = null;
		hoveredSlotField = null;
		disabled = true;
		PELogger.logWarn("GuiSlotPickEvents: cannot resolve hovered slot; Slot fields=" + namesOf(slotFields));
		return null;
	}

	private static Field tryDeclaredField(String name)
	{
		try
		{
			Field f = GuiContainer.class.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		}
		catch (Throwable t)
		{
			return null;
		}
	}

	private static String namesOf(List<Field> fields)
	{
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < fields.size(); i++)
		{
			if (i > 0) sb.append(", ");
			sb.append(fields.get(i).getName());
		}
		return sb.append("]").toString();
	}
}
