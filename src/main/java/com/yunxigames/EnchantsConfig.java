package com.yunxigames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 更多附魔（yunxigames enchants 包）的独立配置。
 *
 * <p>文件位置：{@code <游戏目录>/config/yg-enchants.json}。字段全部是 public，Gson 直接读写；
 * 缺少的字段会保留默认值，所以升级后旧配置文件依然可用。每包配置相互独立。
 */
public final class EnchantsConfig extends YgConfig {
	public static final String FILE_NAME = "yg-enchants.json";

	// ================================================================ v1.12.0
	// 下面两组是 v1.12.0 的新功能：附魔突破（3 个新附魔）+ 全局事件系统（青蛙雨 / 天降陨石）。

	// --------------------------------------------- 玩法总开关

	/**
	 * <b>玩法总开关</b>（默认 true）：关闭后本包所有附魔特效、击杀升级、图书管理员随机
	 * 交易与蓝银撑杆跳全部停摆；下面各子开关的配置<b>原样保留</b>，重新打开即整体恢复。
	 *
	 * <p>为什么单设总开关而不让命令逐个关子开关：子开关是玩家精调出来的布局，一键启停
	 * 不应该破坏它 —— 与 faces / swap 包的 {@code facesEnabled} / {@code hurtSwapEnabled}
	 * 同一模式。旧配置文件没写这一项时由基类 mergeMissingFields 补回默认 true，升级无感。
	 */
	public boolean enchantsEnabled = true;

	// --------------------------------------------- 附魔突破（总开关）

	/**
	 * <b>附魔突破总开关</b>（默认 true）。
	 *
	 * <p>三个新附魔：雷霆万钧（头盔）、臭脚（鞋）、碎裂（武器/工具）。关掉后
	 * 既不会在随机掉落里带出碎裂，穿戴/使用的特效也全部停用。
	 */
	public boolean enableEnchantmentBreakthrough = true;

	/** 雷霆万钧穿戴后，每隔多少游戏刻召唤一次雷击（默认 100 = 5 秒）。 */
	public int thunderIntervalTicks = 100;

	/** 雷雨天频率提升：每隔多少刻召唤一次（默认 20 = 1 秒）。 */
	public int thunderStormIntervalTicks = 20;

	/** 雷击覆盖半径（格，默认 32 = 2 个区块）。 */
	public double thunderRadius = 24.0D;

	/** 臭脚穿戴后，清理附近花草的半径（格，默认 8）。 */
	public double stinkyRadius = 8.0D;

	/** 臭脚给附近玩家施加的反胃时长（秒，默认 8）。 */
	public int stinkyNauseaSeconds = 8;

	/** 臭脚清理花草 + 刷新反胃的节奏（游戏刻，默认 20 = 1 秒）。 */
	public int stinkyTickInterval = 20;

	/**
	 * 臭脚的<b>隐藏 buff</b>：穿戴者附近更容易刷出亡灵生物。
	 *
	 * <p>每个节奏点按这个概率额外刷 1 只亡灵（默认 0.05 = 5%/秒），且附近亡灵数量超过
	 * {@link #stinkyUndeadCap} 时不再刷。亡灵本身<b>不吃反胃</b>（反而被吸引）。
	 */
	public double stinkyUndeadSpawnChance = 0.05D;

	/** 臭脚附近亡灵数量上限（默认 4），超过就不再刷。 */
	public int stinkyUndeadCap = 4;

	/**
	 * <b>碎裂</b>出现在随机掉落的武器/工具上的概率（默认 0.10 = 10%）。
	 *
	 * <p>只落在武器/工具类（剑、镐、斧、铲、锄、三叉戟、重锤、弓、弩、钓竿等）上，
	 * 普通方块/消耗品不带这个附魔。
	 */
	public double shatterApplyChance = 0.10D;

	/** 碎裂「每次使用（攻击 / 挖方块）」触发特效的概率（默认 0.15）。 */
	public double shatterProcChance = 0.15D;

	/**
	 * 触发特效后，负面（反噬）的比例（默认 0.25 = 触发里 1/4 是负面）。
	 *
	 * <p>正面 = 对目标秒杀 / 对方块秒破 + 顺手碎掉背包里一件物品；
	 * 负面 = 碎裂穿戴者<b>全身装备 + 手中武器/工具</b>。
	 */
	public double shatterNegativeChance = 0.25D;

	/** 正面特效里「顺手碎掉背包一件物品」的概率（默认 0.5，混沌风味）。 */
	public double shatterSelfShatterChance = 0.5D;

