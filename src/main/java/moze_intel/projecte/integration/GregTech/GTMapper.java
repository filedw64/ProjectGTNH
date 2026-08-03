package moze_intel.projecte.integration.GregTech;

import com.google.common.collect.ImmutableMap;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import moze_intel.projecte.emc.NormalizedSimpleStack;
import moze_intel.projecte.integration.AbstractIntegrationMapper;
import moze_intel.projecte.utils.PELogger;

import java.util.HashMap;
import java.util.Map;

public class GTMapper extends AbstractIntegrationMapper {
    public static void init() {
        PELogger.logTrace("RecipeMap compressorRecipes has %s recipes", RecipeMaps.compressorRecipes.getAllRecipes().size());
    }

    @Override
    protected void doAddMappings() {
        // 压缩机: 1+1 -> 1
		processGTRecipeMap(RecipeMaps.compressorRecipes);
		// 提取机: 1 -> 1
		processGTRecipeMap(RecipeMaps.extractorRecipes);
		// 中子态素压缩机: 1+1 -> 1
		processGTRecipeMap(RecipeMaps.neutroniumCompressorRecipes);
		// 合金炉: 2(可能有模具) -> 1
		// 合金炉熔铸: 1(+模具) -> 1 TODO: check 熔铸配方是否在内
		processGTRecipeMap(RecipeMaps.alloySmelterRecipes);
		// 粉碎机: 1 -> 4
		/*processGTRecipeMap(RecipeMaps.maceratorRecipes);
		// 锻造锤: 2+2 -> 2+2, 仅处理输出只有一种物品/流体的
		processGTRecipeMap(RecipeMaps.hammerRecipes);
		// PCB工厂: 6(可能有编程电路与蜂群)+3 -> 9(1)
		processGTRecipeMap(RecipeMaps.pcbFactoryRecipes);
		// 纳米锻炉: 6(可能有透镜)+3 -> 2(1), 仅处理输出只有一种物品/流体的
		processGTRecipeMap(RecipeMaps.nanoForgeRecipes);
		// UU增幅液生产器: 1(s) -> 1(l)
		processGTRecipeMap(RecipeMaps.amplifierRecipes);
		// 质量发生器: UU增幅液体 -> UU物质
		processGTRecipeMap(RecipeMaps.massFabFakeRecipes);
		// 复制机: UU -> 1(s/l)
		processGTRecipeMap(RecipeMaps.replicatorRecipes);
		// 冲压机床: 6(可能有编程电路与模具)+1 -> 1
		processGTRecipeMap(RecipeMaps.formingPressRecipes);
		// 板材切割机: 2(可能有编程电路)+1(忽略) -> 4, 仅处理输出只有一种物品的
		processGTcutterRecipeMap(RecipeMaps.cutterRecipes);
		// 食材切片机: 1(s)(+刀刃) -> 1(s) TODO: check 忽略刀刃
		processGTcutterRecipeMap(RecipeMaps.slicerRecipes);
		// 压模机: 2(可能有模具、模头、铸模) -> 1
		processGTRecipeMap(RecipeMaps.extruderRecipes);
		// 酿造室: 1+1 -> 1(l)
		processGTRecipeMap(RecipeMaps.brewingRecipes);
		// 车床: 1 -> 2, 仅处理输出只有一种物品的
		processGTRecipeMap(RecipeMaps.latheRecipes);
		// 两极磁化机: 1 -> 1
		processGTRecipeMap(RecipeMaps.polarizerRecipes);
		// 流体灌装机: 1 -> 1+1 或 1+1 -> 1, 仅处理 1+1 -> 1
		processGTRecipeMap(RecipeMaps.fluidCannerRecipes);
		// 装配线加工: 16+4(+研究结果) -> 1
		processGTRecipeMap(RecipeMaps.assemblylineVisualRecipes);
		// 组装机: 9(可能有编程电路)+1 -> 1
		processGTRecipeMap(RecipeMaps.assemblerRecipes);
		// 电路组装机: 6+1 -> 1
		processGTRecipeMap(RecipeMaps.circuitAssemblerRecipes);
		// 卷板机: 1(+编程电路) -> 1
		processGTRecipeMap(RecipeMaps.benderRecipes);
		// 聚爆压缩机: 1(+炸药) -> 2(可能有灰烬/粉末)
		processGTimplosionRecipeMap(RecipeMaps.implosionRecipes);
		// 流体固化器: 1(可能是模具/编程电路)+1 -> 1
		processGTRecipeMap(RecipeMaps.fluidSolidifierRecipes);
		// 电磁离析机: 1 -> 3, 反向
		processReverseGTRecipeMap(RecipeMaps.electroMagneticSeparatorRecipes);
		// 离心机: 2(可能有编程电路)+1 -> 6+1, 双向, 仅处理扣除编程电路后，某侧只有一种物品/流体的
		processTwoWayGTRecipeMap(RecipeMaps.centrifugeRecipes);
		// 太阳能板制造厂: 9+3 -> 1
		processGTRecipeMap(RecipeMaps.solarFactoryRecipes);
		// 电解机: 2(可能有编程电路)+1 -> 6+1, 反向, 仅处理扣除单元、编程电路后，输入只有一种物品/流体的
		processReverseGTRecipeMap(RecipeMaps.electrolyzerRecipes);
		// 打包机: 2(可能有设计图) -> 1
		processGTRecipeMap(RecipeMaps.packagerRecipes);
		// 化学反应釜: 2(可能有编程电路)+1 -> 2+1, 双向, 仅处理扣除单元、编程电路后，某侧只有一种物品/流体的
		processTwoWayGTRecipeMap(RecipeMaps.chemicalReactorRecipes);
		// 大型化学反应釜: 6(可能有编程电路)+6 -> 6+6, 双向, 仅处理扣除编程电路后，某侧只有一种物品/流体的
		processTwoWayGTRecipeMap(RecipeMaps.multiblockChemicalReactorRecipes);
		// 蒸馏室: 1(可能有编程电路)+1 -> 1+1, 双向, 仅处理扣除单元、编程电路后, 某侧只有一种物品/流体的
		processTwoWayGTRecipeMap(RecipeMaps.distilleryRecipes);
		// 蒸馏塔: 2(可能有编程电路)+1 -> 1+11, 反向, 仅处理扣除编程电路后, 输入只有一种物品/流体的
		processReverseGTRecipeMap(RecipeMaps.distillationTowerRecipes);
		// 化学浸洗机: 2(可能有编程电路)+2 -> 3+2, 仅处理输出只有一种物品/流体的
		processGTRecipeMap(RecipeMaps.chemicalBathRecipes);
		// 高压釜: 2(可能有编程电路)+1 -> 4+1, 仅处理输出只有一种物品/流体的
		processGTRecipeMap(RecipeMaps.autoclaveRecipes);
		// 搅拌机: 9(可能有编程电路)+1 -> 4+1, 仅处理扣除单元后, 输出只有一种物品/流体的
		processGTRecipeMap(RecipeMaps.mixerRecipes);
		// 激光蚀刻机: 4(可能有编程电路与透镜等)+2 -> 4+2, 仅处理输出只有一种物品/流体的
		processGTRecipeMap(RecipeMaps.laserEngraverRecipes);
		// 流体加热器: 1(可能有编程电路)+1 -> 1+1, 双向, 仅处理扣除编程电路后，某侧只有一种物品/流体的
		processTwoWayGTRecipeMap(RecipeMaps.fluidHeaterRecipes);
		// 筛选机: 1+1 -> 9+1, 双向, 仅处理某侧只有一种物品/流体的
		processTwoWayGTRecipeMap(RecipeMaps.sifterRecipes);
		// 工业高炉: 6(可能有编程电路)+1 -> 6+1, TODO: 仅处理扣除灰烬后, 输出只有一种物品/流体的
		processGTRecipeMap(RecipeMaps.blastFurnaceRecipes);
		// 流体提取机: 1 -> 1+1, 双向, 仅处理某侧只有一种物品/流体的
		processTwoWayGTRecipeMap(RecipeMaps.fluidExtractionRecipes);
		// 线材轧机: 2(可能有编程电路) -> 1
		processGTRecipeMap(RecipeMaps.wiremillRecipes);
		// 发酵槽: 1(l) -> 1(l)
		processGTRecipeMap(RecipeMaps.fermentingRecipes);
		// 洗矿机: 1+1(水，忽略) -> 3, 反向
		processGToreWasherRecipeMap(RecipeMaps.oreWasherRecipes);
		// TODO: check 以下配方
		// 热力离心机
		/*processGTRecipeMap(RecipeMaps.thermalCentrifugeRecipes);
		// 回收机
		processGTRecipeMap(RecipeMaps.recyclerRecipes);
		// 熔炉
		processGTRecipeMap(RecipeMaps.furnaceRecipes);
		// 微波炉
		processGTRecipeMap(RecipeMaps.microwaveRecipes);
		// 扫描仪
		processGTRecipeMap(RecipeMaps.scannerFakeRecipes);
		// 碎石机
		processGTRecipeMap(RecipeMaps.rockBreakerFakeRecipes);
		// 量子计算机
		processGTRecipeMap(RecipeMaps.quantumComputerFakeRecipes);
		// 等离子电弧炉
		processGTRecipeMap(RecipeMaps.plasmaArcFurnaceRecipes);
		// 电弧炉
		processGTRecipeMap(RecipeMaps.arcFurnaceRecipes);
		// 打印机
		processGTRecipeMap(RecipeMaps.printerRecipes);
		// 装罐机
		processGTRecipeMap(RecipeMaps.cannerRecipes);
		// 解包器
		processGTRecipeMap(RecipeMaps.unpackagerRecipes);
		// 聚变反应堆
		processGTRecipeMap(RecipeMaps.fusionRecipes);
		// 等离子锻炉
		processGTRecipeMap(RecipeMaps.plasmaForgeRecipes);
		// 超维度搅拌机
		processGTRecipeMap(RecipeMaps.transcendentPlasmaMixerRecipes);
		// 太空项目
		processGTRecipeMap(RecipeMaps.spaceProjectFakeRecipes);
		// 砖高炉
		processGTRecipeMap(RecipeMaps.primitiveBlastRecipes);
		// 真空冷冻机
		processGTRecipeMap(RecipeMaps.vacuumFreezerRecipes);
		// 石油裂化机
		processGTRecipeMap(RecipeMaps.crackingRecipes);
		// 热解炉
		processGTRecipeMap(RecipeMaps.pyrolyseRecipes);
		// 内燃发电机
		processGTRecipeMap(RecipeMaps.dieselFuels);
		// 极限内燃引擎
		processGTRecipeMap(RecipeMaps.extremeDieselFuels);
		// 燃气轮机
		processGTRecipeMap(RecipeMaps.gasTurbineFuels);
		// ?????
		processGTRecipeMap(RecipeMaps.hotFuels);
		processGTRecipeMap(RecipeMaps.denseLiquidFuels);
		processGTRecipeMap(RecipeMaps.plasmaFuels);
		processGTRecipeMap(RecipeMaps.magicFuels);
		processGTRecipeMap(RecipeMaps.smallNaquadahReactorFuels);
		processGTRecipeMap(RecipeMaps.largeNaquadahReactorFuels);
		processGTRecipeMap(RecipeMaps.hugeNaquadahReactorFuels);
		processGTRecipeMap(RecipeMaps.extremeNaquadahReactorFuels);
		processGTRecipeMap(RecipeMaps.ultraHugeNaquadahReactorFuels);
		// 大型锅炉
		processGTRecipeMap(RecipeMaps.largeBoilerFakeFuels);
		// 澄清净化单元
		processGTRecipeMap(RecipeMaps.purificationClarifierRecipes);
		// 臭氧净化单元
		processGTRecipeMap(RecipeMaps.purificationOzonationRecipes);
		// 絮凝净化单元
		processGTRecipeMap(RecipeMaps.purificationFlocculationRecipes);
		// PH中和净化单元
		processGTRecipeMap(RecipeMaps.purificationPhAdjustmentRecipes);
		// 极端温度波动净化单元
		processGTRecipeMap(RecipeMaps.purificationPlasmaHeatingRecipes);
		// 高能激光净化单元
		processGTRecipeMap(RecipeMaps.purificationUVTreatmentRecipes);
		// 残留净化剂除气机净化单元
		processGTRecipeMap(RecipeMaps.purificationDegasifierRecipes);
		// 绝对重子完美净化单元
		processGTRecipeMap(RecipeMaps.purificationParticleExtractionRecipes);
		// 核反应堆
		processGTRecipeMap(RecipeMaps.ic2NuclearFakeRecipes);
		// 熵变处理厂
		processGTRecipeMap(RecipeMaps.entropicProcessing);
		// 同位素衰变
		processGTRecipeMap(RecipeMaps.isotopeDecay);*/
    }
	
