package moze_intel.projecte.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.entity.EntityLootBall;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.IGrowable;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityMooshroom;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.ExplosionEvent;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Helper class for anything that touches a World.
 * Notice: Please try to keep methods tidy and alphabetically ordered. Thanks!
 */
public final class WorldHelper
{
	public static final ImmutableList<Class<? extends EntityLiving>> peacefuls = ImmutableList.of(
		EntitySheep.class, EntityPig.class, EntityCow.class,
		EntityMooshroom.class, EntityChicken.class, EntityBat.class,
		EntityVillager.class, EntitySquid.class, EntityOcelot.class,
		EntityWolf.class, EntityHorse.class
	);
	public static final ImmutableList<Class<? extends EntityLiving>> mobs = ImmutableList.of(
		EntityZombie.class, EntitySkeleton.class, EntityCreeper.class,
		EntitySpider.class, EntityEnderman.class, EntitySilverfish.class,
		EntityPigZombie.class, EntityGhast.class, EntityBlaze.class,
		EntitySlime.class, EntityWitch.class
	);

	public static Set<Class<? extends Entity>> interdictionBlacklist = new HashSet<>();

	public static Set<Class<? extends Entity>> swrgBlacklist = new HashSet<>();

	public static boolean blacklistInterdiction(Class<? extends Entity> clazz) {
		return interdictionBlacklist.add(clazz);
	}

	public static boolean blacklistSwrg(Class<? extends Entity> clazz) {
		// 原版这里错误地判断和添加到了 interdictionBlacklist
		return swrgBlacklist.add(clazz);
	}

	public static void createLootDrop(List<ItemStack> drops, World world, double x, double y, double z) {
		if (drops == null || drops.isEmpty())
			return;
		ItemHelper.compactItemList(drops);
		if (!ProjectEConfig.useLootBalls)
			for (ItemStack drop : drops)
				spawnEntityItem(world, drop, x, y, z);
		else world.spawnEntityInWorld(new EntityLootBall(world, drops, x, y, z));
	}

