package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import moze_intel.projecte.api.item.IItemEmc;

import java.util.List;

public class ItemExpansionStar extends ItemPE implements IItemEmc {
	@SideOnly(Side.CLIENT)
	private IIcon[] icons;

	private final String starName;
	private final int recipeMultiplier;
	private final double baseEmcCapacity;

	public ItemExpansionStar(String name, int recipeMultiplier, double baseEmcCapacity) {
		this.starName = name;
		this.recipeMultiplier = recipeMultiplier;
		this.baseEmcCapacity = baseEmcCapacity;

		this.setUnlocalizedName(name);
		this.setMaxDamage(0);
		this.setHasSubtypes(true);
		this.setMaxStackSize(1);
		this.setNoRepair();
	}

	@Override
	public boolean showDurabilityBar(ItemStack stack) {
		return stack.hasTagCompound();
	}

	@Override
	public double getDurabilityForDisplay(ItemStack stack) {
		double starEmc = getEmc(stack);
		if (starEmc == 0) {
			return 1.0;
		}
		return 1.0 - starEmc / getMaximumEmc(stack);
	}

	public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5)
	{
		if (!stack.hasTagCompound())
		{
			stack.stackTagCompound = new NBTTagCompound();
		}
	}

	@Override
	public String getUnlocalizedName(ItemStack stack) {
		if (stack.getItemDamage() > 5) {
			return "pe.debug.metainvalid";
		}
		return super.getUnlocalizedName() + "_" + (stack.getItemDamage() + 1);
	}

	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs cTab, List<ItemStack> list) {
		for (int i = 0; i < 6; ++i) {
			list.add(new ItemStack(item, 1, i));
		}
	}

	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamage(int par1) {
		return icons[MathHelper.clamp_int(par1, 0, 5)];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register) {
		icons = new IIcon[6];
		for (int i = 0; i < 6; i++) {
			// 材质路径映射
			icons[i] = register.registerIcon(this.getTexture("stars", starName + "_" + (i + 1)));
		}
	}

	// -- IItemEmc -- //

	@Override
	public double addEmc(ItemStack stack, double toAdd) {
		double add = Math.min(getMaximumEmc(stack) - getStoredEmc(stack), toAdd);
		ItemPE.addEmcToStack(stack, add);
		return add;
	}

	@Override
	public double extractEmc(ItemStack stack, double toRemove) {
		double sub = Math.min(getStoredEmc(stack), toRemove);
		ItemPE.removeEmc(stack, sub);
		return sub;
	}

	@Override
	public double getStoredEmc(ItemStack stack) {
		return ItemPE.getEmc(stack);
	}

	@Override
	public double getMaximumEmc(ItemStack stack) {
		int meta = MathHelper.clamp_int(stack.getItemDamage(), 0, 5);
		// 容量 = 基础容量 * (合成倍率 ^ 阶级数)
		return this.baseEmcCapacity * Math.pow(this.recipeMultiplier, meta);
	}
}
