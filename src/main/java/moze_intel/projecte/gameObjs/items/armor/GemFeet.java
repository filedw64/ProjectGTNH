package moze_intel.projecte.gameObjs.items.armor;

import com.google.common.collect.Multimap;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.items.IFlightProvider;
import moze_intel.projecte.gameObjs.items.IStepAssister;
import moze_intel.projecte.utils.ChatHelper;
import moze_intel.projecte.utils.ClientKeyHelper;
import moze_intel.projecte.utils.EnumArmorType;
import moze_intel.projecte.utils.PEKeybind;

import java.util.List;

public class GemFeet extends GemArmorBase implements IFlightProvider, IStepAssister
{
	public GemFeet() {
		super(EnumArmorType.FEET);
	}

	public void toggleStepAssist(ItemStack boots, EntityPlayer player)
	{
		if (boots.stackTagCompound == null)
			boots.stackTagCompound = new NBTTagCompound();

		boolean value;
		if (boots.stackTagCompound.hasKey("StepAssist")) {
			boots.stackTagCompound.setBoolean("StepAssist", !boots.stackTagCompound.getBoolean("StepAssist"));
			value = boots.stackTagCompound.getBoolean("StepAssist");
		}
		else {
			boots.stackTagCompound.setBoolean("StepAssist", false);
			value = false;
		}

		final EnumChatFormatting e = value ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
		final String s = value ? "pe.gem.enabled" : "pe.gem.disabled";
		player.addChatMessage(new ChatComponentTranslation("pe.gem.stepassist_tooltip").appendText(" ")
			.appendSibling(ChatHelper.modifyColor(new ChatComponentTranslation(s), e)));
	}

	@Override
	public void onArmorTick(World world, EntityPlayer player, ItemStack stack)
	{
		if (!world.isRemote) {
			if (player instanceof EntityPlayerMP playerMP)
				playerMP.fallDistance = 0;
			return;
		}

		if (!player.capabilities.isFlying && PECore.proxy.isJumpPressed())
			player.motionY += 0.1D;

		// 强化：指数级消除惯性。只要松开了前后和左右移动键，立刻强力制动
		if (player.moveForward == 0 && player.moveStrafing == 0) {
			player.motionX *= 0.5D;
			player.motionZ *= 0.5D;
		}

		if (player.onGround) return;

		if (player.motionY <= 0)
			player.motionY *= 0.9D;

		if (player.moveForward < 0) {
			player.motionX *= 0.9D;
			player.motionZ *= 0.9D;
		}
		else if (player.moveForward > 0 && player.motionX * player.motionX +
			player.motionY * player.motionY + player.motionZ * player.motionZ < 3)
		{
			player.motionX *= 1.1D;
			player.motionZ *= 1.1D;
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltips, boolean unused)
	{
		tooltips.add(StatCollector.translateToLocal("pe.gem.feet.lorename"));
		tooltips.add(String.format(StatCollector.translateToLocal("pe.gem.stepassist.prompt"),
			ClientKeyHelper.getKeyName(PEKeybind.ARMOR_TOGGLE)));

		final EnumChatFormatting e = canStep(stack) ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
		final String s = canStep(stack) ? "pe.gem.enabled" : "pe.gem.disabled";
		tooltips.add(StatCollector.translateToLocal("pe.gem.stepassist_tooltip") + " "
			+ e + StatCollector.translateToLocal(s));
	}

	private boolean canStep(ItemStack stack) {
		return stack.stackTagCompound != null && stack.stackTagCompound.getBoolean("StepAssist");
	}

	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(ItemStack stack)
	{
		Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(stack);
		multimap.put(SharedMonsterAttributes.movementSpeed.getAttributeUnlocalizedName(),
			new AttributeModifier(field_111210_e, "Armor modifier", 1.0, 2));
		return multimap;
	}

	@Override
	public boolean canProvideFlight(ItemStack stack, EntityPlayerMP player) {
		return player.getCurrentArmor(0) == stack;
	}

	@Override
	public boolean canAssistStep(ItemStack stack, EntityPlayerMP player) {
		return player.getCurrentArmor(0) == stack && canStep(stack);
	}
}
