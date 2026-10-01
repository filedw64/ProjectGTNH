package moze_intel.projecte.gameObjs.items.tools;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.entity.EntityPEArrow;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.ArrowNockEvent;

public abstract class PEBowBase extends ItemBow {
	@SideOnly(Side.CLIENT)
	private IIcon[] pullIcons;

	public PEBowBase(String unlocalizedName) {
		this.setUnlocalizedName(PECore.MODID + "_" + unlocalizedName);
		this.setMaxDamage(0);
	}

	// 属性定义方法由子类实现
	public abstract float getVelocityMultiplier();
	public abstract int getDrawTime();
	public abstract int getOverlayColor();
	public abstract boolean ignoresGravity();
	public abstract boolean spawnsLightning();
	public abstract int getEmcCost(); // 新增：每次射击的EMC消耗

	@Override
	public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int useTime) {
		int charge = this.getMaxItemUseDuration(stack) - useTime;

		ArrowLooseEvent event = new ArrowLooseEvent(player, stack, charge);
		MinecraftForge.EVENT_BUS.post(event);
		if (event.isCanceled()) return;
		charge = event.charge;

		float f = (float) charge / (float) getDrawTime();
		f = (f * f + f * 2.0F) / 3.0F;
		if (f < 0.1D) return;
		if (f > 1.0F) f = 1.0F;

		// 扣除 EMC (创造模式不扣除)
		if (!world.isRemote && !player.capabilities.isCreativeMode) {
			// 调用 ProjectE 的 EMCHelper 扣除玩家身上的 EMC (如炼金煤炭、卡莱恩之星)
			double consumed = EMCHelper.consumePlayerFuel(player, getEmcCost());
			// 如果返回的消耗量小于需要的量（或者为-1代表失败），则说明EMC不足
			if (consumed < getEmcCost()) {
				return; // EMC 不足，取消射击
			}
		}

		EntityPEArrow arrow = new EntityPEArrow(world, player, f * 2.0F * getVelocityMultiplier());
		arrow.setIgnoreGravity(ignoresGravity());
		arrow.setSpawnLightning(spawnsLightning());

		if (f == 1.0F) arrow.setIsCritical(true);

		// 处理附魔
		int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
		if (power > 0) arrow.setDamage(arrow.getDamage() + (double) power * 0.5D + 0.5D);

		int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack);
		if (punch > 0) arrow.setKnockbackStrength(punch);

		if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) > 0) arrow.setFire(100);

		world.playSoundAtEntity(player, "random.bow", 1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + f * 0.5F);

		// 设置为无法拾取，因为不再消耗实体箭矢
		arrow.canBePickedUp = 0;

		if (!world.isRemote) world.spawnEntityInWorld(arrow);
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		ArrowNockEvent event = new ArrowNockEvent(player, stack);
		MinecraftForge.EVENT_BUS.post(event);
		if (event.isCanceled()) return event.result;

		// 不再判断背包是否有箭矢，直接允许拉弓
		player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
		return stack;
	}

	@Override
	public EnumAction getItemUseAction(ItemStack stack) {
		return EnumAction.bow;
	}

	@Override
	public int getItemEnchantability() {
		return 20; // 让弓在附魔台更容易出高级附魔
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register) {
		this.itemIcon = register.registerIcon("bow_standby");
		this.pullIcons = new IIcon[3];
		this.pullIcons[0] = register.registerIcon("bow_pulling_0");
		this.pullIcons[1] = register.registerIcon("bow_pulling_1");
		this.pullIcons[2] = register.registerIcon("bow_pulling_2");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(ItemStack stack, int renderPass, EntityPlayer player, ItemStack usingItem, int useRemaining) {
		if (usingItem != null) {
			int time = usingItem.getMaxItemUseDuration() - useRemaining;
			float ratio = (float) time / (float) getDrawTime();
			if (ratio >= 0.9F) return pullIcons[2];
			if (ratio > 0.65F) return pullIcons[1];
			if (ratio > 0.0F) return pullIcons[0];
		}
		return this.itemIcon;
	}

	@Override
	public boolean requiresMultipleRenderPasses() {
		return true;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
		return this.itemIcon;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public int getColorFromItemStack(ItemStack stack, int pass) {
		return pass == 1 ? getOverlayColor() : 0xFFFFFF;
	}
}
