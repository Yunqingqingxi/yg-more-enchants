package com.yunxigames;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

	/**
	 * 击杀生物 → 随机升级一件身上装备的附魔（v1.14.1 起覆盖<b>全部附魔</b>，含原版）。
	 *
	 * <p><b>规则</b>：玩家击杀生物后按 {@code killEnchantLevelUpChance} 掷骰，命中时从
	 * 六个装备槽里挑一件「带着任意附魔且还没到该附魔自身满级」的装备，等级 +1。
	 *
	 * <ul>
	 *   <li><b>只升不降</b>：等级永远 +1 递进（锋利 III→IV→V），不存在替换降级；</li>
	 *   <li><b>各自封顶</b>：以附魔自身的 max_level 为上限（锋利 V / 保护 IV / 本模组 III），
	 *       满级的附魔不进入候选池；</li>
	 *   <li><b>诅咒不升</b>：进 {@code #minecraft:curse} 标签的附魔（绑定/消失/负重/易碎）
	 *       被排除 —— 升级诅咒毫无意义；</li>
	 *   <li><b>只升级已装备的</b>：背包里和附魔书上的不参与 —— 想升级就把附魔穿在身上去打怪。</li>
	 * </ul>
	 */
public final class EnchantmentLevelUps {
	private EnchantmentLevelUps() {
	}

	/** 升级掷骰专用随机源（不与游戏共享序列，行为可独立复现）。 */
	private static final net.minecraft.util.RandomSource RANDOM = net.minecraft.util.RandomSource.create();

	/** 注册击杀钩子。 */
	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(EnchantmentLevelUps::onDeath);
	}

	/** 关服无需清理（无状态）。 */
	public static void reset() {
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		EnchantsConfig config = EnchantsConfig.get();
		if (!config.enableEnchantmentBreakthrough
				|| !config.enableEnchantLevelUp
				|| config.killEnchantLevelUpChance <= 0.0D) {
			return;
		}

		// 玩家死亡不参与；必须是玩家击杀
		if (entity instanceof Player
				|| !(source.getEntity() instanceof ServerPlayer player)
				|| !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		if (RANDOM.nextDouble() >= config.killEnchantLevelUpChance) {
			return;
		}

		// 收集候选：六槽里「带本模组附魔且未满级」的（槽位, 附魔, 当前等级）
		record Candidate(EquipmentSlot slot, Holder<Enchantment> ench, int level) {
		}
		List<Candidate> candidates = new ArrayList<>();

		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
		}) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}

			ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
			if (ench == null || ench.isEmpty()) {
				continue;
			}

			for (Holder<Enchantment> h : ench.keySet()) {
				if (h == null || isCurse(h)) {
					continue;
				}
				int lv = ench.getLevel(h);
				if (lv > 0 && lv < h.value().getMaxLevel()) {
					candidates.add(new Candidate(slot, h, lv));
				}
			}
		}

		if (candidates.isEmpty()) {
			return;
		}

		Candidate pick = candidates.get(RANDOM.nextInt(candidates.size()));
		ItemStack stack = player.getItemBySlot(pick.slot());

		int newLevel = levelUp(stack, pick.ench());
		String fullName = Enchantment.getFullname(pick.ench(), newLevel).getString();
		player.sendSystemMessage(Component.literal(
				"§b[附魔共鸣] 你" + slotName(pick.slot()) + "的 §f" + fullName + "§b 共鸣升级了！"));

		if (config.debugLog) {
			Yg.LOGGER.info("[yg] 击杀升级：{} 槽位 {} {} -> {}",
					player.getName().getString(), pick.slot(),
					pick.ench().unwrapKey().map(k -> k.identifier().toString()).orElse("?"),
					newLevel);
		}
	}

	/**
	 * 把一件物品上的指定附魔升一级并写回（自检可独立调用）。
	 *
	 * <p>只接受「当前等级 ≥1 且 < 满级」的升级请求 —— 未附魔或已满级的物品原样返回当前等级。
	 * 返回升级后的等级。
	 */
	static int levelUp(ItemStack stack, Holder<Enchantment> h) {
		int cur = ModEnchantments.getLevel(stack, h);
		if (cur <= 0 || cur >= h.value().getMaxLevel()) {
			return cur; // 满级封顶 / 未附魔：不动
		}

		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(
				stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
		mutable.set(h, cur + 1);
		EnchantmentHelper.setEnchantments(stack, mutable.toImmutable());
		return cur + 1;
	}

	/** 进 {@code #minecraft:curse} 标签的附魔（绑定 / 消失 / 负重 / 易碎）不参与升级。 */
	private static boolean isCurse(Holder<Enchantment> h) {
		return h.is(net.minecraft.tags.EnchantmentTags.CURSE);
	}

	private static String slotName(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> "头盔上";
			case CHEST -> "胸甲上";
			case LEGS -> "护腿上";
			case FEET -> "靴子上";
			case MAINHAND -> "手上的";
			case OFFHAND -> "副手的";
			default -> "装备上的";
		};
	}
}
