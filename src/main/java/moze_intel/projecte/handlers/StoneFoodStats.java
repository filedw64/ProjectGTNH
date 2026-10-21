package moze_intel.projecte.handlers;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.FoodStats;

/**
 * 携带身之宝石/生命宝石时让 needFood() 恒为 true，从而绕过 EntityPlayer.canEat 的饱食度门槛。
 * 仅覆写 needFood()：regen 与饿死逻辑读的是 foodLevel 字段，不受影响。
 */
public final class StoneFoodStats extends FoodStats
{
	private static boolean loggedFailure;

	private final EntityPlayer player;

	public StoneFoodStats(EntityPlayer player) {
		this.player = player;
	}

	@Override
	public boolean needFood() {
		return super.needFood() || PlayerChecks.hasAlwaysEdibleStone(player);
	}

	public static void ensureApplied(EntityPlayer player) {
		if (player == null || player.worldObj == null) return;

		FoodStats current = player.getFoodStats();
		// clonePlayer 用引用赋值复制 foodStats，换维度/重生后旧实例的 player 字段会失效，需按玩家身份重新绑定
		if (current instanceof StoneFoodStats stone && stone.player == player) return;

		try {
			StoneFoodStats fresh = new StoneFoodStats(player);
			if (current != null) {
				NBTTagCompound tag = new NBTTagCompound();
				current.writeNBT(tag);
				fresh.readNBT(tag);
			}
			ObfuscationReflectionHelper.setPrivateValue(EntityPlayer.class, player, fresh, "foodStats");
		} catch (Throwable t) {
			if (!loggedFailure) {
				loggedFailure = true;
				PELogger.logWarn("Failed to replace food stats with StoneFoodStats, keeping the vanilla instance: " + t);
			}
		}
	}
}
