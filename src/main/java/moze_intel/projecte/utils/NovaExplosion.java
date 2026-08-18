package moze_intel.projecte.utils;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class NovaExplosion extends Explosion
{
	private final World worldObj;

	// 预先计算爆炸射线的方向向量，避免每次爆炸重复执行 Math.sqrt 和浮点除法
	private static final double[][] RAY_VECTORS = new double[1352][3];

	static {
		int idx = 0;
		for (int i = 0; i < 16; ++i) {
			for (int j = 0; j < 16; ++j) {
				for (int k = 0; k < 16; ++k) {
					if (i == 0 || i == 15 || j == 0 || j == 15 || k == 0 || k == 15) {
						final double d0 = i / 7.5D - 1.0D;
						final double d1 = j / 7.5D - 1.0D;
						final double d2 = k / 7.5D - 1.0D;
						final double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
						RAY_VECTORS[idx][0] = d0 / d3;
						RAY_VECTORS[idx][1] = d1 / d3;
						RAY_VECTORS[idx][2] = d2 / d3;
						idx++;
					}
				}
			}
		}
	}

	NovaExplosion(World world, Entity entity, double x, double y, double z, float radius)
	{
		super(world, entity, x, y, z, radius);
		isFlaming = true;
		isSmoking = true;
		worldObj = world;
	}

	@Override
	public void doExplosionA()
	{
		Set<ChunkPosition> hashset = new HashSet<>();

		// 直接遍历预计算的静态射线数组
		for (double[] ray : RAY_VECTORS)
		{
			final double d0 = ray[0];
			final double d1 = ray[1];
			final double d2 = ray[2];

			float f1 = this.explosionSize * (0.7F + this.worldObj.rand.nextFloat() * 0.6F);
			double d5 = this.explosionX;
			double d6 = this.explosionY;
			double d7 = this.explosionZ;

			final float f2 = 0.3F;
			for (; f1 > 0.0F; f1 -= f2 * 0.75F)
			{
				final int j1 = MathHelper.floor_double(d5);
				final int k1 = MathHelper.floor_double(d6);
				final int l1 = MathHelper.floor_double(d7);
				Block block = this.worldObj.getBlock(j1, k1, l1);

				if (block.getMaterial() != Material.air) {
					float f3 = this.exploder != null ? this.exploder.func_145772_a(this, this.worldObj, j1, k1, l1, block)
						: block.getExplosionResistance(null, worldObj, j1, k1, l1, explosionX, explosionY, explosionZ);
					f1 -= (f3 + 0.3F) * f2;
				}

				if (f1 > 0.0F && (this.exploder == null
					|| this.exploder.func_145774_a(this, this.worldObj, j1, k1, l1, block, f1)))
					hashset.add(new ChunkPosition(j1, k1, l1));

				d5 += d0 * f2;
				d6 += d1 * f2;
				d7 += d2 * f2;
			}
		}

		this.affectedBlockPositions.addAll(hashset);
		net.minecraftforge.event.ForgeEventFactory.onExplosionDetonate(this.worldObj, this, Collections.<Entity>emptyList(), this.explosionSize);
	}

	@Override
	public void doExplosionB(boolean spawnParticles)
	{
		worldObj.playSoundEffect(this.explosionX, this.explosionY, this.explosionZ, "random.explode", 3.0F,
			(1.0F + (this.worldObj.rand.nextFloat() - this.worldObj.rand.nextFloat()) * 0.2F) * 0.7F);

		if (this.explosionSize >= 2.0F && this.isSmoking)
			worldObj.spawnParticle("hugeexplosion", this.explosionX, this.explosionY, this.explosionZ,
				1.0D, 0.0D, 0.0D);
		else
			worldObj.spawnParticle("largeexplode", this.explosionX, this.explosionY, this.explosionZ,
				1.0D, 0.0D, 0.0D);

		Iterator<ChunkPosition> iterator;
		ChunkPosition chunkposition;
		Block block;

		// 预分配！避免在大型爆炸中频繁扩容，同时仅在服务端收集掉落物
		List<ItemStack> list = worldObj.isRemote ? null : new ArrayList<>(this.affectedBlockPositions.size());

		if (this.isSmoking)
		{
			iterator = this.affectedBlockPositions.iterator();

			// 爆炸范围极大，如果为每个方块都生成粒子，会导致客户端极其严重的掉帧，可以根据爆炸大小对粒子进行概率节流
			int particleThrottle = this.explosionSize > 10.0F ? (int) (this.explosionSize / 3) : 1;

			while (iterator.hasNext())
			{
				chunkposition = iterator.next();
				final int i = chunkposition.chunkPosX;
				final int j = chunkposition.chunkPosY;
				final int k = chunkposition.chunkPosZ;
				block = worldObj.getBlock(i, j, k);

				if (spawnParticles && (particleThrottle == 1 || worldObj.rand.nextInt(particleThrottle) == 0))
				{
					final double d0 = i + worldObj.rand.nextDouble();
					final double d1 = j + worldObj.rand.nextDouble();
					final double d2 = k + worldObj.rand.nextDouble();
					double d3 = d0 - this.explosionX;
					double d4 = d1 - this.explosionY;
					double d5 = d2 - this.explosionZ;
					final double d6 = MathHelper.sqrt_double(d3 * d3 + d4 * d4 + d5 * d5);
					d3 /= d6;
					d4 /= d6;
					d5 /= d6;
					double d7 = 0.5D / (d6 / this.explosionSize + 0.1D);
					d7 *= worldObj.rand.nextFloat() * worldObj.rand.nextFloat() + 0.3F;
					d3 *= d7;
					d4 *= d7;
					d5 *= d7;
					worldObj.spawnParticle("explode", (d0 + this.explosionX) / 2.0D,
						(d1 + this.explosionY) / 2.0D, (d2 + this.explosionZ) / 2.0D, d3, d4, d5);
					worldObj.spawnParticle("smoke", d0, d1, d2, d3, d4, d5);
				}

				if (block.getMaterial() != Material.air) {
					// 掉落物的计算必须仅在服务端执行
					if (!worldObj.isRemote) {
						ArrayList<ItemStack> drops = block.getDrops(worldObj, i, j, k, worldObj.getBlockMetadata(i, j, k), 0);
						if (drops != null && !drops.isEmpty())
							list.addAll(drops);
					}
					block.onBlockExploded(worldObj, i, j, k, this);
				}
			}

			// 生成战利品球必须仅在服务端执行
			if (!worldObj.isRemote) {
				Entity ent = this.getExplosivePlacedBy();
				if (ent != null)
					WorldHelper.createLootDrop(list, worldObj, ent.posX, ent.posY, ent.posZ);
				else WorldHelper.createLootDrop(list, worldObj, explosionX, explosionY, explosionZ);
			}
		}
	}
}
