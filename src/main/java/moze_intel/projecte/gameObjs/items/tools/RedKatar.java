package moze_intel.projecte.gameObjs.items.tools;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.api.item.IExtraFunction;

import java.util.List;

public class RedKatar extends PEToolBase implements IExtraFunction
{
	public RedKatar()
	{
		super("rm_katar", (byte)4, new String[] {
			StatCollector.translateToLocal("pe.katar.mode1"), StatCollector.translateToLocal("pe.katar.mode2"),
		});
		this.setNoRepair();
		this.peToolMaterial = "rm_tools";
		this.pePrimaryToolClass = "katar";
		this.harvestMaterials.add(Material.wood);
		this.harvestMaterials.add(Material.web);
		this.harvestMaterials.add(Material.cloth);
		this.harvestMaterials.add(Material.plants);
		this.harvestMaterials.add(Material.leaves);
		this.harvestMaterials.add(Material.vine);

		this.secondaryClasses.add("sword");
		this.secondaryClasses.add("axe");
		this.secondaryClasses.add("shears");
	}

	private void checkAndApplyLooting(ItemStack stack)
	{
		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());

		if (!stack.getTagCompound().getBoolean("AutoLootingApplied"))
		{
			stack.getTagCompound().setBoolean("AutoLootingApplied", true);
			if (stack.getTagCompound().hasKey("ench", 9))
			{
				NBTTagList enchants = stack.getTagCompound().getTagList("ench", 10);
				for (int i = enchants.tagCount() - 1; i >= 0; i--)
				{
					if (enchants.getCompoundTagAt(i).getShort("id") == Enchantment.looting.effectId)
					{
						enchants.removeTag(i);
					}
				}
			}
			stack.addEnchantment(Enchantment.looting, 10);
		}
	}

	@Override
	public void onCreated(ItemStack stack, World world, EntityPlayer player)
	{
		super.onCreated(stack, world, player);
		checkAndApplyLooting(stack);
	}

	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase damaged, EntityLivingBase damager)
	{
		checkAndApplyLooting(stack);
		if (!damager.worldObj.isRemote)
		{
			damaged.hurtResistantTime = 0;
			damaged.attackEntityFrom(DamageSource.outOfWorld, 1000.0F * (getCharge(stack) + 1));
		}
		return true;
	}

	@Override
	public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player)
	{
		checkAndApplyLooting(stack);
		shearBlock(stack, x, y, z, player);
		return false;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		checkAndApplyLooting(stack);
		player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
		if (world.isRemote) return stack;

		MovingObjectPosition mop = this.getMovingObjectPositionFromPlayer(world, player, false);
		if (mop != null)
		{
			if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
			{
				Block blockHit = world.getBlock(mop.blockX, mop.blockY, mop.blockZ);
				if (blockHit instanceof BlockGrass || blockHit instanceof BlockDirt) {
					tillAOE(stack, player, world, mop.blockX, mop.blockY, mop.blockZ, world.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ), 0);
				} else if (blockHit instanceof BlockLog) {
					clearOdAOE(world, stack, player, "logWood", 0);
				} else if (blockHit instanceof BlockLeaves) {
					clearOdAOE(world, stack, player, "treeLeaves", 0);
				}
			}
		}
		else
		{
			shearEntityAOE(stack, player, 0);
		}
		return stack;
	}

	@Override
	public void doExtraFunction(ItemStack stack, EntityPlayer player)
	{
		checkAndApplyLooting(stack);
		attackAOE(stack, player, getMode(stack) == 1, 10000.0F, 0);
	}

	@Override
	public EnumAction getItemUseAction(ItemStack par1ItemStack)
	{
		return EnumAction.block;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack par1ItemStack)
	{
		return 72000;
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
			// 交替颜色 + 混淆乱码 + 占位符
			sb.append(colors[i % colors.length]).append(EnumChatFormatting.OBFUSCATED).append("X");
		}
		return sb.toString();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean b)
	{
		super.addInformation(stack, player, list, b);
		// 伪装成原版的蓝色属性面板
		list.add(""); // 留一行空行，和原版格式保持一致
		list.add(EnumChatFormatting.BLUE + "+ " + getRainbowGlitch(12) + EnumChatFormatting.BLUE + " 伤害");
	}

	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(ItemStack stack)
	{
		// 返回空的 Multimap，彻底屏蔽掉原版的枯燥面板
		return HashMultimap.create();
	}
}
