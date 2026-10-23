package moze_intel.projecte.utils;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 建筑之杖的单层撤销记录，随玩家 NBT 持久化。
 * 每格记录实际付费来源，撤销时按来源逐格退还。
 */
public class WandUndoProps implements IExtendedEntityProperties {
	public static final String PROP_NAME = "ProjectEWandUndo";

	private static final String TAG_RECORDS = "Records";
	private static final String TAG_X = "X";
	private static final String TAG_Y = "Y";
	private static final String TAG_Z = "Z";
	private static final String TAG_PREV_BLOCK = "PrevBlock";
	private static final String TAG_PREV_META = "PrevMeta";
	private static final String TAG_PLACED_BLOCK = "PlacedBlock";
	private static final String TAG_PLACED_META = "PlacedMeta";
	private static final String TAG_DIM = "Dim";
	private static final String TAG_PAY = "Pay";

	private final EntityPlayer player;
	private final List<Placement> records = new ArrayList<>();

	/**
	 * 一格放置记录，方块一律存注册名，跨存档/跨 mod 变更也能识别。
	 */
	public static class Placement {
		public final int posX;
		public final int posY;
		public final int posZ;
		public final String prevBlockName;
		public final int prevMeta;
		public final String placedBlockName;
		public final int placedMeta;
		public final int dimensionId;
		public final int paySource;

		private Placement(int posX, int posY, int posZ, String prevBlockName, int prevMeta,
		                  String placedBlockName, int placedMeta, int dimensionId, int paySource) {
			this.posX = posX;
			this.posY = posY;
			this.posZ = posZ;
			this.prevBlockName = prevBlockName;
			this.prevMeta = prevMeta;
			this.placedBlockName = placedBlockName;
			this.placedMeta = placedMeta;
			this.dimensionId = dimensionId;
			this.paySource = paySource;
		}
	}

	/**
	 * 照抄 TransmutationProps 的挂载方式，由 {@link Handler} 在实体构造时调用。
	 */
	public static void register(EntityPlayer player) {
		player.registerExtendedProperties(PROP_NAME, new WandUndoProps(player));
	}

	public static WandUndoProps getDataFor(EntityPlayer player) {
		IExtendedEntityProperties props = player.getExtendedProperties(PROP_NAME);
		return props instanceof WandUndoProps wand ? wand : null;
	}

	public WandUndoProps(EntityPlayer player) {
		this.player = player;
	}

	public List<Placement> getRecords() {
		return records;
	}

	public boolean isEmpty() {
		return records.isEmpty();
	}

	public void clear() {
		records.clear();
	}

	/**
	 * 记一格；方块未注册则无法还原，直接丢弃这条记录。
	 */
	public void add(int posX, int posY, int posZ, Block prevBlock, int prevMeta, Block placedBlock, int placedMeta, int dimensionId, int paySource) {
		String prevName = Block.blockRegistry.getNameForObject(prevBlock);
		String placedName = Block.blockRegistry.getNameForObject(placedBlock);

		if (prevName == null || placedName == null) return;

		records.add(new Placement(posX, posY, posZ, prevName, prevMeta, placedName, placedMeta, dimensionId, paySource));
	}

	/**
	 * 注册名反查方块，失败返回 null（调用方跳过该条）。
	 */
	public static Block getBlock(String registryName) {
		Object obj = Block.blockRegistry.getObject(registryName);
		return obj instanceof Block block ? block : null;
	}

	@Override
	public void saveNBTData(NBTTagCompound playerData) {
		NBTTagCompound data = new NBTTagCompound();
		NBTTagList list = new NBTTagList();

		for (Placement record : records) {
			NBTTagCompound tag = new NBTTagCompound();
			tag.setInteger(TAG_X, record.posX);
			tag.setInteger(TAG_Y, record.posY);
			tag.setInteger(TAG_Z, record.posZ);
			tag.setString(TAG_PREV_BLOCK, record.prevBlockName);
			tag.setInteger(TAG_PREV_META, record.prevMeta);
			tag.setString(TAG_PLACED_BLOCK, record.placedBlockName);
			tag.setInteger(TAG_PLACED_META, record.placedMeta);
			tag.setInteger(TAG_DIM, record.dimensionId);
			tag.setInteger(TAG_PAY, record.paySource);
			list.appendTag(tag);
		}

		data.setTag(TAG_RECORDS, list);
		playerData.setTag(PROP_NAME, data);
	}

	@Override
	public void loadNBTData(NBTTagCompound playerData) {
		records.clear();

		NBTTagCompound data = playerData.getCompoundTag(PROP_NAME);
		NBTTagList list = data.getTagList(TAG_RECORDS, Constants.NBT.TAG_COMPOUND);

		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			String prevName = tag.getString(TAG_PREV_BLOCK);
			String placedName = tag.getString(TAG_PLACED_BLOCK);

			// 反序列化失败（方块已被移除）就跳过该条
			if (getBlock(prevName) == null || getBlock(placedName) == null) continue;

			records.add(new Placement(tag.getInteger(TAG_X), tag.getInteger(TAG_Y), tag.getInteger(TAG_Z),
				prevName, tag.getInteger(TAG_PREV_META), placedName, tag.getInteger(TAG_PLACED_META),
				tag.getInteger(TAG_DIM), tag.getInteger(TAG_PAY)));
		}
	}

	@Override
	public void init(Entity entity, World world) {}

	/**
	 * 镜像 PlayerEvents.onConstruct 里的挂载点，由 PECore 注册到 Forge 事件总线。
	 */
	public static class Handler {
		@SubscribeEvent
		public void onEntityConstructing(EntityEvent.EntityConstructing event) {
			if (event.entity instanceof EntityPlayer player && !(player instanceof FakePlayer)) {
				register(player);
			}
		}

		// 照抄 PlayerEvents.onPlayerClone：死亡重生的新实体会丢掉 props，需要手动搬一次
		@SubscribeEvent
		public void onPlayerClone(PlayerEvent.Clone event) {
			if (!event.wasDeath) return;

			WandUndoProps original = getDataFor(event.original);
			WandUndoProps cloned = getDataFor(event.entityPlayer);

			if (original == null || cloned == null) return;

			NBTTagCompound data = new NBTTagCompound();
			original.saveNBTData(data);
			cloned.loadNBTData(data);
		}
	}
}
