package moze_intel.projecte.gameObjs.items.rings;

import com.google.common.collect.Lists;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.api.item.IPedestalItem;
import moze_intel.projecte.gameObjs.tiles.DMPedestalTile;
import moze_intel.projecte.utils.WorldHelper;

import java.util.List;

public class MindStone extends RingToggle implements IPedestalItem
{
	private final int TRANSFER_RATE = 50;

	public MindStone() {
		super("mind_stone");
		this.setNoRepair();
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int par4, boolean par5) {
		if (world.isRemote || par4 > 8 || !(entity instanceof EntityPlayer player))
			return;

		super.onUpdate(stack, world, entity, par4, par5);

		if (stack.getItemDamage() == 0) return;

		if (!canStore(stack)) {
			this.changeMode(player, stack);
			return;
		}

		if (getXP(player) > 0) {
			int toAdd = Math.min(getXP(player), TRANSFER_RATE);
			addStoredXP(stack, toAdd);
			removeXP(player, TRANSFER_RATE);
		}
	}

	// 修复：对空气潜行右键，无视开关状态，直接提取
	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (world.isRemote || !player.isSneaking())
			return super.onItemRightClick(stack, world, player);

		int storedXP = getStoredXP(stack);
		if (storedXP > 0)
		{
			int toAdd = removeStoredXP(stack, storedXP);
			if (toAdd > 0) {
				addXP(player, toAdd);
				world.playSoundAtEntity(player, "random.orb", 0.5F, 1.0F); // 播放提示音
			}
		}
		return stack;
	}

	// 修复：对方块潜行右键
	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int par4, int par5, int par6,
							 int par7, float par8, float par9, float par10)
	{
		if (world.isRemote) return false;

		if (player.isSneaking())
		{
			int storedXP = getStoredXP(stack);
			if (storedXP > 0)
			{
				int toAdd = removeStoredXP(stack, storedXP);
				if (toAdd > 0) {
					addXP(player, toAdd);
					world.playSoundAtEntity(player, "random.orb", 0.5F, 1.0F);
					return true;
				}
			}
			return false;
		}

		// 没潜行且戒指没激活时，单次提取 50 点
		if (stack.getItemDamage() == 0 && getStoredXP(stack) != 0)
		{
			int toAdd = removeStoredXP(stack, TRANSFER_RATE);
			if (toAdd > 0) {
				addXP(player, toAdd);
				world.playSoundAtEntity(player, "random.orb", 0.5F, 1.0F);
				return true;
			}
		}
		return false;
	}

	private void removeXP(EntityPlayer player, int amount)
	{
		int experiencetotal = getXP(player) - amount;

		if (experiencetotal < 0) {
			player.experienceTotal = 0;
			player.experienceLevel = 0;
			player.experience = 0;
		}
		else {
			player.experienceTotal = experiencetotal;
			player.experienceLevel = getLvlForXP(experiencetotal);
			player.experience = (float)(experiencetotal - getXPForLvl(player.experienceLevel)) / (float)player.xpBarCap();
		}
	}

	private void addXP(EntityPlayer player, int amount)
	{
		long experiencetotal = (long) getXP(player) + amount;
		if (experiencetotal > Integer.MAX_VALUE)
			experiencetotal = Integer.MAX_VALUE;

		player.experienceTotal = (int) experiencetotal;
		player.experienceLevel = getLvlForXP((int) experiencetotal);
		player.experience = (experiencetotal - getXPForLvl(player.experienceLevel)) / (float)player.xpBarCap();

		// 修复客户端不同步：强制调用原版方法，触发向客户端发送经验更新数据包 (S1FPacketSetExperience)
		player.addExperience(0);
	}

	private int getXP(EntityPlayer player) {
		return getXPForLvl(player.experienceLevel) + (int)(player.experience * player.xpBarCap());
	}

	private int getXPForLvl(int level) {
		if (level < 0) return Integer.MAX_VALUE;
		if (level <= 15) return level * 17;
		if (level <= 30) return (int) (((level * level) * 1.5D) - (29.5D * level) + 360.0D);
		return (int) (((level * level) * 3.5D) - (151.5D * level) + 2220.0D);
	}

	private int getLvlForXP(int totalXP) {
		int result = 0;
		while (getXPForLvl(result) <= totalXP)
			result++;
		return --result;
	}

	private int getStoredXP(ItemStack stack)
	{
		if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
		return stack.stackTagCompound.getInteger("StoredXP");
	}

	private boolean canStore(ItemStack stack)
	{
		return getStoredXP(stack) <= Integer.MAX_VALUE;
	}

	private void setStoredXP(ItemStack stack, int XP) {
		if (!stack.hasTagCompound())
			stack.stackTagCompound = new NBTTagCompound();
		stack.stackTagCompound.setInteger("StoredXP", XP);
	}

	private void addStoredXP(ItemStack stack, int XP)
	{
		long result = (long) getStoredXP(stack) + XP;
		if (result > Integer.MAX_VALUE)
			result = Integer.MAX_VALUE;
		setStoredXP(stack, (int) result);
	}

	private int removeStoredXP(ItemStack stack, int XP) {
		int currentXP = getStoredXP(stack);
		int result;
		int returnResult;

		if (currentXP < XP) {
			result = 0;
			returnResult = currentXP;
		}
		else {
			result = currentXP - XP;
			returnResult = XP;
		}

		setStoredXP(stack, result);
		return returnResult;
	}

	@Override
	public void updateInPedestal(World world, int x, int y, int z) {
		DMPedestalTile tile = ((DMPedestalTile) world.getTileEntity(x, y, z));
		List<EntityXPOrb> orbs = world.getEntitiesWithinAABB(EntityXPOrb.class, tile.getEffectBounds());

		if (orbs.isEmpty()) return;

		ItemStack mindStone = tile.getItemStack();
		if (!mindStone.hasTagCompound())
			mindStone.stackTagCompound = new NBTTagCompound();

		long currentXP = getStoredXP(mindStone);
		boolean isFull = currentXP >= Integer.MAX_VALUE;
		int xpToAdd = 0;

		for (EntityXPOrb orb : orbs) {
			WorldHelper.gravitateEntityTowards(orb, x + 0.5, y + 0.5, z + 0.5);
			if (world.isRemote || orb.isDead || !(orb.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) < 1.21))
				continue;
			if (isFull) continue;

			if (currentXP + xpToAdd + orb.xpValue > Integer.MAX_VALUE) {
				int maxAdd = (int) (Integer.MAX_VALUE - (currentXP + xpToAdd));
				orb.xpValue -= maxAdd;
				xpToAdd += maxAdd;
				isFull = true;
			}
			else {
				xpToAdd += orb.xpValue;
				orb.setDead();
			}
		}

		if (xpToAdd > 0)
			addStoredXP(mindStone, xpToAdd);
	}

	@Override
	public List<String> getPedestalDescription() {
		return Lists.newArrayList(StatCollector.translateToLocal("pe.mind.pedestal1"));
	}
}
