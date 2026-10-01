package moze_intel.projecte.integration.ae2;

import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.MEEMCLinkPKT;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@SideOnly(Side.CLIENT)
public class GuiMEEMCLink extends GuiContainer {

	private static final ResourceLocation TEXTURE = new ResourceLocation("projecte", "textures/gui/me_emc_link.png");
	private final ContainerMEEMCLink container;
	private GuiButton btnAccess, btnFilter, btnPrecision, btnClaim;

	public GuiMEEMCLink(InventoryPlayer playerInv, TileMEEMCLink tile) {
		super(new ContainerMEEMCLink(playerInv, tile));
		this.container = (ContainerMEEMCLink) inventorySlots;
		this.xSize = 176;
		this.ySize = 184;
	}

	@Override public void initGui() {
		super.initGui();
		buttonList.clear();
		buttonList.add(new GuiButton(0, guiLeft + 8, guiTop + 28, 16, 13, "-10"));
		buttonList.add(new GuiButton(1, guiLeft + 25, guiTop + 28, 14, 13, "-1"));
		buttonList.add(new GuiButton(2, guiLeft + 40, guiTop + 28, 14, 13, "+1"));
		buttonList.add(new GuiButton(3, guiLeft + 55, guiTop + 28, 16, 13, "+10"));
		btnAccess = new GuiButton(4, guiLeft + 8, guiTop + 43, 64, 13, getAccessModeText()); buttonList.add(btnAccess);
		btnFilter = new GuiButton(5, guiLeft + 8, guiTop + 57, 64, 13, getFilterModeText()); buttonList.add(btnFilter);
		btnPrecision = new GuiButton(7, guiLeft + 8, guiTop + 71, 64, 13, getPrecisionText()); buttonList.add(btnPrecision);
		btnClaim = new GuiButton(6, guiLeft + 8, guiTop + 85, 64, 13, "Link / Claim"); buttonList.add(btnClaim);
	}

	private String getAccessModeText() {
		int mode = container.getAccessMode();
		if (mode == 0) return EnumChatFormatting.GREEN + "Read/Write";
		if (mode == 1) return EnumChatFormatting.YELLOW + "Read Only";
		return EnumChatFormatting.RED + "Write Only";
	}

	private String getFilterModeText() {
		int mode = container.getFilterMode();
		if (mode == 0) return EnumChatFormatting.GRAY + "All Items";
		if (mode == 1) return EnumChatFormatting.AQUA + "Whitelist";
		return EnumChatFormatting.GOLD + "Blacklist";
	}

	private String getPrecisionText() {
		int prec = container.getFilterPrecision();
		if (prec == 0) return EnumChatFormatting.WHITE + "Exact";
		if (prec == 1) return EnumChatFormatting.YELLOW + "Fuzzy";
		return EnumChatFormatting.LIGHT_PURPLE + "OreDict";
	}

	@Override protected void actionPerformed(GuiButton btn) {
		int x = 0, y = 0, z = 0;
		if (container.getTile() != null) {
			x = container.getTile().xCoord; y = container.getTile().yCoord; z = container.getTile().zCoord;
		}
		if (btn.id >= 0 && btn.id <= 3) {
			int delta = btn.id == 0 ? -10 : btn.id == 1 ? -1 : btn.id == 2 ? 1 : 10;
			PacketHandler.sendToServer(new MEEMCLinkPKT(x, y, z, 0, container.getPriority() + delta));
		} else if (btn.id == 4) PacketHandler.sendToServer(new MEEMCLinkPKT(x, y, z, 1, (container.getAccessMode() + 1) % 3));
		else if (btn.id == 5) PacketHandler.sendToServer(new MEEMCLinkPKT(x, y, z, 2, (container.getFilterMode() + 1) % 3));
		else if (btn.id == 6) PacketHandler.sendToServer(new MEEMCLinkPKT(x, y, z, 3, 0));
		else if (btn.id == 7) PacketHandler.sendToServer(new MEEMCLinkPKT(x, y, z, 4, (container.getFilterPrecision() + 1) % 3));
	}

	@Override public void updateScreen() {
		super.updateScreen();
		if (btnAccess != null) btnAccess.displayString = getAccessModeText();
		if (btnFilter != null) btnFilter.displayString = getFilterModeText();
		if (btnPrecision != null) btnPrecision.displayString = getPrecisionText();
	}

	@Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
		fontRendererObj.drawString("ME EMC Link", 8, 6, 0x404040);
		fontRendererObj.drawString("Pri: " + container.getPriority(), 8, 17, 0x555555);
		String owner = container.getOwnerName();
		fontRendererObj.drawString("Owner: " + (owner.isEmpty() ? EnumChatFormatting.RED + "Unbound" : EnumChatFormatting.DARK_BLUE + (owner.length() > 10 ? owner.substring(0, 8) + ".." : owner)), 78, 6, 0x404040);

		int relX = mouseX - guiLeft, relY = mouseY - guiTop;
		List<String> tooltip = new ArrayList<>();

		if (relX >= 8 && relX <= 72 && relY >= 17 && relY <= 41) tooltip.add("Storage Priority: " + container.getPriority());
		else if (relX >= 8 && relX <= 72 && relY >= 43 && relY <= 56) tooltip.add("Access Mode");
		else if (relX >= 8 && relX <= 72 && relY >= 57 && relY <= 70) tooltip.add("Filter Mode");
		else if (relX >= 8 && relX <= 72 && relY >= 71 && relY <= 84) tooltip.add("Match Precision");
		else if (relX >= 8 && relX <= 72 && relY >= 85 && relY <= 98) tooltip.add("Claim Ownership");

		if (!tooltip.isEmpty()) drawHoveringText(tooltip, relX, relY, fontRendererObj);
	}

	@Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
		mc.getTextureManager().bindTexture(TEXTURE);
		drawTexturedModalRect((width - xSize) / 2, (height - ySize) / 2, 0, 0, xSize, ySize);
	}
}
