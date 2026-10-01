package com.yunxigames;

import com.yunxigames.command.EnchantsCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 附魔突破模块入口：雷霆万钧 / 臭脚 / 碎裂 / 磁石 / 贪婪 / 负重诅咒 / 易碎诅咒 / 汲取 / 疾风 / 威压 /
 * 蓝银撑杆跳，以及击杀升级、图书管理员重做。
 *
 * <p>本模块依赖 {@code yg-core} 基础库，自身通过 {@link SelfTest#registerStep} 把
 * 属于附魔的若干自检步骤挂进统一的自检流程，避免 core 反向依赖本模块。
 */
public class YunxiGamesEnchants implements ModInitializer {
	public static final String MOD_ID = "yg_enchants";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// 游戏内命令：/yg enchants on|off|status（总开关只停玩法、不动各子开关）
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				EnchantsCommand.register(dispatcher));

		// 附魔效果（雷霆 / 臭脚 / 碎裂等）注册
		EnchantmentEffects.register();
		EnchantmentLevelUps.register();
		// 蓝银撑杆跳：右键立杆 + 倒杆积分（自成一类，杆的状态全在内存里）
		PoleVault.register();

		// 雷霆万钧 / 臭脚 每刻结算（碎裂由攻击 / 破坏方块的钩子驱动），撑杆跳的杆也每刻推进
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			EnchantmentEffects.tick(server);
			PoleVault.tick(server);
		});

		// 关服清掉附魔效果计时、击杀升级缓存与立着的杆
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			EnchantmentEffects.reset();
			EnchantmentLevelUps.reset();
			PoleVault.reset();
		});

		// 把附魔相关的自检步骤挂进统一自检流程
		SelfTest.register(() -> EnchantsConfig.get().selfTestRolls);
		SelfTest.registerStep("⑫ 附魔突破·注册+核心定义",
				ctx -> EnchantSelfTest.checkModEnchantments(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("⑱ 雷霆万钧·雷击生成",
				ctx -> EnchantSelfTest.checkThunderLightning(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㉒ 贪婪·注册+额外掉落",
				ctx -> EnchantSelfTest.checkGreed(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("⑲ 臭脚·花草枯萎+亡灵生成",
				ctx -> EnchantSelfTest.checkStinkyFeet(ctx.server, ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㉑ 磁石·注册+吸附",
				ctx -> EnchantSelfTest.checkMagnet(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㉓ 负重诅咒·注册+移速减益",
				ctx -> EnchantSelfTest.checkCurseBurden(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㉔ 易碎诅咒·注册+护甲碎裂",
				ctx -> EnchantSelfTest.checkCurseFrailty(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㉖ 新附魔·汲取/疾风/威压",
				ctx -> EnchantSelfTest.checkNewEnchantments(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㉗ 击杀升级·只升不降+满级封顶",
				ctx -> EnchantSelfTest.checkEnchantLevelUp(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㉞ 升级全附魔·含原版+排除诅咒+15%",
				ctx -> EnchantSelfTest.checkUniversalLevelUp(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㊱ 图书管理员·随机顶级附魔书交易",
				ctx -> EnchantSelfTest.checkLibrarian(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㊲ 蓝银撑杆跳·只认木棍+物理弧线+倒杆",
				ctx -> EnchantSelfTest.checkPoleVault(ctx.level, EnchantsConfig.get()));
		SelfTest.registerStep("㊳ 蓄力撑杆跳·生长+按档位顶碎方块",
				ctx -> EnchantSelfTest.checkPoleVaultCharge(ctx.level, EnchantsConfig.get()));

		LOGGER.info("[yg-enchants] 附魔模块已加载");
	}
}
