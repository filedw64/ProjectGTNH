package moze_intel.projecte.gameObjs.items;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import moze_intel.projecte.api.item.IExtraFunction;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.item.IExtraFunction;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.Constants;
import moze_intel.projecte.utils.EMCHelper;
import moze_intel.projecte.utils.PlayerHelper;

import java.util.ArrayList;
import java.util.List;

public class MercurialEye extends ItemMode implements IExtraFunction {

	public static final int NORMAL_MODE = 0;
	public static final int TRANSMUTATION_MODE = 1;
	public static final int COPY_PASTE_MODE = 2;
	public static final int CUT_MOVE_MODE = 3;

	public MercurialEye() {
		super("mercurial_eye", (byte)4, new String[] {"Normal", "Transmutation", "Copy/Paste", "Cut/Move"});
		this.setNoRepair();
	}

	public BuildShape getBuildShape(ItemStack stack) {
		if (stack.hasTagCompound() && stack.getTagCompound().hasKey("BuildShape")) {
			int ordinal = stack.getTagCompound().getByte("BuildShape");
			if (ordinal >= 0 && ordinal < BuildShape.values().length) {
				return BuildShape.values()[ordinal];
			}
		}
		return BuildShape.CUBE;
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
		if (!world.isRemote && entity instanceof EntityPlayer) {
			NBTTagCompound nbt = stack.getTagCompound();
			if (nbt != null && nbt.hasKey("AnchorDimID")) {
				if (nbt.getInteger("AnchorDimID") != world.provider.dimensionId) {
					clearSelection(stack, (EntityPlayer) entity);
					((EntityPlayer) entity).addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "维度切换，墨丘利之眼选区已重置。"));
				}
			}
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
		if (!player.worldObj.isRemote) {
			MercurialClipboard.clearClipboard(player.getUniqueID());
		}
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (world.isRemote) return stack;
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt == null) { nbt = new NBTTagCompound(); stack.setTagCompound(nbt); }

		if (player.isSneaking()) {
			clearSelection(stack, player);
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Mercurial Eye 重置完毕。"));
			return stack;
		}

		int mode = this.getMode(stack);
		if ((mode == COPY_PASTE_MODE || mode == CUT_MOVE_MODE) && nbt.getBoolean("HasClipboard")) {
			executePaste(stack, (EntityPlayerMP) player, world);
			return stack;
		}

		// 原版的隔空射线追踪逻辑 (主要用于模式 0 和 1)
		if (mode == NORMAL_MODE || mode == TRANSMUTATION_MODE) {
			Vec3 eyePos = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
			Vec3 lookVec = player.getLookVec();
			int targetX, targetY, targetZ;

			if (!nbt.hasKey("AnchorX")) {
				targetX = (int) Math.round(eyePos.xCoord + lookVec.xCoord * 5);
				targetY = (int) Math.round(eyePos.yCoord + lookVec.yCoord * 5);
				targetZ = (int) Math.round(eyePos.zCoord + lookVec.zCoord * 5);
			} else {
				int anchorX = nbt.getInteger("AnchorX");
				int anchorY = nbt.getInteger("AnchorY");
				int anchorZ = nbt.getInteger("AnchorZ");
				RayTraceMath.Plane plane;
				double planeVal;
				double absX = Math.abs(lookVec.xCoord); double absY = Math.abs(lookVec.yCoord); double absZ = Math.abs(lookVec.zCoord);

				if (absX > absY && absX > absZ) { plane = RayTraceMath.Plane.X; planeVal = anchorX; }
				else if (absY > absX && absY > absZ) { plane = RayTraceMath.Plane.Y; planeVal = anchorY; }
				else { plane = RayTraceMath.Plane.Z; planeVal = anchorZ; }

				Vec3 hit = RayTraceMath.getIntersection(eyePos, lookVec, plane, planeVal);
				if (hit != null) {
					targetX = (int) Math.round(hit.xCoord);
					targetY = (int) Math.round(hit.yCoord);
					targetZ = (int) Math.round(hit.zCoord);
				} else {
					targetX = (int) Math.round(eyePos.xCoord + lookVec.xCoord * 5);
					targetY = (int) Math.round(eyePos.yCoord + lookVec.yCoord * 5);
					targetZ = (int) Math.round(eyePos.zCoord + lookVec.zCoord * 5);
				}
			}
			this.onItemUse(stack, player, world, targetX, targetY, targetZ, -1, 0, 0, 0);
		}
		return stack;
	}

	@Override
	public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
		if (world.isRemote) return true;
		NBTTagCompound nbt = stack.getTagCompound();
		if (nbt == null) { nbt = new NBTTagCompound(); stack.setTagCompound(nbt); }

		if (player.isSneaking()) {
			clearSelection(stack, player);
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.YELLOW + "Mercurial Eye 重置完毕。"));
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
			executePaste(stack, (EntityPlayerMP) player, world);
			return true;
		}

		// 模式 0 和 1 的前置检查：是否在 GUI 里选了方块
		ItemStack targetItem = nbt.hasKey("TargetItem") ? ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("TargetItem")) : null;
		if ((mode == NORMAL_MODE || mode == TRANSMUTATION_MODE) && (targetItem == null || Block.getBlockFromItem(targetItem.getItem()) == Blocks.air)) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "Please select a target block via Radial Menu."));
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
				player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Point 1 Set. Click to set base area."));
			} else {
				player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Point 1 Set. Click to build/select."));
			}
			return true;
		}

		int startX = nbt.getInteger("AnchorX");
		int startY = nbt.getInteger("AnchorY");
		int startZ = nbt.getInteger("AnchorZ");

		// 设置中点 (部分形状需要 3 个点)
		if (reqClicks == 3 && !nbt.hasKey("MidX")) {
			nbt.setInteger("MidX", x); nbt.setInteger("MidY", y); nbt.setInteger("MidZ", z);
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Point 2 Set. Click to set height and build."));
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
			captureRegion(stack, (EntityPlayerMP) player, world, startX, startY, startZ, endX, endY, endZ, mode == CUT_MOVE_MODE);
		} else {
			nbt.removeTag("AnchorX"); nbt.removeTag("AnchorY"); nbt.removeTag("AnchorZ");
			nbt.removeTag("AnchorDimID");
			nbt.removeTag("MidX"); nbt.removeTag("MidY"); nbt.removeTag("MidZ");
			executeBulkBuild(stack, (EntityPlayerMP) player, world, startX, startY, startZ, endX, endY, endZ, targetItem);
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

		if (sizeX * sizeY * sizeZ > 100000) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "选区过大！最大允许体积为 100,000。"));
			return;
		}

		ClipboardData clipboard = new ClipboardData(sizeX, sizeY, sizeZ);

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					Block block = world.getBlock(x, y, z);
					if (block == Blocks.air) continue;

					int meta = world.getBlockMetadata(x, y, z);
					TileEntity te = world.getTileEntity(x, y, z);
					NBTTagCompound teData = null;
					if (te != null) {
						teData = new NBTTagCompound();
						te.writeToNBT(teData);
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
						if (te != null) world.removeTileEntity(x, y, z);
						world.setBlock(x, y, z, Blocks.air, 0, 2);
					}
				}
			}
		}

		MercurialClipboard.saveClipboard(player.getUniqueID(), clipboard);
		NBTTagCompound nbt = stack.getTagCompound();
		nbt.setBoolean("HasClipboard", true);

		nbt.setInteger("ClipSizeX", sizeX);
		nbt.setInteger("ClipSizeY", sizeY);
		nbt.setInteger("ClipSizeZ", sizeZ);

		nbt.setInteger("LocalMinX", minX - anchorX);
		nbt.setInteger("LocalMaxX", maxX - anchorX);
		nbt.setInteger("LocalMinY", minY - anchorY);
		nbt.setInteger("LocalMaxY", maxY - anchorY);
		nbt.setInteger("LocalMinZ", minZ - anchorZ);
		nbt.setInteger("LocalMaxZ", maxZ - anchorZ);

		TransformMatrix matrix = new TransformMatrix();
		matrix.writeToNBT(nbt);

		player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.AQUA + (isCut ? "剪切" : "复制") + "完毕。"));
		nbt.removeTag("AnchorX"); nbt.removeTag("AnchorY"); nbt.removeTag("AnchorZ");
	}

	private void executePaste(ItemStack stack, EntityPlayerMP player, World world) {
		ClipboardData clipboard = MercurialClipboard.getClipboard(player.getUniqueID());
		if (clipboard == null || clipboard.blocks.isEmpty()) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "剪贴板为空！"));
			stack.getTagCompound().setBoolean("HasClipboard", false);
			return;
		}

		NBTTagCompound nbt = stack.getTagCompound();
		int pasteX = nbt.hasKey("PasteX") ? nbt.getInteger("PasteX") : (int) player.posX;
		int pasteY = nbt.hasKey("PasteY") ? nbt.getInteger("PasteY") : (int) player.posY;
		int pasteZ = nbt.hasKey("PasteZ") ? nbt.getInteger("PasteZ") : (int) player.posZ;

		TransformMatrix matrix = new TransformMatrix();
		matrix.readFromNBT(nbt);

		double totalEmcCost = clipboard.getTotalEmcCost() * matrix.stackCount;
		double currentEmc = Transmutation.getEmc(player);
		if (currentEmc < totalEmcCost) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "EMC 不足！需要: " + totalEmcCost));
			return;
		}

		int[] rotBounds = matrix.getRotatedBounds(
			nbt.getInteger("LocalMinX"), nbt.getInteger("LocalMinY"), nbt.getInteger("LocalMinZ"),
			nbt.getInteger("LocalMaxX"), nbt.getInteger("LocalMaxY"), nbt.getInteger("LocalMaxZ")
		);
		int rotSizeX = rotBounds[3] - rotBounds[0] + 1;
		int rotSizeY = rotBounds[4] - rotBounds[1] + 1;
		int rotSizeZ = rotBounds[5] - rotBounds[2] + 1;

		int blocksPlaced = 0;

		for (int step = 0; step < matrix.stackCount; step++) {
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

				world.setBlock(targetX, targetY, targetZ, info.block, rotatedMeta, 2);

				if (rotatedNbt != null) {
					rotatedNbt.setInteger("x", targetX);
					rotatedNbt.setInteger("y", targetY);
					rotatedNbt.setInteger("z", targetZ);
					TileEntity te = TileEntity.createAndLoadEntity(rotatedNbt);
					if (te != null) {
						world.setTileEntity(targetX, targetY, targetZ, te);
					}
				}
				blocksPlaced++;
			}
		}

		if (totalEmcCost > 0) {
			Transmutation.setEmc(player, currentEmc - totalEmcCost);
			Transmutation.sync(player);
		}

		player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.AQUA + "已放置 " + blocksPlaced + " 个方块。"));
		world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);

		if (this.getMode(stack) == CUT_MOVE_MODE) {
			clearSelection(stack, player);
		}
	}

	// 恢复：原版的模式 0 和 1 的批量建造逻辑
	private void executeBulkBuild(ItemStack eye, EntityPlayerMP player, World world, int startX, int startY, int startZ, int endX, int endY, int endZ, ItemStack targetItem) {
		Block newBlock = Block.getBlockFromItem(targetItem.getItem());
		int newMeta = targetItem.getItemDamage();
		double reqEmc = EMCHelper.getEmcValue(targetItem);
		if (reqEmc <= 0) return;

		int minX = Math.min(startX, endX); int maxX = Math.max(startX, endX);
		int minY = Math.min(startY, endY); int maxY = Math.max(startY, endY);
		int minZ = Math.min(startZ, endZ); int maxZ = Math.max(startZ, endZ);

		if ((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) > 8192) {
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.RED + "Area too large! Max volume is 8192 blocks."));
			return;
		}

		double[] currentEmc = new double[] { Transmutation.getEmc(player) };
		int mode = this.getMode(eye);
		BuildShape shape = getBuildShape(eye);
		List<MercurialHistory.BlockChange> historyChanges = new ArrayList<>();

		if (shape == BuildShape.LINE) {
			int dist = (int) Math.ceil(Math.sqrt(Math.pow(endX - startX, 2) + Math.pow(endY - startY, 2) + Math.pow(endZ - startZ, 2)));
			for (int i = 0; i <= dist; i++) {
				double t = dist == 0 ? 0 : (double) i / dist;
				int px = (int) Math.round(startX + (endX - startX) * t);
				int py = (int) Math.round(startY + (endY - startY) * t);
				int pz = (int) Math.round(startZ + (endZ - startZ) * t);
				MercurialHistory.BlockChange bc = tryPlaceBlock(world, player, mode, px, py, pz, newBlock, newMeta, reqEmc, currentEmc);
				if (bc != null) historyChanges.add(bc);
			}
		} else {
			for (int x = minX; x <= maxX; x++) {
				for (int y = minY; y <= maxY; y++) {
					for (int z = minZ; z <= maxZ; z++) {
						if (!shape.isBlockInShape(x, y, z, startX, startY, startZ, endX, endY, endZ)) continue;
						MercurialHistory.BlockChange bc = tryPlaceBlock(world, player, mode, x, y, z, newBlock, newMeta, reqEmc, currentEmc);
						if (bc != null) historyChanges.add(bc);
					}
				}
			}
		}

		if (!historyChanges.isEmpty()) {
			MercurialHistory.addRecord(player, historyChanges);
			Transmutation.setEmc(player, currentEmc[0]);
			Transmutation.sync(player);
			world.playSoundAtEntity(player, "projecte:item.pepower", 1.0F, 1.0F);
			player.addChatComponentMessage(new ChatComponentText(EnumChatFormatting.AQUA + "Placed " + historyChanges.size() + " blocks."));
		}
	}

	private MercurialHistory.BlockChange tryPlaceBlock(World world, EntityPlayerMP player, int mode, int x, int y, int z, Block newBlock, int newMeta, double reqEmc, double[] currentEmc) {
		Block oldBlock = world.getBlock(x, y, z);
		int oldMeta = world.getBlockMetadata(x, y, z);

		if (mode == NORMAL_MODE) {
			if (!oldBlock.isReplaceable(world, x, y, z)) return null;
			if (currentEmc[0] < reqEmc) return null;
			if (PlayerHelper.hasBreakPermission(player, x, y, z)) {
				world.setBlock(x, y, z, newBlock, newMeta, 2);
				currentEmc[0] -= reqEmc;
				return new MercurialHistory.BlockChange(x, y, z, oldBlock, oldMeta, newBlock, newMeta, reqEmc, null);
			}
		} else if (mode == TRANSMUTATION_MODE) {
			if (oldBlock == Blocks.air || (oldBlock == newBlock && oldMeta == newMeta)) return null;
			if (oldBlock.getBlockHardness(world, x, y, z) < 0.0F) return null;
			double oldEmc = 0;
			net.minecraft.item.Item oldItem = net.minecraft.item.Item.getItemFromBlock(oldBlock);
			if (oldItem != null) {
				ItemStack oldStack = new ItemStack(oldItem, 1, oldMeta);
				if (EMCHelper.doesItemHaveEmc(oldStack)) {
					oldEmc = EMCHelper.getEmcValue(oldStack);
				}
			}
			double emcDiff = reqEmc - oldEmc;
			if (currentEmc[0] < emcDiff) return null;
			if (PlayerHelper.hasBreakPermission(player, x, y, z)) {
				net.minecraft.tileentity.TileEntity oldTe = world.getTileEntity(x, y, z);
				net.minecraft.nbt.NBTTagCompound oldTeData = null;
				if (oldTe != null) {
					oldTeData = new net.minecraft.nbt.NBTTagCompound();
					oldTe.writeToNBT(oldTeData);
					world.removeTileEntity(x, y, z);
				}
				world.setBlock(x, y, z, newBlock, newMeta, 2);
				currentEmc[0] -= emcDiff;
				return new MercurialHistory.BlockChange(x, y, z, oldBlock, oldMeta, newBlock, newMeta, emcDiff, oldTeData);
			}
		}
		return null;
	}

	@Override
	public void doExtraFunction(ItemStack stack, EntityPlayer player) {}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) { return 1; }

	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register) {
		this.itemIcon = register.registerIcon(this.getTexture("mercurial_eye"));
	}
}
