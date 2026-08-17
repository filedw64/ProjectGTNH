package moze_intel.projecte.gameObjs.items.tools;

import com.google.common.collect.Multimap;
import moze_intel.projecte.api.item.IExtraFunction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

public class RedKatar extends PEToolBase implements IExtraFunction
{
	public RedKatar() {
		super("rm_katar", (byte)4, new String[] {
			StatCollector.translateToLocal("pe.katar.mode1"),
			StatCollector.translateToLocal("pe.katar.mode2")
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
		this.secondaryClasses.add("hoe");
		this.secondaryClasses.add("shears");
	}

	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase damaged, EntityLivingBase damager)
	{
		if (!damager.worldObj.isRemote) {
			damaged.hurtResistantTime = 0; // 清除无敌帧
			attackWithCharge(stack, damaged, damager, KATAR_BASE_ATTACK);
		}
		return true;
	}

	@Override
	public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player)
	{
		shearBlock(stack, x, y, z, player);
		return false;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player)
	{
		player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
		if (world.isRemote) return stack;

		MovingObjectPosition mop = this.getMovingObjectPositionFromPlayer(world, player, false);
		if (mop != null) {
			if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK)
			{
				Block blockHit = world.getBlock(mop.blockX, mop.blockY, mop.blockZ);
				if (blockHit instanceof BlockGrass || blockHit instanceof BlockDirt) {
					tillAOE(stack, player, world, mop.blockX, mop.blockY, mop.blockZ,
						world.getBlockMetadata(mop.blockX, mop.blockY, mop.blockZ), 0);
				}
				else if (blockHit instanceof BlockLog)
					clearOdAOE(world, stack, player, "logWood", 0);
				else if (blockHit instanceof BlockLeaves)
					clearOdAOE(world, stack, player, "treeLeaves", 0);
			}
		}
		else shearEntityAOE(stack, player, 0);
		return stack;
	}

	@Override
	public void doExtraFunction(ItemStack stack, EntityPlayer player) {
		attackAOE(stack, player, getMode(stack) == 1, Float.MAX_VALUE, 0);
	}

	@Override
	public EnumAction getItemUseAction(ItemStack par1ItemStack) {
		return EnumAction.block;
	}

	@Override
	public int getMaxItemUseDuration(ItemStack par1ItemStack) {
		return 72000;
	}

	@Override
	public Multimap<String, AttributeModifier> getAttributeModifiers(ItemStack stack)
	{
		byte charge = stack.stackTagCompound == null ? 0 : getCharge(stack);
		float damage = KATAR_BASE_ATTACK + charge; // Sword

		Multimap<String, AttributeModifier> multimap = super.getAttributeModifiers(stack);
		multimap.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(), new AttributeModifier(field_111210_e, "Weapon modifier", damage, 0));
		return multimap;
	}
}
