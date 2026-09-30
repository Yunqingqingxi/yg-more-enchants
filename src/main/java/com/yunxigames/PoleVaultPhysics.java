package com.yunxigames;

/**
 * 「蓝银撑杆跳」的纯数学内核：起跳反解 + 蓄力生长 + 顶碎方块的判定 + 倒杆刚体积分。
 *
 * <p><b>为什么这一层不引用任何 Minecraft 类型</b>（连 {@code Vec3} 都不用）：
 * 物理是玩法里唯一能被「钉死」的部分，做成纯 double 函数就能被普通 JUnit 直接测，
 * 不用起服务器、不用 mock 世界。世界状态（方块、粒子、玩家）由 {@link PoleVault} 喂进来 ——
 * 包括「这块方块是不是石头类」「硬度多少」这两个由 tag / 注册表查出来的事实。
 *
 * <p><b>为什么用 MC 的重力常数而不是现实世界的 9.8</b>：玩家的竖直运动每刻执行
 * {@code vy = (vy - 0.08) * 0.98}，所以「给多大初速能上多高」必须按这个积分器反解。
 * 连续解 {@code h = v²/(2g)} 在 MC 里只是量级估计 —— 每刻 2% 的阻力会吃掉约 7% 的高度。
 *
 * <h2>v1.2.0：从「杆顶硬上限」改成「蓄力无上限」</h2>
 * <p>起跳高度不再被固定杆长封顶，而是由<b>蓄力时长</b>换算出的杆长决定：
 * 蓄得越久蓝银草长得越高（一路顶碎挡路的方块），人就被撑得越高。
 * 这一层只管「蓄了多少秒该有多长」和「这么长的杆能顶碎什么」，
 * 「世界上方还有没有空间」由 {@link PoleVault} 拿世界高度去夹。
 */
final class PoleVaultPhysics {
	private PoleVaultPhysics() {
	}

	/** MC 的重力加速度（格/刻²）—— 与玩家自由落体同一套常数。 */
	static final double GRAVITY = 0.08D;

	/** MC 每刻施加在竖直速度上的空气阻力。 */
	static final double DRAG = 0.98D;

	/** 一秒的游戏刻数。 */
	static final double TICKS_PER_SECOND = 20.0D;

	/**
	 * 握杆留白（格）：玩家高 1.8 格，脚底最高到杆顶下方 1 格时头顶正好与杆顶齐平。
	 *
	 * <p>这是「人不会被送到比自己撑的杆还高的地方」这条几何约束的来源 ——
	 * 它是人的尺寸，不是平衡旋钮，所以写成常量而不是配置项。
	 */
	static final double HEADROOM = 1.0D;

	/** 二分反解的迭代次数（60 次足够把 double 逼到机器精度）。 */
	private static final int SOLVER_ITERATIONS = 60;

	/** 模拟用的最大刻数：防呆，正常抛物线的上升段不会超过这个数。 */
	private static final int MAX_TICKS = 4000;

	/**
	 * 给定竖直初速，能升到多高（逐刻跑 MC 那套 {@code vy=(vy-0.08)*0.98; y+=vy}）。
	 *
	 * <p>返回的是「脚底上升的格数」，不含起跳点本身的高度。
	 */
	static double apexHeight(double vy) {
		double v = vy;
		double y = 0.0D;
		for (int i = 0; i < MAX_TICKS; i++) {
			v = (v - GRAVITY) * DRAG;
			if (v <= 0.0D) {
				break;
			}
			y += v;
		}
		return y;
	}

	/** 反解：要跳到 {@code apex} 格高，需要多大的竖直初速（apexHeight 对 vy 单调递增）。 */
	static double vyForApex(double apex) {
		if (!(apex > 0.0D)) {
			return 0.0D;
		}

		double lo = 0.0D;
		double hi = 1.0D;
		while (apexHeight(hi) < apex && hi < 4096.0D) {
			hi *= 2.0D;
		}

		for (int i = 0; i < SOLVER_ITERATIONS; i++) {
			double mid = (lo + hi) / 2.0D;
			if (apexHeight(mid) < apex) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) / 2.0D;
	}

	/** 助跑动能按 {@code η·v²/(2g)} 折算成的高度（格）。 */
	static double runUpHeight(double speed, double efficiency) {
		if (!(speed > 0.0D) || !(efficiency > 0.0D)) {
			return 0.0D;
		}
		return efficiency * speed * speed / (2.0D * GRAVITY);
	}

