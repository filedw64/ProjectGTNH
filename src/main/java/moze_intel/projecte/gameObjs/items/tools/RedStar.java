package moze_intel.projecte.gameObjs.items.tools;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.utils.ItemHelper;
import moze_intel.projecte.utils.ToolTipHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockClay;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockGravel;
import net.minecraft.block.BlockSand;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import java.util.List;

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

		this.secondaryClasses.add("pickaxe");
		this.secondaryClasses.add("chisel");
		this.secondaryClasses.add("shovel");
		this.secondaryClasses.add("axe");

		this.secondaryClasses.add("wrench");
		this.secondaryClasses.add("wirecutter");

		for (String str : secondaryClasses)
			setHarvestLevel(str, 4);
		setHarvestLevel(pePrimaryToolClass, 4);
	}

	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase damaged, EntityLivingBase damager)
	{
		if (!damager.worldObj.isRemote) {
			damaged.hurtResistantTime = 0; // 清除无敌帧
			attackWithCharge(stack, damaged, damager, STAR_BASE_ATTACK);
		}
		return true;
	}

	@Override
	public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player)
	{
		if (player.worldObj.isRemote && getMode(stack) == 4) {
			if (stack.stackTagCompound == null)
				stack.stackTagCompound = new NBTTagCompound();
			final long lastBreak = stack.stackTagCompound.getLong("PE_LastPrecisionBreak");
			final long currentTime = player.worldObj.getTotalWorldTime();

			if (currentTime - lastBreak < 5) return true;
			stack.getTagCompound().setLong("PE_LastPrecisionBreak", currentTime);
		}
		return false;
	}

	@Override
	public boolean onBlockDestroyed(ItemStack stack, World world, Block block, int x, int y, int z, EntityLivingBase eLiving)
	{
		if (getMode(stack) != 4)
			digBasedOnMode(stack, world, block, x, y, z, eLiving);
		return true;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (world.isRemote || getMode(stack) == 4) return stack;

		if (ProjectEConfig.pickaxeAoeVeinMining)
			mineOreVeinsInAOE(stack, player);

		MovingObjectPosition mop = this.getMovingObjectPositionFromPlayer(world, player, true);
		if (mop == null) return stack;

		if (mop.typeOfHit != MovingObjectType.BLOCK) return stack;

		Block block = world.getBlock(mop.blockX, mop.blockY, mop.blockZ);

		if (block instanceof BlockGravel || block instanceof BlockClay) {
			if (ProjectEConfig.pickaxeAoeVeinMining)
				digAOE(stack, world, player, false, 0);
			else tryVeinMine(stack, player, mop);
		}
		else if (ItemHelper.isOre(block, world.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ))) {
			if (!ProjectEConfig.pickaxeAoeVeinMining)
				tryVeinMine(stack, player, mop);
		}
		else if (block instanceof BlockGrass || block instanceof BlockDirt || block instanceof BlockSand)
			digAOE(stack, world, player, false, 0);
		else digAOE(stack, world, player, true, 0);
		return stack;
	}

	@Override
	public float getDigSpeed(ItemStack stack, Block block, int metadata)
	{
		if (block == ObjHandler.matterBlock || block == ObjHandler.dmFurnaceOff || block == ObjHandler.dmFurnaceOn
			|| block == ObjHandler.rmFurnaceOff || block == ObjHandler.rmFurnaceOn)
		{
			return 1200000.0F;
		}

		return super.getDigSpeed(stack, block, metadata) + 48.0F;
	}

	// ==================== 神器彩字特效区 ====================

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean b)
	{
		super.addInformation(stack, player, list, b);
		list.add("");
		list.add(EnumChatFormatting.BLUE + "+ " + ToolTipHelper.getRainbowGlitch(9) + EnumChatFormatting.BLUE + " 挖掘速度");
		list.add(EnumChatFormatting.BLUE + "+ " + ToolTipHelper.getRainbowGlitch(4) + EnumChatFormatting.BLUE + " 挖掘等级");
	}

	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(ItemStack stack)
	{
		/*if (ProjectEConfig.useOldDamage)
		{
			return super.getAttributeModifiers(stack);
		}

		byte charge = stack.stackTagCompound == null ? 0 : getCharge(stack);
		float damage = STAR_BASE_ATTACK + charge;

		Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(stack);
		multimap.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(), new AttributeModifier(field_111210_e, "Weapon modifier", damage, 0));
		return multimap;*/
		// 屏蔽原版属性面板
		return HashMultimap.create();
	}
}
