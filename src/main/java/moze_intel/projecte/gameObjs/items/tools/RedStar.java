package moze_intel.projecte.gameObjs.items.tools;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockClay;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockGravel;
import net.minecraft.block.BlockSand;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.utils.ItemHelper;

import java.util.List;
import java.util.Set;

public class RedStar extends PEToolBase
{
	public RedStar()
	{
		super("rm_morning_star", (byte) 4, new String[]{
			StatCollector.translateToLocal("pe.morningstar.mode1"),
			StatCollector.translateToLocal("pe.morningstar.mode2"),
			StatCollector.translateToLocal("pe.morningstar.mode3"),
			StatCollector.translateToLocal("pe.morningstar.mode4"),
			EnumChatFormatting.RED + "精确挖掘 (Precision)"
		});
		this.setNoRepair();
		this.peToolMaterial = "rm_tools";
		this.pePrimaryToolClass = "morning_star";

		this.harvestMaterials.add(Material.grass);
		this.harvestMaterials.add(Material.ground);
		this.harvestMaterials.add(Material.sand);
		this.harvestMaterials.add(Material.snow);
		this.harvestMaterials.add(Material.clay);
		this.harvestMaterials.add(Material.iron);
		this.harvestMaterials.add(Material.anvil);
		this.harvestMaterials.add(Material.rock);
		this.harvestMaterials.add(Material.wood);
		this.harvestMaterials.add(Material.plants);
		this.harvestMaterials.add(Material.vine);

		this.secondaryClasses.addAll(Sets.newHashSet(
			"pickaxe", "shovel", "axe", "sword", "hoe", "wrench", "wirecutter"
		));
	}

	private void checkAndApplyFortune(ItemStack stack)
	{
		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());

		if (!stack.getTagCompound().getBoolean("AutoFortuneApplied"))
		{
			stack.getTagCompound().setBoolean("AutoFortuneApplied", true);
			if (stack.getTagCompound().hasKey("ench", 9))
			{
				NBTTagList enchants = stack.getTagCompound().getTagList("ench", 10);
				for (int i = enchants.tagCount() - 1; i >= 0; i--)
				{
					if (enchants.getCompoundTagAt(i).getShort("id") == Enchantment.fortune.effectId)
					{
						enchants.removeTag(i);
					}
				}
			}
			stack.addEnchantment(Enchantment.fortune, 10);
		}
	}

	@Override
	public void onCreated(ItemStack stack, World world, EntityPlayer player)
	{
		super.onCreated(stack, world, player);
		checkAndApplyFortune(stack);
	}

	@Override
	public Set<String> getToolClasses(ItemStack stack)
	{
		Set<String> classes = super.getToolClasses(stack);
		Set<String> result = Sets.newHashSet();

		if (classes != null) result.addAll(classes);
		if (ProjectEConfig.redStarCamouflage) result.addAll(this.secondaryClasses);

		result.add(this.pePrimaryToolClass);
		return result;
	}

	@Override
	public int getHarvestLevel(ItemStack stack, String toolClass)
	{
		return 100;
	}

	@Override
	public boolean canHarvestBlock(Block block, ItemStack stack)
	{
		return true;
	}

	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase damaged, EntityLivingBase damager)
	{
		checkAndApplyFortune(stack);
		if (!damager.worldObj.isRemote)
		{
			damaged.hurtResistantTime = 0;
			damaged.attackEntityFrom(DamageSource.generic, 500.0F * (getCharge(stack) + 1));
		}
		return true;
	}

	@Override
	public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player)
	{
		checkAndApplyFortune(stack);

		if (getMode(stack) == 4)
		{
			if (player.worldObj.isRemote)
			{
				long lastBreak = stack.getTagCompound().getLong("PE_LastPrecisionBreak");
				long currentTime = player.worldObj.getTotalWorldTime();

				if (currentTime - lastBreak < 5) return true;
				stack.getTagCompound().setLong("PE_LastPrecisionBreak", currentTime);
			}
		}
		return false;
	}

	@Override
	public boolean onBlockDestroyed(ItemStack stack, World world, Block block, int x, int y, int z, EntityLivingBase eLiving)
	{
		checkAndApplyFortune(stack);
		if (getMode(stack) != 4) digBasedOnMode(stack, world, block, x, y, z, eLiving);
		return true;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		checkAndApplyFortune(stack);
		if (!world.isRemote)
		{
			if (getMode(stack) == 4) return stack;

			if (ProjectEConfig.pickaxeAoeVeinMining) mineOreVeinsInAOE(stack, player);

			MovingObjectPosition mop = this.getMovingObjectPositionFromPlayer(world, player, true);
			if (mop == null) return stack;

			if (mop.typeOfHit == MovingObjectType.BLOCK)
			{
				Block block = world.getBlock(mop.blockX, mop.blockY, mop.blockZ);

				if (block instanceof BlockGravel || block instanceof BlockClay) {
					if (ProjectEConfig.pickaxeAoeVeinMining) digAOE(stack, world, player, false, 0);
					else tryVeinMine(stack, player, mop);
				}
				else if (ItemHelper.isOre(block, world.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ))) {
					if (!ProjectEConfig.pickaxeAoeVeinMining) tryVeinMine(stack, player, mop);
				}
				else if (block instanceof BlockGrass || block instanceof BlockDirt || block instanceof BlockSand) {
					digAOE(stack, world, player, false, 0);
				}
				else {
					digAOE(stack, world, player, true, 0);
				}
			}
		}
		return stack;
	}

	@Override
	public float getDigSpeed(ItemStack stack, Block block, int metadata)
	{
		return Float.MAX_VALUE;
	}

	// ==================== 神器彩字特效区 ====================

	@SideOnly(Side.CLIENT)
	private String getRainbowGlitch(int length)
	{
		StringBuilder sb = new StringBuilder();
		EnumChatFormatting[] colors = {
			EnumChatFormatting.RED, EnumChatFormatting.GOLD, EnumChatFormatting.YELLOW,
			EnumChatFormatting.GREEN, EnumChatFormatting.AQUA, EnumChatFormatting.BLUE,
			EnumChatFormatting.LIGHT_PURPLE
		};
		for (int i = 0; i < length; i++) {
			sb.append(colors[i % colors.length]).append(EnumChatFormatting.OBFUSCATED).append("X");
		}
		return sb.toString();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean b)
	{
		super.addInformation(stack, player, list, b);
		list.add("");
		list.add(EnumChatFormatting.BLUE + "+ " + getRainbowGlitch(9) + EnumChatFormatting.BLUE + " 挖掘速度");
		list.add(EnumChatFormatting.BLUE + "+ " + getRainbowGlitch(4) + EnumChatFormatting.BLUE + " 挖掘等级");
	}

	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(ItemStack stack)
	{
		// 屏蔽原版属性面板
		return HashMultimap.create();
	}
}