	/**
	 * 水平初速：立杆那一刻的助跑动量，随蓄力在 {@code horizontalChargeSeconds} 秒内涨到上限。
	 *
	 * <p>只增不减：蓄力是「把杆越蹬越有劲」，不会因为蓄得久而把助跑动量吃掉。
	 * 上限由配置给（默认 0.5 格/刻 ≈ 10 m/s，比疾跑 0.28 快得多），
	 * 所以哪怕只蓄 1 秒，飞出去的水平距离也明显拉长。
	 */
	static double horizontalSpeed(double runUpSpeed, double chargeSeconds,
			double horizontalChargeSeconds, double maxHorizontalSpeed, double forwardRetain) {
		double runUp = runUpSpeed > 0.0D ? runUpSpeed : 0.0D;
		double charge = chargeSeconds > 0.0D ? chargeSeconds : 0.0D;
		double window = horizontalChargeSeconds > 0.0D ? horizontalChargeSeconds : 0.0D;
		double ratio = window <= 0.0D ? 1.0D : Math.min(1.0D, charge / window);
		double max = maxHorizontalSpeed > 0.0D ? maxHorizontalSpeed : 0.0D;
		double base = runUp + ratio * Math.max(0.0D, max - runUp);
		double retain = forwardRetain > 0.0D ? forwardRetain : 0.0D;
		return base * retain;
	}

	/**
	 * 一次撑杆跳的起跳解算结果。
	 *
	 * @param vy              竖直初速（格/刻）
	 * @param horizontalSpeed 水平初速（格/刻，由 {@link #horizontalSpeed} 单独算好传进来）
	 * @param apex            预期峰值高度（格）
	 * @param refused         助跑不足，这一跳不成立
	 */
	record Launch(double vy, double horizontalSpeed, double apex, boolean refused) {
	}

	/**
	 * 起跳解算：起跳高度 = <b>杆顶留白后的高度 + 助跑动能折算的高度</b>，水平速度由调用方算好传入。
	 *
	 * <p>v1.2.0 起这里<b>不再有高度上限</b>：杆能长多高，人就能被撑多高。
	 * 唯一的边界是 {@link PoleVault} 用世界高度夹出来的杆长。
	 *
	 * <p>注意「杆长」是<b>杆顶点离地的高度</b>：脚底最多到杆顶下方 {@link #HEADROOM} 格，
	 * 所以 5 格杆只能把人送到 4 格。
	 */
	static Launch solveLaunch(double runUpSpeed, double poleLength, double horizontalSpeed,
			double runUpEfficiency, double minRunUp) {
		// NaN / 负速度一律当 0：配置钳制链的历史坑就是 NaN 会穿透 min/max，这里把同一道防线
		// 钉在物理入口，绝不让 NaN 变成「一个很大的高度」
		double speed = runUpSpeed > 0.0D ? runUpSpeed : 0.0D;
		double horizontal = horizontalSpeed > 0.0D ? horizontalSpeed : 0.0D;

		// 站着不动是撑不起来杆的：没有水平动量，人只会原地蹬腿
		if (!(speed >= minRunUp)) {
			return new Launch(0.0D, horizontal, 0.0D, true);
		}

		double apex = Math.max(0.0D, poleLength - HEADROOM) + runUpHeight(speed, runUpEfficiency);
		return new Launch(vyForApex(apex), horizontal, apex, false);
	}

	/** 附魔等级决定的生长速度（格/秒）= 基础值 + 每高一级的额外值。 */
	static double growthRate(int level, double base, double perLevelExtra) {
		double safeBase = base > 0.0D ? base : 0.0D;
		double safeExtra = perLevelExtra > 0.0D ? perLevelExtra : 0.0D;
		return safeBase + safeExtra * Math.max(0, level - 1);
	}

	/**
	 * 这一刻的蓄力应该把杆顶到多长（格，含基础长度）。
	 *
	 * <p>蓄力时长有硬上限：超过之后不再增长 —— 站着撑五分钟已经很离谱了，
	 * 再往上加只会先撞到世界高度。
	 */
	static double targetLength(double baseLength, int chargeTicks, int level,
			double growPerSecond, double growPerLevelExtra, double maxChargeSeconds) {
		double capTicks = Math.max(0.0D, maxChargeSeconds) * TICKS_PER_SECOND;
		double ticks = Math.min(Math.max(0, chargeTicks), capTicks);
		double base = baseLength > 0.0D ? baseLength : 0.0D;
		return base + growthRate(level, growPerSecond, growPerLevelExtra) * ticks / TICKS_PER_SECOND;
	}