	/**
	 * Equivalent of World.newExplosion
	 */
	public static void createNovaExplosion(World world, Entity exploder, double x, double y, double z, float power)
	{
		NovaExplosion explosion = new NovaExplosion(world, exploder, x, y, z, power);
		if (!MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Start(world, explosion)))
		{
			explosion.doExplosionA();
			explosion.doExplosionB(true);
		}
	}

	public static void extinguishNearby(World world, EntityPlayer player)
	{
		if (!(player instanceof EntityPlayerMP entityPlayerMP))
			return;

		final int minX = (int) (player.posX - 1), maxX = (int) (player.posX + 1);
		final int minY = (int) (player.posY - 1), maxY = (int) (player.posY + 1);
		final int minZ = (int) (player.posZ - 1), maxZ = (int) (player.posZ + 1);

		for (int x = minX; x <= maxX; x++)
			for (int z = minZ; z <= maxZ; z++)
				for (int y = minY; y <= maxY; y++)
					if (world.getBlock(x, y, z) == Blocks.fire && PlayerHelper.hasBreakPermission(entityPlayerMP, x, y, z))
						world.setBlockToAir(x, y, z);
	}

	public static void freezeInBoundingBox(World world, AxisAlignedBB box, EntityPlayer player, boolean random)
	{
		final int minX = (int) box.minX, maxX = (int) box.maxX;
		final int minY = (int) box.minY, maxY = (int) box.maxY;
		final int minZ = (int) box.minZ, maxZ = (int) box.maxZ;

		final boolean flag = player instanceof EntityPlayerMP;

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					Block b = world.getBlock(x, y, z);
					if ((b == Blocks.water || b == Blocks.flowing_water) && (!random || world.rand.nextInt(128) == 0))
					{
						if (flag)
							PlayerHelper.checkedReplaceBlock((EntityPlayerMP) player, x, y, z, Blocks.ice, 0);
						else world.setBlock(x, y, z, Blocks.ice);
					}
					else if (b.isSideSolid(world, x, y, z, ForgeDirection.UP))
					{
						Block b2 = world.getBlock(x, y + 1, z);
						if (b2 == Blocks.air && (!random || world.rand.nextInt(128) == 0))
						{
							if (flag)
								PlayerHelper.checkedReplaceBlock((EntityPlayerMP) player, x, y + 1, z, Blocks.snow_layer, 0);
							else world.setBlock(x, y + 1, z, Blocks.snow_layer);
						}
					}
				}
			}
		}
	}

	public static List<TileEntity> getAdjacentTileEntities(World world, TileEntity tile)
	{
		return ImmutableList.copyOf(getAdjacentTileEntitiesMapped(world, tile).values());
	}

	public static Map<ForgeDirection, TileEntity> getAdjacentTileEntitiesMapped(final World world, final TileEntity tile)
	{
		Map<ForgeDirection, TileEntity> ret = new EnumMap<>(ForgeDirection.class);

		for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
			TileEntity candidate = world.getTileEntity(tile.xCoord + dir.offsetX, tile.yCoord + dir.offsetY, tile.zCoord + dir.offsetZ);
			if (candidate != null) {
				ret.put(dir, candidate);
			}
		}

		return ret;
	}

	public static ArrayList<ItemStack> getBlockDrops(World world, EntityPlayer player, Block block, ItemStack stack, int x, int y, int z)
	{
		int meta = world.getBlockMetadata(x, y, z);

		if (EnchantmentHelper.getEnchantmentLevel(Enchantment.silkTouch.effectId, stack) > 0 && block.canSilkHarvest(world, player, x, y, z, meta))
		{
			return Lists.newArrayList(new ItemStack(block, 1, meta));
		}

		return block.getDrops(world, x, y, z, meta, EnchantmentHelper.getEnchantmentLevel(Enchantment.fortune.effectId, stack));
	}

	/**
	 * Gets an AABB for AOE digging operations. The offset increases both the breadth and depth of the box.
	 */
	public static AxisAlignedBB getBroadDeepBox(Coordinates coords, ForgeDirection direction, int offset)
	{
		if (direction.offsetX > 0)
		{
			return AxisAlignedBB.getBoundingBox(coords.x - offset, coords.y - offset, coords.z - offset, coords.x, coords.y + offset, coords.z + offset);
		}
		else if (direction.offsetX < 0)
		{
			return AxisAlignedBB.getBoundingBox(coords.x, coords.y - offset, coords.z - offset, coords.x + offset, coords.y + offset, coords.z + offset);
		}
		else if (direction.offsetY > 0)
		{
			return AxisAlignedBB.getBoundingBox(coords.x - offset, coords.y - offset, coords.z - offset, coords.x + offset, coords.y, coords.z + offset);
		}
		else if (direction.offsetY < 0)
		{
			return AxisAlignedBB.getBoundingBox(coords.x - offset, coords.y, coords.z - offset, coords.x + offset, coords.y + offset, coords.z + offset);
		}
		else if (direction.offsetZ > 0)
		{
			return AxisAlignedBB.getBoundingBox(coords.x - offset, coords.y - offset, coords.z - offset, coords.x + offset, coords.y + offset, coords.z);
		}
		else if (direction.offsetZ < 0)
		{
			return AxisAlignedBB.getBoundingBox(coords.x - offset, coords.y - offset, coords.z, coords.x + offset, coords.y + offset, coords.z + offset);
		}
		return AxisAlignedBB.getBoundingBox(0, 0, 0, 0, 0, 0);
	}

	/**
	 * Returns in AABB that is always 3x3 orthogonal to the side hit, but varies in depth in the direction of the side hit
	 */
	public static AxisAlignedBB getDeepBox(Coordinates coords, ForgeDirection direction, int depth)
	{
		if (direction.offsetX != 0)
		{
			if (direction.offsetX > 0)
			{
				return AxisAlignedBB.getBoundingBox(coords.x - depth, coords.y - 1, coords.z - 1, coords.x, coords.y + 1, coords.z + 1);
			}
			else return AxisAlignedBB.getBoundingBox(coords.x, coords.y - 1, coords.z - 1, coords.x + depth, coords.y + 1, coords.z + 1);
		}
		else if (direction.offsetY != 0)
		{
			if (direction.offsetY > 0)
			{
				return AxisAlignedBB.getBoundingBox(coords.x - 1, coords.y - depth, coords.z - 1, coords.x + 1, coords.y, coords.z + 1);
			}
			else return AxisAlignedBB.getBoundingBox(coords.x - 1, coords.y, coords.z - 1, coords.x + 1, coords.y + depth, coords.z + 1);
		}
		else
		{
			if (direction.offsetZ > 0)
			{
				return AxisAlignedBB.getBoundingBox(coords.x - 1, coords.y - 1, coords.z - depth, coords.x + 1, coords.y + 1, coords.z);
			}
			else return AxisAlignedBB.getBoundingBox(coords.x - 1, coords.y - 1, coords.z, coords.x + 1, coords.y + 1, coords.z + depth);
		}
	}

	/**
	 * Gets an AABB for AOE digging operations. The charge increases only the breadth of the box.
	 * Y level remains constant. As such, a direction hit is unneeded.
	 */
	public static AxisAlignedBB getFlatYBox(Coordinates coords, int offset)
	{
		return AxisAlignedBB.getBoundingBox(coords.x - offset, coords.y, coords.z - offset, coords.x + offset, coords.y, coords.z + offset);
	}

	public static <T extends Entity> T getNewEntityInstance(Class<T> c, World world)
	{
		try
		{
			Constructor<T> constr = c.getConstructor(World.class);
			T ent = constr.newInstance(world);

			if (ent instanceof EntitySkeleton)
			{
				if (world.rand.nextInt(2) == 0)
				{
					((EntitySkeleton) ent).setSkeletonType(1);
					ent.setCurrentItemOrArmor(0, new ItemStack(Items.stone_sword));
				}
				else
				{
					ent.setCurrentItemOrArmor(0, new ItemStack(Items.bow));
				}
			}
			else if (ent instanceof EntityPigZombie)
			{
				ent.setCurrentItemOrArmor(0, new ItemStack(Items.golden_sword));
			}

			return ent;
		}
		catch (Exception e)
		{
			PELogger.logFatal("Could not create new entity instance for: "+c.getCanonicalName());
			e.printStackTrace();
		}

		return null;
	}

	public static EntityLiving getRandomEntity(World world, EntityLiving toRandomize)
	{
		Class<? extends EntityLiving> entClass = toRandomize.getClass();

		if (peacefuls.contains(entClass))
		{
			return getNewEntityInstance(CollectionHelper.getRandomListEntry(peacefuls, entClass), world);
		}
		else if (mobs.contains(entClass))
		{
			return getNewEntityInstance(CollectionHelper.getRandomListEntry(mobs, entClass), world);
		}
		return null;
	}

	public static List<TileEntity> getTileEntitiesWithinAABB(World world, AxisAlignedBB bBox)
	{
		List<TileEntity> list = new ArrayList<>();

		final int minX = (int) bBox.minX, maxX = (int) bBox.maxX;
		final int minY = (int) bBox.minY, maxY = (int) bBox.maxY;
		final int minZ = (int) bBox.minZ, maxZ = (int) bBox.maxZ;

		for (int x = minX; x <= maxX; x++)
			for (int z = minZ; z <= maxZ; z++)
				for (int y = minY; y <= maxY; y++) {
					TileEntity tile = world.getTileEntity(x, y, z);
					if (tile != null)
						list.add(tile);
				}

		return list;
	}

	/**
	 * Gravitates an entity, vanilla xp orb style, towards a position
	 * Code adapted from EntityXPOrb and OpenBlocks Vacuum Hopper, mostly the former
	 */
	public static void gravitateEntityTowards(Entity ent, double x, double y, double z)
	{
		double dX = x - ent.posX;
		double dY = y - ent.posY;
		double dZ = z - ent.posZ;
		double dist = Math.sqrt(dX * dX + dY * dY + dZ * dZ);

		double vel = 1.0 - dist / 15.0;
		if (vel > 0.0D)
		{
			vel *= vel;
			ent.motionX += dX / dist * vel * 0.05;
			ent.motionY += dY / dist * vel * 0.1;
			ent.motionZ += dZ / dist * vel * 0.05;
			ent.moveEntity(ent.motionX, ent.motionY, ent.motionZ);
		}
	}

	public static void growNearbyRandomly(boolean harvest, World world, double xCoord, double yCoord, double zCoord, EntityPlayer player)
	{
		int chance = harvest ? 16 : 32;
		// 提前计算整型边界，避免在三层循环内做重复的浮点转整型计算
		final int minX = (int) (xCoord - 5), maxX = (int) (xCoord + 5);
		final int minY = (int) (yCoord - 3), maxY = (int) (yCoord + 3);
		final int minZ = (int) (zCoord - 5), maxZ = (int) (zCoord + 5);

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					Block crop = world.getBlock(x, y, z);

					// Vines, leaves, tallgrass, deadbush, doubleplants
					if (crop instanceof IShearable) {
						if (!harvest) continue;
						if (player == null || PlayerHelper.hasBreakPermission((EntityPlayerMP) player, x, y, z))
							world.func_147480_a(x, y, z, true);
					}
					// Carrot, cocoa, wheat, grass (creates flowers and tall grass in vicinity),
					// Mushroom, potato, sapling, stems, tallgrass
					else if (crop instanceof IGrowable growable) {
						if (harvest && !growable.func_149851_a(world, x, y, z, false)) {
							if (player == null || PlayerHelper.hasBreakPermission((EntityPlayerMP) player, x, y, z))
								world.func_147480_a(x, y, z, true);
						}
						else if (world.rand.nextInt(chance) == 0) {
							if (ProjectEConfig.harvBandGrass || !crop.getUnlocalizedName().toLowerCase(Locale.ROOT).contains("grass"))
								growable.func_149853_b(world, world.rand, x, y, z);
						}
					}
					// All modded
					// Cactus, Reeds, Netherwart, Flower
					else if (crop instanceof IPlantable plantable) {
						if (world.rand.nextInt(chance / 4) == 0)
							for (int i = 0; i < (harvest ? 8 : 4); i++)
								crop.updateTick(world, x, y, z, world.rand);
						if (!harvest) continue;

						if (crop instanceof BlockFlower)
							if (player == null || PlayerHelper.hasBreakPermission(((EntityPlayerMP) player), x, y, z))
								world.func_147480_a(x, y, z, true);
						if (crop == Blocks.reeds || crop == Blocks.cactus) {
							boolean shouldHarvest = true;
							for (int i = 1; i < 3; i++) {
								if (world.getBlock(x, y + i, z) != crop) {
									shouldHarvest = false;
									break;
								}
							}
							if (shouldHarvest) {
								for (int i = crop == Blocks.reeds ? 1 : 0; i < 3; i++)
									if (player == null || PlayerHelper.hasBreakPermission(((EntityPlayerMP) player), x, y + i, z))
										world.func_147480_a(x, y + i, z, true);
							}
						}
						if (crop == Blocks.nether_wart) {
							int meta = plantable.getPlantMetadata(world, x, y, z);
							if (meta == 3 && (player == null || PlayerHelper.hasBreakPermission(((EntityPlayerMP) player), x, y, z)))
								world.func_147480_a(x, y, z, true);
						}
					}
				}
			}
		}
	}

	/**
	 * Recursively mines out a vein of the given Block, starting from the provided coordinates
	 */
	public static void harvestVein(World world, EntityPlayer player, ItemStack stack, Coordinates coords, Block target, List<ItemStack> currentDrops, int numMined)
	{
		Queue<Coordinates> queue = new ArrayDeque<>(Constants.MAX_VEIN_SIZE);
		Set<Coordinates> visited = new HashSet<>();

		// 将初始坐标加入队列
		queue.add(coords);

		while (!queue.isEmpty())
		{
			Coordinates current = queue.poll();

			// 直接遍历 3x3x3 的区域
			for (int x = current.x - 1; x <= current.x + 1; x++) {
				for (int z = current.z - 1; z <= current.z + 1; z++) {
					for (int y = current.y - 1; y <= current.y + 1; y++) {
						Coordinates nextCoords = new Coordinates(x, y, z);

						// 避免重复检查和死循环
						if (!visited.add(nextCoords))
							continue;

						Block block = world.getBlock(x, y, z);

						if (block != target && (target != Blocks.lit_redstone_ore || block != Blocks.redstone_ore))
							continue;

						if (!PlayerHelper.hasBreakPermission(((EntityPlayerMP) player), x, y, z))
							continue;

						currentDrops.addAll(getBlockDrops(world, player, block, stack, x, y, z));
						world.setBlockToAir(x, y, z);
						numMined++;
						queue.add(nextCoords);

						if (numMined >= Constants.MAX_VEIN_SIZE)
							return;
					}
				}
			}
		}
	}

	public static void igniteNearby(World world, EntityPlayer player)
	{
		if (!(player instanceof EntityPlayerMP entityPlayerMP))
			return;

		final int minX = (int) (player.posX - 8), maxX = (int) (player.posX + 8);
		final int minY = (int) (player.posY - 5), maxY = (int) (player.posY + 5);
		final int minZ = (int) (player.posZ - 8), maxZ = (int) (player.posZ + 8);

		for (int x = minX; x <= maxX; x++)
			for (int z = minZ; z <= maxZ; z++)
				for (int y = minY; y <= maxY; y++)
					if (world.rand.nextInt(128) == 0 && world.isAirBlock(x, y, z))
						PlayerHelper.checkedPlaceBlock(entityPlayerMP, x, y, z, Blocks.fire, 0);
	}

	public static boolean isArrowInGround(EntityArrow arrow) {
		return ReflectionHelper.getArrowInGround(arrow);
	}

	/**
	 * Repels projectiles and mobs in the given AABB away from a given point
	 */
	public static void repelEntitiesInAABBFromPoint(World world, AxisAlignedBB effectBounds, double x, double y, double z, boolean isSWRG)
	{
		List<Entity> list = world.getEntitiesWithinAABB(Entity.class, effectBounds);

		for (int i = 0, listSize = list.size(); i < listSize; i++) {
			Entity ent = list.get(i);
			if ((isSWRG && !swrgBlacklist.contains(ent.getClass()))
				|| (!isSWRG && !interdictionBlacklist.contains(ent.getClass()))) {
				if ((ent instanceof EntityLiving) || (ent instanceof IProjectile)) {
					if (!isSWRG && ProjectEConfig.interdictionMode && !(ent instanceof IMob || ent instanceof IProjectile))
						continue;

					if (ent instanceof EntityArrow && ent.onGround)
						continue;

					// 废弃 Vec3 对象分配，禁止火把和 SWRG 每 tick 都会执行这个操作，避免大量创建 Vec3 对象
					final double dX = ent.posX - x;
					final double dY = ent.posY - y;
					final double dZ = ent.posZ - z;
					final double distance = Math.sqrt(dX * dX + dY * dY + dZ * dZ) + 0.1D;

					ent.motionX += dX / 1.5D / distance;
					ent.motionY += dY / 1.5D / distance;
					ent.motionZ += dZ / 1.5D / distance;
				}
			}
		}
	}

	public static void spawnEntityItem(World world, ItemStack stack, double x, double y, double z)
	{
		final double dx = world.rand.nextDouble() * 0.8D;
		final double dy = world.rand.nextDouble() * 0.8D;
		final double dz = world.rand.nextDouble() * 0.8D;
		EntityItem entityitem = new EntityItem(world, x + dx, y + dy, z + dz, stack);
		entityitem.motionX = world.rand.nextGaussian() * 0.05D;
		entityitem.motionY = world.rand.nextGaussian() * 0.05D + 0.2F;
		entityitem.motionZ = world.rand.nextGaussian() * 0.05D;
		world.spawnEntityInWorld(entityitem);
	}
}
