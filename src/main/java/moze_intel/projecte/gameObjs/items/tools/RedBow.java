package moze_intel.projecte.gameObjs.items.tools;

import moze_intel.projecte.utils.AchievementHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public class RedBow extends PEBowBase {
	public RedBow() {
		super("rm_bow");
	}

	@Override
	public float getVelocityMultiplier() { return 3.5F; }

	@Override
	public int getDrawTime() { return 12; }

	@Override
	public boolean ignoresGravity() { return true; }

	@Override
	public boolean spawnsLightning() { return false; }

	@Override
	public int getEmcCost() { return 1024; }

	@Override
	public int getHomingTier() { return 0; }

	@Override
	public String getSpecialKey() { return "pe.rm_bow.special"; }

	@Override
	public void onCreated(ItemStack stack, World world, EntityPlayer player) {
		if (!world.isRemote) player.addStat(AchievementHandler.RM_BOW, 1);
	}
}
