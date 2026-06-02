package com.livingvillages.mixin;

import com.livingvillages.behavior.AngerBehavior;
import com.livingvillages.behavior.CampfireBehavior;
import com.livingvillages.behavior.CampfireConversation;
import com.livingvillages.behavior.GiftBehavior;
import com.livingvillages.behavior.ReputationSystem;
import com.livingvillages.behavior.VillagerBehaviorHandler;
import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerEntity.class)
public class VillagerEntityMixin implements IVillagerBehaviorState {

    @Unique private int lv$conversationCooldown = (int)(Math.random() * 2400);
    @Unique private int lv$weatherCooldown      = (int)(Math.random() * 100);
    @Unique private int lv$campfireCooldown     = (int)(Math.random() * 600);
    @Unique private int lv$ambientCooldown      = (int)(Math.random() * 1200);
    @Unique private boolean lv$seekingShelter   = false;
    @Unique private boolean lv$wasRaining       = false;
    @Unique private BlockPos lv$campfireTarget  = null;
    @Unique private BlockPos lv$shelterTarget   = null;
    @Unique private int lv$celebrationTimer     = 0;
    @Unique private int lv$campfireSeat         = -1;
    @Unique private java.util.UUID lv$angryTarget = null;
    @Unique private int lv$angerCooldown        = 0;
    @Unique private int lv$napTimer             = 0;

    // ── HEAD inject: runs before tickMovement() ──────────────────────────────
    // Entity-entity collision pushes happen between ticks (outside the entity's
    // own tick). By the time our TAIL inject runs, the push velocity is already
    // set; tickMovement() will apply it NEXT tick. The HEAD inject cancels that
    // velocity BEFORE tickMovement runs, so seated villagers can never be moved.
    @Inject(method = "tick", at = @At("HEAD"))
    private void lv$onTickHead(CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity)(Object)this;
        if (!(self.getWorld() instanceof ServerWorld) || !self.isAlive()) return;
        IVillagerBehaviorState state = (IVillagerBehaviorState) self;

        BlockPos campfire = state.livingvillages$getCampfireTarget();
        int seatIdx = state.livingvillages$getCampfireSeat();
        if (campfire == null || seatIdx < 0) return;

        // Don't freeze panicking villagers — they need to flee
        if (self.getBrain().hasActivity(net.minecraft.entity.ai.brain.Activity.PANIC)) return;

