package moze_intel.projecte.gameObjs.items.tools;

import moze_intel.projecte.utils.AchievementHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class DarkBow extends PEBowBase {
	public DarkBow() {
		super("dm_bow");
	}

	@Override
	public float getVelocityMultiplier() { return 2.5F; }

	@Override
	public int getDrawTime() { return 16; }

	@Override
	public boolean ignoresGravity() { return false; }

	@Override
	public boolean spawnsLightning() { return false; }

	@Override
	public int getEmcCost() { return 256; }

	@Override
	public int getHomingTier() { return 0; }

	@Override
	public String getSpecialKey() { return "pe.dm_bow.special"; }

	@Override
	public void onCreated(ItemStack stack, World world, EntityPlayer player) {
		if (!world.isRemote) player.addStat(AchievementHandler.DM_BOW, 1);
	}
}
