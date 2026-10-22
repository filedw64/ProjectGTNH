package moze_intel.projecte.gameObjs.entity;

import cpw.mods.fml.relauncher.ReflectionHelper;
import moze_intel.projecte.config.ProjectEConfig;
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
	private static final int MAX_LIFE_TICKS = 1200;

	private boolean ignoreGravity = false;
	private boolean spawnLightning = false;
	private boolean hasHit = false;

	private int homingTier = 0;
	private int newTargetCooldown = 0;

	// 反射获取原版箭矢的飞行时间（用于穿透射出者）；取不到就静默跳过
	private static Field ticksInAirField;

	static {
		try {
			ticksInAirField = ReflectionHelper.findField(EntityArrow.class, "ticksInAir", "field_70257_an");
			ticksInAirField.setAccessible(true);
		} catch (Exception ignored) {}
	}

	public EntityPEArrow(World world) {
		super(world);
		this.canBePickedUp = 0;
	}

	public EntityPEArrow(World world, EntityLivingBase shooter, float velocity) {
		super(world, shooter, velocity);
		this.canBePickedUp = 0;
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
		if (worldObj == null) {
			return;
		}

		// ticksInAir 被常驻清零，原版靠它计数的飞行寿命判定永不触发；这里补 1200 tick（60 秒）硬上限，防止实体泄漏
		if (this.ticksExisted > MAX_LIFE_TICKS) {
			if (!this.worldObj.isRemote) {
				this.setDead();
			}
			return;
		}

		if (ticksInAirField != null) {
			try {
				ticksInAirField.setInt(this, 0);
			} catch (Exception ignored) {}
		}

		super.onUpdate();

		boolean inGround = WorldHelper.isArrowInGround(this);

		// 1. 无视重力
		if (this.ignoreGravity && !inGround && this.ticksExisted > 1) {
			this.motionY += 0.05D;
		}

		// 2. 索敌与追踪（homingTier <= 0 时整段不执行）
		if (this.homingTier > 0 && !inGround) {
			if (!worldObj.isRemote && this.ticksExisted > 3) {
				EntityLiving current = getTarget();
				if (current != null && !current.isEntityAlive()) {
					dataWatcher.updateObject(DW_TARGET_ID, NO_TARGET);
					current = null;
				}
				if (current == null && newTargetCooldown <= 0) {
					findNewTarget();
				} else {
					newTargetCooldown--;
				}
			}

			if (this.ticksExisted > 3) {
				EntityLiving target = getTarget();
				if (target != null) {
					// 追踪轨迹粒子：隔 tick 生成，降低客户端开销
					if (this.ticksExisted % 2 == 0) {
						this.worldObj.spawnParticle("flame", this.posX + this.motionX / 4.0D, this.posY + this.motionY / 4.0D, this.posZ + this.motionZ / 4.0D, -this.motionX / 2, -this.motionY / 2 + 0.2D, -this.motionZ / 2);
					}

					// setThrowableHeading 的标量参数是速度，会把箭速重设；这里沿用当前速率，避免追踪时箭速骤降
					double speed = Math.sqrt(this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ);
					if (speed > 0.0D) {
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

						setThrowableHeading(adjustedLookVec.x, adjustedLookVec.y, adjustedLookVec.z, (float) speed, 0);
					}
				}
			}
		}

		// 3. 落地或命中实体（命中后速度归零）算一次命中：落雷一次并消失
		if (inGround || (this.ticksExisted > 3 && this.motionX == 0 && this.motionY == 0 && this.motionZ == 0)) {
			if (!worldObj.isRemote) {
				if (this.spawnLightning && ProjectEConfig.bowLightningEnabled && !this.hasHit) {
					this.hasHit = true;
					worldObj.spawnEntityInWorld(new EntityLightningBolt(worldObj, this.posX, this.posY, this.posZ));
				}
				this.setDead();
			}
		}
	}

	private void findNewTarget() {
		double radius = 16.0D * this.homingTier;
		List<Entity> nearby = worldObj.getEntitiesWithinAABB(Entity.class, this.boundingBox.expand(radius, radius, radius));

		EntityLiving nearest = null;
		double nearestDistSq = Double.MAX_VALUE;

		for (Entity candidate : nearby) {
			if (!(candidate instanceof EntityLiving) || candidate instanceof EntityPEArrow) {
				continue;
			}
			if (candidate == this.shootingEntity || !candidate.isEntityAlive()) {
				continue;
			}

			double distSq = this.getDistanceSqToEntity(candidate);
			if (distSq < nearestDistSq) {
				nearestDistSq = distSq;
				nearest = (EntityLiving) candidate;
			}
		}

		if (nearest != null) {
			dataWatcher.updateObject(DW_TARGET_ID, nearest.getEntityId());
		}
		newTargetCooldown = 5;
	}

	private EntityLiving getTarget() {
		Entity target = worldObj.getEntityByID(dataWatcher.getWatchableObjectInt(DW_TARGET_ID));
		return target instanceof EntityLiving ? (EntityLiving) target : null;
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