	// --------------------------------------------- 附魔突破二期（v1.13）

	/**
	 * <b>磁石</b>（v1.13 新附魔，任意护甲）：穿戴时定期把半径内掉落物吸向自己。
	 *
	 * <p>跳过还处于拾取延迟的掉落物（玩家自己 Q 丢的东西不会被立刻吸回来）。
	 * 随 {@link #enableEnchantmentBreakthrough} 总开关一起生效。
	 */
	public boolean enableMagnet = true;

	/** 磁石吸附半径（格，默认 8）。 */
	public double magnetRadius = 8.0D;

	/** 磁石吸附结算间隔（游戏刻，默认 5 —— 每秒 4 次）。 */
	public int magnetIntervalTicks = 5;

	/** 磁石每次结算给掉落物的朝向玩家速度系数（默认 0.35，太大会把物品甩过头）。 */
	public double magnetPullStrength = 0.35D;

	/**
	 * <b>贪婪</b>（v1.13 新附魔，挖掘类工具）：挖方块时按概率<b>额外</b>随机掉一件物品。
	 *
	 * <p>额外掉落走 {@code DropRandomizer.makeStack}（和随机掉落同一套规则，附魔书/药水都带真实数据）。
	 */
	public boolean enableGreed = true;

	/** 贪婪每次挖掘额外掉一件的概率（默认 0.10 = 10%）。 */
	public double greedExtraChance = 0.10D;

	/**
	 * <b>诅咒系总开关</b>（v1.13）：负重诅咒 / 易碎诅咒的<b>效果</b>开关。
	 *
	 * <p>注意：即使关掉效果，这两枚附魔作为注册表成员仍可能出现在随机附魔书里
	 * （名字仍是红色诅咒字）——只是穿了没任何副作用。想让池子里根本不出诅咒，
	 * 见 README「已知限制」：需要改 {@code DropRandomizer.enchantedBook} 的过滤。
	 */
	public boolean enableCursedEnchantments = true;

	/** 负重诅咒：全身移速降低比例（默认 0.20 = -20%，ADD_MULTIPLIED_TOTAL）。 */
	public double curseBurdenSpeedPenalty = 0.20D;

	/** 易碎诅咒：受到攻击时，一件已装备的护甲按此概率直接碎裂消失（默认 0.10）。 */
	public double curseFrailtyBreakChance = 0.10D;

	// --------------------------------------------- 附魔突破三期（v1.14）

	/**
	 * <b>击杀随机升级附魔</b>（v1.14）：玩家击杀生物后按概率让一件身上装备的本模组附魔 +1 级。
	 *
	 * <p>只升不降（I→II→III），满级（III）的装备不再进入候选。只升级<b>已穿戴/手持</b>的，
	 * 背包与附魔书不参与。全部自定义附魔（含诅咒）都吃这个机制 —— 升级诅咒是真实的抉择。
	 */
	public boolean enableEnchantLevelUp = true;

	/** 每次玩家击杀生物触发升级掷骰的概率（v1.14.1 起默认 15%，覆盖全部附魔）。 */
	public double killEnchantLevelUpChance = 0.15D;

	/**
	 * <b>汲取</b>（v1.14 新附魔，锐利武器）：命中时按「每级 1.5 半心」回复生命。
	 *
	 * <p>挂在攻击命中（AFTER_DAMAGE）上，目标死了或没造成伤害就不回血。
	 */
	public boolean enableLeech = true;

	/** 汲取每级回复的生命（半心为单位，1.5 = 每级 0.75 颗心，默认 1.5）。 */
	public double leechHealPerLevel = 1.5D;

	/**
	 * <b>疾风</b>（v1.14 新附魔，靴子）：移速 +5%×等级（与负重诅咒同属性、可并存抵消）。
	 */
	public boolean enableSwift = true;

	/**
	 * <b>威压</b>（v1.14 新附魔，头盔）：定期震慑半径（6 格 × 等级）内的敌对生物，
	 * 施加缓慢（强度 = 等级）。
	 */
	public boolean enableDread = true;

	// ---------- v1.14.1 图书管理员交易重做 ----------

	/**
	 * <b>图书管理员交易随机刷新</b>（v1.14.1）：每次右键打开交易都重掷，
	 * 卖「顶级附魔书」（全部种类、等级=各自 max_level），代价是随机物品 ×最多 3 个。
	 */
	public boolean enableLibrarianRefresh = true;