        BlockPos seat = CampfireBehavior.getSeat(campfire, seatIdx);
        if (self.squaredDistanceTo(seat.getX() + 0.5, self.getY(), seat.getZ() + 0.5) < 2.25) {
            Vec3d v = self.getVelocity();
            self.setVelocity(0, v.y, 0);
        }
    }

    // ── Gift interaction — cancel trade screen when villager holds a gift ────
    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void lv$onInteractMob(PlayerEntity player, Hand hand,
                                   CallbackInfoReturnable<ActionResult> cir) {
        VillagerEntity self = (VillagerEntity)(Object)this;
        // If the villager has ANY item in their main hand, assume it's a gift.
        // Villagers only hold items via our mod, so this is safe.
        if (self.getStackInHand(Hand.MAIN_HAND).isEmpty()) return;
        if (self.getStackInHand(Hand.MAIN_HAND).isOf(net.minecraft.item.Items.FISHING_ROD)) return;

        if (hand == Hand.MAIN_HAND && self.getWorld() instanceof ServerWorld sw) {
            GiftBehavior.tryAccept(self, player, sw);
        }
        cir.setReturnValue(ActionResult.SUCCESS);
        cir.cancel();
    }

    // ── NBT persistence ───────────────────────────────────────────────────────
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void lv$onWrite(NbtCompound nbt, CallbackInfo ci) {
        ReputationSystem.writeNbt((VillagerEntity)(Object)this, nbt);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void lv$onRead(NbtCompound nbt, CallbackInfo ci) {
        ReputationSystem.readNbt((VillagerEntity)(Object)this, nbt);
    }

    private static final String[] TRAPPED_LINES = {
        "I guess I had no other choice...", "Do what you must.",
        "Not like I can say no.", "Fine. Take it.",
        "I would rather not, but...", "You leave me little option.",
        "This is not how I wanted to spend my day.",
        "At least it is over.", "Whatever. Here.",
        "I suppose this is my life now.", "Just... take it.",
        "Not much I can do about it.", "If I must.",
    };

    // ── Trade reaction + reputation ───────────────────────────────────────────
    @Inject(method = "afterUsing", at = @At("TAIL"))
    private void lv$onAfterUsing(TradeOffer offer, CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity)(Object)this;
        if (!(self.getWorld() instanceof ServerWorld serverWorld)) return;

        var customer = self.getCustomer();
        if (!(customer instanceof PlayerEntity)) return;
        PlayerEntity player = (PlayerEntity) customer;

        if (isTrapped(self)) {
            // Trapped — no reputation gain, show forced-trade phrases
            String phrase = TRAPPED_LINES[self.getRandom().nextInt(TRAPPED_LINES.length)];
            CampfireConversation.spawnSpeech(self, phrase, serverWorld);
        } else {
            // Free — normal reaction bubble + rep gain
            CampfireConversation.spawnTradeBubble(self, serverWorld);
            ReputationSystem.addReputation(self, player, 1);
        }
    }

    /** Villager is considered trapped if riding a vehicle or surrounded on 3+ sides. */
    @Unique
    private static boolean isTrapped(VillagerEntity villager) {
        if (villager.hasVehicle()) return true; // minecart / boat
        net.minecraft.util.math.BlockPos pos = villager.getBlockPos();
        net.minecraft.world.World world = villager.getWorld();
        int walls = 0;
        for (net.minecraft.util.math.Direction dir : new net.minecraft.util.math.Direction[]{
                net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.SOUTH,
                net.minecraft.util.math.Direction.EAST,  net.minecraft.util.math.Direction.WEST}) {
            net.minecraft.util.math.BlockPos n = pos.offset(dir);
            if (!world.getBlockState(n).isAir()
                    && !(world.getBlockState(n).getBlock() instanceof net.minecraft.block.DoorBlock)) {
                walls++;
            }
        }
        return walls >= 3;
    }

    // ── TAIL inject: runs after Brain tasks ──────────────────────────────────
    @Inject(method = "tick", at = @At("TAIL"))
    private void lv$onTickTail(CallbackInfo ci) {
        VillagerEntity self = (VillagerEntity)(Object)this;
        if (!(self.getWorld() instanceof ServerWorld serverWorld) || !self.isAlive()) return;
        VillagerBehaviorHandler.tick(self, serverWorld);
    }

    @Override public int livingvillages$getConversationCooldown() { return lv$conversationCooldown; }
    @Override public void livingvillages$setConversationCooldown(int v) { lv$conversationCooldown = v; }
    @Override public int livingvillages$getWeatherCooldown() { return lv$weatherCooldown; }
    @Override public void livingvillages$setWeatherCooldown(int v) { lv$weatherCooldown = v; }
    @Override public int livingvillages$getCampfireCooldown() { return lv$campfireCooldown; }
    @Override public void livingvillages$setCampfireCooldown(int v) { lv$campfireCooldown = v; }
    @Override public int livingvillages$getAmbientCooldown() { return lv$ambientCooldown; }
    @Override public void livingvillages$setAmbientCooldown(int v) { lv$ambientCooldown = v; }
    @Override public boolean livingvillages$isSeekingShelter() { return lv$seekingShelter; }
    @Override public void livingvillages$setSeekingShelter(boolean v) { lv$seekingShelter = v; }
    @Override public boolean livingvillages$wasRaining() { return lv$wasRaining; }
    @Override public void livingvillages$setWasRaining(boolean v) { lv$wasRaining = v; }
    @Override public BlockPos livingvillages$getCampfireTarget() { return lv$campfireTarget; }
    @Override public void livingvillages$setCampfireTarget(BlockPos v) { lv$campfireTarget = v; }
    @Override public BlockPos livingvillages$getShelterTarget() { return lv$shelterTarget; }
    @Override public void livingvillages$setShelterTarget(BlockPos v) { lv$shelterTarget = v; }
    @Override public int livingvillages$getCelebrationTimer() { return lv$celebrationTimer; }
    @Override public void livingvillages$setCelebrationTimer(int v) { lv$celebrationTimer = v; }
    @Override public int livingvillages$getCampfireSeat() { return lv$campfireSeat; }
    @Override public void livingvillages$setCampfireSeat(int v) { lv$campfireSeat = v; }
    @Override public java.util.UUID livingvillages$getAngryTarget() { return lv$angryTarget; }
    @Override public void livingvillages$setAngryTarget(java.util.UUID v) { lv$angryTarget = v; }
    @Override public int livingvillages$getAngerCooldown() { return lv$angerCooldown; }
    @Override public void livingvillages$setAngerCooldown(int v) { lv$angerCooldown = v; }
    @Override public int livingvillages$getNapTimer() { return lv$napTimer; }
    @Override public void livingvillages$setNapTimer(int v) { lv$napTimer = v; }
}
