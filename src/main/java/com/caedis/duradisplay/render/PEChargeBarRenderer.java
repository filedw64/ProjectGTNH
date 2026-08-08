package com.caedis.duradisplay.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

public class PEChargeBarRenderer extends OverlayRenderer {
	public final static PEChargeBarRenderer instance = new PEChargeBarRenderer();
	public double per;

	private PEChargeBarRenderer() {}

	public PEChargeBarRenderer setPercentage(double per) {
		this.per = per;
		return this;
	}

	@Override
	public void Render(final FontRenderer fr, final int x, final int y) {
		final double length = per * 13.0D;
		final int k = (int) Math.round(per * 255.0D);

		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glDisable(GL11.GL_BLEND);

		final int color = 255 - k << 16 | k << 8;
		final int i1 = (255 - k) / 4 << 16 | 16128;

		renderQuad(x + 2, y + 13, 13, 2, 0);
		renderQuad(x + 2, y + 13, 12, 1, i1);
		renderQuad(x + 2, y + 13, length, 1, color);

		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private final static Tessellator tessellator = Tessellator.instance;

	private static void renderQuad(final double xPos, final double yPos, final double width, final double height, final int color) {
		tessellator.startDrawingQuads();
		tessellator.setColorOpaque_I(color);
		tessellator.addVertex(xPos, yPos, 0.0D);
		tessellator.addVertex(xPos, yPos + height, 0.0D);
		tessellator.addVertex(xPos + width, yPos + height, 0.0D);
		tessellator.addVertex(xPos + width, yPos, 0.0D);
		tessellator.draw();
	}
}