	/** 图书管理员每笔交易的代价物品数量上限（默认 3）。 */
	public int librarianMaxCost = 3;

	// --------------------------------------------- 蓝银撑杆跳（v1.1.0，v1.2.0 起可蓄力）

	/**
	 * <b>蓝银撑杆跳</b>（v1.1.0 新附魔，<b>只能附在木棍上</b>）总开关。
	 *
	 * <p>手持带本附魔的木棍右键：立杆并开始蓄力（按住右键），松手起跳。关掉后右键无响应，
	 * 内存里的杆也会立刻清空（它们本来就只是粒子，不留世界状态）。
	 */
	public boolean enablePoleVault = true;

	/**
	 * <b>基础杆长</b>（格，默认 5）：也就是「一立起来就 5 格高」；头顶净空不足时按净空缩短。
	 *
	 * <p>v1.2.0 起这只是<b>起点</b>：蓄力会让杆继续往上长。
	 */
	public double poleVaultLength = 5.0D;

	/**
	 * <b>蓄力生长速度</b>（格/秒，默认 1.0）：每多蓄一秒，杆就再长这么高。
	 *
	 * <p>三个关键刻度（默认值下）：6 秒 ≈ 11 格、30 秒 ≈ 35 格、5 分钟 ≈ 305 格（先撞世界高度）。
	 */
	public double poleVaultGrowPerSecond = 1.0D;

	/** 每高一级附魔额外的生长速度（格/秒/级，默认 0.5）：III 级是 I 级的两倍。 */
	public double poleVaultGrowPerLevelExtra = 0.5D;

	/**
	 * <b>蓄力硬上限</b>（秒，默认 300 = 5 分钟）：到点自动起跳，不再往上长。
	 *
	 * <p>它同时是「绝对不会卡在蓄力状态里」的兜底 —— 万一客户端没把「放手」信号送上来，
	 * 到点也会自己蹦出去。
	 */
	public double poleVaultMaxChargeSeconds = 300.0D;

	/**
	 * 能顶碎<b>易碎方块</b>（非石头类）所需的蓄力（秒，默认 6）。
	 *
	 * <p>「易碎」= 不属于 {@code #minecraft:mineable/pickaxe}、且硬度不超过
	 * {@link #poleVaultFragileMaxHardness}：泥土、沙子、木头、树叶、玻璃、羊毛……
	 */
	public double poleVaultFragileSeconds = 6.0D;

	/**
	 * 能顶碎<b>石头类方块</b>所需的额外蓄力（秒，默认 30）。
	 *
	 * <p>石头类 = 属于 {@code #minecraft:mineable/pickaxe}（石头 / 圆石 / 深板岩 / 各种矿石…），
	 * 且硬度不超过 {@link #poleVaultStoneMaxHardness}。黑曜石（50）、远古残骸（30）不在其列。
	 */
	public double poleVaultStoneSeconds = 30.0D;

	/** 易碎档的硬度上限（默认 3.0）：木头/木板 2.0、陶瓦 1.25 都能碎。 */
	public double poleVaultFragileMaxHardness = 3.0D;

	/** 石头档的硬度上限（默认 5.0）：覆盖石头 1.5 与深板岩矿 4.5，挡下黑曜石 50。 */
	public double poleVaultStoneMaxHardness = 5.0D;

	/**
	 * 顶碎的方块是否掉落物品（默认 true）。
	 *
	 * <p>关掉就是「粉碎」：一路渣都不剩。开着时按原版掉落走（掉落物会顺着杆往下掉）。
	 */
	public boolean poleVaultBreakDrops = true;

	/**
	 * 蓄力时人离杆底的最大距离（格，默认 4）：跑开就中断蓄力、杆就地散掉。
	 *
	 * <p>物理上说得通：手离开杆，力就传不上去了。
	 */
	public double poleVaultChargeMaxDistance = 4.0D;

	/**
	 * 超过这个长度的杆不再整根倒伏，而是自顶向下散掉（格，默认 24）。
	 *
	 * <p>长杆倒伏在物理上要花十几秒（α ∝ 1/L）且会横扫半个屏幕，不如让蓝银草自己散开。
	 */
	public double poleVaultToppleMaxLength = 24.0D;

