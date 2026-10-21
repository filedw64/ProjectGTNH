package moze_intel.projecte.gameObjs.gui.transport.nei;

import codechicken.nei.VisiblityData;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import java.util.Collections;
import java.util.List;
import moze_intel.projecte.gameObjs.gui.transport.GUIMatterInterface;
import moze_intel.projecte.gameObjs.tiles.transport.TileMatterInterface;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

/**
 * GUIMatterInterface 的 NEI 适配层：本类加载即拉入 codechicken.* 类型，所以**只允许在 NEI 存在时**
 * 由 {@code moze_intel.projecte.utils.GuiHandler} 实例化（条件分支 + try/catch(Throwable) 兜住 LinkageError）。
 * 反向依赖被严格禁止：GUIMatterInterface 及其父链不得引用本类，否则无 NEI 的整合包又会类加载失败。
 *
 * 除 handleDragNDrop 外全部沿用 NEI 的 INEIGuiAdapter 默认语义：不变可见性 / 空列表 / null / false。
 */
public class GUIMatterInterfaceNei extends GUIMatterInterface implements INEIGuiHandler
{
	public GUIMatterInterfaceNei(InventoryPlayer invPlayer, TileMatterInterface tile)
	{
		super(invPlayer, tile);
	}

	@Override
	public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility)
	{
		return currentVisibility;
	}

	@Override
	public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item)
	{
		return Collections.emptyList();
	}

	@Override
	public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui)
	{
		return null;
	}

	@Override
	public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h)
	{
		return false;
	}

	/**
	 * NEI 把物品从面板拖到 GUI 上时走这里（另一条入口是「手持物品点面板」）。
	 * gui != this 的判定沿用原实现：NEI 按实例注册，理论上只会把本 GUI 自己的拖放送进来。
	 */
	@Override
	public boolean handleDragNDrop(GuiContainer gui, int mousex, int mousey, ItemStack draggedStack, int button)
	{
		if (gui != this)
			return false;

		return handleExternalDrag(mousex, mousey, draggedStack, button);
	}
}
