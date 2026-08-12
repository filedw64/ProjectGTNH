package moze_intel.projecte.gameObjs.items.tools;

import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import net.minecraft.block.Block;
import net.minecraft.block.BlockClay;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockGravel;
import net.minecraft.block.BlockSand;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.utils.ItemHelper;

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

	@Override
	public Set<String> getToolClasses(ItemStack stack)
	{
		Set<String> classes = super.getToolClasses(stack);
		Set<String> result = Sets.newHashSet();

		if (classes != null)
		{
			result.addAll(classes);
		}

		// Config 拦截
		if (ProjectEConfig.redStarCamouflage)
		{
			result.addAll(this.secondaryClasses);
		}

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
		if (getMode(stack) == 4)
		{
			// 仅在客户端(Remote)执行冷却拦截。
			// 避免服务端与客户端Tick不同步导致服务端拒绝挖掘（即产生幽灵方块现象）
			if (player.worldObj.isRemote)
			{
				if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());

				long lastBreak = stack.getTagCompound().getLong("PE_LastPrecisionBreak");
				long currentTime = player.worldObj.getTotalWorldTime();

				if (currentTime - lastBreak < 5)
				{
					return true; // 拦截客户端的挖掘请求，不向服务端发包
				}
				stack.getTagCompound().setLong("PE_LastPrecisionBreak", currentTime);
			}
		}
		return false; // 服务端永远允许挖掘请求
	}

	@Override
	public boolean onBlockDestroyed(ItemStack stack, World world, Block block, int x, int y, int z, EntityLivingBase eLiving)
	{
		// 如果不是精确挖掘模式，触发 AOE 破坏
		if (getMode(stack) != 4)
		{
			digBasedOnMode(stack, world, block, x, y, z, eLiving);
		}
		return true;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		if (!world.isRemote)
		{
			// 精确挖掘模式禁用所有右键大范围功能
			if (getMode(stack) == 4)
			{
				return stack;
			}

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
		// 物质解离速度
		return Float.MAX_VALUE;
	}

	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(ItemStack stack)
	{
		byte charge = stack.stackTagCompound == null ? 0 : getCharge(stack);
		float damage = 500.0F * (charge + 1);

		Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(stack);
		multimap.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(), new AttributeModifier(field_111210_e, "Weapon modifier", damage, 0));
		return multimap;
	}
}
