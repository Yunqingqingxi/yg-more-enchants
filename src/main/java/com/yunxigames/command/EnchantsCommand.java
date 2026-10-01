package com.yunxigames.command;

import com.mojang.brigadier.CommandDispatcher;
import com.yunxigames.EnchantsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * {@code /yg enchants ...}：附魔玩法（更多附魔包）的游戏内启停与状态。
 *
 * <p>各玩法包统一往 {@code /yg} 根下挂以玩法名命名的子树（Brigadier 会把各包注册的
 * 同名根节点合并成一棵命令树），与 drops 包的 {@code /yg drops} 同一布局。
 * 总开关 {@code enchantsEnabled} 只停玩法、不动玩家精调的各子开关 —— 重新打开即整体恢复。
 */
public final class EnchantsCommand {
	/** 与 drops 包同一权限档（等价旧「权限等级 2」，OP 可用）。 */
	private static final PermissionCheck PERMISSION = new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER);

	private EnchantsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("yg")
				.requires(Commands.hasPermission(PERMISSION))
				.then(Commands.literal("enchants")
						.executes(context -> status(context.getSource()))
						.then(Commands.literal("on")
								.executes(context -> toggle(context.getSource(), true)))
						.then(Commands.literal("off")
								.executes(context -> toggle(context.getSource(), false)))));
	}

	private static int toggle(CommandSourceStack source, boolean enabled) {
		EnchantsConfig config = EnchantsConfig.get();
		config.enchantsEnabled = enabled;
		config.save();
		source.sendSuccess(() -> Component.literal("[yg] 附魔玩法整体：" + (enabled ? "开启" : "关闭")
				+ "（各子开关配置保持不变，回显见 /yg enchants）"), false);
		return status(source);
	}

	private static int status(CommandSourceStack source) {
		EnchantsConfig config = EnchantsConfig.get();
		source.sendSuccess(() -> Component.literal(String.format(
				"[yg] 附魔玩法=%s | 子开关：特效=%s 击杀升级=%s 图书管理员=%s 撑杆跳=%s 诅咒=%s 汲取=%s 疾风=%s 威压=%s 磁石=%s 贪婪=%s",
				config.enchantsEnabled ? "开" : "关",
				config.enableEnchantmentBreakthrough ? "开" : "关",
				config.enableEnchantLevelUp ? "开" : "关",
				config.enableLibrarianRefresh ? "开" : "关",
				config.enablePoleVault ? "开" : "关",
				config.enableCursedEnchantments ? "开" : "关",
				config.enableLeech ? "开" : "关",
				config.enableSwift ? "开" : "关",
				config.enableDread ? "开" : "关",
				config.enableMagnet ? "开" : "关",
				config.enableGreed ? "开" : "关")), false);
		return 1;
	}
}
