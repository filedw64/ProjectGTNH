package moze_intel.projecte.gameObjs.customRecipes;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.RecipeSorter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** A startup-only, server-authoritative riddle recipe. Custom IRecipe stays out of NEI. */
public final class RecipeTome implements IRecipe
{
	public static final int SIZE = 9;
	private static volatile List<Ingredient> selected = Collections.emptyList();
	private static List<Ingredient> startupSelection = Collections.emptyList();

	public static void register()
	{
		List<Ingredient> pool = new ArrayList<>();
		vanilla(pool, "emerald", new ItemStack(Items.emerald));
		vanilla(pool, "diamond", new ItemStack(Items.diamond));
		vanilla(pool, "coal_ore", new ItemStack(Blocks.coal_ore));
		vanilla(pool, "end_stone", new ItemStack(Blocks.end_stone));
		vanilla(pool, "crafting_table", new ItemStack(Blocks.crafting_table));
		vanilla(pool, "glass", new ItemStack(Blocks.glass));
		vanilla(pool, "beacon", new ItemStack(Blocks.beacon));
		vanilla(pool, "record", new ItemStack(Items.record_11));
		vanilla(pool, "bed", new ItemStack(Items.bed));
		vanilla(pool, "bone", new ItemStack(Items.bone));
		add(pool, "stone", "等价交换", new ItemStack(ObjHandler.philosStone));
		add(pool, "matter", "等价交换", new ItemStack(ObjHandler.matter, 1, 1));
		add(pool, "arcana", "等价交换", new ItemStack(ObjHandler.arcana));
		add(pool, "bag", "等价交换", new ItemStack(ObjHandler.alchBag));
		add(pool, "table", "等价交换", new ItemStack(ObjHandler.transmuteStone));

		// Exact metadata identifies the seed and its ore names; matching ignores metadata/NBT.
		optional(pool, "beans", "暮色森林", "TwilightForest", "TwilightForest:magicBeans:0;TwilightForest:magicBean:0");
		optional(pool, "time_tree", "暮色森林", "TwilightForest", "TwilightForest:tile.TFSapling:5");
		optional(pool, "sword", "末影接口", "EnderIO", "EnderIO:itemEndSteelSword:0");
		optional(pool, "weather", "末影接口", "EnderIO", "EnderIO:blockWeatherObelisk:0");
		optional(pool, "slice", "末影接口", "EnderIO", "EnderIO:blockSliceAndSplice:0");
		optional(pool, "capacitor", "末影接口", "EnderIO", "EnderIO:itemBasicCapacitor:2");
		optional(pool, "circuit", "通用机械", "Mekanism", "Mekanism:ControlCircuit:3");
		optional(pool, "solar", "通用机械", "Mekanism&MekanismGenerators", "MekanismGenerators:Generator:5");
		optional(pool, "disassembler", "通用机械", "Mekanism", "Mekanism:AtomicDisassembler:0");
		optional(pool, "petal", "植物魔法", "Botania", "Botania:petal:0");
		optional(pool, "rainbow", "植物魔法", "Botania", "Botania:rainbowRod:0");
		optional(pool, "sins", "植物魔法", "Botania", "Botania:rune:9");
		optional(pool, "overgrowth", "植物魔法", "Botania", "Botania:overgrowthSeed:0");
		optional(pool, "bottle", "植物魔法", "Botania", "Botania:manaBottle:0");
		optional(pool, "head", "植物魔法", "Botania", "minecraft:skull:3");
		optional(pool, "brewery", "植物魔法", "Botania", "Botania:brewery:0");
		optional(pool, "controller", "应用能源2", "appliedenergistics2", "appliedenergistics2:tile.BlockController:0");
		optional(pool, "press", "应用能源2", "appliedenergistics2", "appliedenergistics2:item.ItemMultiMaterial:13");
		optional(pool, "singularity", "应用能源2", "appliedenergistics2", "appliedenergistics2:item.ItemMultiMaterial:47");
		optional(pool, "interface", "应用能源2", "appliedenergistics2", "appliedenergistics2:tile.BlockInterface:0");
		optional(pool, "robot", "建筑", "BuildCraft|Robotics", "BuildCraft|Robotics:robot:0");
		optional(pool, "heart", "龙之研究", "DraconicEvolution", "DraconicEvolution:dragonHeart:0");
		optional(pool, "chaos", "龙之研究", "DraconicEvolution", "DraconicEvolution:chaosShard:0");
		optional(pool, "reactor", "龙之研究", "DraconicEvolution", "DraconicEvolution:reactorCore:0");
		optional(pool, "eternal", "无尽贪婪", "eternalsingularity", "eternalsingularity:eternal_singularity:0");
		optional(pool, "asgard", "无尽贪婪 / 植物魔法", "Avaritia&Botania", "Botania:specialFlower:0");
		optional(pool, "akashic", "无尽贪婪 / 神秘时代4", "Avaritia&Thaumcraft", "Avaritia:Akashic_Record:0");
		optional(pool, "wand", "无尽贪婪 / 神秘时代4", "Avaritia&Thaumcraft", "Thaumcraft:WandCasting:9000");
		optional(pool, "neutronium", "无尽贪婪", "Avaritia", "Avaritia:Resource_Block:0");
		optional(pool, "infinity", "无尽贪婪", "Avaritia", "Avaritia:Resource:6");
		optional(pool, "armok", "无尽贪婪 / 血魔法", "Avaritia&AWWayofTime", "Avaritia:Orb_Armok:0");
		optional(pool, "essence", "极限末地生存", "HardcoreEnderExpansion", "HardcoreEnderExpansion:essence:0");
		optional(pool, "living", "极限末地生存", "HardcoreEnderExpansion", "HardcoreEnderExpansion:living_matter:0");
		optional(pool, "internet", "开放式电脑", "OpenComputers", "OpenComputers:item:48");
		optional(pool, "stargate", "星门", "SGCraft", "gcewing_sg:stargateBase:0;SGCraft:stargateBase:0");
		optional(pool, "red_heart", "匠魂", "TConstruct", "TConstruct:heartCanister:1");
		optional(pool, "pig_iron", "匠魂", "TConstruct", "TConstruct:materials:18");
		optional(pool, "netherite", "把未来带回现实", "etfuturum", "etfuturum:netherite_ingot:0");
		optional(pool, "totem", "把未来带回现实", "etfuturum", "etfuturum:totem:0;etfuturum:totem_of_undying:0");
		optional(pool, "elytra", "把未来带回现实", "etfuturum", "etfuturum:elytra:0");
		optional(pool, "eye", "神秘时代4", "Thaumcraft", "Thaumcraft:ItemEldritchObject:0");
		optional(pool, "book", "神秘时代4", "Thaumcraft", "Thaumcraft:ItemThaumonomicon:0");
		optional(pool, "goggles", "神秘时代4", "Thaumcraft", "Thaumcraft:ItemGoggles:0");
		optional(pool, "primal", "神秘时代4", "Thaumcraft", "Thaumcraft:ItemResource:14");
		optional(pool, "balanced", "神秘时代4", "Thaumcraft", "Thaumcraft:ItemShard:6");

		// Additional 1.7.10 candidates, resolved without optional Java API dependencies.
		optional(pool, "ender_pouch", "末影存储", "EnderStorage", "EnderStorage:enderPouch:0");
		optional(pool, "ender_chest", "末影存储", "EnderStorage", "EnderStorage:enderChest:0");
		optional(pool, "barrel", "JABBA", "JABBA", "JABBA:barrel:0");
		optional(pool, "dolly", "JABBA", "JABBA", "JABBA:mover:0");
		optional(pool, "diamond_dolly", "JABBA", "JABBA", "JABBA:moverDiamond:0");
		optional(pool, "tuning_fork", "JABBA", "JABBA", "JABBA:tuningFork:0");
		optional(pool, "barrel_hammer", "JABBA", "JABBA", "JABBA:hammer:0");
		optional(pool, "void_upgrade", "储物抽屉", "StorageDrawers", "StorageDrawers:upgradeVoid:0");
		optional(pool, "drawer_key", "储物抽屉", "StorageDrawers", "StorageDrawers:upgradeLock:0");
		optional(pool, "quantify_key", "储物抽屉", "StorageDrawers", "StorageDrawers:quantifyKey:0");
		optional(pool, "packing_tape", "储物抽屉", "StorageDrawers", "StorageDrawers:tape:0");
		optional(pool, "nature_compass", "自然罗盘", "naturescompass", "naturescompass:NaturesCompass:0");
		optional(pool, "iron_chest", "铁箱子", "IronChest", "IronChest:BlockIronChest:0");

		Random random = new Random();
		Collections.shuffle(pool, random);
		// Reserve one of the nine slots on half the rolls. On other rolls the
		// base still takes part in ordinary selection, so its total chance is >= 50%.
		List<Ingredient> result = new ArrayList<>();
		Set<Item> used = new HashSet<>();
		if (random.nextBoolean()) {
			for (Ingredient ingredient : pool) {
				if ("stargate".equals(ingredient.key)) {
					result.add(ingredient);
					used.add(ingredient.seed.getItem());
					break;
				}
			}
		}
		for (Ingredient ingredient : pool) {
			if (used.add(ingredient.seed.getItem())) result.add(ingredient);
			if (result.size() == SIZE) break;
		}
		Collections.shuffle(result, random);
		selected = Collections.unmodifiableList(result);
		startupSelection = selected;
		RecipeSorter.register("projecte:random_tome", RecipeTome.class, RecipeSorter.Category.SHAPELESS, "after:minecraft:shapeless");
		GameRegistry.addRecipe(new RecipeTome());
		PELogger.logInfo("Selected nine hidden Tome recipe ingredients for this game session.");
	}

