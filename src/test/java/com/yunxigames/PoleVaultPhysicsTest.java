package com.yunxigames;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 蓝银撑杆跳的物理内核测试。
 *
 * <p>这一层刻意不碰任何 Minecraft 类型（{@link PoleVaultPhysics} 只有 double），
 * 所以能在纯 JUnit 里把物理钉死：峰值反解自洽、蓄力 → 杆长、无高度上限、水平动量守恒、
 * 分档顶碎方块、以及倒杆的能量守恒 —— 真服务器上只能看个热闹，精度得靠这里。
 */
class PoleVaultPhysicsTest {

	/** 与 {@link EnchantsConfig} 默认值一致的一组参数。 */
	private static final double GROW_PER_SECOND = 1.0D;
	private static final double GROW_PER_LEVEL = 0.5D;
	private static final double MAX_CHARGE = 300.0D;
	private static final double FRAGILE_SECONDS = 6.0D;
	private static final double STONE_SECONDS = 30.0D;
	private static final double FRAGILE_MAX = 3.0D;
	private static final double STONE_MAX = 5.0D;
	private static final double HORIZONTAL_CHARGE_SECONDS = 1.0D;
	private static final double MAX_HORIZONTAL = 0.5D;

	/** 蓄满水的水平初速（蓄 60 秒肯定满了）。 */
	private static double horizontal(double runUp) {
		return PoleVaultPhysics.horizontalSpeed(runUp, 60.0D,
				HORIZONTAL_CHARGE_SECONDS, MAX_HORIZONTAL, 1.0D);
	}

	private static PoleVaultPhysics.Launch launch(double runUp, double poleLength) {
		return PoleVaultPhysics.solveLaunch(runUp, poleLength, horizontal(runUp), 1.0D, 0.15D);
	}

	private static double length(int chargeTicks, int level) {
		return PoleVaultPhysics.targetLength(5.0D, chargeTicks, level,
				GROW_PER_SECOND, GROW_PER_LEVEL, MAX_CHARGE);
	}

	private static boolean canBreak(double seconds, boolean stoneLike, double hardness) {
		return PoleVaultPhysics.canBreakAt(seconds, stoneLike, hardness,
				FRAGILE_SECONDS, STONE_SECONDS, FRAGILE_MAX, STONE_MAX);
	}

	@Test
	void apexSolverIsSelfConsistent() {
		for (double apex : new double[] { 0.5D, 1.0D, 2.0D, 3.0D, 4.0D, 64.0D, 300.0D }) {
			double vy = PoleVaultPhysics.vyForApex(apex);
			assertEquals(apex, PoleVaultPhysics.apexHeight(vy), 1.0E-6D,
					"反解出来的初速跑回积分器必须正好落在 " + apex + " 格");
		}
	}

	@Test
	void solverUsesMcIntegratorNotClosedForm() {
		// MC 的每刻阻力让实际需要的初速比闭式解 √(2gh) 高一成以上 ——
		// 用闭式解算高度，人就飞不到算好的位置
		double closedForm = Math.sqrt(2.0D * PoleVaultPhysics.GRAVITY * 4.0D);
		assertTrue(PoleVaultPhysics.vyForApex(4.0D) > closedForm * 1.05D,
				"必须按 MC 的积分器反解，而不是连续解 √(2gh)");
	}

	@Test
	void apexGrowsWithPoleAndHasNoCap() {
		// 5 格杆：脚底最多到杆顶下方 1 格（人高 1.8），再加助跑动能折算的一点点
		assertEquals(4.0D + PoleVaultPhysics.runUpHeight(0.28D, 1.0D),
				launch(0.28D, 5.0D).apex(), 1.0E-9D);

		// 300 格杆：同一条公式，没有任何人为上限
		PoleVaultPhysics.Launch tall = launch(0.28D, 300.0D);
		assertFalse(tall.refused());
		assertEquals(299.0D + PoleVaultPhysics.runUpHeight(0.28D, 1.0D), tall.apex(), 1.0E-9D);
		assertTrue(PoleVaultPhysics.apexHeight(tall.vy()) > 299.0D, "反解出来的初速要真能上天");
	}

