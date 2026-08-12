package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;

import java.util.List;

public class AlchemicalFuel extends ItemPE
{
	// 提取为静态常量，节省内存
	private static final String[] NAMES = new String[] {"alchemical_coal", "mobius", "aeternalis"};

	@SideOnly(Side.CLIENT)
	private IIcon[] icons;

	public AlchemicalFuel()
	{
		this.setUnlocalizedName("fuel");
		this.setHasSubtypes(true);
		this.setMaxDamage(0);
	}

	@Override
	public String getUnlocalizedName(ItemStack stack)
	{
		int meta = stack.getItemDamage();
		// 更简洁的越界检查
		if (meta < 0 || meta >= NAMES.length)
		{
			return "pe.debug.metainvalid";
		}
		return super.getUnlocalizedName() + "_" + NAMES[meta];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void getSubItems(Item item, CreativeTabs cTab, List<ItemStack> list)
	{
		for (int i = 0; i < NAMES.length; ++i)
			list.add(new ItemStack(item, 1, i));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamage(int par1)
	{
		return icons[MathHelper.clamp_int(par1, 0, NAMES.length - 1)];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register)
	{
		icons = new IIcon[NAMES.length];
		for (int i = 0; i < NAMES.length; i++)
			icons[i] = register.registerIcon(this.getTexture("fuels", NAMES[i]));
	}
}
