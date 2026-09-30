package com.yunxigames;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yg-enchants 单元测试：validate() 钳制的纯逻辑验证。
 * 特别覆盖 min/max 钳制链的 NaN 盲区（Math.min/max 遇 NaN 原样放行）。
 */
class EnchantsUnitTest {

	@Test
	void nanRadiusFallsBackToCodeDefault() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.thunderRadius = Double.NaN;
		cfg.validate();
		assertEquals(24.0D, cfg.thunderRadius, "NaN 必须先回落默认值再钳制");
	}

	@Test
	void nanStinkyAndMagnetRadiusFallBack() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.stinkyRadius = Double.NaN;
		cfg.magnetRadius = Double.NaN;
		cfg.validate();
		assertEquals(8.0D, cfg.stinkyRadius);
		assertEquals(8.0D, cfg.magnetRadius);
	}

	@Test
	void outOfRangeValuesAreClamped() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.magnetRadius = -5.0D;
		cfg.thunderIntervalTicks = 0;
		cfg.curseBurdenSpeedPenalty = 5.0D;
		cfg.validate();
		assertEquals(1.0D, cfg.magnetRadius, "低于下界钳到下界");
		assertEquals(100, cfg.thunderIntervalTicks);
		assertEquals(0.9D, cfg.curseBurdenSpeedPenalty, "高于上界钳到上界");
	}

	@Test
	void nanProbabilityStyleFieldsFallToDefault() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.curseBurdenSpeedPenalty = Double.NaN;
		cfg.shatterApplyChance = Double.NaN;
		cfg.validate();
		assertEquals(0.20D, cfg.curseBurdenSpeedPenalty, "!(x>=lo) 写法对 NaN 恒真，应落默认值");
		assertEquals(0.0D, cfg.shatterApplyChance);
	}

	@Test
	void poleVaultFieldsAreClampedAndNaNGuarded() {
		EnchantsConfig cfg = new EnchantsConfig();
		cfg.poleVaultLength = Double.NaN;        // NaN 必须先回落默认值，再进 min/max 链
		cfg.poleVaultGrowPerSecond = Double.NaN;
		cfg.poleVaultGrowPerLevelExtra = -1.0D;
		cfg.poleVaultMaxChargeSeconds = Double.NaN;
		cfg.poleVaultFragileSeconds = Double.NaN;
		cfg.poleVaultStoneSeconds = -5.0D;
		cfg.poleVaultFragileMaxHardness = -2.0D;
		cfg.poleVaultStoneMaxHardness = -3.0D;
		cfg.poleVaultChargeMaxDistance = -1.0D;
		cfg.poleVaultToppleMaxLength = Double.NaN;
		cfg.poleVaultRunUpEfficiency = 99.0D;
		cfg.poleVaultMinRunUp = 5.0D;
		cfg.poleVaultForwardRetain = -1.0D;
		cfg.poleVaultHorizontalSpeed = Double.NaN;
		cfg.poleVaultHorizontalChargeSeconds = -3.0D;
		cfg.poleVaultCooldownTicks = -20;
		cfg.poleVaultToppleNudge = Double.NaN;
		cfg.validate();

		assertEquals(5.0D, cfg.poleVaultLength, "NaN 杆长回落默认 5");
		assertEquals(1.0D, cfg.poleVaultGrowPerSecond, "NaN 生长速度回落默认 1 格/秒");
		assertEquals(0.0D, cfg.poleVaultGrowPerLevelExtra, "负的等级加成钳到 0");
		assertEquals(300.0D, cfg.poleVaultMaxChargeSeconds, "NaN 蓄力上限回落默认 5 分钟");
		assertEquals(6.0D, cfg.poleVaultFragileSeconds, "NaN 易碎档回落默认 6 秒");
		assertEquals(6.0D, cfg.poleVaultStoneSeconds, "石头档不得早于易碎档（-5 被抬到 6）");
		assertEquals(0.0D, cfg.poleVaultFragileMaxHardness, "负硬度上限钳到 0");
		assertEquals(0.0D, cfg.poleVaultStoneMaxHardness, "石头档硬度上限不得低于易碎档");
		assertEquals(0.5D, cfg.poleVaultChargeMaxDistance, "负距离钳到下界 0.5");
		assertEquals(24.0D, cfg.poleVaultToppleMaxLength, "NaN 倒伏长度上限回落默认 24");
		assertEquals(3.0D, cfg.poleVaultRunUpEfficiency, "效率上界 3");
		assertEquals(1.0D, cfg.poleVaultMinRunUp, "门槛上界 1.0 格/刻");
		assertEquals(0.0D, cfg.poleVaultForwardRetain, "负的动量保留钳到 0");
		assertEquals(0.5D, cfg.poleVaultHorizontalSpeed, "NaN 水平速度上限回落默认 0.5");
		assertEquals(0.0D, cfg.poleVaultHorizontalChargeSeconds, "负的蓄力窗口钳到 0（立刻蓄满）");
		assertEquals(0, cfg.poleVaultCooldownTicks, "负冷却钳到 0");
		assertEquals(0.003D, cfg.poleVaultToppleNudge, 1.0E-12D, "NaN 倒杆冲量回落默认");
	}

	@Test
	void NaNRunUpSpeedStillRefusesToLaunch() {
		// 玩家速度理论上不会 NaN，但配置钳制链的历史坑就是「NaN 会穿透 min/max」，
		// 这里把同一类防线钉在物理入口上：NaN 助跑速度既不能算出 NaN 高度，也不能放行
		PoleVaultPhysics.Launch nan = PoleVaultPhysics.solveLaunch(
				Double.NaN, 5.0D, Double.NaN, 1.0D, 0.15D);
		assertTrue(nan.refused(), "NaN 速度必须判为「撑不起来」而不是放行");
		assertFalse(Double.isNaN(nan.horizontalSpeed()), "水平速度也不能是 NaN");
	}
}
