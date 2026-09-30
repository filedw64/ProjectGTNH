package moze_intel.projecte.gameObjs.entity;

import moze_intel.projecte.utils.WorldHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.world.World;

public class EntityPEArrow extends EntityArrow {
	private boolean ignoreGravity = false;
	private boolean spawnLightning = false;
	private boolean hasHit = false;

	public EntityPEArrow(World world) {
		super(world);
	}

	public EntityPEArrow(World world, EntityLivingBase shooter, float velocity) {
		super(world, shooter, velocity);
	}

	public void setIgnoreGravity(boolean ignoreGravity) {
		this.ignoreGravity = ignoreGravity;
	}

	public void setSpawnLightning(boolean spawnLightning) {
		this.spawnLightning = spawnLightning;
	}

	@Override
	public void onUpdate() {
		super.onUpdate();

		boolean inGround = WorldHelper.isArrowInGround(this);

		// 处理无视重力：抵消原版 EntityArrow 里的 -0.05 Y轴速度衰减
		if (this.ignoreGravity && !inGround && this.ticksExisted > 1) {
			this.motionY += 0.05D;
		}

		// 落地直接消失 (包含落雷逻辑)
		if (inGround || (this.ticksExisted > 3 && this.motionX == 0 && this.motionY == 0 && this.motionZ == 0)) {
			if (!worldObj.isRemote && this.spawnLightning && !this.hasHit) {
				this.hasHit = true;
				EntityLightningBolt lightning = new EntityLightningBolt(worldObj, this.posX, this.posY, this.posZ);
				worldObj.spawnEntityInWorld(lightning);
			}
			if (!worldObj.isRemote) {
				this.setDead(); // 触地或停滞后立即清除箭矢实体
			}
		}
	}
}