	@Test
	void horizontalSpeedRampsWithCharge() {
		// 零蓄力：只有立杆那一刻的助跑动量（站着立杆的话连这个都没有）
		assertEquals(0.28D, PoleVaultPhysics.horizontalSpeed(0.28D, 0.0D,
				HORIZONTAL_CHARGE_SECONDS, MAX_HORIZONTAL, 1.0D), 1.0E-12D);
		// 蓄到一半：线性爬升
		assertEquals(0.39D, PoleVaultPhysics.horizontalSpeed(0.28D, 0.5D,
				HORIZONTAL_CHARGE_SECONDS, MAX_HORIZONTAL, 1.0D), 1.0E-12D);
		// 蓄满 1 秒就到上限，再蓄也不会更快
		assertEquals(MAX_HORIZONTAL, PoleVaultPhysics.horizontalSpeed(0.28D, 1.0D,
				HORIZONTAL_CHARGE_SECONDS, MAX_HORIZONTAL, 1.0D), 1.0E-12D);
		assertEquals(MAX_HORIZONTAL, PoleVaultPhysics.horizontalSpeed(0.28D, 300.0D,
				HORIZONTAL_CHARGE_SECONDS, MAX_HORIZONTAL, 1.0D), 1.0E-12D);
		// 只增不减：助跑本来就比上限快时不会被蓄力拖慢
		assertEquals(0.9D, PoleVaultPhysics.horizontalSpeed(0.9D, 0.2D,
				HORIZONTAL_CHARGE_SECONDS, MAX_HORIZONTAL, 1.0D), 1.0E-12D);
		// 保留系数仍是可调倍率
		assertEquals(0.75D, PoleVaultPhysics.horizontalSpeed(0.28D, 60.0D,
				HORIZONTAL_CHARGE_SECONDS, MAX_HORIZONTAL, 1.5D), 1.0E-12D);
		// NaN 蓄力按「没蓄」处理，绝不能变成 NaN 速度
		assertEquals(0.28D, PoleVaultPhysics.horizontalSpeed(0.28D, Double.NaN,
				HORIZONTAL_CHARGE_SECONDS, MAX_HORIZONTAL, 1.0D), 1.0E-12D);
		// 蓄力窗口为 0 = 立刻蓄满
		assertEquals(MAX_HORIZONTAL, PoleVaultPhysics.horizontalSpeed(0.1D, 0.0D,
				0.0D, MAX_HORIZONTAL, 1.0D), 1.0E-12D);
	}

	@Test
	void standingStillIsRefused() {
		assertTrue(PoleVaultPhysics.solveLaunch(0.0D, 300.0D, 0.0D, 1.0D, 0.15D).refused(),
				"站着不动撑不起来，杆再长也没用");
		assertTrue(PoleVaultPhysics.solveLaunch(0.14D, 5.0D, horizontal(0.14D), 1.0D, 0.15D).refused(),
				"差一点点也不够");
		assertFalse(PoleVaultPhysics.solveLaunch(0.15D, 5.0D, horizontal(0.15D), 1.0D, 0.15D).refused(),
				"刚好到门槛就该放行");
	}

	@Test
	void chargeGrowsThePoleAndCapsAtTheHardLimit() {
		assertEquals(5.0D, length(0, 3), 1.0E-9D, "零蓄力就是基础杆长");
		// III 级：1.0 + 0.5×2 = 2.0 格/秒 → 6 秒长 12 格、30 秒长 60 格
		assertEquals(17.0D, length(6 * 20, 3), 1.0E-9D);
		assertEquals(65.0D, length(30 * 20, 3), 1.0E-9D);
		assertEquals(5.0D + 2.0D * 300.0D, length(300 * 20, 3), 1.0E-9D);
		// 硬上限：再蓄也不会长（5 分钟就是 5 分钟）
		assertEquals(length(300 * 20, 3), length(3000 * 20, 3), 1.0E-9D);

		// 等级只影响生长速度：I 级 1.0/秒，III 级 2.0/秒
		assertEquals(11.0D, length(6 * 20, 1), 1.0E-9D);
		assertEquals(17.0D, length(6 * 20, 3), 1.0E-9D);
	}

