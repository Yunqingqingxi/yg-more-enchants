# AGENTS.md — 更多附魔（yg-more-enchants）开发规范

> 本包是 yunxigames 系列的玩法包之一。系列总览、公共约定与全系列踩坑速查见
> [yunxigames 文档仓库](https://github.com/Yunqingqingxi/yunxigames) 的 AGENTS.md（必读）。
> 本文件是本仓库开发者（人类与 AI）的入口，开工前通读。

## 1. 本包是什么

**更多附魔**：十个自定义附魔（雷霆万钧 / 臭脚 / 碎裂 / 磁石 / 贪婪 / 负重与易碎诅咒 / 汲取 / 疾风 / 威压）、
蓝银撑杆跳（1.1 引入，1.2~1.3 修物理）、击杀升级、图书管理员重做。

- mod id：`yg_enchants`，jar：`yg-enchants-<版本>.jar`，配置：`config/yg-enchants.json`，入口 `YunxiGamesEnchants`
- 当前版本线：1.0 → 1.3.0（撑杆跳 1.1~1.3）

### 类地图

| 类 | 职责 |
| --- | --- |
| `EnchantsConfig` | 本包全部配置项 + `validate()` 钳制 |
| `ModEnchantments` | 自定义附魔解析（附魔 JSON → 运行期对象） |
| `EnchantmentEffects` | 运行期效果（十个附魔） |
| `PoleVault` / `PoleVaultPhysics` | 撑杆跳（蓄力 / 起跳判定 / 物理积分） |
| `EnchantmentLevelUps` | 击杀升级 |
| `LibrarianTrades` | 图书管理员交易重做（`mixin/VillagerMixin` 注入） |
| `EnchantSelfTest` | 本包自检 |

### 三条设计底线 / 向后兼容承诺

1. 只在服务端做判定；2. 一局制、零持久化；3. 物品不凭空消失。
mod id / jar 名 / 配置文件名 / lang key 永不改；配置字段只增不删；删字段 / 改默认行为升 major；
语义化版本 + GitHub Release 附 jar。

## 2. 环境（硬性）

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.159.0+26.2 |
| **JDK** | **25**（本机 `D:\Java\jdk-25`，runServer/build 必须显式指定） |

一切 gradle 命令加 `--offline`。

## 3. 常用命令

```bash
./gradlew compileJava --offline            # 开发期每个功能写完就跑
./gradlew test --offline                   # 三层 JUnit 测试
./gradlew smokeTest --offline              # 只跑冒烟
JAVA_HOME='D:\Java\jdk-25' ./gradlew runServer --offline > selftest-<版本>.log 2>&1
JAVA_HOME='D:\Java\jdk-25' ./gradlew build --offline
```

- runServer 工作目录是本仓库自己的 `run/`（首次跑改 `run/eula.txt` 为 `eula=true`）；
- 自检前把 `run/config/yg-enchants.json` 的 `selfTestRolls` 改成 `200`，跑完**改回 `0`**；
- 自检完 runServer 不自退，手动结束 java 进程，否则 `run/` 被锁。

## 4. 代码规范

1. 一个功能一个类，类头 javadoc 写「是什么 + 为什么」；
2. 一切数值进本包 `EnchantsConfig`，带中文注释，每个功能独立开关（「爽但不劝退」）；
3. 新配置项必须在 `validate()` 钳制：`!(x >= lo && x <= hi)` 顺带治 NaN；
4. 中文注释 / 文案 / lang 键值；
5. 26.2 API 不确定：**先查反混淆 jar，别猜**。

## 5. 测试节奏

- 三层 JUnit（Smoke / Unit / Regression）+ runServer 自检；批量开发期只跑 `compileJava`；
- **新增附魔 / 功能必须同步新增自检项**并更新本包 README 的自检表；
- 配置测试基建：`YgConfig.configDirOverride`、`mergeMissingFields`（`raw.has` 判缺项补回）、
  `orDefaultIfNaN`；构造器与 `validate()` 包内可见是测试前提，别改回 private。

## 6. 本包专属坑（全系列公共坑见系列仓库 AGENTS §7）

- **附魔 JSON**：`src/main/resources/data/yg/enchantment/*.json`（数据包定义）；
- **诅咒红字**：26.2 无 `curse` json 字段，靠 `data/minecraft/tags/enchantment/curse.json`
  附魔标签（tag 是 **merge** 不覆盖 vanilla）；
- **铁砧不拦附魔书**（原版对附魔书不做兼容性检查）→ 只想让附魔认某类物品：
  附魔 JSON 的 `supported_items` 指向自定义 tag（`data/<ns>/tags/item/<name>.json`），
  运行期再判一次物品；自检用 `Enchantment#canEnchant(ItemStack)` 正面钉死；
- **撑杆跳的两个 26.2 大坑**：
  - 给玩家速度冲量只 `setDeltaMovement(...)` 客户端跟不上，必须再置 `hurtMarked = true`
    —— 广播 `ClientboundSetEntityMotionPacket` 的是 `ServerEntity#sendChanges()`，
    **不在 `ServerPlayer` / `ServerGamePacketListenerImpl` 里**（在那儿搜不到不代表机制不存在）；
  - 读玩家这一 tick 的位移（助跑速度）用 `ServerPlayer#getKnownMovement()`，
    `getDeltaMovement()` 服务端手上这份基本是空的；
- 物品 / 实体供给复用本包 `LootSupply`（动态扫 `BuiltInRegistries`，mod 物品自动兼容）。
