package moze_intel.projecte.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import org.lwjgl.opengl.GL11;
import moze_intel.projecte.gameObjs.items.armor.GemFeet;

import java.awt.Color;

@SideOnly(Side.CLIENT)
public class PlayerRender
{
	private static final ModelYue yuemodel = new ModelYue();

	private static final String SIN_UUID = "5f86012c-ca4b-451a-989c-8fab167af647";
	private static final String CLAR_UUID = "e5c59746-9cf7-4940-a849-d09e1f1efc13";

	private static final String PIONEER_1_UUID = "f8afe105-6f53-4f95-bb79-efb4662005ab";
	private static final String PIONEER_2_UUID = "a2449286-6fed-4d1f-9fe7-d75df67b5a76";

	private static final ResourceLocation TEX_HEART = new ResourceLocation("projecte:textures/models/heartcircle.png");
	private static final ResourceLocation TEX_YUE = new ResourceLocation("projecte:textures/models/yuecircle.png");

	@SubscribeEvent
	public void playerRender(RenderPlayerEvent.Specials.Pre evt)
	{
		String currentUUID = evt.entityPlayer.getUniqueID().toString();

		boolean isSin = currentUUID.equals(SIN_UUID);
		boolean isClar = currentUUID.equals(CLAR_UUID);
		boolean isPioneer = currentUUID.equals(PIONEER_1_UUID) || currentUUID.equals(PIONEER_2_UUID);

		if(isSin || isClar || isPioneer)
		{
			GL11.glPushMatrix();
			evt.renderer.modelBipedMain.bipedBody.postRender(0.0625f);

			if (evt.entityPlayer.isSneaking())
			{
				GL11.glRotatef(-28.64789F, 1.0F, 0.0F, 0.0F);
				GL11.glTranslatef(0.0f, -0.1f, 0.0f);
			}

			GL11.glRotatef(180, 0, 0, 1);
			GL11.glScalef(3.0f, 3.0f, 3.0f);
			GL11.glTranslatef(-0.5f, -0.498f, -0.5f);

			GL11.glDisable(GL11.GL_LIGHTING);
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

			if(isClar)
			{
				GL11.glColor4f(0.49F, 0.97F, 1.0F, 1.0F);
				Minecraft.getMinecraft().renderEngine.bindTexture(TEX_HEART);
			}
			else if(isSin)
			{
				GL11.glColor4f(0.0F, 1.0F, 0.0F, 1.0F);
				Minecraft.getMinecraft().renderEngine.bindTexture(TEX_YUE);
			}
			else if(isPioneer)
			{
				float hue = (System.currentTimeMillis() % 21000L) / 21000.0f;
				int color = Color.HSBtoRGB(hue, 1.0f, 1.0f);

				float r = ((color >> 16) & 0xFF) / 255.0f;
				float g = ((color >> 8) & 0xFF) / 255.0f;
				float b = (color & 0xFF) / 255.0f;

				GL11.glColor4f(r, g, b, 0.5F);
				Minecraft.getMinecraft().renderEngine.bindTexture(TEX_YUE);
			}

			yuemodel.renderAll();

			GL11.glDisable(GL11.GL_BLEND);
			GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glPopMatrix();
		}
	}

	@SubscribeEvent
	public void onFOVUpdateEvent(FOVUpdateEvent evt)
	{
		ItemStack boots = evt.entity.getCurrentArmor(0);
		if (boots != null && boots.getItem() instanceof GemFeet)
		{
			evt.newfov = evt.fov - 0.4F;
		}
	}
}