	/**
	 * 助跑动能折算成高度的效率（默认 1.0 = 不设损耗）。
	 *
	 * <p>MC 里重力大、跑速低，助跑本身只能贡献不到半格（疾跑约 0.49 格），所以高度主要靠杆；
	 * 助跑真正的意义在<b>水平动量</b>：跑多快就飞多远。
	 */
	public double poleVaultRunUpEfficiency = 1.0D;

	/**
	 * 立杆（开始蓄力）所需的最小水平速度（格/刻，默认 0.15）。
	 *
	 * <p>参考：走路约 0.216、疾跑约 0.28。低于这个值撑不起来 —— 站着不动没有动量，
	 * 物理上也跳不了撑杆跳。注意判定发生在<b>立杆那一刻</b>：立杆后原地蓄力，动量存着，松手还给你。
	 */
	public double poleVaultMinRunUp = 0.15D;

	/** 起跳后保留的水平动量比例（默认 1.0 = 动量守恒）；调大像被杆甩出去，调小像原地拔高。 */
	public double poleVaultForwardRetain = 1.0D;

	/**
	 * <b>蓄满水平动量后的水平速度</b>（格/刻，默认 0.5 ≈ 10 m/s）。
	 *
	 * <p>参考：疾跑约 0.28。立杆那一刻的助跑动量是下限，蓄力会把水平速度顶到这个上限 ——
	 * 所以撑杆跳能真的「飞出去一段」，而不是原地弹高。
	 */
	public double poleVaultHorizontalSpeed = 0.5D;

	/** 蓄满水平动量所需的蓄力（秒，默认 1.0）：蓄 1 秒就顶到 {@link #poleVaultHorizontalSpeed}。 */
	public double poleVaultHorizontalChargeSeconds = 1.0D;

	/**
	 * 撑杆跳自己的落地是否免摔落伤害（默认 true）。
	 *
	 * <p>拦的是「这一跳造成的下坠」：起跳后一段时间内、人在空中时把摔落距离按住
	 * （fallDistance 是摔伤的唯一输入）。关掉就是硬核物理 —— 蓄 5 分钟撑上去再摔下来，自求多福。
	 */
	public boolean poleVaultCushionedLanding = true;

	/** 撑杆跳冷却（游戏刻，默认 60 = 3 秒），走原版物品冷却 —— 零状态、客户端能看到冷却条。 */
	public int poleVaultCooldownTicks = 60;

	/**
	 * 放手瞬间传给杆的反向角冲量（弧度/刻，默认 0.003）。
	 *
	 * <p>决定杆倒得多快：杆立在「不稳定平衡」上（α ∝ sinθ，θ≈0 时几乎不动），
	 * 所以它天生先是纹丝不动、随后越倒越快。默认值下约 1.7 秒倒平，与人上升的时间尺度对得上。
	 */
	public double poleVaultToppleNudge = 0.003D;

	/**
	 * 杆的粒子特效开关（默认 true）。
	 *
	 * <p>只影响<b>起跳之后</b>留在原地的那根粒子杆 —— 蓄力期间不画杆
	 * （杆还只是手里那根蓝银草，人正好站在立杆点上，画柱子会像挂在人身上）。
	 * 关掉后只剩动作、音效与动作栏数字。
	 */
	public boolean enablePoleVaultParticles = true;


	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Logger LOGGER = LoggerFactory.getLogger("yg-enchants.json");
	private static volatile EnchantsConfig instance;

	EnchantsConfig() {  // 包内可见：单元测试与 YgConfig 缺项补回需要 new 默认实例
	}

	/** 取当前配置；首次调用会从磁盘载入。 */
	public static EnchantsConfig get() {
		EnchantsConfig local = instance;
		if (local == null) {
			synchronized (EnchantsConfig.class) {
				local = instance;
				if (local == null) {
					local = load();
				}
			}
		}
		return local;
	}

