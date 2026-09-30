package com.yunxigames;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Set;

/**
 * 解析本模组的全部自定义附魔（雷霆万钧 / 臭脚 / 碎裂 / 磁石 / 贪婪 / 负重与易碎诅咒 /
 * 汲取 / 疾风 / 威压 / 蓝银撑杆跳）的 {@link Holder<Enchantment>}，并提供若干判断工具。
 *
 * <p>26.2 里 {@code Enchantment} 是「数据包驱动」的注册表，没有
 * {@code BuiltInRegistries.ENCHANTMENT} 这种内建字段，所以自定义附魔走的是
 * {@code data/<modid>/enchantment/*.json} 数据包，运行时再从注册表按 id 取 {@link Holder}。
 * 这里把解析结果缓存下来（注册表全局不变），避免每次都查。
 */
public final class ModEnchantments {
	private ModEnchantments() {
	}

	public static final String THUNDEROUS = "thunderous";
	public static final String STINKY_FEET = "stinky_feet";
	public static final String SHATTER = "shatter";
	public static final String MAGNET = "magnet";
	public static final String GREED = "greed";
	public static final String CURSE_OF_BURDEN = "curse_of_burden";
	public static final String CURSE_OF_FRAILTY = "curse_of_frailty";
	public static final String LEECH = "leech";
	public static final String SWIFT = "swift";
	public static final String DREAD = "dread";
	public static final String POLE_VAULT = "pole_vault";

	/** 自定义附魔的统一等级上限（与 datapack json 的 max_level 保持一致）。 */
	public static final int MAX_LEVEL = 3;

	/** 碎裂可附着的武器 / 工具 id（与 {@code data/yg/enchantment/shatter.json} 的 supported_items 一一对应）。 */
	private static final Set<String> WEAPON_TOOL_IDS = Set.of(
			"minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:iron_sword",
			"minecraft:golden_sword", "minecraft:diamond_sword", "minecraft:netherite_sword",
			"minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "minecraft:iron_pickaxe",
			"minecraft:golden_pickaxe", "minecraft:diamond_pickaxe", "minecraft:netherite_pickaxe",
			"minecraft:wooden_axe", "minecraft:stone_axe", "minecraft:iron_axe",
			"minecraft:golden_axe", "minecraft:diamond_axe", "minecraft:netherite_axe",
			"minecraft:wooden_shovel", "minecraft:stone_shovel", "minecraft:iron_shovel",
			"minecraft:golden_shovel", "minecraft:diamond_shovel", "minecraft:netherite_shovel",
			"minecraft:wooden_hoe", "minecraft:stone_hoe", "minecraft:iron_hoe",
			"minecraft:golden_hoe", "minecraft:diamond_hoe", "minecraft:netherite_hoe",
			"minecraft:trident", "minecraft:mace", "minecraft:bow",
			"minecraft:crossbow", "minecraft:fishing_rod", "minecraft:shears");

	private static volatile Holder<Enchantment> thunderousHolder;
	private static volatile Holder<Enchantment> stinkyHolder;
	private static volatile Holder<Enchantment> shatterHolder;
	private static volatile Holder<Enchantment> magnetHolder;
	private static volatile Holder<Enchantment> greedHolder;
	private static volatile Holder<Enchantment> burdenHolder;
	private static volatile Holder<Enchantment> frailtyHolder;
	private static volatile Holder<Enchantment> leechHolder;
	private static volatile Holder<Enchantment> swiftHolder;
	private static volatile Holder<Enchantment> dreadHolder;
	private static volatile Holder<Enchantment> poleVaultHolder;

	/** 从注册表解析全部自定义附魔（幂等，带缓存）。 */
	public static void resolve(ServerLevel level) {
		resolve(level.registryAccess());
	}