	/**
	 * 这一块方块现在顶不顶得碎。
	 *
	 * <p>三道闸：
	 * <ol>
	 *   <li><b>硬度为负 = 永远打不动</b>（基岩 / 屏障 / 传送门框架）—— 杆就停在这儿；</li>
	 *   <li><b>蓄力不够</b>：{@code fragileSeconds} 之前什么都顶不碎；</li>
	 *   <li><b>石头类</b>（{@code #minecraft:mineable/pickaxe}）还要额外熬到 {@code stoneSeconds}，
	 *       同时硬度预算从「易碎上限」升到「石头上限」。</li>
	 * </ol>
	 *
	 * <p>为什么石头类走 tag、难易走硬度而不是硬编码方块 id：别人 mod 加的方块会自动落到
	 * 正确的档位，不需要本模组做适配（见 AGENTS「第三方 mod 兼容设计约定」）。
	 *
	 * @param chargeSeconds      已蓄力秒数
	 * @param stoneLike          是否石头类（调用方查 {@code BlockTags.MINEABLE_WITH_PICKAXE}）
	 * @param hardness           方块硬度（{@code getDestroySpeed}，负数 = 不可破坏）
	 * @param fragileSeconds     能顶碎易碎方块所需的蓄力秒数
	 * @param stoneSeconds       能顶碎石头类方块所需的蓄力秒数
	 * @param fragileMaxHardness 易碎档的硬度上限
	 * @param stoneMaxHardness   石头档的硬度上限
	 */
	static boolean canBreakAt(double chargeSeconds, boolean stoneLike, double hardness,
			double fragileSeconds, double stoneSeconds,
			double fragileMaxHardness, double stoneMaxHardness) {
		// 负硬度（基岩 / 屏障）与 NaN 一律打不动；!(x >= 0) 的写法顺带把 NaN 拦下
		if (!(hardness >= 0.0D)) {
			return false;
		}
		// !(x >= y) 而不是 x < y：NaN 蓄力时不放行
		if (!(chargeSeconds >= Math.max(0.0D, fragileSeconds))) {
			return false;
		}
		if (stoneLike && !(chargeSeconds >= Math.max(0.0D, stoneSeconds))) {
			return false;
		}

		double budget = chargeSeconds >= Math.max(0.0D, stoneSeconds)
				? stoneMaxHardness
				: fragileMaxHardness;
		return hardness <= budget;
	}

	/**
	 * 倒伏角加速度（弧度/刻²）：匀质细杆绕底端倒下，{@code α = 3g/(2L)·sinθ}。
	 *
	 * <p>θ 是与竖直方向的夹角。θ≈0 时 α≈0 —— 杆立在「不稳定平衡」上，所以它天生
	 * 先是纹丝不动、随后越倒越快，这段节奏和人上升的时间尺度天然对得上，不用手写缓动。
	 */
	static double toppleAlpha(double tilt, double length) {
		return 3.0D * GRAVITY / (2.0D * Math.max(0.5D, length)) * Math.sin(tilt);
	}

	/**
	 * 半隐式欧拉推进一刻（对倒立摆这种能量增长的刚体，显式欧拉会越算越飞）。
	 *
	 * @return {@code {新的 tilt, 新的 omega}}
	 */
	static double[] stepTopple(double tilt, double omega, double length, double maxTilt) {
		double nextOmega = omega + toppleAlpha(tilt, length);
		double nextTilt = tilt + nextOmega;
		if (nextTilt > maxTilt) {
			nextTilt = maxTilt;
		}
		return new double[] { nextTilt, nextOmega };
	}

	/**
	 * 杆倒平那一刻的角速度（能量守恒解析解 {@code ω = √(3g/L)}）。
	 *
	 * <p>自检拿它核对数值积分 —— 数值解跑偏了就说明积分器写错了。
	 */
	static double rodImpactOmega(double length) {
		return Math.sqrt(3.0D * GRAVITY / Math.max(0.5D, length));
	}
}
