package moze_intel.projecte.gameObjs.items.tools;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.gameObjs.entity.EntityPEArrow;
import moze_intel.projecte.playerData.Transmutation;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.ArrowNockEvent;

import java.util.List;

public abstract class PEBowBase extends ItemBow {
	@SideOnly(Side.CLIENT)
	private IIcon[] pullIcons;
	@SideOnly(Side.CLIENT)
	private IIcon overlayIcon;
	@SideOnly(Side.CLIENT)
	private IIcon[] overlayPullIcons;

	public PEBowBase(String unlocalizedName) {
		this.setUnlocalizedName("pe_" + unlocalizedName);
		this.setMaxDamage(0);
		this.setNoRepair();
	}

	// 属性定义方法由子类实现
	public abstract float getVelocityMultiplier();
	public abstract int getDrawTime();
	public abstract boolean ignoresGravity();
	public abstract boolean spawnsLightning();
	public abstract int getEmcCost(); // 每次射击满蓄力的EMC消耗
	public abstract int getHomingTier(); // 0 = 不追踪
	public abstract String getSpecialKey(); // 特殊能力描述的翻译键
	public abstract int getAbilityTier(); // 自带效果梯度 n：暗物质 1、红物质 2、风暴 3
	public abstract int getOverlayColor(); // 罩层颜色 0xRRGGBB

	// 自带效果数值：抢夺 n、冲击 2n-1、力量 2n+1
	public int getPowerLevel() { return getAbilityTier() * 2 + 1; }
	public int getPunchLevel() { return getAbilityTier() * 2 - 1; }
	public int getLootingLevel() { return getAbilityTier(); }

	@Override
	public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int useTime) {
		int charge = this.getMaxItemUseDuration(stack) - useTime;

		ArrowLooseEvent event = new ArrowLooseEvent(player, stack, charge);
		MinecraftForge.EVENT_BUS.post(event);
		if (event.isCanceled()) return;
		charge = event.charge;

		float f = (float) charge / (float) getDrawTime();
		f = (f * f + f * 2.0F) / 3.0F;
		if (f < 0.1F) return;
		if (f > 1.0F) f = 1.0F;

		// 按蓄力比例扣除使用者 EMC 网络中的余额，创造模式跳过
		if (!world.isRemote && !player.capabilities.isCreativeMode) {
			int cost = (int) Math.ceil(getEmcCost() * f);
			double currentEmc = Transmutation.getEmc(player);
			if (currentEmc < cost) {
				player.addChatMessage(new ChatComponentTranslation("pe.bow.no_emc"));
				return;
			}
			Transmutation.setEmc(player, currentEmc - cost);
			if (player instanceof EntityPlayerMP) {
				Transmutation.sync(player);
			}
		}

		world.playSoundAtEntity(player, "random.bow", 1.0F, 1.0F / (itemRand.nextFloat() * 0.4F + 1.2F) + f * 0.5F);

		// 实体箭只由服务端生成
		if (world.isRemote) return;

		EntityPEArrow arrow = new EntityPEArrow(world, player, f * 2.0F * getVelocityMultiplier());
		arrow.setIgnoreGravity(ignoresGravity());
		arrow.setSpawnLightning(spawnsLightning());
		arrow.setHomingTier(getHomingTier());

		// 满蓄力
		if (f >= 1.0F) arrow.setIsCritical(true);

		// 力量/冲击只由弓自带梯度决定，忽略弓上的对应附魔
		arrow.setDamage(arrow.getDamage() + (double) getPowerLevel() * 0.5D + 0.5D);
		arrow.setKnockbackStrength(getPunchLevel());
		arrow.setLootingLevel(getLootingLevel());

		if (EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) > 0) arrow.setFire(100);

		// 设置为无法拾取，因为不再消耗实体箭矢
		arrow.canBePickedUp = 0;

		world.spawnEntityInWorld(arrow);
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		ArrowNockEvent event = new ArrowNockEvent(player, stack);
		MinecraftForge.EVENT_BUS.post(event);
		if (event.isCanceled()) return stack;

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
		// pass0 用原版弓贴图（无 domain 前缀即 minecraft），pass1 用本 MOD 的罩层贴图
		this.itemIcon = register.registerIcon("bow_standby");
		this.pullIcons = new IIcon[3];
		this.pullIcons[0] = register.registerIcon("bow_pulling_0");
		this.pullIcons[1] = register.registerIcon("bow_pulling_1");
		this.pullIcons[2] = register.registerIcon("bow_pulling_2");

		this.overlayIcon = register.registerIcon("projecte:bow_overlay_standby");
		this.overlayPullIcons = new IIcon[3];
		this.overlayPullIcons[0] = register.registerIcon("projecte:bow_overlay_pulling_0");
		this.overlayPullIcons[1] = register.registerIcon("projecte:bow_overlay_pulling_1");
		this.overlayPullIcons[2] = register.registerIcon("projecte:bow_overlay_pulling_2");
	}

	@Override
	public boolean requiresMultipleRenderPasses() { return true; }

	@Override
	public int getRenderPasses(int metadata) { return 2; }

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
		return pass == 1 ? this.overlayIcon : this.itemIcon;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public int getColorFromItemStack(ItemStack stack, int pass) {
		return pass == 1 ? getOverlayColor() : 0xFFFFFF;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(ItemStack stack, int renderPass, EntityPlayer player, ItemStack usingItem, int useRemaining) {
		// idx: -1 = 没在拉弓（用 standby 图），0..2 = 拉弓帧；两个 pass 共用同一帧下标
		int idx = -1;
		if (usingItem != null) {
			int time = usingItem.getMaxItemUseDuration() - useRemaining;
			float ratio = (float) time / (float) getDrawTime();
			if (ratio >= 0.9F) idx = 2;
			else if (ratio > 0.65F) idx = 1;
			else if (ratio > 0.0F) idx = 0;
		}

		if (renderPass == 1) return idx < 0 ? this.overlayIcon : this.overlayPullIcons[idx];
		return idx < 0 ? this.itemIcon : this.pullIcons[idx];
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> list, boolean advanced) {
		list.add(StatCollector.translateToLocalFormatted("pe.bow.tooltip1", getEmcCost()));
		list.add(StatCollector.translateToLocalFormatted("pe.bow.tooltip2", getDrawTime()));
		list.add(StatCollector.translateToLocalFormatted("pe.bow.tooltip3", StatCollector.translateToLocal(getSpecialKey())));
		list.add(StatCollector.translateToLocalFormatted("pe.bow.tooltip4", getLootingLevel(), getPunchLevel(), getPowerLevel()));
	}
}
