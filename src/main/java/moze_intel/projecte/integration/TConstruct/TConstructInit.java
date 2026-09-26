package moze_intel.projecte.integration.TConstruct;

import moze_intel.projecte.gameObjs.ObjHandler;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import tconstruct.library.TConstructRegistry;
import tconstruct.library.crafting.LiquidCasting;
import tconstruct.library.crafting.Smeltery;
import tconstruct.library.util.IPattern;
import tconstruct.smeltery.TinkerSmeltery;
import tconstruct.tools.TinkerTools;

public class TConstructInit {

	public static final int MAT_ID_DARK_MATTER = 256;
	public static final int MAT_ID_RED_MATTER = 257;

	public static final Fluid fluidDarkMatter = new Fluid("molten_dark_matter").setDensity(3000).setViscosity(6000).setTemperature(2000).setLuminosity(15);
	public static final Fluid fluidRedMatter = new Fluid("molten_red_matter").setDensity(3000).setViscosity(6000).setTemperature(3000).setLuminosity(15);

	public static void init() {
		// 注册流体
		FluidRegistry.registerFluid(fluidDarkMatter);
		FluidRegistry.registerFluid(fluidRedMatter);

		// 注册匠魂材料
		// 参数: ID, 内部名, 本地化名, 挖掘等级, 耐久, 挖掘速度, 攻击力, 手柄系数, 强化槽(reinforced), 碎石系数(stonebound), 样式, 颜色
		TConstructRegistry.addToolMaterial(MAT_ID_DARK_MATTER, "DarkMatter", "material.darkmatter", 7, 102400, 1500, 20, 2.5f, 2, 0f, "", 0x2E004F);
		TConstructRegistry.addToolMaterial(MAT_ID_RED_MATTER, "RedMatter", "material.redmatter", 10, 104857600, 2000, 23, 32.0f, 3, 0f, "", 0x990000);

		// 弓
		TConstructRegistry.addBowMaterial(MAT_ID_DARK_MATTER, 5, 2.0f);
		TConstructRegistry.addBowMaterial(MAT_ID_RED_MATTER, 2, 3.0f);

		// 熔融配方
		ItemStack darkMatterItem = new ItemStack(ObjHandler.matter, 1, 0);
		ItemStack redMatterItem = new ItemStack(ObjHandler.matter, 1, 1);

		Smeltery.addMelting(darkMatterItem, 2000, new FluidStack(fluidDarkMatter, 144));
		Smeltery.addMelting(redMatterItem, 3000, new FluidStack(fluidRedMatter, 144));

		// 添加浇铸配方
		LiquidCasting tableCasting = TConstructRegistry.getTableCasting();
		for (int iter = 0; iter < TinkerTools.patternOutputs.length; iter++) {
			if (TinkerTools.patternOutputs[iter] == null) continue;

			ItemStack cast = new ItemStack(TinkerSmeltery.metalPattern, 1, iter + 1);

			// 计算流体消耗
			int cost = ((IPattern) TinkerSmeltery.metalPattern).getPatternCost(cast) * 144 / 2;

			ItemStack dmPart = new ItemStack(TinkerTools.patternOutputs[iter], 1, MAT_ID_DARK_MATTER);
			ItemStack rmPart = new ItemStack(TinkerTools.patternOutputs[iter], 1, MAT_ID_RED_MATTER);

			tableCasting.addCastingRecipe(dmPart, new FluidStack(fluidDarkMatter, cost), cast, 100);
			tableCasting.addCastingRecipe(rmPart, new FluidStack(fluidRedMatter, cost), cast, 150);
		}

		// 注册特性
		TraitHandler traitHandler = new TraitHandler();
		TConstructRegistry.registerActiveToolMod(traitHandler);
		MinecraftForge.EVENT_BUS.register(traitHandler);
	}
}