	public static synchronized void resolve(RegistryAccess access) {
		if (thunderousHolder != null && stinkyHolder != null && shatterHolder != null
				&& magnetHolder != null && greedHolder != null
				&& burdenHolder != null && frailtyHolder != null
				&& leechHolder != null && swiftHolder != null && dreadHolder != null
				&& poleVaultHolder != null) {
			return;
		}

		var lookup = access.lookupOrThrow(Registries.ENCHANTMENT);

		thunderousHolder = resolveOne(lookup, THUNDEROUS);
		stinkyHolder = resolveOne(lookup, STINKY_FEET);
		shatterHolder = resolveOne(lookup, SHATTER);
		magnetHolder = resolveOne(lookup, MAGNET);
		greedHolder = resolveOne(lookup, GREED);
		burdenHolder = resolveOne(lookup, CURSE_OF_BURDEN);
		frailtyHolder = resolveOne(lookup, CURSE_OF_FRAILTY);
		leechHolder = resolveOne(lookup, LEECH);
		swiftHolder = resolveOne(lookup, SWIFT);
		dreadHolder = resolveOne(lookup, DREAD);
		poleVaultHolder = resolveOne(lookup, POLE_VAULT);
	}

	private static Holder<Enchantment> resolveOne(
			net.minecraft.core.Registry<Enchantment> lookup, String name) {
		return lookup.get(ResourceKey.create(Registries.ENCHANTMENT,
				Identifier.fromNamespaceAndPath("yg", name))).orElse(null);
	}

	public static Holder<Enchantment> thunderous(ServerLevel level) {
		resolve(level);
		return thunderousHolder;
	}

	public static Holder<Enchantment> stinkyFeet(ServerLevel level) {
		resolve(level);
		return stinkyHolder;
	}

	public static Holder<Enchantment> shatter(ServerLevel level) {
		resolve(level);
		return shatterHolder;
	}

	public static Holder<Enchantment> magnet(ServerLevel level) {
		resolve(level);
		return magnetHolder;
	}

	public static Holder<Enchantment> greed(ServerLevel level) {
		resolve(level);
		return greedHolder;
	}

	public static Holder<Enchantment> curseOfBurden(ServerLevel level) {
		resolve(level);
		return burdenHolder;
	}

	public static Holder<Enchantment> curseOfFrailty(ServerLevel level) {
		resolve(level);
		return frailtyHolder;
	}

	public static Holder<Enchantment> leech(ServerLevel level) {
		resolve(level);
		return leechHolder;
	}

	public static Holder<Enchantment> swift(ServerLevel level) {
		resolve(level);
		return swiftHolder;
	}

	public static Holder<Enchantment> dread(ServerLevel level) {
		resolve(level);
		return dreadHolder;
	}

	/** 蓝银撑杆跳（只能附在木棍上，见 {@code data/yg/enchantment/pole_vault.json}）。 */
	public static Holder<Enchantment> poleVault(ServerLevel level) {
		resolve(level);
		return poleVaultHolder;
	}

	/** 自检用：按名字取附魔 Holder（不存在返回 null）。 */
	public static Holder<Enchantment> byName(ServerLevel level, String name) {
		resolve(level);
		return switch (name) {
			case THUNDEROUS -> thunderousHolder;
			case STINKY_FEET -> stinkyHolder;
			case SHATTER -> shatterHolder;
			case MAGNET -> magnetHolder;
			case GREED -> greedHolder;
			case CURSE_OF_BURDEN -> burdenHolder;
			case CURSE_OF_FRAILTY -> frailtyHolder;
			case LEECH -> leechHolder;
			case SWIFT -> swiftHolder;
			case DREAD -> dreadHolder;
			case POLE_VAULT -> poleVaultHolder;
			default -> null;
		};
	}

	/** 物品堆上某附魔的等级（未附魔返回 0）。 */
	public static int getLevel(ItemStack stack, Holder<Enchantment> holder) {
		if (holder == null || stack == null || stack.isEmpty()) {
			return 0;
		}

		ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
		return ench == null ? 0 : ench.getLevel(holder);
	}

	/** 物品堆上是否带有某个本模组附魔。 */
	public static boolean hasEnchantment(ItemStack stack, Holder<Enchantment> holder) {
		if (holder == null || stack == null || stack.isEmpty()) {
			return false;
		}

		ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
		return ench != null && ench.getLevel(holder) > 0;
	}

	/** 该物品是不是武器 / 工具（用于决定碎裂 10% 是否可能附着）。 */
	public static boolean isWeaponOrTool(Item item) {
		if (item == null) {
			return false;
		}

		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		return id != null && WEAPON_TOOL_IDS.contains(id.toString());
	}
}
