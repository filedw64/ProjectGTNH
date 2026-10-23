package moze_intel.projecte.rendering;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.gameObjs.items.BuildersWand;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.WandHelper;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WandRenderer {

	// 同一瞄准目标最多每 250ms 重算一次（原为 100ms 无条件重算，现在更省且手感无差别）
	private static final long CACHE_TTL_MS = 250L;
	private static final int[] EMPTY_SEGMENTS = new int[0];
	private static final double[] EMPTY_BOXES = new double[0];
	private static final int[] EMPTY_FACES = new int[0];
	// 外壳只有一根轮廓线，再按距离逐格淡出会把它撕成碎片，所以统一透明度
	private static final float PREVIEW_ALPHA = 0.5F;
	// 填充面比线框淡：太浓会把脚下的地形糊住
	private static final float PREVIEW_FILL_ALPHA = 0.30F;

	// 自适应渲染预算：只影响预览画出多少根外壳线段，不影响实际放置区域。写死不开放配置。
	// 耗时 > SLOW_MS 折半，< FAST_MS 翻倍，两者之间保持不动（缓冲区）。
	private static final int MIN_RENDER_BUDGET = 256;
	private static final double SLOW_MS = 4.0D;
	private static final double FAST_MS = 1.5D;
	private static final double SMOOTHING = 0.25D;

	// 预览快照：HUD / GUI 只读，不重复跑 BFS
	private static WandHelper.PlacementPlan lastPlan;
	private static int lastAffordable;
	private static double lastEmcCost;

	private final List<ChunkCoordinates> cachedBlocks = new ArrayList<>();
	private boolean cacheValid = false;
	private World cacheWorld;
	private int cacheX;
	private int cacheY;
	private int cacheZ;
	private int cacheSide;
	private byte cacheMode;
	private long cacheTime;
	// 聚焦方块也进缓存键：切换聚焦后立刻重算，不再滞后 CACHE_TTL_MS
	private Item cacheFocusItem;
	private int cacheFocusMeta = -1;
	// 本次缓存对应的"能真正放下"的格数，随缓存一起重算
	private int affordable;
	// 自适应预算：本帧最多画几个框；换目标、换模式都不重置，保持已收敛的值
	private int renderBudget = MIN_RENDER_BUDGET;
	private double smoothedMs;
	// 本次计划的外壳线段：每 6 个 int 是一段 (x1,y1,z1,x2,y2,z2)，按方块正序生成
	private int[] hullSegments = EMPTY_SEGMENTS;
	private int hullCount;
	// 非满格方块（台阶/楼梯/栅栏等）按真实包围盒逐块画棱，坐标是浮点，与外壳二选一
	private double[] boxSegments = EMPTY_BOXES;
	private int boxCount;
	// 预览方块是否满格：满格才走 buildHull 的外壳算法
	private boolean boxFull = true;
	private double boxMinX;
	private double boxMinY;
	private double boxMinZ;
	private double boxMaxX = 1.0D;
	private double boxMaxY = 1.0D;
	private double boxMaxZ = 1.0D;
	// 暴露面：每 4 个 int 是 (x, y, z, dir)，dir 0=DOWN 1=UP 2=NORTH 3=SOUTH 4=WEST 5=EAST
	private int[] exposedFaces = EMPTY_FACES;
	private int faceCount;
	// 展开一个面的 4 个顶点，复用数组，避免逐面分配
	private final double[] faceVerts = new double[12];

	public static WandHelper.PlacementPlan getLastPlan() {
		return lastPlan;
	}

	public static int getLastAffordable() {
		return lastAffordable;
	}

	public static double getLastEmcCost() {
		return lastEmcCost;
	}

	/**
	 * 被瞄准方块的物品形态；方块无对应物品时返回 null。
	 */
	public static ItemStack blockStackAt(World world, MovingObjectPosition target) {
		Block block = world.getBlock(target.blockX, target.blockY, target.blockZ);

		if (block == null || Item.getItemFromBlock(block) == null) return null;

		return new ItemStack(block, 1, world.getBlockMetadata(target.blockX, target.blockY, target.blockZ));
	}

	/**
	 * 能真正放下的格数。affordableCount 拿不到 player，创造模式必须在这里特判。
	 */
	public static int previewCount(EntityPlayer player, WandHelper.PlacementPlan plan, ItemStack targetStack) {
		if (plan == null) return 0;
		if (player.capabilities.isCreativeMode) return plan.size();
		if (targetStack == null) return 0;

		int count = WandHelper.affordableCount(plan, Transmutation.getEmc(player), WandHelper.countInventory(player, targetStack));
		return Math.max(0, Math.min(count, plan.size()));
	}

	/**
	 * 前 count 格里由 EMC 承担的成本：EMC 优先，背包只补差额。
	 */
	public static double previewEmcCost(EntityPlayer player, WandHelper.PlacementPlan plan, int count) {
		if (plan == null || player.capabilities.isCreativeMode || !plan.hasKnowledge || plan.emcPerBlock <= 0 || count <= 0) return 0.0D;

		int coveredByEmc = (int) Math.floor(Transmutation.getEmc(player) / plan.emcPerBlock);
		return Math.min(count, coveredByEmc) * plan.emcPerBlock;
	}

	/**
	 * 成本显示：整数不带小数点，其余保留一位。
	 */
	public static String formatEmc(double value) {
		if (value == Math.floor(value) && !Double.isInfinite(value)) return Long.toString((long) value);

		return String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private static void clearSnapshot() {
		lastPlan = null;
		lastAffordable = 0;
		lastEmcCost = 0.0D;
	}

	@SubscribeEvent
	public void onDrawHighlight(DrawBlockHighlightEvent event) {
		EntityPlayer player = event.player;
		ItemStack heldItem = player == null ? null : player.getHeldItem();

		// 未手持建筑之杖，或未瞄准方块（1.7.10 的钩子允许 target 为 null）：清快照并退出
		if (heldItem == null || !(heldItem.getItem() instanceof BuildersWand)
			|| event.target == null
			|| event.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
			clearSnapshot();
			return;
		}

		// 触发缓存重算（必要时），外壳/包围盒线段随之更新
		getBlocksForRender(player, heldItem, (BuildersWand) heldItem.getItem(), event.target);

		// 0 段时连 GL 状态都不碰
		int segments = previewSegments();

		if (segments <= 0) return;

		// 自适应预算按线段计量：画不动的尾部直接丢掉，放置区域不受影响
		int cap = maxRenderBudget();
		int drawSegments = Math.min(segments, Math.min(this.renderBudget, cap));

		if (drawSegments <= 0) return;

		double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
		double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
		double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

		// GL 状态交给 push/pop：中途抛异常也不会把 glDepthMask(false) 泄漏给后续渲染。
		// 深度测试保持原样，被遮挡的线仍被正确遮挡（不照抄 MercurialEye 关掉深度测试的做法）。
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glPushMatrix();

		try {
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glLineWidth(2.0F);
			GL11.glDisable(GL11.GL_TEXTURE_2D);
			// 填充面没有法线，开着光照会发暗；深度测试保持原样，不关深度测试
			GL11.glDisable(GL11.GL_LIGHTING);
			GL11.glDepthMask(false);

			// 填充批与线框批共用这一次耗时测量
			long drawStart = System.nanoTime();

			Tessellator tess = Tessellator.instance;
			int drawFaces = Math.min(this.faceCount, Math.min(this.renderBudget, cap));

			// 填充批：半透明面，只画法线朝向玩家的那一半，避免远近两面叠在一起越叠越白
			if (drawFaces > 0) {
				double eyeX = playerX;
				double eyeY = playerY + player.getEyeHeight();
				double eyeZ = playerZ;

				// startDrawing 会把 hasColor 重置为 false，颜色必须写在它之后，否则回退成环境色（不透明白）
				tess.startDrawing(GL11.GL_QUADS);
				tess.setColorRGBA_F(1.0F, 1.0F, 1.0F, PREVIEW_FILL_ALPHA);

				int[] faces = this.exposedFaces;

				for (int i = 0; i < drawFaces; i++) {
					int o = i * 4;
					int dir = faces[o + 3];

					if (!faceVisible(faces[o], faces[o + 1], faces[o + 2], dir, eyeX, eyeY, eyeZ)) continue;

					expandFace(faces[o], faces[o + 1], faces[o + 2], dir);

					double[] v = this.faceVerts;

					for (int k = 0; k < 12; k += 3) {
						tess.addVertex(v[k] - playerX, v[k + 1] - playerY, v[k + 2] - playerZ);
					}
				}

				tess.draw();
			}

			// 所有线段塞进一次 GL_LINES 批次，避免逐方块一个 DrawCall
			// 同上：颜色必须在 startDrawing 之后设置
			tess.startDrawing(GL11.GL_LINES);
			tess.setColorRGBA_F(1.0F, 1.0F, 1.0F, PREVIEW_ALPHA);

			if (this.boxFull) {
				int[] seg = this.hullSegments;

				for (int i = 0; i < drawSegments; i++) {
					int o = i * 6;

					tess.addVertex(seg[o] - playerX, seg[o + 1] - playerY, seg[o + 2] - playerZ);
					tess.addVertex(seg[o + 3] - playerX, seg[o + 4] - playerY, seg[o + 5] - playerZ);
				}
			} else {
				double[] box = this.boxSegments;

				for (int i = 0; i < drawSegments; i++) {
					int o = i * 6;

					tess.addVertex(box[o] - playerX, box[o + 1] - playerY, box[o + 2] - playerZ);
					tess.addVertex(box[o + 3] - playerX, box[o + 4] - playerY, box[o + 5] - playerZ);
				}
			}

			tess.draw();

			adjustBudget((System.nanoTime() - drawStart) / 1000000.0D, cap);
		} finally {
			Tessellator.instance.setTranslation(0, 0, 0);
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}

		// 保留原版方块瞄准框：不取消原事件
	}

	/**
	 * 【性能】按"世界 + 坐标 + 面 + 模式 + 聚焦方块"做 key 缓存：key 不变且距上次重算不足 250ms 时直接复用。
	 * 渲染循环只读缓存，不触碰世界、背包或 EMC。
	 */
	private List<ChunkCoordinates> getBlocksForRender(EntityPlayer player, ItemStack heldItem, BuildersWand wand, MovingObjectPosition target) {
		World world = player.worldObj;
		byte mode = wand.getMode(heldItem);
		// 聚焦方块决定可负担性与成本，必须一起进 key，否则切换后预览会滞后到 TTL 过期
		ItemStack focus = wand.getFocusStack(heldItem);
		Item focusItem = focus == null ? null : focus.getItem();
		int focusMeta = focus == null ? -1 : focus.getItemDamage();
		long now = System.currentTimeMillis();

		boolean sameKey = this.cacheValid
			&& this.cacheWorld == world
			&& this.cacheX == target.blockX
			&& this.cacheY == target.blockY
			&& this.cacheZ == target.blockZ
			&& this.cacheSide == target.sideHit
			&& this.cacheMode == mode
			&& this.cacheFocusItem == focusItem
			&& this.cacheFocusMeta == focusMeta;

		if (!sameKey || now - this.cacheTime > CACHE_TTL_MS) {
			recompute(player, world, target, mode, focus);

			this.cacheWorld = world;
			this.cacheX = target.blockX;
			this.cacheY = target.blockY;
			this.cacheZ = target.blockZ;
			this.cacheSide = target.sideHit;
			this.cacheMode = mode;
			this.cacheFocusItem = focusItem;
			this.cacheFocusMeta = focusMeta;
			this.cacheValid = true;
			this.cacheTime = now;
		}

		return this.cachedBlocks;
	}

	/**
	 * 重算计划并顺带算一次可负担性：EMC 余额与背包数量每次重算只查一次，不放进绘制循环。
	 * 计费与可负担性按"收费栈"算：有聚焦方块用聚焦方块，否则回退到被点击方块。
	 */
	private void recompute(EntityPlayer player, World world, MovingObjectPosition target, byte mode, ItemStack focus) {
		ItemStack costStack = focus == null ? blockStackAt(world, target) : focus;

		WandHelper.PlacementPlan plan = WandHelper.plan(world, target.blockX, target.blockY, target.blockZ,
			target.sideHit, mode, ProjectEConfig.buildersWandMaxBlocks, player, costStack);

		this.cachedBlocks.clear();
		this.cachedBlocks.addAll(plan.positions);

		this.affordable = previewCount(player, plan, costStack);
		// 满格方块画外壳，非满格方块按真实包围盒画
		computePreviewBox(world, target, costStack);

		if (this.boxFull) {
			this.boxSegments = EMPTY_BOXES;
			this.boxCount = 0;
			buildHull(this.affordable);
		} else {
			buildBoxes(this.affordable);
		}

		// 填充面自带一份占用集，与 buildHull 内部那份互不影响
		buildFaces(this.affordable);

		lastPlan = plan;
		lastAffordable = this.affordable;
		lastEmcCost = previewEmcCost(player, plan, this.affordable);
	}

	/**
	 * 自适应预算的上界就是配置里的原上限。
	 */
	private static int maxRenderBudget() {
		return Math.max(1, ProjectEConfig.buildersWandMaxBlocks);
	}

	/**
	 * 三区调节：超过折半区就减半，低于翻倍区就加倍，落在缓冲区则原样保持。
	 * 用指数滑动平均平滑，避免单帧抖动把预算来回甩；下界 MIN_RENDER_BUDGET 与上界 cap 都夹住。
	 */
	private void adjustBudget(double elapsedMs, int cap) {
		int floor = Math.min(MIN_RENDER_BUDGET, cap);

		this.smoothedMs = this.smoothedMs <= 0.0D
			? elapsedMs
			: this.smoothedMs + (elapsedMs - this.smoothedMs) * SMOOTHING;

		int next;

		if (this.smoothedMs > SLOW_MS) {
			next = this.renderBudget / 2;
		} else if (this.smoothedMs < FAST_MS) {
			next = this.renderBudget * 2;
		} else {
			next = this.renderBudget;
		}

		if (next < floor) next = floor;
		if (next > cap) next = cap;

		this.renderBudget = next;
	}

	/**
	 * 收集暴露面：邻格不在本次计划里，该面就是暴露面。自建一份占用集，与 buildHull 内部那份互不影响。
	 * 每 4 个 int 一个面 (x, y, z, dir)，dir 0=DOWN 1=UP 2=NORTH 3=SOUTH 4=WEST 5=EAST。
	 */
	private void buildFaces(int n) {
		this.exposedFaces = EMPTY_FACES;
		this.faceCount = 0;

		if (n <= 0) return;

		// 满格方块才用占用集剔除内部面；非满格的包围盒不铺满整格，每个面都可能露出来
		Set<Long> occupied = this.boxFull ? new HashSet<>(Math.max(16, n * 2)) : null;

		if (occupied != null) {
			for (int i = 0; i < n; i++) {
				ChunkCoordinates c = this.cachedBlocks.get(i);
				occupied.add(packKey(c.posX, c.posY, c.posZ, 3));
			}
		}

		// 多数形状暴露面远少于 6n（薄片约 2n 加周长），先按 2n 估，不够再翻倍
		int[] out = new int[Math.max(16, n * 2) * 4];
		int count = 0;

		for (int i = 0; i < n; i++) {
			ChunkCoordinates c = this.cachedBlocks.get(i);

			for (int dir = 0; dir < 6; dir++) {
				int nx = c.posX;
				int ny = c.posY;
				int nz = c.posZ;

				switch (dir) {
					case 0:
						ny--;
						break;
					case 1:
						ny++;
						break;
					case 2:
						nz--;
						break;
					case 3:
						nz++;
						break;
					case 4:
						nx--;
						break;
					default:
						nx++;
						break;
				}

				// 邻格也在计划里 => 内部面，不画
				if (occupied != null && occupied.contains(packKey(nx, ny, nz, 3))) continue;

				if (count + 4 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);

				out[count++] = c.posX;
				out[count++] = c.posY;
				out[count++] = c.posZ;
				out[count++] = dir;
			}
		}

		this.exposedFaces = out;
		this.faceCount = count / 4;
	}

	/**
	 * dot(法线, 眼位 - 面心) > 0 才画。法线是轴对齐的，只有法线那一轴的分量非零，
	 * 所以等价于比较眼位与该面所在平面——顺便省掉一半面，避免远近两面叠色。
	 */
	private boolean faceVisible(int x, int y, int z, int dir, double eyeX, double eyeY, double eyeZ) {
		switch (dir) {
			case 0:
				return eyeY < y + this.boxMinY;
			case 1:
				return eyeY > y + this.boxMaxY;
			case 2:
				return eyeZ < z + this.boxMinZ;
			case 3:
				return eyeZ > z + this.boxMaxZ;
			case 4:
				return eyeX < x + this.boxMinX;
			default:
				return eyeX > x + this.boxMaxX;
		}
	}

	// 按 dir 把面展开成 4 个顶点，顺序是从外侧看逆时针，配合背面剔除不会漏画
	private void expandFace(int x, int y, int z, int dir) {
		double x0 = x + this.boxMinX;
		double y0 = y + this.boxMinY;
		double z0 = z + this.boxMinZ;
		double x1 = x + this.boxMaxX;
		double y1 = y + this.boxMaxY;
		double z1 = z + this.boxMaxZ;
		double[] v = this.faceVerts;

		switch (dir) {
			case 0:
				setFaceVertex(v, 0, x0, y0, z0);
				setFaceVertex(v, 1, x1, y0, z0);
				setFaceVertex(v, 2, x1, y0, z1);
				setFaceVertex(v, 3, x0, y0, z1);
				break;
			case 1:
				setFaceVertex(v, 0, x0, y1, z0);
				setFaceVertex(v, 1, x0, y1, z1);
				setFaceVertex(v, 2, x1, y1, z1);
				setFaceVertex(v, 3, x1, y1, z0);
				break;
			case 2:
				setFaceVertex(v, 0, x0, y0, z0);
				setFaceVertex(v, 1, x0, y1, z0);
				setFaceVertex(v, 2, x1, y1, z0);
				setFaceVertex(v, 3, x1, y0, z0);
				break;
			case 3:
				setFaceVertex(v, 0, x0, y0, z1);
				setFaceVertex(v, 1, x1, y0, z1);
				setFaceVertex(v, 2, x1, y1, z1);
				setFaceVertex(v, 3, x0, y1, z1);
				break;
			case 4:
				setFaceVertex(v, 0, x0, y0, z0);
				setFaceVertex(v, 1, x0, y0, z1);
				setFaceVertex(v, 2, x0, y1, z1);
				setFaceVertex(v, 3, x0, y1, z0);
				break;
			default:
				setFaceVertex(v, 0, x1, y0, z0);
				setFaceVertex(v, 1, x1, y1, z0);
				setFaceVertex(v, 2, x1, y1, z1);
				setFaceVertex(v, 3, x1, y0, z1);
				break;
		}
	}

	private static void setFaceVertex(double[] v, int index, double x, double y, double z) {
		int o = index * 3;

		v[o] = x;
		v[o + 1] = y;
		v[o + 2] = z;
	}

	/**
	 * 本次缓存要画的线段总数：满格走外壳，非满格走逐块包围盒。
	 */
	private int previewSegments() {
		return this.boxFull ? this.hullCount : this.boxCount;
	}

	/**
	 * 预览方块的真实包围盒。预览里所有格子都是同一种方块，所以只按瞄准位置算一次。
	 * 满格（0,0,0,1,1,1）继续交给外壳算法；包围盒一律在 finally 里还原，异常按满格处理且不刷日志。
	 */
	private void computePreviewBox(World world, MovingObjectPosition target, ItemStack costStack) {
		this.boxFull = true;
		this.boxMinX = 0.0D;
		this.boxMinY = 0.0D;
		this.boxMinZ = 0.0D;
		this.boxMaxX = 1.0D;
		this.boxMaxY = 1.0D;
		this.boxMaxZ = 1.0D;

		if (costStack == null) return;

		Block block = Block.getBlockFromItem(costStack.getItem());

		if (block == null) return;

		double minX = block.getBlockBoundsMinX();
		double minY = block.getBlockBoundsMinY();
		double minZ = block.getBlockBoundsMinZ();
		double maxX = block.getBlockBoundsMaxX();
		double maxY = block.getBlockBoundsMaxY();
		double maxZ = block.getBlockBoundsMaxZ();

		try {
			try {
				block.setBlockBoundsBasedOnState(world, target.blockX, target.blockY, target.blockZ);
			} catch (Exception e) {
				block.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
			}

			double x0 = block.getBlockBoundsMinX();
			double y0 = block.getBlockBoundsMinY();
			double z0 = block.getBlockBoundsMinZ();
			double x1 = block.getBlockBoundsMaxX();
			double y1 = block.getBlockBoundsMaxY();
			double z1 = block.getBlockBoundsMaxZ();

			if (x0 == 0.0D && y0 == 0.0D && z0 == 0.0D && x1 == 1.0D && y1 == 1.0D && z1 == 1.0D) return;

			this.boxMinX = x0;
			this.boxMinY = y0;
			this.boxMinZ = z0;
			this.boxMaxX = x1;
			this.boxMaxY = y1;
			this.boxMaxZ = z1;
			this.boxFull = false;
		} finally {
			// 包围盒挂在 Block 实例上，是全局状态，不还原会污染别的渲染
			block.setBlockBounds((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
		}
	}

	/**
	 * 非满格方块：每格画真实包围盒的 12 条棱，追加到同一批 GL_LINES 里。
	 * 顺序与 buildHull 一致（方块正序），自适应预算照旧裁尾部。
	 */
	private void buildBoxes(int n) {
		this.boxSegments = EMPTY_BOXES;
		this.boxCount = 0;
		this.hullSegments = EMPTY_SEGMENTS;
		this.hullCount = 0;

		// 每格固定 12 段，单次绘制最多 min(renderBudget, cap) 段，超出的尾块永远画不到就不生成
		int blocks = Math.min(n, (maxRenderBudget() + 11) / 12);

		if (blocks <= 0) return;

		double minX = this.boxMinX;
		double minY = this.boxMinY;
		double minZ = this.boxMinZ;
		double maxX = this.boxMaxX;
		double maxY = this.boxMaxY;
		double maxZ = this.boxMaxZ;

		// 每格 72 个 double，精确分配无需扩容
		double[] out = new double[blocks * 72];
		int count = 0;

		for (int i = 0; i < blocks; i++) {
			ChunkCoordinates c = this.cachedBlocks.get(i);

			double x0 = c.posX + minX;
			double y0 = c.posY + minY;
			double z0 = c.posZ + minZ;
			double x1 = c.posX + maxX;
			double y1 = c.posY + maxY;
			double z1 = c.posZ + maxZ;

			// 上下两个矩形
			count = addEdge(out, count, x0, y0, z0, x1, y0, z0);
			count = addEdge(out, count, x1, y0, z0, x1, y0, z1);
			count = addEdge(out, count, x1, y0, z1, x0, y0, z1);
			count = addEdge(out, count, x0, y0, z1, x0, y0, z0);
			count = addEdge(out, count, x0, y1, z0, x1, y1, z0);
			count = addEdge(out, count, x1, y1, z0, x1, y1, z1);
			count = addEdge(out, count, x1, y1, z1, x0, y1, z1);
			count = addEdge(out, count, x0, y1, z1, x0, y1, z0);
			// 4 根竖棱
			count = addEdge(out, count, x0, y0, z0, x0, y1, z0);
			count = addEdge(out, count, x1, y0, z0, x1, y1, z0);
			count = addEdge(out, count, x1, y0, z1, x1, y1, z1);
			count = addEdge(out, count, x0, y0, z1, x0, y1, z1);
		}

		this.boxSegments = out;
		this.boxCount = count / 6;
	}

	// 追加一段线段：调用方已按 blocks * 72 分配够容量
	private static int addEdge(double[] out, int count, double x0, double y0, double z0, double x1, double y1, double z1) {
		out[count++] = x0;
		out[count++] = y0;
		out[count++] = z0;
		out[count++] = x1;
		out[count++] = y1;
		out[count++] = z1;
		return count;
	}

	/**
	 * 只保留几何体的外壳棱，两类线丢弃：被 4 个方块围住的体积内部棱，
	 * 以及表面在同一平面延续的面内网格线（2 格在 2x2 相邻）。
	 * 线段按方块正序生成，因此自适应预算裁掉的是尾部，与放置顺序一致。
	 */
	private void buildHull(int n) {
		this.hullSegments = EMPTY_SEGMENTS;
		this.hullCount = 0;

		if (n <= 0) return;

		Set<Long> occupied = new HashSet<>(Math.max(16, n * 2));

		for (int i = 0; i < n; i++) {
			ChunkCoordinates c = this.cachedBlocks.get(i);
			occupied.add(packKey(c.posX, c.posY, c.posZ, 3));
		}

		Set<Long> checked = new HashSet<>(Math.max(16, n * 4));
		// 外壳线段数远小于方块数（薄片约等于周长），先给一个够用的缓冲，不够再翻倍
		int[] out = new int[Math.max(1024, n * 4)];
		int count = 0;

		for (int i = 0; i < n; i++) {
			ChunkCoordinates c = this.cachedBlocks.get(i);

			for (int axis = 0; axis < 3; axis++) {
				for (int du = 0; du <= 1; du++) {
					for (int dv = 0; dv <= 1; dv++) {
						int ex = c.posX;
						int ey = c.posY;
						int ez = c.posZ;

						if (axis == 0) {
							ey = c.posY + du;
							ez = c.posZ + dv;
						} else if (axis == 1) {
							ex = c.posX + du;
							ez = c.posZ + dv;
						} else {
							ex = c.posX + du;
							ey = c.posY + dv;
						}

						// 同一条棱会被相邻方块重复遇到，只判定一次
						if (!checked.add(packKey(ex, ey, ez, axis))) continue;

						int mask = 0;

						for (int a = 0; a <= 1; a++) {
							for (int b = 0; b <= 1; b++) {
								int cx;
								int cy;
								int cz;

								if (axis == 0) {
									cx = ex;
									cy = ey - 1 + a;
									cz = ez - 1 + b;
								} else if (axis == 1) {
									cy = ey;
									cx = ex - 1 + a;
									cz = ez - 1 + b;
								} else {
									cz = ez;
									cx = ex - 1 + a;
									cy = ey - 1 + b;
								}

								if (occupied.contains(packKey(cx, cy, cz, 3))) mask |= 1 << (a * 2 + b);
							}
						}

						// 4 格全在 => 体积内部的棱
						if (mask == 15) continue;
						// 恰好 2 格且在 2x2 里共边 => 表面在同一平面延续，是面内网格线
						if (mask == 3 || mask == 5 || mask == 10 || mask == 12) continue;

						int fx = ex;
						int fy = ey;
						int fz = ez;

						if (axis == 0) {
							fx = ex + 1;
						} else if (axis == 1) {
							fy = ey + 1;
						} else {
							fz = ez + 1;
						}

						if (count + 6 > out.length) out = java.util.Arrays.copyOf(out, out.length * 2);

						out[count++] = ex;
						out[count++] = ey;
						out[count++] = ez;
						out[count++] = fx;
						out[count++] = fy;
						out[count++] = fz;
					}
				}
			}
		}

		this.hullSegments = out;
		this.hullCount = count / 6;
	}

	/**
	 * 坐标压缩：2 位轴标记 + 22 位 x + 22 位 z + 14 位 y。
	 * 轴 0/1/2 表示棱，轴 3 表示方块占用，两者不会撞键。
	 */
	private static long packKey(int x, int y, int z, int axis) {
		return ((long) (axis & 3) << 58)
			| (((long) (x & 0x3FFFFF)) << 36)
			| (((long) (z & 0x3FFFFF)) << 14)
			| ((long) (y & 0x3FFF));
	}
}
