package projectgtnh.gameObjs.gui;

import projectgtnh.PECore;
import projectgtnh.gameObjs.container.AlchBagContainer;
import projectgtnh.gameObjs.container.AlchChestContainer;
import projectgtnh.gameObjs.container.inventory.AlchBagInventory;
import projectgtnh.gameObjs.tiles.AlchChestTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class GUIAlchChest extends GuiContainer
{
	private static final ResourceLocation texture = new ResourceLocation(PECore.MODID.toLowerCase(), "textures/gui/alchchest.png");
	
	public GUIAlchChest(InventoryPlayer invPlayer, AlchChestTile tile) 
	{
		super(new AlchChestContainer(invPlayer, tile));
		this.xSize = 255;
		this.ySize = 230;
	}
	
	public GUIAlchChest(InventoryPlayer invPlayer, AlchBagInventory invBag)
	{
		super(new AlchBagContainer(invPlayer, invBag));
		this.xSize = 255;
		this.ySize = 230;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float var1, int var2, int var3) 
	{
		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().renderEngine.bindTexture(texture);
		this.drawTexturedModalRect((width - xSize) / 2, (height - ySize) / 2, 0, 0, xSize, ySize);
	}
}
