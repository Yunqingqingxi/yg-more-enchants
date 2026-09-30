package com.yunxigames;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.List;

/**
 * 图书管理员交易重做（v1.14.1）：每次打开交易都随机刷新，只卖<b>顶级</b>附魔书。
 *
 * <ul>
 *   <li><b>每次打开随机刷新</b>：右键图书管理员时（mixin 注入 mobInteract）重掷全部交易
 *       —— 不再是「终身锁定一本」，多开几次就能逛出所有种类的附魔书；</li>
 *   <li><b>全部种类 + 全是顶级</b>：从注册表全部附魔（排除诅咒）随机抽取，
 *       等级一律 = 该附魔的 max_level（锋利 V / 保护 IV / 本模组 III…）；</li>
 *   <li><b>收购价上限 3 颗绿宝石</b>：每笔 1~3 颗（{@code librarianMaxPrice} 可调）。</li>
 * </ul>
 *
 * <p>只影响<b>图书管理员</b>职业，其它村民交易不动。
 */
public final class LibrarianTrades {
	private LibrarianTrades() {
	}

	/** 是否图书管理员（职业 key = minecraft:librarian）。 */
	public static boolean isLibrarian(Villager villager) {
		return villager.getVillagerData().profession().unwrapKey()
				.map(k -> k == VillagerProfession.LIBRARIAN)
				.orElse(false);
	}

	/** 刷新指定图书管理员的交易（每次打开调用）。 */
	public static void refreshOffers(ServerLevel level, Villager villager) {
		villager.setOffers(generateOffers(level, level.getRandom(),
				EnchantsConfig.get().librarianMaxCost));
	}

	/**
	 * 生成一组随机顶级附魔书交易（自检可独立调用）。
	 *
	 * <p>每次 3 笔；每笔 = <b>随机物品 × 1~{@code maxCount} 个</b> 换 一本随机顶级附魔书
	 * （代价物品从随机掉落主池抽 —— 用你在游戏里真的见得到的东西换书）。
	 */
	public static MerchantOffers generateOffers(ServerLevel level, RandomSource random, int maxCount) {
		MerchantOffers offers = new MerchantOffers();

		List<Holder<Enchantment>> pool = new ArrayList<>();
		for (Holder<Enchantment> h : level.registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.listElements().toList()) {
			if (h != null && !h.is(net.minecraft.tags.EnchantmentTags.CURSE)) {
				pool.add(h);
			}
		}
		if (pool.isEmpty()) {
			return offers;
		}

		for (int i = 0; i < 3; i++) {
			Holder<Enchantment> pick = pool.get(random.nextInt(pool.size()));
			int maxLevel = pick.value().getMaxLevel();

			// 顶级附魔书：等级 = 自身 max_level
			// （v1.14.1 修正：附魔书必须写 STORED_ENCHANTMENTS 组件 —— 这是铁砧实际读取的组件，
			//   写 ENCHANTMENTS 会导致铁砧取不出附魔）
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			ItemEnchantments.Mutable ench = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			ench.set(pick, maxLevel);
			book.set(DataComponents.STORED_ENCHANTMENTS, ench.toImmutable());
			book.set(DataComponents.CUSTOM_NAME, Component.literal(
					"§b顶级 · " + Enchantment.getFullname(pick, maxLevel).getString()));

			// 代价：随机掉落主池里的一件物品 × 1~maxCount（上限 3）
			List<String> costIds = LootSupply.samplePool(level, 1, random);
			if (costIds.isEmpty()) {
				return offers;
			}
			Item costItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(costIds.get(0)));
			if (costItem == null || costItem == Items.AIR) {
				i--; // 池子抽到空值重抽
				continue;
			}
			int count = 1 + random.nextInt(Math.max(1, Math.min(3, maxCount)));
			offers.add(new MerchantOffer(
					new ItemCost(costItem, count),
					book,
					0, 4, 0.0F));
		}
		return offers;
	}

	/** 供 mixin 调用的统一入口（带配置开关）。 */
	public static void onOpenTrading(Villager villager) {
		if (!EnchantsConfig.get().enableLibrarianRefresh
				|| !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		if (!isLibrarian(villager)) {
			return;
		}
		refreshOffers(level, villager);

		if (EnchantsConfig.get().debugLog) {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(villager.getType());
			Yg.LOGGER.info("[yg] 图书管理员 {} 交易已刷新", id);
		}
	}

	/** 占位：保留 ResourceKey import 的语义引用（LIBRARIAN key 由 isLibrarian 使用）。 */
	static ResourceKey<VillagerProfession> librarianKey() {
		return VillagerProfession.LIBRARIAN;
	}
}
