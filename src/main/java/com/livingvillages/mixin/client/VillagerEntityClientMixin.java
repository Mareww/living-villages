package com.livingvillages.mixin.client;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public class VillagerEntityClientMixin {

    /**
     * Client-side intercept: if the villager is holding a gift item in their
     * main hand, cancel the interaction before the trade screen opens locally.
     * The server-side mixin handles the actual item transfer.
     */
    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void lv$blockTradeIfGift(PlayerEntity player, Hand hand,
                                      CallbackInfoReturnable<ActionResult> cir) {
        VillagerEntity self = (VillagerEntity)(Object)this;
        // Villagers only hold items in their main hand when our mod puts a gift there.
        // Blocking the interaction here prevents the client from opening the trade screen.
        if (!self.getStackInHand(Hand.MAIN_HAND).isEmpty()
                && !self.getStackInHand(Hand.MAIN_HAND).isOf(net.minecraft.item.Items.FISHING_ROD)) {
            cir.setReturnValue(ActionResult.SUCCESS);
            cir.cancel();
        }
    }
}
