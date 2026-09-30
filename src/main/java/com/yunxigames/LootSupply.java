package com.yunxigames;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 本包自带的随机物品供给（每个玩法包各持一份，互不依赖）。
 *
 * <p>为什么每包要自带一份：yunxigames 系列的原则是「一个玩法一个 jar，包与包零依赖」——
 * 附魔的贪婪、事件福到的礼物、悬赏与 Bingo 的奖励都需要「从全部物品里随机取一件」，
 * 这些能力不能依赖随机掉落那个包，于是各自打包一份最小实现。
 *
 * <p><b>物品池走 {@link BuiltInRegistries#ITEM} 全量注册表</b>，所以任何模组添加的物品
 * 都会自动进入随机池（不写死原版 id）；只排除明显不该掉的东西（空气、刷怪蛋、
 * 命令方块之类的技术性方块）。
 *
 * <p>附魔书 / 药水一律写<b>真实数据</b>（随机附魔、随机效果），不给玩家空壳 —— 这是项目的硬性底线。
 */
public final class LootSupply {

	/** 技术性物品：掉出来只会添乱，不进随机池。 */
	private static final Set<Item> BANNED = Set.of(
			Items.COMMAND_BLOCK, Items.CHAIN_COMMAND_BLOCK, Items.REPEATING_COMMAND_BLOCK,
			Items.COMMAND_BLOCK_MINECART, Items.BARRIER, Items.STRUCTURE_BLOCK, Items.STRUCTURE_VOID,
			Items.JIGSAW, Items.LIGHT, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK,
			Items.BUNDLE);

	/** 宝藏池：奖励用（悬赏 / Bingo 连线 / 通关之类）。 */
	private static final List<Item> TREASURE = List.of(
			Items.DIAMOND, Items.DIAMOND_BLOCK, Items.GOLD_INGOT, Items.GOLD_BLOCK,
			Items.IRON_INGOT, Items.IRON_BLOCK, Items.EMERALD, Items.EMERALD_BLOCK,
			Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
			Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE, Items.EXPERIENCE_BOTTLE,
			Items.ENDER_PEARL, Items.BLAZE_ROD, Items.SHULKER_SHELL, Items.ELYTRA);

	/** 药水类物品：必须写真实效果。 */
	private static final Set<Item> POTION_ITEMS = Set.of(
			Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

	private static volatile List<Item> itemPool;
	private static volatile List<Holder<Enchantment>> enchantmentHolders;

	private LootSupply() {
	}

	// ------------------------------------------------------------ 对外

	/** 从全部物品里随机取一件（数量 1，附魔书/药水带真实数据）。 */
	public static ItemStack randomItem(ServerLevel level, BlockPos pos, RandomSource random) {
		List<Item> pool = itemPool();
		if (pool.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return makeStack(pool.get(random.nextInt(pool.size())), 1, level, random);
	}

	/** 从宝藏池随机取一件（数量 1~8）。 */
	public static ItemStack randomTreasure(ServerLevel level, RandomSource random) {
		if (TREASURE.isEmpty()) {
			return ItemStack.EMPTY;
		}

		Item item = TREASURE.get(random.nextInt(TREASURE.size()));
		int max = Math.max(1, new ItemStack(item).getMaxStackSize());
		return makeStack(item, 1 + random.nextInt(Math.min(8, max)), level, random);
	}

	/** 从全部物品里抽 {@code count} 个<b>不重复</b>的物品 id（Bingo 板 / 交易代价用）。 */
	public static List<String> samplePool(ServerLevel level, int count, RandomSource random) {
		List<Item> pool = new ArrayList<>(itemPool());
		List<String> picked = new ArrayList<>();

		int attempts = Math.min(count * 30, pool.size() * 3);
		while (picked.size() < count && attempts-- > 0 && !pool.isEmpty()) {
			Item item = pool.remove(random.nextInt(pool.size()));
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (id != null) {
				picked.add(id.toString());
			}
		}
		return picked;
	}

	/** 造一个带真实数据的物品（自检用：验证不会掉出空壳附魔书 / 空药水）。 */
	public static ItemStack makeRealSpecial(Item item, int count, ServerLevel level, RandomSource random) {
		return makeStack(item, count, level, random);
	}

	/** 敌对判定（原版敌对分类，排除 Boss 级特殊生物）。 */
	public static boolean isHostile(EntityType<?> type) {
		if (type == null || type.getCategory() != MobCategory.MONSTER) {
			return false;
		}

		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		if (id == null) {
			return false;
		}

		String path = id.getPath();
		return !path.equals("ender_dragon") && !path.equals("wither")
				&& !path.equals("giant") && !path.equals("illusioner");
	}

	// ------------------------------------------------------------ 内部

	private static List<Item> itemPool() {
		List<Item> pool = itemPool;
		if (pool != null) {
			return pool;
		}

		synchronized (LootSupply.class) {
			if (itemPool != null) {
				return itemPool;
			}

			List<Item> built = new ArrayList<>();
			for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
				if (item == null || item == Items.AIR || BANNED.contains(item)) {
					continue;
				}

				// 刷怪蛋整个剔出：掉一颗等于无限刷物资
				if (item instanceof SpawnEggItem) {
					continue;
				}

				Identifier id = BuiltInRegistries.ITEM.getKey(item);
				if (id != null && id.getPath().endsWith("_spawn_egg")) {
					continue;
				}

				// 模组里可能出现拿不到默认堆叠的占位物品，跳过更安全
				if (new ItemStack(item).isEmpty()) {
					continue;
				}

				built.add(item);
			}

			itemPool = List.copyOf(built);
			return itemPool;
		}
	}

	/** 造物品：附魔书 / 药水写真实数据，其余原样（数量照给）。 */
	private static ItemStack makeStack(Item item, int count, ServerLevel level, RandomSource random) {
		if (item == Items.ENCHANTED_BOOK) {
			return enchantedBook(count, level, random);
		}

		if (POTION_ITEMS.contains(item)) {
			ItemStack stack = randomPotion(item, random);
			stack.setCount(Math.max(1, count));
			return stack;
		}

		return new ItemStack(item, count);
	}

	/** 造一本带 1~3 条随机真实附魔的书。 */
	private static ItemStack enchantedBook(int count, ServerLevel level, RandomSource random) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.setCount(Math.max(1, count));

		List<Holder<Enchantment>> all = holders(level);
		if (all.isEmpty()) {
			return book; // 注册表拿不到时：留空书也比崩好
		}

		ItemEnchantments.Mutable ench = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		int want = 1 + random.nextInt(3);
		for (int i = 0; i < want; i++) {
			Holder<Enchantment> pick = all.get(random.nextInt(all.size()));
			ench.set(pick, 1 + random.nextInt(Math.max(1, pick.value().getMaxLevel())));
		}

		book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, ench.toImmutable());
		return book;
	}

	/** 造一个带真实药水效果的物品；拿不到就退化成裸物品，不崩。 */
	private static ItemStack randomPotion(Item item, RandomSource random) {
		List<Potion> potions = BuiltInRegistries.POTION.stream()
				.filter(p -> !p.getEffects().isEmpty())
				.toList();

		if (potions.isEmpty()) {
			return new ItemStack(item);
		}

		Potion pick = potions.get(random.nextInt(potions.size()));
		return BuiltInRegistries.POTION.getResourceKey(pick)
				.flatMap(BuiltInRegistries.POTION::get)
				.map(holder -> PotionContents.createItemStack(item, holder))
				.orElseGet(() -> new ItemStack(item));
	}

	private static List<Holder<Enchantment>> holders(ServerLevel level) {
		List<Holder<Enchantment>> cached = enchantmentHolders;
		if (cached != null) {
			return cached;
		}

		synchronized (LootSupply.class) {
			if (enchantmentHolders == null) {
				List<Holder<Enchantment>> list = new ArrayList<>();
				level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
						.listElements().forEach(list::add);
				enchantmentHolders = List.copyOf(list);
			}
			return enchantmentHolders;
		}
	}
}