	/** 从磁盘读取配置（文件缺失或损坏时回退到默认值），并把规范化后的结果写回。 */
	public static synchronized EnchantsConfig load() {
		Path path = configPath(FILE_NAME);
		EnchantsConfig loaded = null;
		com.google.gson.JsonObject raw = null;

		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				// 先解析成 JsonObject 留底：merge 用它区分「json 里没写这一项」和「明确写了值」
				raw = GSON.fromJson(reader, com.google.gson.JsonObject.class);
				loaded = GSON.fromJson(raw, EnchantsConfig.class);
			} catch (IOException | JsonParseException e) {
				LOGGER.warn("[yg-enchants.json] 读取 {} 失败，改用默认配置：{}", path, e.toString());
			}
		}

		if (loaded == null) {
			loaded = new EnchantsConfig();
		} else {
			mergeMissingFields(loaded, raw, new EnchantsConfig());
		}

		loaded.validate();
		instance = loaded;
		loaded.save();
		return loaded;
	}

	/** 把当前配置写回磁盘。 */
	public synchronized void save() {
		Path path = configPath(FILE_NAME);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			LOGGER.error("[yg-enchants.json] 写入 {} 失败：{}", path, e.toString());
		}
	}

	/** 修正越界 / 缺失的值，并解析各个 id 列表。 */
	void validate() {
		// min/max 钳制链对 NaN 会原样放行（Math.min/max 遇 NaN 返回 NaN），先回落默认值再钳
		thunderRadius = orDefaultIfNaN(thunderRadius, 24.0D);
		stinkyRadius = orDefaultIfNaN(stinkyRadius, 8.0D);
		magnetRadius = orDefaultIfNaN(magnetRadius, 8.0D);

		// ---- v1.12.0 附魔突破 ----
		if (!(thunderIntervalTicks >= 1)) thunderIntervalTicks = 100;
		thunderIntervalTicks = Math.min(20 * 600, thunderIntervalTicks);
		if (!(thunderStormIntervalTicks >= 1)) thunderStormIntervalTicks = 20;
		thunderStormIntervalTicks = Math.min(thunderIntervalTicks, thunderStormIntervalTicks);
		thunderRadius = Math.min(128.0D, Math.max(1.0D, thunderRadius));
		stinkyRadius = Math.min(32.0D, Math.max(1.0D, stinkyRadius));
		stinkyNauseaSeconds = Math.min(600, Math.max(1, stinkyNauseaSeconds));
		stinkyTickInterval = Math.min(20 * 60, Math.max(1, stinkyTickInterval));
		if (!(stinkyUndeadSpawnChance >= 0.0D)) stinkyUndeadSpawnChance = 0.0D;
		if (stinkyUndeadSpawnChance > 1.0D) stinkyUndeadSpawnChance = 1.0D;
		stinkyUndeadCap = Math.min(64, Math.max(0, stinkyUndeadCap));

		if (!(shatterApplyChance >= 0.0D)) shatterApplyChance = 0.0D;
		if (shatterApplyChance > 1.0D) shatterApplyChance = 1.0D;
		if (!(shatterProcChance >= 0.0D)) shatterProcChance = 0.0D;
		if (shatterProcChance > 1.0D) shatterProcChance = 1.0D;
		if (!(shatterNegativeChance >= 0.0D)) shatterNegativeChance = 0.0D;
		if (shatterNegativeChance > 1.0D) shatterNegativeChance = 1.0D;
		if (!(shatterSelfShatterChance >= 0.0D)) shatterSelfShatterChance = 0.0D;
		if (shatterSelfShatterChance > 1.0D) shatterSelfShatterChance = 1.0D;

		// ---- v1.13.0 ----
		magnetRadius = Math.min(32.0D, Math.max(1.0D, magnetRadius));
		magnetIntervalTicks = Math.min(20 * 60, Math.max(1, magnetIntervalTicks));
		if (!(magnetPullStrength >= 0.0D)) magnetPullStrength = 0.35D;
		magnetPullStrength = Math.min(2.0D, magnetPullStrength);
		if (!(greedExtraChance >= 0.0D)) greedExtraChance = 0.0D;
		if (greedExtraChance > 1.0D) greedExtraChance = 1.0D;
		if (!(curseBurdenSpeedPenalty >= 0.0D)) curseBurdenSpeedPenalty = 0.20D;
		curseBurdenSpeedPenalty = Math.min(0.9D, curseBurdenSpeedPenalty);
		if (!(curseFrailtyBreakChance >= 0.0D)) curseFrailtyBreakChance = 0.0D;
		if (curseFrailtyBreakChance > 1.0D) curseFrailtyBreakChance = 1.0D;

		// ---- v1.14.0 ----
		if (!(killEnchantLevelUpChance >= 0.0D)) killEnchantLevelUpChance = 0.0D;
		if (killEnchantLevelUpChance > 1.0D) killEnchantLevelUpChance = 1.0D;
		if (!(leechHealPerLevel >= 0.0D)) leechHealPerLevel = 1.5D;
		leechHealPerLevel = Math.min(10.0D, leechHealPerLevel);

		// 图书管理员交易
		if (librarianMaxCost < 1) librarianMaxCost = 3;
		librarianMaxCost = Math.min(16, librarianMaxCost);

		// ---- v1.1.0 / v1.2.0 蓝银撑杆跳 ----
		// 杆长是起跳高度的基准，下界不能低于 1（否则立杆就没意义了）
		poleVaultLength = orDefaultIfNaN(poleVaultLength, 5.0D);
		poleVaultLength = Math.min(64.0D, Math.max(1.0D, poleVaultLength));
		poleVaultGrowPerSecond = orDefaultIfNaN(poleVaultGrowPerSecond, 1.0D);
		poleVaultGrowPerSecond = Math.min(16.0D, Math.max(0.0D, poleVaultGrowPerSecond));
		poleVaultGrowPerLevelExtra = orDefaultIfNaN(poleVaultGrowPerLevelExtra, 0.5D);
		poleVaultGrowPerLevelExtra = Math.min(16.0D, Math.max(0.0D, poleVaultGrowPerLevelExtra));
		poleVaultMaxChargeSeconds = orDefaultIfNaN(poleVaultMaxChargeSeconds, 300.0D);
		poleVaultMaxChargeSeconds = Math.min(3600.0D, Math.max(1.0D, poleVaultMaxChargeSeconds));
		// 易碎档在前、石头档在后：石头的门槛必须不早于易碎，否则「先碎木头再碎石头」的节奏会倒过来
		poleVaultFragileSeconds = orDefaultIfNaN(poleVaultFragileSeconds, 6.0D);
		poleVaultFragileSeconds = Math.min(600.0D, Math.max(0.0D, poleVaultFragileSeconds));
		poleVaultStoneSeconds = orDefaultIfNaN(poleVaultStoneSeconds, 30.0D);
		poleVaultStoneSeconds = Math.min(600.0D,
				Math.max(poleVaultFragileSeconds, poleVaultStoneSeconds));
		poleVaultFragileMaxHardness = orDefaultIfNaN(poleVaultFragileMaxHardness, 3.0D);
		poleVaultFragileMaxHardness = Math.min(100.0D, Math.max(0.0D, poleVaultFragileMaxHardness));
		poleVaultStoneMaxHardness = orDefaultIfNaN(poleVaultStoneMaxHardness, 5.0D);
		poleVaultStoneMaxHardness = Math.min(100.0D,
				Math.max(poleVaultFragileMaxHardness, poleVaultStoneMaxHardness));
		poleVaultChargeMaxDistance = orDefaultIfNaN(poleVaultChargeMaxDistance, 4.0D);
		poleVaultChargeMaxDistance = Math.min(32.0D, Math.max(0.5D, poleVaultChargeMaxDistance));
		poleVaultToppleMaxLength = orDefaultIfNaN(poleVaultToppleMaxLength, 24.0D);
		poleVaultToppleMaxLength = Math.min(1024.0D, Math.max(1.0D, poleVaultToppleMaxLength));
		poleVaultRunUpEfficiency = orDefaultIfNaN(poleVaultRunUpEfficiency, 1.0D);
		poleVaultRunUpEfficiency = Math.min(3.0D, Math.max(0.0D, poleVaultRunUpEfficiency));
		poleVaultMinRunUp = orDefaultIfNaN(poleVaultMinRunUp, 0.15D);
		poleVaultMinRunUp = Math.min(1.0D, Math.max(0.0D, poleVaultMinRunUp));
		poleVaultForwardRetain = orDefaultIfNaN(poleVaultForwardRetain, 1.0D);
		poleVaultForwardRetain = Math.min(3.0D, Math.max(0.0D, poleVaultForwardRetain));
		poleVaultHorizontalSpeed = orDefaultIfNaN(poleVaultHorizontalSpeed, 0.5D);
		poleVaultHorizontalSpeed = Math.min(8.0D, Math.max(0.0D, poleVaultHorizontalSpeed));
		poleVaultHorizontalChargeSeconds = orDefaultIfNaN(poleVaultHorizontalChargeSeconds, 1.0D);
		poleVaultHorizontalChargeSeconds = Math.min(600.0D,
				Math.max(0.0D, poleVaultHorizontalChargeSeconds));
		poleVaultCooldownTicks = Math.min(20 * 600, Math.max(0, poleVaultCooldownTicks));
		poleVaultToppleNudge = orDefaultIfNaN(poleVaultToppleNudge, 0.003D);
		poleVaultToppleNudge = Math.min(0.05D, Math.max(0.0D, poleVaultToppleNudge));
	}
}
