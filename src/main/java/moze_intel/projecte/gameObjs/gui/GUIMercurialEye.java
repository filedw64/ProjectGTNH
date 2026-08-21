package moze_intel.projecte.gameObjs.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.container.MercurialEyeContainer;
import moze_intel.projecte.gameObjs.container.inventory.MercurialEyeInventory;

public class GUIMercurialEye extends GuiContainer
{
	private static final ResourceLocation texture = new ResourceLocation(PECore.MODID.toLowerCase(), "textures/gui/mercurial_eye.png");

	public GUIMercurialEye(InventoryPlayer invPlayer, MercurialEyeInventory inventory)
	{
		super(new MercurialEyeContainer(invPlayer, inventory));
		this.xSize = 171;
		this.ySize = 134;
	}

	@Override
	protected void drawGuiContainerBackgroundLayer(float var1, int var2, int var3)
	{
		GL11.glColor4f(1F, 1F, 1F, 1F);
		Minecraft.getMinecraft().renderEngine.bindTexture(texture);
		this.drawTexturedModalRect((width - xSize) / 2, (height - ySize) / 2, 0, 0, xSize, ySize);
	}
}
