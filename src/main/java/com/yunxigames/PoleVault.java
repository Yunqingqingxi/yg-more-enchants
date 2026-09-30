package com.yunxigames;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「蓝银撑杆跳」玩法：木棍附魔后右键立杆、按住蓄力，松手把杆撑起来向前飞出去。
 *
 * <h2>动作全流程（每一步都能单独解释）</h2>
 * <ol>
 *   <li><b>立杆</b>（右键按下）：校验「踩在地上 / 有助跑速度 / 头顶有净空」→ 在起跳点前方
 *       {@value #PLANT_AHEAD} 格立起一根 {@code poleVaultLength} 格高的杆。
 *       <b>助跑方向与水平动量在这一刻存下来</b> —— 立杆后原地蓄力，松手时把动量还给你。</li>
 *   <li><b>蓄力</b>（按住右键）：蓝银草每秒往上长「{@code poleVaultGrowPerSecond} + 每级加成」格，
 *       一路把挡路的方块顶碎（{@code poleVaultFragileSeconds} 起顶得动泥土木头这类易碎方块，
 *       {@code poleVaultStoneSeconds} 起连石头类也顶得动；基岩/黑曜石永远顶不动 → 杆停在它下面）。
 *       蓄力到 {@code poleVaultMaxChargeSeconds} 自动起跳。
 *       <b>这段时间不画杆</b>：杆还只是手里那根蓝银草，人正好站在立杆点上，画一整根粒子柱
 *       会看着像挂在人身上；反馈靠动作栏数字 + 方块被顶碎的原版演出。</li>
 *   <li><b>起跳</b>（松手）：一个冲量 + 之后纯原版抛物线。竖直初速由
 *       {@link PoleVaultPhysics#solveLaunch} 反解 MC 的积分器得到，峰值 = 杆顶留白后的高度
 *       + 助跑动能折算的高度 —— <b>杆有多长就能撑多高，没有人为上限</b>；
 *       水平速度照搬立杆那一刻的助跑动量。
 *       杆在这一刻「现形」：沿杆打一束粒子，之后它<b>钉在立杆点上</b>当「这是我的杆」的标记 ——
 *       人飞出去，粒子留在原地，全程不跟人走。</li>
 *   <li><b>收场</b>：短杆整根倒伏（刚体，倒向与起跳方向相反 = 角动量守恒），长杆自顶向下散掉。</li>
 * </ol>
 *
 * <h2>「松手」是怎么拿到的（不装客户端 mod 的唯一通道）</h2>
 * <p>立杆时服务端调 {@link ServerPlayer#startUsingItem} 把玩家置成「使用中」：
 * 客户端 {@code Minecraft#handleKeybinds} 里 {@code isUsingItem() && !keyUse.isDown()}
 * 会立刻发 {@code RELEASE_USE_ITEM}，服务端 {@code handlePlayerAction} 收到就清标志 ——
 * 所以我们只要盯着 {@code isUsingItem()} 变假，就是「松手」，延迟最多 1 刻。
 *
 * <p>万一这个「使用中」状态没生效（原版对 useDuration = 0 的物品没有明确保证），还有兜底：
 * 按住右键时客户端每 4 刻发一次 use 包，我们的回调会一直被调用；断流超过
 * {@value #RELEASE_GAP_TICKS} 刻就当作松手。两条通道谁先到用谁，再加「蓄力到顶自动起跳」兜底，
 * 玩家永远不会卡在蓄力状态里。
 *
 * <h2>零持久化</h2>
 * <p>杆、蓄力、落地缓冲都只活在内存里（{@link #POLES} / {@link #CHARGES} / {@link #CUSHION}），
 * 关服即清；冷却走原版物品冷却，不写任何 NBT。
 */
public final class PoleVault {
	private PoleVault() {
	}

	/** 立杆点在起跳点前方多少格：踩在脚尖前，人升起来后杆就落到身下、身后。 */
	static final double PLANT_AHEAD = 0.7D;

	/** 杆倒平之后的淡出时长（刻）。 */
	static final int FADE_TICKS = 15;

	/** 长杆自顶向下散掉用多久（刻）。 */
	static final int DISSOLVE_TICKS = 30;

	/** 撒粒子的节奏：每几刻沿杆撒一趟（杆可以很长，不能每刻都撒）。 */
	static final int PARTICLE_INTERVAL = 3;

	/** 沿杆撒粒子的采样上限：杆再长也只撒这么多个点（否则几百格杆会刷爆带宽）。 */
	static final int MAX_PARTICLES = 24;

	/** 蓄力时动作栏提示的刷新间隔（刻）。 */
	static final int HINT_INTERVAL = 10;

	/**
	 * 落地缓冲的最长保护时间（刻，30 秒）。
	 *
	 * <p>正常情况缓冲到「人真的落地」就结束；这个上限只是兜底：万一玩家落在水里 / 藤蔓上
	 * 一直不算落地，也不会变成永久免摔落。
	 */
	static final int CUSHION_TICKS = 20 * 30;

	/** 起跳失败后的短冷却（刻）：只为防刷屏，不占用完整冷却。 */
	static final int FAIL_COOLDOWN_TICKS = 10;

	/**
	 * 右键包断流多少刻算「松手」（兜底通道）。
	 *
	 * <p>客户端按住右键时每 4 刻发一次 use 包，所以阈值必须大于 4；取 5 意味着最坏情况
	 * 松手后 5 刻内一定被发现。
	 */
	static final int RELEASE_GAP_TICKS = 5;

	/** 杆倒平的角度（弧度）。 */
	private static final double MAX_TILT = Math.PI / 2.0D;

	/**
	 * 一根立在地上的杆。
	 *
	 * <p>{@link #length} 在蓄力期间会一路增长；{@link #released} 为真之后才谈得上倒伏 / 散开。
	 */
	static final class Pole {
		final ServerLevel world;
		/** 杆底（起跳点前方的地面）。 */
		final Vec3 base;
		/** 当前杆长（格，含基础长度）。 */
		double length;
		/** 倒伏方向（水平单位向量，与起跳方向相反）；蓄力期间是零向量。 */
		Vec3 fallDir = Vec3.ZERO;
		/** 与竖直方向的夹角（弧度，0 = 立正）。 */
		double tilt;
		/** 角速度（弧度/刻）。 */
		double omega;
		/** 已存在的刻数（控制粒子节奏）。 */
		int age;
		/** &gt;0 表示正在淡出（倒平 / 撞墙之后）。 */
		int fade;
		/** &gt;0 表示正在自顶向下散开（长杆的收场）。 */
		int dissolve;
		/** 是否已经脱离蓄力（放手或中断）。 */
		boolean released;
		/**
		 * 「穿过去了但当时还顶不碎」的方块（树叶、花草这类没有碰撞体积的）。
		 *
		 * <p>为什么必须记账：杆是往上长的，一旦长过某个方块就不会再看它一眼 ——
		 * 如果蓄力到 6 秒时叶子早被绕过去了，那片叶子就永远是叶子（用户实测的 bug：
		 * 「怎么连树叶都顶不破」）。所以每次穿过去都记一笔，蓄力跨过档位后回头补碎。
		 */
		final List<BlockPos> pendingBreaks = new ArrayList<>();

		Pole(ServerLevel world, Vec3 base, double length) {
			this.world = world;
			this.base = base;
			this.length = length;
		}

		/** 杆上参数位置（0 = 杆底，1 = 杆顶）的世界坐标。 */
		Vec3 pointAt(double param) {
			double d = length * param;
			double s = Math.sin(tilt) * d;
			return new Vec3(
					base.x + fallDir.x * s,
					base.y + Math.cos(tilt) * d,
					base.z + fallDir.z * s);
		}
	}

	/** 一次进行中的蓄力（按玩家记）。 */
	static final class Charge {
		final UUID playerId;
		final ServerLevel world;
		final Pole pole;
		/** 立杆那一刻的助跑方向（水平单位向量）。 */
		final Vec3 dir;
		/** 立杆那一刻的水平速度（动量守恒的那份动量）。 */
		final double momentum;
		/** 立杆时的附魔等级（决定生长速度）。 */
		final int enchLevel;
		/** 已蓄力刻数。 */
		int chargeTicks;
		/** 最近一次收到右键包的游戏刻（兜底通道用）。 */
		long lastCallbackTick;
		/** 客户端是否进过「使用中」状态（进了就走精确通道）。 */
		boolean sawUsing;
		/** 已经播报到第几档（0 没播 / 1 易碎档 / 2 石头档）。 */
		int announcedTier;

		Charge(UUID playerId, ServerLevel world, Pole pole, Vec3 dir, double momentum, int enchLevel) {
			this.playerId = playerId;
			this.world = world;
			this.pole = pole;
			this.dir = dir;
			this.momentum = momentum;
			this.enchLevel = enchLevel;
		}
	}

	/** 世界里立着的杆（内存态，关服清空）。 */
	private static final List<Pole> POLES = new ArrayList<>();

	/** 正在蓄力的玩家：UUID → 蓄力状态。 */
	private static final Map<UUID, Charge> CHARGES = new HashMap<>();

	/** 落地缓冲状态：起跳后按住摔落距离，直到人真的落地（或超时兜底）。 */
	static final class Cushion {
		/** 是否已经确认离地（起跳那一两刻服务端还以为人站在地上）。 */
		boolean airborne;
		/** 剩余保护刻数（超时兜底）。 */
		int left = CUSHION_TICKS;
	}

	/** 正在享受落地缓冲的玩家：UUID → 缓冲状态。 */
	private static final Map<UUID, Cushion> CUSHION = new HashMap<>();

	/** 挂上右键钩子。 */
	public static void register() {
		UseItemCallback.EVENT.register(PoleVault::onUseItem);
	}

	/** 关服清理：杆是粒子、蓄力与缓冲是内存计数，丢掉即可。 */
	public static void reset() {
		POLES.clear();
		CHARGES.clear();
		CUSHION.clear();
	}

	// ------------------------------------------------------------ 右键

	private static InteractionResult onUseItem(Player player, Level world, InteractionHand hand) {
		// 客户端侧的伪造调用不处理（本系列所有判定都只在服务端）
		if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		EnchantsConfig config = EnchantsConfig.get();
		if (!config.enableEnchantmentBreakthrough || !config.enablePoleVault) {
			return InteractionResult.PASS;
		}

		ItemStack stack = sp.getMainHandItem();
		// supported_items 已经把附魔限死在木棍上，这里再判一次物品是「铁砧把书拍在别的物品上」的兜底
		if (!stack.is(Items.STICK)) {
			return InteractionResult.PASS;
		}

		// 蓄力中：这次右键包只是「还按着」的心跳，不重新立杆
		Charge charging = CHARGES.get(sp.getUUID());
		if (charging != null) {
			charging.lastCallbackTick = level.getGameTime();
			return InteractionResult.SUCCESS;
		}

		Holder<Enchantment> holder = ModEnchantments.poleVault(level);
		int enchLevel = ModEnchantments.getLevel(stack, holder);
		if (enchLevel <= 0) {
			return InteractionResult.PASS;
		}

		if (sp.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.PASS; // 冷却中：静默，别刷屏
		}

		// 骑乘 / 滑翔 / 游泳时没有「地面」可以借力，直接不响应
		if (sp.isPassenger() || sp.isFallFlying() || sp.isInWater()) {
			return InteractionResult.PASS;
		}

		if (!sp.onGround()) {
			return fail(sp, stack, "§7[蓝银撑杆跳] 得先踩在地上才立得住杆");
		}

		// 助跑：方向取「助跑方向」而不是视线方向 —— 撑杆跳是沿动量方向飞出去的。
		// 动量在这一刻存下来，立杆后原地蓄力不会把它丢掉。
		Vec3 runUp = sp.getKnownMovement();
		double speed = Math.sqrt(runUp.x * runUp.x + runUp.z * runUp.z);
		if (speed < config.poleVaultMinRunUp || speed < 1.0E-4D) {
			return fail(sp, stack, "§7[蓝银撑杆跳] 助跑不足 —— 撑杆跳靠的是跑起来的动量，站着撑不动");
		}
		Vec3 dir = new Vec3(runUp.x, 0.0D, runUp.z).normalize();

		// 立杆点：脚尖前方；杆穿不过石头，净空多少就立多高
		BlockPos column = BlockPos.containing(
				sp.getX() + dir.x * PLANT_AHEAD, sp.getY(), sp.getZ() + dir.z * PLANT_AHEAD);
		int clearance = measureClearance(level, column, 8);
		if (clearance < 2) {
			return fail(sp, stack, "§7[蓝银撑杆跳] 头顶只剩 " + clearance + " 格，杆立不起来");
		}

		Vec3 base = new Vec3(column.getX() + 0.5D, sp.getY(), column.getZ() + 0.5D);
		Pole pole = plant(level, base, Math.min(config.poleVaultLength, clearance));

		Charge charge = new Charge(sp.getUUID(), level, pole, dir, speed, enchLevel);
		charge.lastCallbackTick = level.getGameTime();
		CHARGES.put(sp.getUUID(), charge);

		// 把玩家置成「使用中」：客户端一松手就会发 RELEASE_USE_ITEM（服务端当刻清标志），
		// 这是不装客户端 mod 也能拿到「松手」信号的正路
		sp.startUsingItem(InteractionHand.MAIN_HAND);

		level.playSound(null, base.x, base.y, base.z,
				SoundEvents.BAMBOO_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		sp.sendSystemMessage(Component.literal("§b[蓝银撑杆跳] §7杆立起 "
				+ SelfTest.trim(pole.length) + " 格 —— §f按住右键蓄力§7，松手起跳"), true);

		return InteractionResult.SUCCESS;
	}

	/** 起跳失败：给一句人话 + 一个短冷却（不占用完整冷却，方便立刻重试）。 */
	private static InteractionResult fail(ServerPlayer player, ItemStack stack, String message) {
		player.sendSystemMessage(Component.literal(message), true);
		player.getCooldowns().addCooldown(stack, FAIL_COOLDOWN_TICKS);
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------ 每刻

	/** 每刻推进：蓄力生长 + 倒杆积分 + 落地缓冲。由入口挂在 END_SERVER_TICK 上。 */
	static void tick(MinecraftServer server) {
		EnchantsConfig config = EnchantsConfig.get();
		if (!config.enableEnchantmentBreakthrough || !config.enablePoleVault) {
			// 玩法被关掉：内存里的杆与蓄力直接丢掉（它们本来就只是粒子，不留世界状态）
			POLES.clear();
			CHARGES.clear();
			CUSHION.clear();
			return;
		}

		tickCharges(server, config);
		tickPoles(config);
		tickCushion(server, config);
	}

	/** 蓄力推进：生长 + 顶碎方块 + 松手判定。 */
	private static void tickCharges(MinecraftServer server, EnchantsConfig config) {
		if (CHARGES.isEmpty()) {
			return;
		}

		int maxTicks = maxChargeTicks(config);
		Iterator<Map.Entry<UUID, Charge>> it = CHARGES.entrySet().iterator();
		while (it.hasNext()) {
			Charge charge = it.next().getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(charge.playerId);

			String abort = abortReason(player, charge, config);
			if (abort != null) {
				it.remove();
				dissolve(charge.pole);
				if (player != null) {
					player.stopUsingItem();
					player.sendSystemMessage(Component.literal(abort), true);
				}
				continue;
			}

			boolean full = charge.chargeTicks >= maxTicks;
			if (!full) {
				charge.chargeTicks++;
			}

			growTo(charge.pole, charge.chargeTicks, charge.enchLevel, player, config);
			// 之前「穿过去了但还顶不碎」的方块（树叶/花草），蓄力跨过档位后回头补碎
			flushPendingBreaks(charge.pole, charge.chargeTicks, player, config);
			announceTier(player, charge, config);
			// 蓄力期间**不画杆**：这时杆还只是手里那根蓝银草，人正好站在立杆点上，
			// 画一整根粒子柱会看着像挂在人身上。起跳那一刻才打一束粒子让它「现形」（见 launch）

			if (charge.chargeTicks % HINT_INTERVAL == 0 || full) {
				hint(player, charge, config, full);
			}

			if (full || isReleased(player, charge)) {
				it.remove();
				launch(player, charge, config);
			}
		}
	}

	/**
	 * 「松手」判定：优先用原版的「使用中」状态（≤1 刻延迟），退回右键包心跳
	 * （≤ {@value #RELEASE_GAP_TICKS} 刻延迟）。
	 */
	private static boolean isReleased(ServerPlayer player, Charge charge) {
		boolean using = player.isUsingItem();
		if (charge.sawUsing) {
			// 客户端进过使用状态：它一松手就发 RELEASE_USE_ITEM，服务端立刻清标志
			return !using;
		}

		charge.sawUsing = using;
		return charge.world.getGameTime() - charge.lastCallbackTick > RELEASE_GAP_TICKS;
	}

	/** 蓄力中断的原因（null = 一切正常）。 */
	private static String abortReason(ServerPlayer player, Charge charge, EnchantsConfig config) {
		if (player == null || !player.isAlive()) {
			return "§7[蓝银撑杆跳] 蓄力中断";
		}
		if (player.isPassenger() || player.isFallFlying()) {
			return "§7[蓝银撑杆跳] 蓄力中断";
		}
		if (player.hurtTime > 0) {
			return "§c[蓝银撑杆跳] 挨了一下，蓄力散了";
		}
		if (!player.getMainHandItem().is(Items.STICK)
				|| ModEnchantments.getLevel(player.getMainHandItem(),
						ModEnchantments.poleVault(charge.world)) <= 0) {
			return "§7[蓝银撑杆跳] 手里的杆没了";
		}

		double max = config.poleVaultChargeMaxDistance;
		Vec3 base = charge.pole.base;
		if (player.distanceToSqr(base.x, base.y, base.z) > max * max) {
			return "§7[蓝银撑杆跳] 离杆太远，蓄力散了";
		}
		return null;
	}

	/** 蓄力进度提示（动作栏）。 */
	private static void hint(ServerPlayer player, Charge charge, EnchantsConfig config, boolean full) {
		StringBuilder text = new StringBuilder("§b[蓝银撑杆跳] §7蓄力 ")
				.append(SelfTest.trim(charge.chargeTicks / PoleVaultPhysics.TICKS_PER_SECOND))
				.append(" s · 杆高 ").append(SelfTest.trim(charge.pole.length)).append(" 格");
		if (full) {
			text.append(" §e(已满，起跳！)");
		} else if (charge.pole.length >= worldHeadroom(charge.pole)) {
			text.append(" §c(到世界顶了)");
		} else if (!canGrow(charge, config)) {
			text.append(" §c(顶住了：这块顶不动)");
		}
		player.sendSystemMessage(Component.literal(text.toString()), true);
	}

	/** 跨过 6 秒 / 30 秒档位时给一次反馈音 + 文案（只在跨过的那一刻播）。 */
	private static void announceTier(ServerPlayer player, Charge charge, EnchantsConfig config) {
		double seconds = charge.chargeTicks / PoleVaultPhysics.TICKS_PER_SECOND;
		int tier = seconds >= config.poleVaultStoneSeconds ? 2
				: seconds >= config.poleVaultFragileSeconds ? 1 : 0;
		if (tier <= charge.announcedTier) {
			return;
		}
		charge.announcedTier = tier;

		Vec3 base = charge.pole.base;
		if (tier == 1) {
			charge.pole.world.playSound(null, base.x, base.y, base.z,
					SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.6F);
			player.sendSystemMessage(Component.literal(
					"§b[蓝银撑杆跳] §7蓝银草开始顶碎易碎方块了"), true);
		} else {
			charge.pole.world.playSound(null, base.x, base.y, base.z,
					SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 0.9F);
			player.sendSystemMessage(Component.literal(
					"§d[蓝银撑杆跳] §7蓝银草硬化 —— 连石头也顶得动了"), true);
		}
	}

	/** 倒杆 / 散开 / 淡出。 */
	static void tickPoles(EnchantsConfig config) {
		if (POLES.isEmpty()) {
			return;
		}

		Iterator<Pole> it = POLES.iterator();
		while (it.hasNext()) {
			Pole pole = it.next();
			pole.age++;

			if (pole.fade > 0) {
				pole.fade--;
				// 淡出时从杆顶往下收：像草叶散掉，而不是整体变淡
				emitParticles(pole, config, pole.length * (double) pole.fade / FADE_TICKS);
				if (pole.fade <= 0) {
					it.remove();
				}
				continue;
			}

			if (pole.dissolve > 0) {
				// 长杆的收场：自顶向下散开（长杆整根倒伏要十几秒，还会横扫半个屏幕）
				pole.dissolve--;
				emitParticles(pole, config, pole.length * (double) pole.dissolve / DISSOLVE_TICKS);
				if (pole.dissolve <= 0) {
					it.remove();
				}
				continue;
			}

			if (!pole.released) {
				continue; // 还在蓄力：不画杆（起跳那一刻才现形，见 tickCharges 的注释）
			}

			double[] next = PoleVaultPhysics.stepTopple(pole.tilt, pole.omega, pole.length, MAX_TILT);
			pole.tilt = next[0];
			pole.omega = next[1];

			boolean flat = pole.tilt >= MAX_TILT - 1.0E-9D;
			// 杆头扎进实心方块当撞墙处理：停住姿态然后淡出（粒子不挡路，但倒伏姿态要老实）
			boolean blocked = !flat && tipBlocked(pole);
			if (flat || blocked) {
				pole.fade = FADE_TICKS;
				Vec3 tip = pole.pointAt(0.9D);
				pole.world.playSound(null, tip.x, tip.y, tip.z,
						SoundEvents.BAMBOO_BREAK, SoundSource.PLAYERS, 0.8F, flat ? 0.7F : 1.2F);
			}

			emitParticles(pole, config, pole.length);
		}
	}

	/**
	 * 落地缓冲：腾空期间把摔落距离按住 —— fallDistance 是摔伤的唯一输入。
	 *
	 * <p>持续到「人真的落地」，而不是固定几秒 —— 蓄满 5 分钟能撑到 250 格以上，
	 * 整段飞行本来就超过 3 秒，固定窗口会让蓄得最狠的那一跳摔死（最不该摔死的一跳）。
	 */
	private static void tickCushion(MinecraftServer server, EnchantsConfig config) {
		if (CUSHION.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Cushion>> it = CUSHION.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Cushion> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Cushion cushion = entry.getValue();

			if (player == null) {
				it.remove();
				continue;
			}

			if (!cushion.airborne) {
				// 起跳那一两刻服务端还以为人站在地上：先等它真的离地
				cushion.airborne = !player.onGround();
			} else if (player.onGround()) {
				it.remove(); // 落地：这一跳结束了
				continue;
			}

			if (--cushion.left <= 0) {
				it.remove(); // 超时兜底（落在水里 / 藤蔓上迟迟不算落地）
				continue;
			}

			if (config.poleVaultCushionedLanding && cushion.airborne) {
				player.resetFallDistance();
			}
		}
	}

	// ------------------------------------------------------------ 生长与顶碎

	/** 蓄力上限换算成刻。 */
	static int maxChargeTicks(EnchantsConfig config) {
		return (int) Math.round(Math.max(1.0D, config.poleVaultMaxChargeSeconds)
				* PoleVaultPhysics.TICKS_PER_SECOND);
	}

	/**
	 * 把杆长推进到「这么多刻蓄力」应有的高度，一路顶碎挡路的方块，返回推进后的长度。
	 *
	 * <p>每刻只推进一小段（生长速度 ÷ 20 格），所以正常情况下一次只会碰到一个新方块；
	 * 自检可以一次性喂一个大蓄力值，把整段柱子的判定跑完。
	 */
	static double growTo(Pole pole, int chargeTicks, int level, Entity breaker, EnchantsConfig config) {
		double target = Math.min(
				PoleVaultPhysics.targetLength(config.poleVaultLength, chargeTicks, level,
						config.poleVaultGrowPerSecond, config.poleVaultGrowPerLevelExtra,
						config.poleVaultMaxChargeSeconds),
				worldHeadroom(pole));
		if (pole.length >= target) {
			return pole.length;
		}

		double step = PoleVaultPhysics.growthRate(level, config.poleVaultGrowPerSecond,
				config.poleVaultGrowPerLevelExtra) / PoleVaultPhysics.TICKS_PER_SECOND;
		double next = Math.min(target, pole.length + Math.max(step, 1.0E-3D));

		int from = (int) Math.floor(pole.length);
		int to = (int) Math.floor(next - 1.0E-6D);
		for (int i = from; i <= to; i++) {
			if (!passOrBreak(pole, i, chargeTicks, breaker, config)) {
				pole.length = Math.max(pole.length, i); // 顶到这块打不动的方块的底部
				return pole.length;
			}
		}

		pole.length = next;
		return pole.length;
	}

	/** 杆现在还能不能往上长 —— 只为给玩家一句「顶住了」的动作栏提示。 */
	private static boolean canGrow(Charge charge, EnchantsConfig config) {
		Pole pole = charge.pole;
		int index = (int) Math.floor(pole.length);
		BlockPos pos = BlockPos.containing(pole.base.x, pole.base.y + index, pole.base.z);
		BlockState state = pole.world.getBlockState(pos);
		if (state.getCollisionShape(pole.world, pos).isEmpty()) {
			return true;
		}
		return PoleVaultPhysics.canBreakAt(
				charge.chargeTicks / PoleVaultPhysics.TICKS_PER_SECOND,
				state.is(BlockTags.MINEABLE_WITH_PICKAXE, s -> true),
				state.getDestroySpeed(pole.world, pos),
				config.poleVaultFragileSeconds, config.poleVaultStoneSeconds,
				config.poleVaultFragileMaxHardness, config.poleVaultStoneMaxHardness);
	}

	/**
	 * 第 {@code index} 格（相对杆底）能不能过：
	 * <b>顶得碎就顶碎 → 顶不碎但没碰撞体积（树叶/花草/水）就穿过去并记账 → 顶不碎又有碰撞体积就停住</b>。
	 *
	 * <p>踩过的坑：早先的判定是「碰撞体积为空就直接放行」，而<b>树叶恰恰没有碰撞体积</b> ——
	 * 于是判定压根没走到「能不能顶碎」，树叶永远顶不破。现在顺序反过来：
	 * 先问能不能顶碎，顶不碎才谈「挡不挡路」。
	 *
	 * <p>石头类走 {@code #minecraft:mineable/pickaxe} tag、难易走硬度 —— 不硬编码方块 id，
	 * 别人 mod 加的方块会自动落到正确档位。
	 */
	private static boolean passOrBreak(Pole pole, int index, int chargeTicks, Entity breaker,
			EnchantsConfig config) {
		ServerLevel level = pole.world;
		BlockPos pos = BlockPos.containing(pole.base.x, pole.base.y + index, pole.base.z);
		BlockState state = level.getBlockState(pos);

		if (state.isAir()) {
			return true; // 空气：没什么可顶的
		}

		if (!PoleVaultPhysics.canBreakAt(
				chargeTicks / PoleVaultPhysics.TICKS_PER_SECOND,
				state.is(BlockTags.MINEABLE_WITH_PICKAXE, s -> true),
				state.getDestroySpeed(level, pos),
				config.poleVaultFragileSeconds, config.poleVaultStoneSeconds,
				config.poleVaultFragileMaxHardness, config.poleVaultStoneMaxHardness)) {
			// 还顶不碎：没有碰撞体积的东西（树叶 / 花草 / 火把 / 水）不挡路，先穿过去记账；
			// 有碰撞体积的（石头 / 木头…）就顶在这儿，杆停在它下面
			if (state.getCollisionShape(level, pos).isEmpty()) {
				if (!pole.pendingBreaks.contains(pos)) {
					pole.pendingBreaks.add(pos.immutable());
				}
				return true;
			}
			return false;
		}

		breakBlock(level, pos, state, breaker, config);
		return true;
	}

	/** 蓄力补碎：把之前「穿过去但顶不碎」的方块按当前档位回头补上。 */
	static void flushPendingBreaks(Pole pole, int chargeTicks, Entity breaker, EnchantsConfig config) {
		if (pole.pendingBreaks.isEmpty()) {
			return;
		}

		Iterator<BlockPos> it = pole.pendingBreaks.iterator();
		while (it.hasNext()) {
			BlockPos pos = it.next();
			BlockState state = pole.world.getBlockState(pos);
			if (state.isAir()) {
				it.remove();
				continue;
			}
			if (PoleVaultPhysics.canBreakAt(
					chargeTicks / PoleVaultPhysics.TICKS_PER_SECOND,
					state.is(BlockTags.MINEABLE_WITH_PICKAXE, s -> true),
					state.getDestroySpeed(pole.world, pos),
					config.poleVaultFragileSeconds, config.poleVaultStoneSeconds,
					config.poleVaultFragileMaxHardness, config.poleVaultStoneMaxHardness)) {
				breakBlock(pole.world, pos, state, breaker, config);
				it.remove();
			}
		}
	}

	/** 顶碎一格：走原版破坏流程（掉落按配置），2001 = 原版的「方块被破坏」粒子/音效事件。 */
	private static void breakBlock(ServerLevel level, BlockPos pos, BlockState state,
			Entity breaker, EnchantsConfig config) {
		level.destroyBlock(pos, config.poleVaultBreakDrops, breaker, 512);
		level.levelEvent(null, 2001, pos, Block.getId(state));
		level.playSound(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
				SoundEvents.BAMBOO_BREAK, SoundSource.PLAYERS, 0.7F, 1.3F);
	}

	/** 世界上方还剩多少格（杆再长也没意义，还会把人送出世界）。 */
	private static double worldHeadroom(Pole pole) {
		return Math.max(1.0D, pole.world.getMaxY() - pole.base.y - 1.0D);
	}

	// ------------------------------------------------------------ 起跳与收场

	private static void launch(ServerPlayer player, Charge charge, EnchantsConfig config) {
		Pole pole = charge.pole;
		double chargeSeconds = charge.chargeTicks / PoleVaultPhysics.TICKS_PER_SECOND;

		// 水平：立杆时的助跑动量是下限，蓄力在 poleVaultHorizontalChargeSeconds 秒内把它顶到上限
		double horizontal = PoleVaultPhysics.horizontalSpeed(charge.momentum, chargeSeconds,
				config.poleVaultHorizontalChargeSeconds, config.poleVaultHorizontalSpeed,
				config.poleVaultForwardRetain);
		PoleVaultPhysics.Launch launch = PoleVaultPhysics.solveLaunch(
				charge.momentum, pole.length, horizontal,
				config.poleVaultRunUpEfficiency, config.poleVaultMinRunUp);

		player.stopUsingItem();

		if (launch.refused()) {
			dissolve(pole);
			player.sendSystemMessage(Component.literal("§7[蓝银撑杆跳] 这一跳没撑起来"), true);
			return;
		}

		// 起跳：竖直初速来自杆长（蓄力越久杆越高），水平速度照搬立杆时的助跑动量
		player.setDeltaMovement(charge.dir.x * launch.horizontalSpeed(), launch.vy(),
				charge.dir.z * launch.horizontalSpeed());
		player.hurtMarked = true; // 与击退同一条通道：让客户端把速度换成这一份
		player.resetFallDistance();

		// 收场：短杆整根倒伏（倒向与起跳方向相反 = 角动量守恒），长杆自顶向下散掉
		if (pole.length > config.poleVaultToppleMaxLength) {
			dissolve(pole);
		} else {
			release(pole, charge.dir.scale(-1.0D), config.poleVaultToppleNudge);
		}

		// 杆在这一刻「现形」：沿杆打一束粒子，之后它就钉在立杆点上当「这是我的杆」的标记
		// （人飞出去了，粒子留在原地 —— 全程不跟人走）
		emitBurst(pole, config);

		player.getCooldowns().addCooldown(player.getMainHandItem(),
				Math.max(1, config.poleVaultCooldownTicks));
		if (config.poleVaultCushionedLanding) {
			CUSHION.put(player.getUUID(), new Cushion());
		}

		ServerLevel level = charge.world;
		level.playSound(null, pole.base.x, pole.base.y, pole.base.z,
				SoundEvents.BAMBOO_PLACE, SoundSource.PLAYERS, 1.0F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TRIDENT_RIPTIDE_2, SoundSource.PLAYERS, 1.0F, 1.0F);

		player.sendSystemMessage(Component.literal("§b[蓝银撑杆跳] §7蓄力 "
				+ SelfTest.trim(chargeSeconds)
				+ " s · 杆高 " + SelfTest.trim(pole.length)
				+ " 格 · 腾空 " + SelfTest.trim(launch.apex())
				+ " 格 · 水平 " + SelfTest.trim(launch.horizontalSpeed()) + " 格/刻"), true);
	}

	// ------------------------------------------------------------ 杆的工具

	/** 在世界里立一根杆（右键路径与自检共用）。 */
	static Pole plant(ServerLevel level, Vec3 base, double length) {
		Pole pole = new Pole(level, base, length);
		POLES.add(pole);
		return pole;
	}

	/** 放手：把杆交给倒伏动画（短杆）。 */
	static void release(Pole pole, Vec3 fallDir, double nudge) {
		pole.released = true;
		pole.fallDir = fallDir;
		pole.omega = nudge;
		pole.pendingBreaks.clear(); // 放手之后不再补碎（杆已经离开蓄力状态）
	}

	/** 让杆自顶向下散掉（长杆的收场、以及蓄力中断）。 */
	static void dissolve(Pole pole) {
		pole.released = true;
		pole.dissolve = Math.max(pole.dissolve, DISSOLVE_TICKS);
		pole.pendingBreaks.clear();
	}

	/** 当前立着的杆数量（自检用）。 */
	static int poleCount() {
		return POLES.size();
	}

	/**
	 * 从 {@code base} 往上数「能立几格杆」：碰到有碰撞体积的方块就封顶。
	 *
	 * <p>用碰撞体积而不是 {@code isSolid()} —— 高草、火把这类「不是实心但不该穿」的东西
	 * 也该挡住杆。
	 */
	static int measureClearance(ServerLevel level, BlockPos base, int max) {
		int free = 0;
		for (int i = 0; i < max; i++) {
			BlockPos pos = base.above(i);
			BlockState state = level.getBlockState(pos);
			if (!state.getCollisionShape(level, pos).isEmpty()) {
				break;
			}
			free++;
		}
		return free;
	}

	/** 杆头（离杆顶留一点余量，免得贴着天花板的杆一开始就判定撞墙）是否扎进了方块。 */
	private static boolean tipBlocked(Pole pole) {
		Vec3 tip = pole.pointAt(0.97D);
		BlockPos pos = BlockPos.containing(tip.x, tip.y, tip.z);
		BlockState state = pole.world.getBlockState(pos);
		return !state.getCollisionShape(pole.world, pos).isEmpty();
	}

	/**
	 * 起跳那一刻沿杆打一束粒子：杆「现形」，成为「这是我的撑杆棍」的标记。
	 *
	 * <p>这不跟随玩家：粒子全部按 {@link Pole#pointAt} 算，钉在立杆点上，
	 * 人飞出去之后那根粒子杆还留在原地（短杆随后倒伏、长杆自顶向下散掉）。
	 */
	private static void emitBurst(Pole pole, EnchantsConfig config) {
		if (!config.enablePoleVaultParticles || !(pole.length > 0.0D)) {
			return;
		}

		ServerLevel world = pole.world;
		int samples = Math.max(1, (int) Math.round(Math.min(pole.length * 3.0D, MAX_PARTICLES * 2.0D)));
		for (int i = 0; i <= samples; i++) {
			Vec3 p = pole.pointAt((double) i / samples);
			world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
			world.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
		}
	}

	/** 沿杆撒「蓝银草」粒子：蓝（灵魂火）+ 银（末地烛），杆顶偶尔来一星电火花。 */
	private static void emitParticles(Pole pole, EnchantsConfig config, double visibleLength) {
		if (!config.enablePoleVaultParticles || pole.age % PARTICLE_INTERVAL != 0
				|| !(pole.length > 0.0D)) {
			return;
		}

		ServerLevel world = pole.world;
		double len = Math.max(0.0D, Math.min(visibleLength, pole.length));
		int samples = Math.max(1, (int) Math.round(Math.min(len * 2.0D, MAX_PARTICLES)));
		for (int i = 0; i <= samples; i++) {
			Vec3 p = pole.pointAt((double) i / samples * len / pole.length);
			world.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
			if (i % 2 == 0) {
				world.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
			}
		}

		if (pole.age % (PARTICLE_INTERVAL * 2) == 0) {
			Vec3 top = pole.pointAt(len / pole.length);
			world.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 3,
					0.12D, 0.12D, 0.12D, 0.01D);
		}
	}
}
