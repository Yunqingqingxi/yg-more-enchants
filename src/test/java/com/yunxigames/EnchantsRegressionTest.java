package com.yunxigames;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-enchants 回归测试：钉死 Gson 缺项补回与「显式 false 不可被偷改」的历史坑。
 */
class EnchantsRegressionTest {

	@TempDir
	Path configDir;

	@BeforeEach
	void injectConfigDir() {
		YgConfig.configDirOverride = configDir;
	}

	@AfterEach
	void resetConfigDir() {
		YgConfig.configDirOverride = null;
	}

	@Test
	void missingBooleanFieldsFallBackToCodeDefaultTrue() throws Exception {
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableMagnet\": true}");
		EnchantsConfig cfg = EnchantsConfig.load();
		assertTrue(cfg.enableLeech, "缺项布尔必须补回代码默认 true");
		assertTrue(cfg.enableSwift, "缺项布尔必须补回代码默认 true");
	}

	@Test
	void explicitFalseInJsonMustNotBeOverwritten() throws Exception {
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableGreed\": false}");
		EnchantsConfig cfg = EnchantsConfig.load();
		assertFalse(cfg.enableGreed, "玩家明确写 false 必须保持 false");
	}

	@Test
	void missingNumericFieldFallsBackToCodeDefaultNotZero() throws Exception {
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableMagnet\": true}");
		EnchantsConfig cfg = EnchantsConfig.load();
		assertEquals(8.0D, cfg.magnetRadius,
				"数值缺项应补回代码默认 8.0，而不是 Gson 的 0（min/max 钳制会放过 0 吗？不会，但默认值不为 0 的字段必须靠补回）");
	}

	@Test
	void missingPoleVaultFieldsFallBackToCodeDefaults() throws Exception {
		// 老配置文件里没有蓝银撑杆跳那几项：布尔不能被 Gson 读成 false（玩法静默消失），
		// 数值不能读成 0（杆长 0、生长 0、档位 0 秒都会让玩法直接失真）
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableMagnet\": true}");
		EnchantsConfig cfg = EnchantsConfig.load();
		assertTrue(cfg.enablePoleVault, "缺项的撑杆跳总开关必须补回 true");
		assertTrue(cfg.poleVaultCushionedLanding, "缺项的落地缓冲必须补回 true");
		assertTrue(cfg.enablePoleVaultParticles, "缺项的粒子开关必须补回 true");
		assertTrue(cfg.poleVaultBreakDrops, "缺项的顶碎掉落开关必须补回 true");
		assertEquals(5.0D, cfg.poleVaultLength, "缺项杆长补回 5 格，而不是 0");
		assertEquals(1.0D, cfg.poleVaultGrowPerSecond, "缺项生长速度补回 1 格/秒");
		assertEquals(6.0D, cfg.poleVaultFragileSeconds, "缺项易碎档补回 6 秒");
		assertEquals(30.0D, cfg.poleVaultStoneSeconds, "缺项石头档补回 30 秒");
		assertEquals(300.0D, cfg.poleVaultMaxChargeSeconds, "缺项蓄力上限补回 5 分钟");
		assertEquals(0.5D, cfg.poleVaultHorizontalSpeed, "缺项水平速度上限补回 0.5 格/刻");
		assertEquals(1.0D, cfg.poleVaultHorizontalChargeSeconds, "缺项水平蓄力窗口补回 1 秒");
		assertEquals(0.003D, cfg.poleVaultToppleNudge, 1.0E-12D, "缺项倒杆冲量补回代码默认");
	}

	@Test
	void missingMasterSwitchFallsBackToTrueAndExplicitFalseIsKept() throws Exception {
		// v1.4.0 新增玩法总开关 enchantsEnabled：老配置文件没写这一项，必须补回默认 true
		//（否则升级后整个附魔玩法静默关闭 —— Gson 缺项坑的又一个变种）；
		// 而玩家显式写 false 的则必须保留，不许被补回逻辑偷偷打开。
		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enableMagnet\": true}");
		assertTrue(EnchantsConfig.load().enchantsEnabled,
				"旧配置缺 enchantsEnabled 必须补回默认 true（玩法不能静默消失）");

		Files.writeString(configDir.resolve(EnchantsConfig.FILE_NAME),
				"{\"enchantsEnabled\": false}");
		assertFalse(EnchantsConfig.load().enchantsEnabled,
				"玩家明确写 false 的总开关必须保持 false");
	}
}