	private void processGTRecipeMap(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes())
			addGTRecipeConversion(gtre, false);
	}

	private void processReverseGTRecipeMap(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes())
			addGTRecipeConversion(gtre, true);
	}

	private void processTwoWayGTRecipeMap(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes()) {
			addGTRecipeConversion(gtre, false);
			addGTRecipeConversion(gtre, true);
		}
	}

	private void processGTcutterRecipeMap(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes()) {
			GTRecipe cutgtr = gtre.copy();
			cutgtr.mFluidInputs = null;
			addGTRecipeConversion(cutgtr, false);
		}
	}
	
	private void processGTimplosionRecipeMap(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes()) {
			Map<NormalizedSimpleStack, Integer> in = new HashMap<>();
			NormalizedSimpleStack nssIn = NormalizedSimpleStack.getFor(gtre.mInputs[0]);
			if (nssIn == null) continue;
			in.put(nssIn, gtre.mInputs[0].stackSize);
			if (gtre.mOutputs.length == 2) {
				if (!GTItemHelper.isAsh(gtre.mOutputs[1])){
					NormalizedSimpleStack nssOut = NormalizedSimpleStack.getFor(gtre.mOutputs[1]);
					if (nssOut == null) continue;
					in.put(nssOut, -gtre.mOutputs[1].stackSize);
				}
			}
			NormalizedSimpleStack nssOut = NormalizedSimpleStack.getFor(gtre.mOutputs[0]);
			if (nssOut == null) continue;
			mapper.addConversion(gtre.mOutputs[0].stackSize, nssOut, ImmutableMap.copyOf(in));
		}
	}

	private void processGToreWasherRecipeMap(RecipeMap<?> recipeMap) {
		for (GTRecipe gtre : recipeMap.getAllRecipes()) {
			GTRecipe oreWash = gtre.copy();
			oreWash.mFluidInputs = null;
			addGTRecipeConversion(oreWash, true);
		}
	}

	private boolean doGTRecipeNeedMultiply(GTRecipe gtre) {
		if (gtre.mChances != null)
			for (int ch : gtre.mChances)
				if (ch != 10000) return true;
		return false;
	}
	
	private void addGTRecipeConversion(GTRecipe gtre, boolean reverse) {
		Map<NormalizedSimpleStack, Integer> in = new HashMap<>(),
			out = new HashMap<>();
		boolean chance = doGTRecipeNeedMultiply(gtre);
		if (gtre.mInputs != null)
			for (int i = 0; i < gtre.mInputs.length; i++) {
				NormalizedSimpleStack nss = NormalizedSimpleStack.getFor(gtre.mInputs[i]);
				if (nss == null) return;
				in.put(nss, in.getOrDefault(nss, 0) + gtre.mInputs[i].stackSize * (chance ? 10000 : 1));
			}
		if (gtre.mFluidInputs != null)
			for (int i = 0; i < gtre.mFluidInputs.length; i++) {
				NormalizedSimpleStack nss = NormalizedSimpleStack.getFor(gtre.mFluidInputs[i]);
				if (nss == null) return;
				if (chance && gtre.mFluidInputs[i].amount > 200000) return;
				in.put(nss, in.getOrDefault(nss, 0) + gtre.mFluidInputs[i].amount * (chance ? 10000 : 1));
			}
		if (gtre.mOutputs != null)
			for (int i = 0; i < gtre.mOutputs.length; i++) {
				NormalizedSimpleStack nss = NormalizedSimpleStack.getFor(gtre.mOutputs[i]);
				if (nss == null) return;
				out.put(nss, out.getOrDefault(nss, 0) + gtre.mOutputs[i].stackSize * (chance ? gtre.getOutputChance(i) : 1));
			}
		if (gtre.mFluidOutputs != null)
			for (int i = 0; i < gtre.mFluidOutputs.length; i++) {
				NormalizedSimpleStack nss = NormalizedSimpleStack.getFor(gtre.mFluidOutputs[i]);
				if (nss == null) return;
				if (chance && gtre.mFluidOutputs[i].amount > 200000) return;
				out.put(nss, out.getOrDefault(nss, 0) + gtre.mFluidOutputs[i].amount * (chance ? 10000 : 1));
			}
		if (reverse) {
			Map<NormalizedSimpleStack, Integer> tmp = in;
			in = out;
			out = tmp;
		}
		in.entrySet().removeIf(entry -> entry.getValue() == 0);
		if (in.isEmpty()) return;
		out.entrySet().removeIf(entry -> entry.getValue() == 0);
		if (out.size() != 1) return;
		Map.Entry<NormalizedSimpleStack, Integer> outEntry = out.entrySet().iterator().next();
		mapper.addConversion(outEntry.getValue(), outEntry.getKey(), ImmutableMap.copyOf(in));
	}
}