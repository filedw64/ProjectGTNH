package moze_intel.projecte.integration.GregTech;

import bartworks.API.recipe.BartWorksRecipeMaps;
import com.google.common.collect.ImmutableMap;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.integration.AbstractIntegrationMapper;
import moze_intel.projecte.utils.PELogger;
import net.minecraft.item.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GTMapper extends AbstractIntegrationMapper {
	public static void init() {
		PELogger.logTrace("Succeed to get class RecipeMaps: %s", RecipeMaps.class);
		PELogger.logTrace("Succeed to get class BartWorksRecipeMaps: %s", BartWorksRecipeMaps.class);
	}

	@Override
	protected void doAddMappings() {
		/* 压缩机: 1+1 -> 1 */
		processAllCategory(RecipeMaps.compressorRecipes);

		/* 提取机: 1 -> 1 */
		processAllCategory(RecipeMaps.extractorRecipes);

		/* 中子态素压缩机: 1+1 -> 1 */
		processAllCategory(RecipeMaps.neutroniumCompressorRecipes);

		/* 合金炉: 2(可能有模具) -> 1
		 * 合金炉熔铸: 1(+模具) -> 1
		 * 合金炉回收: 1(+模具/铸件/铸模) -> 1 */
		processDefaultCategory(RecipeMaps.alloySmelterRecipes);

		/* 粉碎机: 1 -> 4
		 * 粉碎机回收: 1 -> 4 */
		processDefaultCategory(RecipeMaps.maceratorRecipes);

		/* 锻造锤: 2+2 -> 2+2, 仅处理输出只有一种物品/流体的
		 * 锻造锤回收: 2+2 -> 2+2 (1 -> 1) */
		processDefaultCategory(RecipeMaps.hammerRecipes);

		/* PCB工厂: 6(可能有编程电路与蜂群)+3 -> 9(1) */
		processAllCategory(RecipeMaps.pcbFactoryRecipes);

		/* 纳米锻炉: 6(可能有透镜)+3 -> 2(1), 仅处理输出只有一种物品/流体的 */
		processAllCategory(RecipeMaps.nanoForgeRecipes);

		// TODO: UU增幅液 / UU物质 相关
		/* UU增幅液生产器: 1(s) -> 1(l) */
		//processAllCategory(RecipeMaps.amplifierRecipes);

		/* 质量发生器: UU增幅液体 -> UU物质 */
		//processAllCategory(RecipeMaps.massFabFakeRecipes);

		/* 复制机: UU -> 1(s/l) */
		//processAllCategory(RecipeMaps.replicatorRecipes);

		/* 冲压机床: 6(可能有编程电路与模具)+1 -> 1 */
		processAllCategory(RecipeMaps.formingPressRecipes);

		/* 板材切割机: 2(可能有编程电路)+1(忽略) -> 4(1) */
		processGTRecipeMapIgnoreFluid(RecipeMaps.cutterRecipes);

		/* 食材切片机: 1(s)(+刀刃) -> 1(s) */
		processGTRecipeMapIgnoreFluid(RecipeMaps.slicerRecipes);

		/* 压模机: 2(可能有模具/模头/铸模) -> 1
		 * 匠魂部件压模: 1(+铸模/铸件) -> 1 */
		processAllCategory(RecipeMaps.extruderRecipes);

		/* 酿造室: 1+1 -> 1(l) */
		processAllCategory(RecipeMaps.brewingRecipes);

		/* 车床: 1 -> 2, 仅处理输出只有一种物品的 */
		processAllCategory(RecipeMaps.latheRecipes);

		/* 两极磁化机: 1 -> 1 */
		processAllCategory(RecipeMaps.polarizerRecipes);

		/* 流体灌装机: 1 -> 1+1 或 1+1 -> 1, 仅处理 1+1 -> 1 */
		processAllCategory(RecipeMaps.fluidCannerRecipes);

		/* 装配线: 16+4(+研究结果) -> 1 */
		processAllCategory(RecipeMaps.assemblylineVisualRecipes);

		/* 组装机: 9(可能有编程电路)+1 -> 1 */
		processAllCategory(RecipeMaps.assemblerRecipes);

		/* 电路组装机: 6+1 -> 1 */
		processAllCategory(RecipeMaps.circuitAssemblerRecipes);

		/* 卷板机: 1(+编程电路) -> 1 */
		processAllCategory(RecipeMaps.benderRecipes);

		/* 聚爆压缩机: 1(+炸药) -> 2(可能有灰烬/粉末) */
		/* 电动聚爆压缩机(BartWorks): 6+1 -> 2+1(可能有灰烬/粉末), 仅处理扣除灰烬后, 输出只有一种物品/流体的 */
		processGTRecipeMapIgnoreAsh(BartWorksRecipeMaps.electricImplosionCompressorRecipes);

		/* 流体固化器: 1(可能是模具/编程电路)+1 -> 1
		 * 匠魂弩箭固化: 1(手柄)+1(l) -> 1(弩箭) */
		processDefaultCategory(RecipeMaps.fluidSolidifierRecipes);

		/* 电磁离析机: 1 -> 3, 不做处理 (RecipeMaps.electroMagneticSeparatorRecipes) */

		/* 离心机: 2(可能有编程电路)+1 -> 6+1, 仅处理输出只有一种物品/流体的 */
		processAllCategory(RecipeMaps.centrifugeRecipes);

		/* 太阳能板制造厂: 9+3 -> 1 */
		processAllCategory(RecipeMaps.solarFactoryRecipes);

		/* 电解机: 2(可能有编程电路)+1 -> 6+1, 仅处理输出只有一种物品/流体的 */
		processAllCategory(RecipeMaps.electrolyzerRecipes);

		/* 打包机: 2(可能有设计图) -> 1 */
		processAllCategory(RecipeMaps.packagerRecipes);
		
		/* 化学反应釜: 2(可能有编程电路)+1 -> 2+1, 仅处理扣除单元后，输出只有一种物品/流体的 TODO: 扣除单元*/
		processAllCategory(RecipeMaps.chemicalReactorRecipes);

		/* 大型化学反应釜: 6(可能有编程电路)+6 -> 6+6, 仅处理输出只有一种物品/流体的 */
		processAllCategory(RecipeMaps.multiblockChemicalReactorRecipes);

		/* 蒸馏室: 1(可能有编程电路)+1 -> 1+1, 仅处理扣除单元后, 输出只有一种物品/流体的 TODO: 扣除单元*/
		processAllCategory(RecipeMaps.distilleryRecipes);

		/* 蒸馏塔: 2(可能有编程电路)+1 -> 1+11, 仅处理扣除单元后, 输出只有一种物品/流体的 TODO: 扣除单元*/
		processAllCategory(RecipeMaps.distillationTowerRecipes);

		/* 化学浸洗机: 2(可能有编程电路)+2 -> 3+2, 仅处理输出只有一种物品/流体的 */
		processAllCategory(RecipeMaps.chemicalBathRecipes);

		/* 高压釜: 2(可能有编程电路)+1 -> 4+1, 仅处理输出只有一种物品/流体的 */
		processAllCategory(RecipeMaps.autoclaveRecipes);

		/* 搅拌机: 9(可能有编程电路)+1 -> 4+1, 仅处理扣除单元后, 输出只有一种物品/流体的 TODO: 扣除单元*/
		processAllCategory(RecipeMaps.mixerRecipes);

		/* 激光蚀刻机: 4(可能有编程电路与透镜等)+2 -> 4+2, 仅处理输出只有一种物品/流体的 */
		processAllCategory(RecipeMaps.laserEngraverRecipes);

		/* 流体加热器: 1(可能有编程电路)+1 -> 1+1, 仅处理输出只有一种物品/流体的 */
		processAllCategory(RecipeMaps.fluidHeaterRecipes);

		/* 筛选机: 1+1 -> 9+1, 不做处理 (RecipeMaps.sifterRecipes) */

		/* 工业高炉: 6(可能有编程电路)+1 -> 6+1 */
		processAllCategory(RecipeMaps.blastFurnaceRecipes);

		/* 砖高炉: 1+燃料 -> 1+灰烬, 扣除灰烬*/
		processGTRecipeMapIgnoreAsh(RecipeMaps.primitiveBlastRecipes);

		/* 流体提取机: 1 -> 1+1
		 * 流体提取机回收: 1 -> 1+1*/
		processDefaultCategory(RecipeMaps.fluidExtractionRecipes);

		/* 线材轧机: 2(可能有编程电路) -> 1 */
		processAllCategory(RecipeMaps.wiremillRecipes);

		/* 发酵槽: 1(l) -> 1(l) */
		processAllCategory(RecipeMaps.fermentingRecipes);

		/* 洗矿机: 1+1(水，忽略) -> 3, 不做处理 (RecipeMaps.oreWasherRecipes) */

		// TODO: check 以下配方
		// 热力离心机
		/*processAllCategory(RecipeMaps.thermalCentrifugeRecipes);
		// 回收机
		processAllCategory(RecipeMaps.recyclerRecipes);
		// 熔炉
		processAllCategory(RecipeMaps.furnaceRecipes);
		// 微波炉
		processAllCategory(RecipeMaps.microwaveRecipes);
		// 扫描仪
		processAllCategory(RecipeMaps.scannerFakeRecipes);
		// 碎石机
		processAllCategory(RecipeMaps.rockBreakerFakeRecipes);
		// 量子计算机
		processAllCategory(RecipeMaps.quantumComputerFakeRecipes);
		// 等离子电弧炉
		processAllCategory(RecipeMaps.plasmaArcFurnaceRecipes);
		// 电弧炉
		processAllCategory(RecipeMaps.arcFurnaceRecipes);
		// 打印机
		processAllCategory(RecipeMaps.printerRecipes);
		// 装罐机
		processAllCategory(RecipeMaps.cannerRecipes);
		// 解包器
		processAllCategory(RecipeMaps.unpackagerRecipes);
		// 聚变反应堆
		processAllCategory(RecipeMaps.fusionRecipes);
		// 等离子锻炉
		processAllCategory(RecipeMaps.plasmaForgeRecipes);
		// 超维度搅拌机
		processAllCategory(RecipeMaps.transcendentPlasmaMixerRecipes);
		// 太空项目
		processAllCategory(RecipeMaps.spaceProjectFakeRecipes);
		// 真空冷冻机
		processAllCategory(RecipeMaps.vacuumFreezerRecipes);
		// 石油裂化机
		processAllCategory(RecipeMaps.crackingRecipes);
		// 热解炉
		processAllCategory(RecipeMaps.pyrolyseRecipes);
		// 内燃发电机
		processAllCategory(RecipeMaps.dieselFuels);
		// 极限内燃引擎
		processAllCategory(RecipeMaps.extremeDieselFuels);
		// 燃气轮机
		processAllCategory(RecipeMaps.gasTurbineFuels);
		// ?????
		processAllCategory(RecipeMaps.hotFuels);
		processAllCategory(RecipeMaps.denseLiquidFuels);
		processAllCategory(RecipeMaps.plasmaFuels);
		processAllCategory(RecipeMaps.magicFuels);
		processAllCategory(RecipeMaps.smallNaquadahReactorFuels);
		processAllCategory(RecipeMaps.largeNaquadahReactorFuels);
		processAllCategory(RecipeMaps.hugeNaquadahReactorFuels);
		processAllCategory(RecipeMaps.extremeNaquadahReactorFuels);
		processAllCategory(RecipeMaps.ultraHugeNaquadahReactorFuels);
		// 大型锅炉
		processAllCategory(RecipeMaps.largeBoilerFakeFuels);
		// 澄清净化单元
		processAllCategory(RecipeMaps.purificationClarifierRecipes);
		// 臭氧净化单元
		processAllCategory(RecipeMaps.purificationOzonationRecipes);
		// 絮凝净化单元
		processAllCategory(RecipeMaps.purificationFlocculationRecipes);
		// PH中和净化单元
		processAllCategory(RecipeMaps.purificationPhAdjustmentRecipes);
		// 极端温度波动净化单元
		processAllCategory(RecipeMaps.purificationPlasmaHeatingRecipes);
		// 高能激光净化单元
		processAllCategory(RecipeMaps.purificationUVTreatmentRecipes);
		// 残留净化剂除气机净化单元
		processAllCategory(RecipeMaps.purificationDegasifierRecipes);
		// 绝对重子完美净化单元
		processAllCategory(RecipeMaps.purificationParticleExtractionRecipes);
		// 核反应堆
		processAllCategory(RecipeMaps.ic2NuclearFakeRecipes);
		// 熵变处理厂
		processAllCategory(RecipeMaps.entropicProcessing);
		// 同位素衰变
		processAllCategory(RecipeMaps.isotopeDecay);*/
	}

	private void processAllCategory(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes())
			addGTRecipeConversion(gtre);
	}

	private void processDefaultCategory(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getBackend().getRecipesByCategory(recipeMap.getDefaultRecipeCategory()))
			addGTRecipeConversion(gtre);
	}

	private void processGTRecipeMapIgnoreFluid(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes()) {
			GTRecipe cutter = gtre.copy();
			cutter.mFluidInputs = null;
			addGTRecipeConversion(cutter);
		}
	}
	
	private void processGTRecipeMapIgnoreAsh(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes()) {
			GTRecipe ignoreAsh = gtre.copy();
			ignoreAsh.mOutputs = Arrays.stream(ignoreAsh.mOutputs).filter(is -> !GTItemHelper.isAsh(is)).toArray(ItemStack[]::new);
			addGTRecipeConversion(ignoreAsh);
		}
	}

	private boolean doGTRecipeNeedMultiply(GTRecipe gtre) {
		if (gtre.mChances != null)
			for (int ch : gtre.mChances)
				if (ch != 10000) return true;
		return false;
	}
	
	private void addGTRecipeConversion(GTRecipe gtre) {
		Map<NormalizedSimpleStack, Integer> in = new HashMap<>(),
			out = new HashMap<>();
		boolean chance = doGTRecipeNeedMultiply(gtre);
		if (gtre.mInputs != null)
			for (int i = 0; i < gtre.mInputs.length; i++) {
				if (gtre.mInputs[i] == null || gtre.mInputs[i].stackSize == 0) continue;
				NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(gtre.mInputs[i]);
				if (nss == null) return;
				in.put(nss, in.getOrDefault(nss, 0) + gtre.mInputs[i].stackSize * (chance ? 10000 : 1));
			}
		if (gtre.mFluidInputs != null)
			for (int i = 0; i < gtre.mFluidInputs.length; i++) {
				if (gtre.mFluidInputs[i] == null) continue;
				NormalizedSimpleStack nss = NormalizedSimpleStack.forFluid(gtre.mFluidInputs[i]);
				if (nss == null) return;
				if (chance && gtre.mFluidInputs[i].amount > 200000) return;
				in.put(nss, in.getOrDefault(nss, 0) + gtre.mFluidInputs[i].amount * (chance ? 10000 : 1));
			}
		if (gtre.mOutputs != null)
			for (int i = 0; i < gtre.mOutputs.length; i++) {
				if (gtre.mOutputs[i] == null) continue;
				NormalizedSimpleStack nss = NormalizedSimpleStack.forItem(gtre.mOutputs[i]);
				if (nss == null) return;
				out.put(nss, out.getOrDefault(nss, 0) + gtre.mOutputs[i].stackSize * (chance ? gtre.getOutputChance(i) : 1));
			}
		if (gtre.mFluidOutputs != null)
			for (int i = 0; i < gtre.mFluidOutputs.length; i++) {
				if (gtre.mFluidOutputs[i] == null) continue;
				NormalizedSimpleStack nss = NormalizedSimpleStack.forFluid(gtre.mFluidOutputs[i]);
				if (nss == null) return;
				if (chance && gtre.mFluidOutputs[i].amount > 200000) return;
				out.put(nss, out.getOrDefault(nss, 0) + gtre.mFluidOutputs[i].amount * (chance ? 10000 : 1));
			}
		in.entrySet().removeIf(entry -> entry.getValue() == 0);
		if (in.isEmpty()) return;
		out.entrySet().removeIf(entry -> entry.getValue() == 0);
		if (out.size() != 1) return;
		Map.Entry<NormalizedSimpleStack, Integer> outEntry = out.entrySet().iterator().next();
		mapper.addConversion(outEntry.getValue(), outEntry.getKey(), ImmutableMap.copyOf(in));
	}
}