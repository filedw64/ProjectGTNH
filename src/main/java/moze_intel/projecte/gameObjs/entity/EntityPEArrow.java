package moze_intel.projecte.gameObjs.entity;

import cpw.mods.fml.relauncher.ReflectionHelper;
import moze_intel.projecte.utils.WorldHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.world.World;

import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix4d;
import javax.vecmath.Vector3d;
import java.lang.reflect.Field;
import java.util.List;

public class EntityPEArrow extends EntityArrow {
	private static final int DW_TARGET_ID = 31;
	private static final int NO_TARGET = -1;

	private boolean ignoreGravity = false;
	private boolean spawnLightning = false;
	private boolean hasHit = false;

	private int homingTier = 0;
	private int newTargetCooldown = 0;

	// 用于反射获取原版箭矢的飞行时间，以实现“穿透射出者”
	private static Field ticksInAirField;

	static {
		try {
			// field_70257_an 是 1.7.10 中 ticksInAir 的混淆名
			ticksInAirField = ReflectionHelper.findField(EntityArrow.class, "ticksInAir", "field_70257_an");
			ticksInAirField.setAccessible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public EntityPEArrow(World world) {
		super(world);
	}

	public EntityPEArrow(World world, EntityLivingBase shooter, float velocity) {
		super(world, shooter, velocity);
	}

	@Override
	public void entityInit() {
		super.entityInit();
		dataWatcher.addObject(DW_TARGET_ID, NO_TARGET);
	}

	public void setIgnoreGravity(boolean ignoreGravity) { this.ignoreGravity = ignoreGravity; }
	public void setSpawnLightning(boolean spawnLightning) { this.spawnLightning = spawnLightning; }
	public void setHomingTier(int tier) { this.homingTier = tier; }

	@Override
	public void onUpdate() {
		// 永远将 ticksInAir 设为 0
		if (ticksInAirField != null) {
			try {
				ticksInAirField.setInt(this, 0);
			} catch (Exception ignored) {}
		}

		super.onUpdate();

		boolean inGround = WorldHelper.isArrowInGround(this);

		// 1. 处理无视重力
		if (this.ignoreGravity && !inGround && this.ticksExisted > 1) {
			this.motionY += 0.05D;
		}

		// 2. 索敌与追踪逻辑
		if (this.homingTier > 0 && !inGround) {
			if (!worldObj.isRemote && this.ticksExisted > 3) {
				if (hasTarget() && !getTarget().isEntityAlive()) {
					dataWatcher.updateObject(DW_TARGET_ID, NO_TARGET);
				}
				if (!hasTarget() && newTargetCooldown <= 0) {
					findNewTarget();
				} else {
					newTargetCooldown--;
				}
			}

			if (this.ticksExisted > 3 && hasTarget()) {
				// 绘制追踪轨迹粒子
				this.worldObj.spawnParticle("flame", this.posX + this.motionX / 4.0D, this.posY + this.motionY / 4.0D, this.posZ + this.motionZ / 4.0D, -this.motionX / 2, -this.motionY / 2 + 0.2D, -this.motionZ / 2);

				Entity target = getTarget();
				if (target != null) {
					Vector3d arrowLoc = new Vector3d(posX, posY, posZ);
					Vector3d targetLoc = new Vector3d(target.posX, target.boundingBox.minY + target.height / 2.0D, target.posZ);

					Vector3d lookVec = new Vector3d(targetLoc);
					lookVec.sub(arrowLoc);
					Vector3d arrowMotion = new Vector3d(this.motionX, this.motionY, this.motionZ);

					double theta = wrap180Radian(arrowMotion.angle(lookVec));

					// 根据 Tier 限制最大转角
					double maxAngle = (Math.PI / 8.0D) * this.homingTier;
					if (this.homingTier >= 3) maxAngle = Math.PI;

					theta = clampAbs(theta, maxAngle);

					Vector3d crossProduct = new Vector3d();
					crossProduct.cross(arrowMotion, lookVec);
					crossProduct.normalize();

					Matrix4d transform = new Matrix4d();
					transform.set(new AxisAngle4d(crossProduct, theta));

					Vector3d adjustedLookVec = new Vector3d(arrowMotion);
					transform.transform(arrowMotion, adjustedLookVec);

					setThrowableHeading(adjustedLookVec.x, adjustedLookVec.y, adjustedLookVec.z, 1.0F, 0);
				}
			}
		}

		// 3. 落地/命中实体 直接消失 (包含落雷逻辑)
		if (inGround || (this.ticksExisted > 3 && this.motionX == 0 && this.motionY == 0 && this.motionZ == 0)) {
			if (!worldObj.isRemote && this.spawnLightning && !this.hasHit) {
				this.hasHit = true;
				EntityLightningBolt lightning = new EntityLightningBolt(worldObj, this.posX, this.posY, this.posZ);
				worldObj.spawnEntityInWorld(lightning);
			}
			if (!worldObj.isRemote) {
				this.setDead();
			}
		}
	}

	private void findNewTarget() {
		double radius = 16.0D * this.homingTier;
		List<EntityLiving> candidates = worldObj.getEntitiesWithinAABB(EntityLiving.class, this.boundingBox.expand(radius, radius, radius));

		candidates.sort((o1, o2) -> {
			double dist = EntityPEArrow.this.getDistanceSqToEntity(o1) - EntityPEArrow.this.getDistanceSqToEntity(o2);
			return Double.compare(dist, 0.0);
		});

		if (!candidates.isEmpty()) {
			dataWatcher.updateObject(DW_TARGET_ID, candidates.get(0).getEntityId());
		}
		newTargetCooldown = 5;
	}

	private EntityLiving getTarget() {
		return (EntityLiving) worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(DW_TARGET_ID));
	}

	private boolean hasTarget() {
		return getTarget() != null;
	}

	private double wrap180Radian(double radian) {
		radian %= 2 * Math.PI;
		while (radian >= Math.PI) radian -= 2 * Math.PI;
		while (radian < -Math.PI) radian += 2 * Math.PI;
		return radian;
	}

	private double clampAbs(double param, double maxMagnitude) {
		if (Math.abs(param) > maxMagnitude) {
			if (param < 0) param = -Math.abs(maxMagnitude);
			else param = Math.abs(maxMagnitude);
		}
		return param;
	}
}
