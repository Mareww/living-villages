package com.livingvillages.mixin;

import com.livingvillages.util.PlayerChestTracker;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void lv$onPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        // Only record successful placements by a player
        if (cir.getReturnValue() != ActionResult.CONSUME
                && cir.getReturnValue() != ActionResult.SUCCESS) return;
        if (context.getPlayer() == null) return;

        BlockItem self = (BlockItem)(Object)this;
        if (!(self.getBlock() instanceof AbstractChestBlock)
                && !(self.getBlock() instanceof BarrelBlock)) return;

        if (context.getWorld() instanceof ServerWorld) {
            PlayerChestTracker.add(context.getBlockPos());
        }
    }
}
