package com.yunxigames.mixin;

import com.yunxigames.LibrarianTrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v1.14.1 图书管理员交易随机刷新：每次右键打开交易前重掷全部交易。
 *
 * <p>只影响图书管理员职业（{@code minecraft:librarian}），其它村民不动；
 * 配置开关 {@code enableLibrarianRefresh} 可整体关闭。
 */
@Mixin(net.minecraft.world.entity.npc.villager.Villager.class)
public class VillagerMixin {

	@Inject(method = "mobInteract", at = @At("HEAD"))
	private void yg$refreshLibrarianTrades(Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		Villager villager = (Villager) (Object) this;
		if (!villager.level().isClientSide() && villager.level() instanceof ServerLevel level) {
			LibrarianTrades.onOpenTrading(villager);
		}
	}
}
