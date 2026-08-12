package moze_intel.projecte.gameObjs.items.armor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import moze_intel.projecte.utils.EnumArmorType;
import moze_intel.projecte.utils.WorldHelper;

import java.util.List;

public class GemLegs extends GemArmorBase
{
	public GemLegs()
	{
		super(EnumArmorType.LEGS);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List<String> tooltips, boolean unused)
	{
		tooltips.add(StatCollector.translateToLocal("pe.gem.legs.lorename"));
	}

	@Override
	public void onArmorTick(World world, EntityPlayer player, ItemStack stack)
	{
		// 当玩家Y坐标低于或等于 -12 时触发
		if (player.posY <= -12.0D)
		{
			player.posY = -12.0D;          // 将玩家强行托在 y=-12 的高度
			if (player.motionY < 0.0D)
			{
				player.motionY = 0.0D;     // 消除向下的速度
			}
			player.fallDistance = 0.0F;    // 清空掉落伤害
			player.onGround = true;        // 让系统认为玩家踩在方块上
		}

		if (world.isRemote)
		{
			// 限制最大下落速度
			if (player.isSneaking() && !player.onGround && player.motionY <= 0)
			{
				// 原版的自然极限下落速度大约是 -3.92
				player.motionY = Math.max(player.motionY * 2, -7.5D);
			}
		}

		if (player.isSneaking())
		{
			// 优化一下：使用 boundingBox 扩张
			AxisAlignedBB box = player.boundingBox.expand(3.5, 3.5, 3.5);
			WorldHelper.repelEntitiesInAABBFromPoint(world, box, player.posX, player.posY, player.posZ, true);
		}
	}
}
