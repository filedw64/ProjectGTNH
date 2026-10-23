package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.api.item.IExtraFunction;
import moze_intel.projecte.network.packets.MercurialNotifyPKT;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.BuildShape;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.PlayerHelper;
import moze_intel.projecte.utils.MercurialHistory;
import moze_intel.projecte.utils.RayTraceMath;
import moze_intel.projecte.utils.mercurial.ClipboardData;
import moze_intel.projecte.utils.mercurial.MercurialClipboard;
import moze_intel.projecte.utils.mercurial.StateRotatorRegistry;
import moze_intel.projecte.utils.mercurial.TransformMatrix;
import moze_intel.projecte.utils.mercurial.IStateRotator;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MercurialEye extends ItemMode implements IExtraFunction {

	public static final int NORMAL_MODE = 0;
	public static final int TRANSMUTATION_MODE = 1;
	public static final int COPY_PASTE_MODE = 2;
	public static final int CUT_MOVE_MODE = 3;
	// 建造与粘贴共用的单次体积上限，客户端预览也引用这一份，不要再写字面量
	public static final int MAX_BUILD_VOLUME = 32767;
	private static final BuildShape[] BUILD_SHAPES = BuildShape.values();

	public MercurialEye() {
		super("mercurial_eye", (byte)4, new String[] {"Normal", "Transmutation", "Copy/Paste", "Cut/Move"});
		this.setNoRepair();
	}

	public BuildShape getBuildShape(ItemStack stack) {
		if (stack.hasTagCompound() && stack.getTagCompound().hasKey("BuildShape")) {
			int ordinal = stack.getTagCompound().getByte("BuildShape");
			if (ordinal >= 0 && ordinal < BUILD_SHAPES.length) {
				return BUILD_SHAPES[ordinal];
			}
		}
		return BuildShape.CUBE;
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
		// 背包里每个槽位每 tick 都会走到这里，而选区只可能在手持时产生和使用：先判 isHeld 再碰 NBT
		if (!isHeld || world.isRemote) return;
		if (!(entity instanceof EntityPlayerMP playerMP)) return;

		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt == null || !nbt.hasKey("AnchorDimID")) return;

		if (nbt.getInteger("AnchorDimID") != world.provider.dimensionId) {
			clearSelection(stack, playerMP);
			MercurialHistory.sendNotify(playerMP, "item.pe_mercurial_eye.msg.dim_changed", MercurialNotifyPKT.LEVEL_WARN);
		}
	}

	private void clearSelection(ItemStack stack, EntityPlayer player) {
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt != null) {
			nbt.removeTag("AnchorX"); nbt.removeTag("AnchorY"); nbt.removeTag("AnchorZ");
			nbt.removeTag("AnchorDimID");
			nbt.removeTag("MidX"); nbt.removeTag("MidY"); nbt.removeTag("MidZ");
			nbt.removeTag("HasClipboard");
		}
		// 用户主动重置选区：连存档一起清，免得下次登录又把旧选区拉回来
		if (!player.worldObj.isRemote) {
			MercurialClipboard.clearClipboard(player.getUniqueID());
		}
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (world.isRemote) return stack;
		if (!(player instanceof EntityPlayerMP playerMP)) return stack;
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt == null) { nbt = new NBTTagCompound(); stack.setTagCompound(nbt); }

		if (player.isSneaking()) {
			clearSelection(stack, playerMP);
			MercurialHistory.sendNotify(playerMP, "item.pe_mercurial_eye.msg.reset", MercurialNotifyPKT.LEVEL_INFO);
			return stack;
		}

		int mode = this.getMode(stack);
		if ((mode == COPY_PASTE_MODE || mode == CUT_MOVE_MODE) && nbt.getBoolean("HasClipboard")) {
			executePaste(stack, playerMP, world);
			return stack;
		}

		// 原版的隔空射线追踪逻辑 (主要用于模式 0 和 1)
		if (mode == NORMAL_MODE || mode == TRANSMUTATION_MODE) {
			Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
			Vec3 lookVec = player.getLookVec();
			int targetX, targetY, targetZ;

			// 隔空选点统一走 RayTraceMath：命中/未命中都落在 targetX/Y/Z（未命中时已在 getPlaneTarget 内退回前方 5 格）
			if (!nbt.hasKey("AnchorX")) {
				int[] forward = RayTraceMath.getForwardPoint(eyePos, lookVec, 5);
				targetX = forward[0]; targetY = forward[1]; targetZ = forward[2];
			} else {
				int[] target = RayTraceMath.getPlaneTarget(eyePos, lookVec, nbt.getInteger("AnchorX"), nbt.getInteger("AnchorY"), nbt.getInteger("AnchorZ"));
				targetX = target[0]; targetY = target[1]; targetZ = target[2];
			}
			this.onItemUse(stack, playerMP, world, targetX, targetY, targetZ, -1, 0, 0, 0);
		}
		return stack;
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		if (world.isRemote) return true;
		if (!(player instanceof EntityPlayerMP playerMP)) return true;
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt == null) { nbt = new NBTTagCompound(); stack.setTagCompound(nbt); }

		if (player.isSneaking()) {
			clearSelection(stack, playerMP);
			MercurialHistory.sendNotify(playerMP, "item.pe_mercurial_eye.msg.reset", MercurialNotifyPKT.LEVEL_INFO);
			return true;
		}

		int mode = this.getMode(stack);

		// 粘贴模式触发
		if ((mode == COPY_PASTE_MODE || mode == CUT_MOVE_MODE) && nbt.getBoolean("HasClipboard")) {
			if (side != -1) {
				ForgeDirection dir = ForgeDirection.getOrientation(side);
				nbt.setInteger("PasteX", x + dir.offsetX);
				nbt.setInteger("PasteY", y + dir.offsetY);
				nbt.setInteger("PasteZ", z + dir.offsetZ);
			} else {
				nbt.setInteger("PasteX", x);
				nbt.setInteger("PasteY", y);
				nbt.setInteger("PasteZ", z);
			}
			executePaste(stack, playerMP, world);
			return true;
		}

		// 模式 0 和 1 的前置检查：是否在 GUI 里选了方块
		ItemStack targetItem = nbt.hasKey("TargetItem") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("TargetItem")) : null;
		if ((mode == NORMAL_MODE || mode == TRANSMUTATION_MODE) && (targetItem == null || Block.getBlockFromItem(targetItem.getItem()) == Blocks.air)) {
			MercurialHistory.sendNotify(playerMP, "item.pe_mercurial_eye.msg.no_target", MercurialNotifyPKT.LEVEL_WARN);
			return true;
		}

		BuildShape shape = getBuildShape(stack);
		int reqClicks = (mode == COPY_PASTE_MODE || mode == CUT_MOVE_MODE) ? 2 : shape.getRequiredClicks();

		// 设置起点
		if (!nbt.hasKey("AnchorX")) {
			nbt.setInteger("AnchorX", x);
			nbt.setInteger("AnchorY", y);
			nbt.setInteger("AnchorZ", z);
			nbt.setInteger("AnchorDimID", world.provider.dimensionId);
			if (reqClicks == 3) {
				MercurialHistory.sendNotify(playerMP, "item.pe_mercurial_eye.msg.point1_area", MercurialNotifyPKT.LEVEL_INFO);
			} else {
				MercurialHistory.sendNotify(playerMP, "item.pe_mercurial_eye.msg.point1_ready", MercurialNotifyPKT.LEVEL_INFO);
			}
			return true;
		}

		int startX = nbt.getInteger("AnchorX");
		int startY = nbt.getInteger("AnchorY");
		int startZ = nbt.getInteger("AnchorZ");

		// 设置中点 (部分形状需要 3 个点)
		if (reqClicks == 3 && !nbt.hasKey("MidX")) {
			nbt.setInteger("MidX", x); nbt.setInteger("MidY", y); nbt.setInteger("MidZ", z);
			MercurialHistory.sendNotify(playerMP, "item.pe_mercurial_eye.msg.point2", MercurialNotifyPKT.LEVEL_INFO);
			return true;
		}

		int endX = x; int endY = y; int endZ = z;

		if (reqClicks == 3) {
			int midX = nbt.getInteger("MidX"); int midZ = nbt.getInteger("MidZ");
			startX = Math.min(startX, midX); startZ = Math.min(startZ, midZ);
			endX = Math.max(nbt.getInteger("AnchorX"), midX); endZ = Math.max(nbt.getInteger("AnchorZ"), midZ);
			startY = nbt.getInteger("AnchorY"); endY = y;
		}

		// 分发执行逻辑
		if (mode == COPY_PASTE_MODE || mode == CUT_MOVE_MODE) {
			captureRegion(stack, playerMP, world, startX, startY, startZ, endX, endY, endZ, mode == CUT_MOVE_MODE);
		} else {
			nbt.removeTag("AnchorX"); nbt.removeTag("AnchorY"); nbt.removeTag("AnchorZ");
			nbt.removeTag("AnchorDimID");
			nbt.removeTag("MidX"); nbt.removeTag("MidY"); nbt.removeTag("MidZ");
			executeBulkBuild(stack, playerMP, world, startX, startY, startZ, endX, endY, endZ, targetItem);
		}

		return true;
	}

	// 复制/剪切/粘贴逻辑
	private void captureRegion(ItemStack stack, EntityPlayerMP player, World world, int anchorX, int anchorY, int anchorZ, int x2, int y2, int z2, boolean isCut) {
		int minX = Math.min(anchorX, x2); int maxX = Math.max(anchorX, x2);
		int minY = Math.min(anchorY, y2); int maxY = Math.max(anchorY, y2);
		int minZ = Math.min(anchorZ, z2); int maxZ = Math.max(anchorZ, z2);

		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;

		// 先转 long 再相乘：远距离锚点下 int 乘法会溢出成负数从而绕过体积上限
		// 上限跟剪贴板容量对齐，扫更大的范围只会白扫一遍最后被丢弃的方块
		if ((long) sizeX * sizeY * sizeZ > ClipboardData.MAX_CLIPBOARD_BLOCKS) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.selection_too_large", MercurialNotifyPKT.LEVEL_ERROR,
				String.valueOf(ClipboardData.MAX_CLIPBOARD_BLOCKS));
			return;
		}

		ClipboardData clipboard = new ClipboardData(sizeX, sizeY, sizeZ);
		// 剪掉的方块要能撤销，否则一次误剪就永久丢方块
		List<MercurialHistory.BlockChange> cutChanges = new ArrayList<>();
		int skipped = 0;

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					Block block = world.getBlock(x, y, z);
					if (block == Blocks.air) continue;

					if (isCut) {
						// 没有破坏权限或基岩之类不可破坏的方块：既不捕获也不删除
						if (!PlayerHelper.hasBreakPermission(player, x, y, z) || block.getBlockHardness(world, x, y, z) < 0.0F) {
							skipped++;
							continue;
						}
					}

					int meta = world.getBlockMetadata(x, y, z);
					NBTTagCompound teData = null;
					// 只有剪切/移动才保存方块实体：源已经删除，不构成复制刷 EMC
					if (isCut) {
						TileEntity te = world.getTileEntity(x, y, z);
						if (te != null) {
							teData = new NBTTagCompound();
							te.writeToNBT(teData);
						}
					}

					double emcCost = 0;
					if (!isCut) {
						ItemStack drop = new ItemStack(block, 1, block.damageDropped(meta));
						if (EMCHelper.doesItemHaveEmc(drop)) {
							emcCost = EMCHelper.getEmcValue(drop);
						}
					}

					clipboard.addBlock(block, meta, teData, x - anchorX, y - anchorY, z - anchorZ, emcCost);

					if (isCut) {
						world.removeTileEntity(x, y, z);
						world.setBlock(x, y, z, Blocks.air, 0, 3);
						// newBlock 记空气、emcDiff 记 0：撤销只把方块放回去，绝不动 EMC
						cutChanges.add(new MercurialHistory.BlockChange(x, y, z, block, meta, Blocks.air, 0, 0, teData));
					}
				}
			}
		}

		NBTTagCompound nbt = stack.getTagCompound();

		// 所有方块都被跳过（例如整片区域都没有破坏权限）时不落盘，避免留下一个空剪贴板
		if (clipboard.blocks.isEmpty()) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.nothing_captured", MercurialNotifyPKT.LEVEL_WARN);
			nbt.removeTag("AnchorX"); nbt.removeTag("AnchorY"); nbt.removeTag("AnchorZ");
			nbt.removeTag("AnchorDimID");
			nbt.removeTag("MidX"); nbt.removeTag("MidY"); nbt.removeTag("MidZ");
			return;
		}

		// 以实际进入剪贴板的方块重算局部边界：一旦被数量上限截断，NBT 也不能再声称包含被丢弃的方块
		ClipboardData.BlockInfo firstBlock = clipboard.blocks.get(0);
		int clipMinX = firstBlock.localX; int clipMaxX = firstBlock.localX;
		int clipMinY = firstBlock.localY; int clipMaxY = firstBlock.localY;
		int clipMinZ = firstBlock.localZ; int clipMaxZ = firstBlock.localZ;
		for (ClipboardData.BlockInfo bi : clipboard.blocks) {
			if (bi.localX < clipMinX) clipMinX = bi.localX;
			if (bi.localX > clipMaxX) clipMaxX = bi.localX;
			if (bi.localY < clipMinY) clipMinY = bi.localY;
			if (bi.localY > clipMaxY) clipMaxY = bi.localY;
			if (bi.localZ < clipMinZ) clipMinZ = bi.localZ;
			if (bi.localZ > clipMaxZ) clipMaxZ = bi.localZ;
		}

		MercurialClipboard.saveClipboard(player.getUniqueID(), clipboard);
		// 剪贴板落盘之后再记撤销：掉线也不丢方块，撤销链也不会指向一个没存下来的剪贴板
		if (!cutChanges.isEmpty()) {
			MercurialHistory.addRecord(player, cutChanges);
		}
		nbt.setBoolean("HasClipboard", true);

		nbt.setInteger("ClipSizeX", clipMaxX - clipMinX + 1);
		nbt.setInteger("ClipSizeY", clipMaxY - clipMinY + 1);
		nbt.setInteger("ClipSizeZ", clipMaxZ - clipMinZ + 1);

		nbt.setInteger("LocalMinX", clipMinX);
		nbt.setInteger("LocalMaxX", clipMaxX);
		nbt.setInteger("LocalMinY", clipMinY);
		nbt.setInteger("LocalMaxY", clipMaxY);
		nbt.setInteger("LocalMinZ", clipMinZ);
		nbt.setInteger("LocalMaxZ", clipMaxZ);

		TransformMatrix matrix = new TransformMatrix();
		matrix.writeToNBT(nbt);

		String recorded = String.valueOf(clipboard.blocks.size());
		if (isCut) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.cut_done", MercurialNotifyPKT.LEVEL_INFO, recorded);
		} else {
			// 纯复制不保存方块实体，这句提示合并进 copy_done 文案，不再单独占一行 HUD
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.copy_done", MercurialNotifyPKT.LEVEL_INFO, recorded);
		}
		if (clipboard.blocks.size() >= ClipboardData.MAX_CLIPBOARD_BLOCKS) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.clipboard_truncated", MercurialNotifyPKT.LEVEL_WARN,
				String.valueOf(ClipboardData.MAX_CLIPBOARD_BLOCKS), recorded);
		}
		if (skipped > 0) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.cut_skipped", MercurialNotifyPKT.LEVEL_WARN, String.valueOf(skipped));
		}

		// 与建造路径保持一致地清锚点：否则 AnchorDimID 残留会让下次切维度时误清剪贴板
		nbt.removeTag("AnchorX"); nbt.removeTag("AnchorY"); nbt.removeTag("AnchorZ");
		nbt.removeTag("AnchorDimID");
		nbt.removeTag("MidX"); nbt.removeTag("MidY"); nbt.removeTag("MidZ");
	}

	private void executePaste(ItemStack stack, EntityPlayerMP player, World world) {
		ClipboardData clipboard = MercurialClipboard.getClipboard(player.getUniqueID());
		if (clipboard == null || clipboard.blocks.isEmpty()) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.clipboard_empty", MercurialNotifyPKT.LEVEL_WARN);
			stack.getTagCompound().setBoolean("HasClipboard", false);
			return;
		}

		NBTTagCompound nbt = stack.getTagCompound();
		int pasteX = nbt.hasKey("PasteX") ? nbt.getInteger("PasteX") : (int) player.posX;
		int pasteY = nbt.hasKey("PasteY") ? nbt.getInteger("PasteY") : (int) player.posY;
		int pasteZ = nbt.hasKey("PasteZ") ? nbt.getInteger("PasteZ") : (int) player.posZ;

		TransformMatrix matrix = new TransformMatrix();
		matrix.readFromNBT(nbt);

		// 服务端实际使用时再夹紧一次堆叠数，不信任 NBT 里的值
		int stackCount = Math.min(64, Math.max(1, matrix.stackCount));

		int[] rotBounds = matrix.getRotatedBounds(
			nbt.getInteger("LocalMinX"), nbt.getInteger("LocalMinY"), nbt.getInteger("LocalMinZ"),
			nbt.getInteger("LocalMaxX"), nbt.getInteger("LocalMaxY"), nbt.getInteger("LocalMaxZ")
		);
		int rotSizeX = rotBounds[3] - rotBounds[0] + 1;
		int rotSizeY = rotBounds[4] - rotBounds[1] + 1;
		int rotSizeZ = rotBounds[5] - rotBounds[2] + 1;

		// 按实际放下的方块逐块计费，不再一次性预扣
		double emcLeft = Transmutation.getEmc(player);
		double totalSpent = 0;
		int blocksPlaced = 0;
		boolean emcShortage = false;
		boolean capped = false;
		List<MercurialHistory.BlockChange> historyChanges = new ArrayList<>();

		outer:
		for (int step = 0; step < stackCount; step++) {
			int offsetX = matrix.stackAxis.offsetX * rotSizeX * step;
			int offsetY = matrix.stackAxis.offsetY * rotSizeY * step;
			int offsetZ = matrix.stackAxis.offsetZ * rotSizeZ * step;

			for (ClipboardData.BlockInfo info : clipboard.blocks) {
				int[] localPos = matrix.applyRotation(info.localX, info.localY, info.localZ);

				int targetX = pasteX + localPos[0] + offsetX;
				int targetY = pasteY + localPos[1] + offsetY;
				int targetZ = pasteZ + localPos[2] + offsetZ;

				if (!world.blockExists(targetX, targetY, targetZ)) continue;

				int rotatedMeta = info.meta;
				NBTTagCompound rotatedNbt = null;
				if (info.nbt != null) {
					rotatedNbt = (NBTTagCompound) info.nbt.copy();
				}

				IStateRotator rotator = StateRotatorRegistry.getRotator(info.block);
				if (rotator != null) {
					rotatedMeta = rotator.rotateMeta(info.block, info.meta, matrix.rotations);
					rotator.rotateNBT(info.block, rotatedMeta, rotatedNbt, matrix.rotations);
				}

				Block oldBlock = world.getBlock(targetX, targetY, targetZ);
				int oldMeta = world.getBlockMetadata(targetX, targetY, targetZ);

				// 目标已经是同方块同 metadata：不收费也不重复放置
				if (oldBlock == info.block && oldMeta == rotatedMeta) continue;

				// 单次粘贴总量上限：64 × 32768 这种合法配置否则会把服务端主线程卡死
				if (blocksPlaced >= MAX_BUILD_VOLUME) {
					capped = true;
					break outer;
				}

				// EMC 不够就停在这里，剩下的方块不收费也不放置
				if (emcLeft < info.emcCost) {
					emcShortage = true;
					break outer;
				}

				// 空气以外的方块都等于"破坏了原方块"，而 checkedPlaceBlock 只查 hasEditPermission：
				// 在别人领地内把高草/花/雪/水换成实心方块不会触发 BreakEvent，保护插件拦不住
				if (oldBlock != Blocks.air && !PlayerHelper.hasBreakPermission(player, targetX, targetY, targetZ)) continue;

				// 这里只做只读快照：writeToNBT 不改变世界，放置被拒绝时必须保证世界原样
				NBTTagCompound oldTeData = null;
				TileEntity oldTe = world.getTileEntity(targetX, targetY, targetZ);
				if (oldTe != null) {
					oldTeData = new NBTTagCompound();
					oldTe.writeToNBT(oldTeData);
				}

				// 空气或可替换方块走放置，其余走替换（两者内部都已含权限检查与 PlaceEvent）
				boolean placed;
				if (oldBlock == Blocks.air || oldBlock.isReplaceable(world, targetX, targetY, targetZ)) {
					placed = PlayerHelper.checkedPlaceBlock(player, targetX, targetY, targetZ, info.block, rotatedMeta);
				} else {
					placed = PlayerHelper.checkedReplaceBlock(player, targetX, targetY, targetZ, info.block, rotatedMeta);
				}
				if (!placed) continue;

				if (rotatedNbt != null) {
					rotatedNbt.setInteger("x", targetX);
					rotatedNbt.setInteger("y", targetY);
					rotatedNbt.setInteger("z", targetZ);
					TileEntity te = TileEntity.createAndLoadEntity(rotatedNbt);
					if (te != null) {
						world.setTileEntity(targetX, targetY, targetZ, te);
					}
				} else if (oldTe != null && !info.block.hasTileEntity(rotatedMeta)) {
					// 新方块自身没有方块实体：显式清掉旧的，避免残留成新方块底下的幽灵容器
					// （新方块自己带 TE 时不能清，否则会删掉 setBlock 刚创建的那个）
					world.removeTileEntity(targetX, targetY, targetZ);
				}

				emcLeft -= info.emcCost;
				totalSpent += info.emcCost;
				blocksPlaced++;
				historyChanges.add(new MercurialHistory.BlockChange(targetX, targetY, targetZ, oldBlock, oldMeta, info.block, rotatedMeta, info.emcCost, oldTeData));
			}
		}

		if (blocksPlaced > 0) {
			Transmutation.setEmc(player, emcLeft);
			Transmutation.sync(player);
			// 粘贴造成的破坏必须可以撤销
			MercurialHistory.addRecord(player, historyChanges);
			world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.pasted", MercurialNotifyPKT.LEVEL_INFO,
				String.valueOf(blocksPlaced), String.format(Locale.ROOT, "%.2f", totalSpent));
		}

		if (capped) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.paste_capped", MercurialNotifyPKT.LEVEL_WARN, String.valueOf(MAX_BUILD_VOLUME));
		}

		if (emcShortage) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.paste_no_emc", MercurialNotifyPKT.LEVEL_ERROR);
		}

		// capped / emcShortage 意味着粘贴没有做完：此时被剪掉的方块只活在剪贴板里，
		// 清掉等于永久丢失；保留后玩家补够 EMC 或腾出空间即可重试
		if (this.getMode(stack) == CUT_MOVE_MODE && !capped && !emcShortage) {
			clearSelection(stack, player);
		}
	}

	// 恢复：原版的模式 0 和 1 的批量建造逻辑
	private void executeBulkBuild(ItemStack eye, EntityPlayerMP player, World world, int startX, int startY, int startZ, int endX, int endY, int endZ, ItemStack targetItem) {
		// TargetItem 来自 NBT，可被直写成非方块物品；Block.getBlockFromItem 对非方块只会返回空气
		if (!(targetItem.getItem() instanceof ItemBlock)) return;

		Block newBlock = Block.getBlockFromItem(targetItem.getItem());
		int newMeta = targetItem.getItemDamage();
		double reqEmc = EMCHelper.getEmcValue(targetItem);
		if (reqEmc <= 0) return;

		int minX = Math.min(startX, endX); int maxX = Math.max(startX, endX);
		int minY = Math.min(startY, endY); int maxY = Math.max(startY, endY);
		int minZ = Math.min(startZ, endZ); int maxZ = Math.max(startZ, endZ);

		// 先转 long 再相乘，避免远距离锚点下 int 溢出成负数而绕过体积上限
		long volumeX = (long) maxX - minX + 1;
		long volumeY = (long) maxY - minY + 1;
		long volumeZ = (long) maxZ - minZ + 1;
		if (volumeX > MAX_BUILD_VOLUME || volumeY > MAX_BUILD_VOLUME || volumeZ > MAX_BUILD_VOLUME || volumeX * volumeY * volumeZ > MAX_BUILD_VOLUME) {
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.build_too_large", MercurialNotifyPKT.LEVEL_ERROR, String.valueOf(MAX_BUILD_VOLUME));
			return;
		}

		double[] currentEmc = new double[] { Transmutation.getEmc(player) };
		int mode = this.getMode(eye);
		BuildShape shape = getBuildShape(eye);
		List<MercurialHistory.BlockChange> historyChanges = new ArrayList<>();

		// 形状迭代与客户端预览共用 BuildShape.forEachBlock，避免两处再次分叉
		shape.forEachBlock(startX, startY, startZ, endX, endY, endZ, (x, y, z) -> {
			MercurialHistory.BlockChange bc = tryPlaceBlock(world, player, mode, x, y, z, newBlock, newMeta, reqEmc, currentEmc);
			if (bc != null) historyChanges.add(bc);
		});

		if (!historyChanges.isEmpty()) {
			MercurialHistory.addRecord(player, historyChanges);
			Transmutation.setEmc(player, currentEmc[0]);
			Transmutation.sync(player);
			world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
			MercurialHistory.sendNotify(player, "item.pe_mercurial_eye.msg.placed", MercurialNotifyPKT.LEVEL_INFO, String.valueOf(historyChanges.size()));
		}
	}

	private MercurialHistory.BlockChange tryPlaceBlock(World world, EntityPlayerMP player, int mode, int x, int y, int z, Block newBlock, int newMeta, double reqEmc, double[] currentEmc) {
		if (!world.blockExists(x, y, z)) return null;
		Block oldBlock = world.getBlock(x, y, z);
		int oldMeta = world.getBlockMetadata(x, y, z);

		if (mode == NORMAL_MODE) {
			if (!oldBlock.isReplaceable(world, x, y, z)) return null;
			if (currentEmc[0] < reqEmc) return null;
			// PlayerHelper 内部会触发 PlaceEvent，返回 false 说明被保护插件或事件取消
			if (PlayerHelper.checkedPlaceBlock(player, x, y, z, newBlock, newMeta)) {
				currentEmc[0] -= reqEmc;
				return new MercurialHistory.BlockChange(x, y, z, oldBlock, oldMeta, newBlock, newMeta, reqEmc, null);
			}
		} else if (mode == TRANSMUTATION_MODE) {
			if (oldBlock == Blocks.air || (oldBlock == newBlock && oldMeta == newMeta)) return null;
			// 带方块实体的方块不参与转化：NBT 内容无法计价，替换等于白送内容
			if (world.getTileEntity(x, y, z) != null) return null;
			if (oldBlock.getBlockHardness(world, x, y, z) < 0.0F) return null;
			Item oldItem = Item.getItemFromBlock(oldBlock);
			if (oldItem == null) return null;
			ItemStack oldStack = new ItemStack(oldItem, 1, oldMeta);
			if (!EMCHelper.doesItemHaveEmc(oldStack)) return null;
			double emcDiff = reqEmc - EMCHelper.getEmcValue(oldStack);
			if (currentEmc[0] < emcDiff) return null;
			// checkedReplaceBlock 内部已含 hasBreakPermission + PlaceEvent
			if (PlayerHelper.checkedReplaceBlock(player, x, y, z, newBlock, newMeta)) {
				currentEmc[0] -= emcDiff;
				return new MercurialHistory.BlockChange(x, y, z, oldBlock, oldMeta, newBlock, newMeta, emcDiff, null);
			}
		}
		return null;
	}

	@Override
	public void doExtraFunction(ItemStack stack, EntityPlayer player) {
		// 滚轮 GUI 由客户端 KeyPressEvent 直接打开，服务端无需处理
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) { return 1; }

	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register) {
		this.itemIcon = register.registerIcon(this.getTexture("mercurial_eye"));
	}
}