	private static void vanilla(List<Ingredient> pool, String key, ItemStack stack)
	{
		add(pool, key, "Minecraft", stack);
	}

	private static void add(List<Ingredient> pool, String key, String mod, ItemStack stack)
	{
		pool.add(new Ingredient(key, mod, stack));
	}

	private static void optional(List<Ingredient> pool, String key, String mod, String requiredMods, String aliases)
	{
		for (String required : requiredMods.split("&"))
			if (!Loader.isModLoaded(required)) return;
		// Mod IDs can contain pipes (BuildCraft|Robotics), so aliases use semicolons.
		for (String alias : aliases.split(";")) {
			String[] parts = alias.split(":", -1);
			if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty()) {
				PELogger.logWarn("Invalid Tome candidate '" + key + "': " + alias);
				continue;
			}
			int metadata;
			try {
				metadata = Integer.parseInt(parts[2]);
			} catch (NumberFormatException exception) {
				PELogger.logWarn("Invalid Tome candidate metadata '" + key + "': " + alias);
				continue;
			}
			Item item = GameRegistry.findItem(parts[0], parts[1]);
			if (item != null) {
				add(pool, key, mod, new ItemStack(item, 1, metadata));
				return;
			}
		}
		PELogger.logWarn("Tome candidate '" + key + "' has no registered item; skipping it.");
	}

	public static void restoreStartupSelection()
	{
		selected = startupSelection;
	}

	public static List<Ingredient> getSelection()
	{
		return selected;
	}

	/** Replaces the complete snapshot atomically when joining a server. */
	public static void receiveSelection(List<Ingredient> ingredients)
	{
		if (ingredients.size() == SIZE)
			selected = Collections.unmodifiableList(new ArrayList<>(ingredients));
	}

	public static void addTooltips(List<String> lines)
	{
		List<Ingredient> ingredients = selected;
		for (int i = 0; i < ingredients.size(); i++) {
			Ingredient ingredient = ingredients.get(i);
			lines.add(StatCollector.translateToLocalFormatted("pe.tome.recipe.condition",
				StatCollector.translateToLocal("pe.tome.recipe.number." + (i + 1)),
				StatCollector.translateToLocal("pe.tome.clue." + ingredient.key)) + " (" + ingredient.mod + ")");
		}
		lines.add("");
		lines.add(StatCollector.translateToLocal("pe.tome.recipe.finale"));
	}

	@Override
	public boolean matches(InventoryCrafting inventory, World world)
	{
		List<Ingredient> ingredients = selected;
		if (ingredients.size() != SIZE) return false;
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < inventory.getSizeInventory(); i++) {
			ItemStack stack = inventory.getStackInSlot(i);
			if (stack != null) stacks.add(stack);
		}
		if (stacks.size() != SIZE) return false;
		boolean[][] edges = new boolean[SIZE][SIZE];
		for (int slot = 0; slot < SIZE; slot++)
			for (int ingredient = 0; ingredient < SIZE; ingredient++)
				edges[slot][ingredient] = ingredients.get(ingredient).matches(stacks.get(slot));
		int[] owners = new int[SIZE];
		java.util.Arrays.fill(owners, -1);
		for (int slot = 0; slot < SIZE; slot++)
			if (!assign(slot, edges, owners, new boolean[SIZE])) return false;
		return true;
	}

	private static boolean assign(int slot, boolean[][] edges, int[] owners, boolean[] visited)
	{
		for (int i = 0; i < SIZE; i++) {
			if (!edges[slot][i] || visited[i]) continue;
			visited[i] = true;
			if (owners[i] < 0 || assign(owners[i], edges, owners, visited)) {
				owners[i] = slot;
				return true;
			}
		}
		return false;
	}

	@Override
	public int getRecipeSize() { return SIZE; }

	@Override
	public ItemStack getRecipeOutput() { return new ItemStack(ObjHandler.tome); }

	@Override
	public ItemStack getCraftingResult(InventoryCrafting inventory) { return getRecipeOutput(); }

	public static final class Ingredient
	{
		public final String key;
		public final String mod;
		public final ItemStack seed;
		private final Set<String> oreNames = new HashSet<>();

		public Ingredient(String key, String mod, ItemStack seed)
		{
			this.key = key;
			this.mod = mod;
			this.seed = seed.copy();
			for (int id : OreDictionary.getOreIDs(seed))
				oreNames.add(OreDictionary.getOreName(id));
		}

		public boolean matches(ItemStack stack)
		{
			if (stack.getItem() == seed.getItem()) return true;
			for (int id : OreDictionary.getOreIDs(stack))
				if (oreNames.contains(OreDictionary.getOreName(id))) return true;
			return false;
		}
	}
}
