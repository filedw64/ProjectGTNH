package moze_intel.projecte.gameObjs.gui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

public final class RefinedButton extends GuiButton {
	public RefinedButton(int stateName, int x, int y, int width, int height, String buttonText) {
		super(stateName, x, y, width, height, buttonText);
	}

	@Override
	public void drawButton(final Minecraft mc, final int mouseX, final int mouseY) {
		if (!this.visible) return;
		mc.getTextureManager().bindTexture(buttonTextures);

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		GL11.glEnable(GL11.GL_BLEND);
		OpenGlHelper.glBlendFunc(770, 771, 1, 0);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		field_146123_n = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
		final int hoverState = getHoverState(field_146123_n);
		final double halfWidth = width / 2.0, halfHeight = height / 2.0;
		final double tYup = 46 + hoverState * 20, tXright = 200 - halfWidth;

		if (height < 20) {
			final double tYdown = tYup + 20 - halfHeight;
			drawTextureRect(xPosition, yPosition, 0, tYup, halfWidth, halfHeight);// 左上
			drawTextureRect(xPosition, yPosition + halfHeight, 0, tYdown, halfWidth, halfHeight);// 左下

			drawTextureRect(xPosition + halfWidth, yPosition, tXright, tYup, halfWidth, halfHeight);// 右上
			drawTextureRect(xPosition + halfWidth, yPosition + halfHeight, tXright, tYdown, halfWidth, halfHeight);// 右下
		}
		else {
			drawTextureRect(xPosition, yPosition, 0, tYup, halfWidth, height);
			drawTextureRect(xPosition + halfWidth, yPosition, tXright, tYup, halfWidth, height);
		}

		int textColor;
		if (packedFGColour != 0)
			textColor = packedFGColour;
		else if (field_146123_n)
			textColor = 16777120;// hovered #FFFFA0
		else textColor = 14737632;// not hovered #E0E0E0

		drawCenteredString(mc.fontRenderer, displayString, xPosition + width / 2, yPosition + (height - 9) / 2, textColor);
	}

	private static final Tessellator tessellator = Tessellator.instance;

	private void drawTextureRect(final double x, final double y, final double textureX, final double textureY, final double width, final double height)
	{
		final double fx = 0.00390625D, fy = 0.00390625D;
		tessellator.startDrawingQuads();
		tessellator.addVertexWithUV(x, y + height, zLevel, textureX * fx, (textureY + height) * fy);
		tessellator.addVertexWithUV(x + width, y + height, zLevel, (textureX + width) * fx, (textureY + height) * fy);
		tessellator.addVertexWithUV(x + width, y, zLevel, (textureX + width) * fx, textureY * fy);
		tessellator.addVertexWithUV(x, y, zLevel, textureX * fx, textureY * fy);
		tessellator.draw();
	}
}