	@Test
	void blockBreakingIsTiered() {
		// 6 秒之前：什么都顶不碎
		assertFalse(canBreak(5.9D, false, 0.5D), "泥土也要熬到易碎档");

		// 易碎档（≥6 秒）：泥土 0.5 / 木板 2.0 行，硬度超过 3.0 的不行；石头仍要等
		assertTrue(canBreak(6.0D, false, 0.5D), "泥土");
		assertTrue(canBreak(10.0D, false, 2.0D), "木板");
		assertFalse(canBreak(10.0D, false, 3.5D), "超过易碎档硬度上限");
		assertFalse(canBreak(10.0D, true, 1.5D), "石头再软也要等石头档");

		// 石头档（≥30 秒）：石头 1.5 / 深板岩矿 4.5 行；远古残骸 30 与黑曜石 50 不行
		assertTrue(canBreak(30.0D, true, 1.5D), "石头");
		assertTrue(canBreak(60.0D, true, 4.5D), "深板岩矿");
		assertTrue(canBreak(60.0D, false, 5.0D), "石头档的硬度预算对易碎方块同样放宽");
		assertFalse(canBreak(600.0D, true, 30.0D), "远古残骸");
		assertFalse(canBreak(600.0D, true, 50.0D), "黑曜石");

		// 硬度为负 = 不可破坏（基岩 / 屏障）：蓄多久都白搭
		assertFalse(canBreak(600.0D, true, -1.0D), "基岩");

		// NaN 穿透是配置钳制链的历史坑，这里在物理入口再钉一次
		assertFalse(canBreak(Double.NaN, false, 0.5D), "NaN 蓄力不放行");
		assertFalse(canBreak(60.0D, false, Double.NaN), "NaN 硬度不放行");
	}

	@Test
	void toppleFollowsEnergyConservation() {
		double poleLength = 5.0D;
		double omega = 0.003D;
		double tilt = 0.0D;
		int ticks = 0;
		while (tilt < Math.PI / 2.0D - 1.0E-9D && ticks < 600) {
			double[] next = PoleVaultPhysics.stepTopple(tilt, omega, poleLength, Math.PI / 2.0D);
			tilt = next[0];
			omega = next[1];
			ticks++;
		}

		assertEquals(Math.PI / 2.0D, tilt, 1.0E-9D, "杆必须真的倒平");
		double analytic = PoleVaultPhysics.rodImpactOmega(poleLength);
		assertEquals(analytic, omega, analytic * 0.2D,
				"倒平那一刻的角速度要对得上能量守恒解析解 √(3g/L)，数值积分跑偏就是积分器写错了");
		assertTrue(ticks > 10 && ticks < 200, "5 格杆的倒伏应该是一两秒的事，实际 " + ticks + " 刻");
	}

	@Test
	void toppleStartsSlowThenAccelerates() {
		// 倒立摆的特征：α ∝ sinθ，θ≈0 时几乎不动 —— 这正是「杆先直挺着、人升上去，再倒」的物理来源
		double poleLength = 5.0D;
		double omega = 0.003D;
		double tilt = 0.0D;
		int total = 0;
		while (tilt < Math.PI / 2.0D - 1.0E-9D && total < 600) {
			double[] next = PoleVaultPhysics.stepTopple(tilt, omega, poleLength, Math.PI / 2.0D);
			tilt = next[0];
			omega = next[1];
			total++;
		}

		double quarter = sampleTilt(poleLength, total / 4);
		double threeQuarters = sampleTilt(poleLength, total * 3 / 4);
		assertTrue(quarter < 0.15D, "前 1/4 时间杆几乎还是直的，实际 " + quarter + " 弧度");
		assertTrue(Math.PI / 2.0D - threeQuarters > quarter * 5.0D,
				"越倒越快：最后 1/4 转过的角度必须远大于最开始的");
	}

	@Test
	void shorterPoleFallsFaster() {
		// 同样的重力，短杆转得更快（ω = √(3g/L)）—— 顺手也验证了杆长真的进了物理
		assertTrue(PoleVaultPhysics.rodImpactOmega(3.0D) > PoleVaultPhysics.rodImpactOmega(5.0D));
	}

	/** 从静止倒到第 ticks 刻时的倾角（测试辅助）。 */
	private static double sampleTilt(double poleLength, int ticks) {
		double omega = 0.003D;
		double tilt = 0.0D;
		for (int i = 0; i < ticks; i++) {
			double[] next = PoleVaultPhysics.stepTopple(tilt, omega, poleLength, Math.PI / 2.0D);
			tilt = next[0];
			omega = next[1];
		}
		return tilt;
	}
}
