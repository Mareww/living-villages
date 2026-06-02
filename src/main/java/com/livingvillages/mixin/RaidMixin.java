package com.livingvillages.mixin;

import com.livingvillages.behavior.CampfireConversation;
import com.livingvillages.behavior.ReputationSystem;
import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.village.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Raid.class)
public class RaidMixin {

    @Unique private boolean lv$rewardedVictory = false;
    @Unique private boolean lv$rewardedLoss    = false;
    @Unique private int lv$celebrationTicks    = 0; // counts down 200 ticks = 10 s

    @Inject(method = "tick", at = @At("TAIL"))
    private void lv$onTick(CallbackInfo ci) {
        Raid self = (Raid)(Object)this;
        if (!(self.getWorld() instanceof ServerWorld sw)) return;

        var center = self.getCenter();
        var villagers = sw.getEntitiesByClass(VillagerEntity.class,
                new Box(center).expand(128.0), v -> true);

        if (!lv$rewardedVictory && self.hasWon()) {
            lv$rewardedVictory = true;
            lv$celebrationTicks = 200; // 10 seconds of celebration
            sw.getPlayers().forEach(player -> {
                villagers.forEach(villager -> {
                    ReputationSystem.addReputation(villager, player, 10);
                    if (villager.squaredDistanceTo(player) < 4096.0) {
                        CampfireConversation.startPraise(villager, player, sw);
                    }
                });
            });
            CampfireConversation.spawnForGroup(villagers,
                    CampfireConversation.RAID_VICTORY_LINES, sw);
            // Launch initial fireworks burst
            lv$launchFireworks(sw, center.getX(), center.getY(), center.getZ(), 5);
        }

        // Celebration loop — jumping, singing, fireworks for 10 seconds
        if (lv$celebrationTicks > 0) {
            lv$celebrationTicks--;
            villagers.forEach(villager -> {
                // Jump every 15 ticks
                if (lv$celebrationTicks % 15 == 0) {
                    villager.setVelocity(villager.getVelocity().x, 0.42,
                            villager.getVelocity().z);
                    villager.velocityModified = true;
                    sw.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                            SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL,
                            0.6f, 1.4f + sw.random.nextFloat() * 0.3f);
                }
                // Note particles every 4 ticks
                if (lv$celebrationTicks % 4 == 0) {
                    sw.spawnParticles(ParticleTypes.NOTE,
                            villager.getX(), villager.getY() + 2.1, villager.getZ(),
                            1, 0.4, 0.1, 0.4, 0.0);
                    sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                            villager.getX(), villager.getY() + 1.6, villager.getZ(),
                            2, 0.3, 0.2, 0.3, 0.0);
                }
            });
            // Launch fireworks every 60 ticks (every 3 s) during celebration
            if (lv$celebrationTicks % 60 == 0 && lv$celebrationTicks > 0) {
                lv$launchFireworks(sw, center.getX(), center.getY(), center.getZ(), 3);
            }
        }

        if (!lv$rewardedLoss && self.hasLost()) {
            lv$rewardedLoss = true;
            // Mourning bubbles — no rep change on loss
            CampfireConversation.spawnForGroup(villagers,
                    CampfireConversation.RAID_LOSS_LINES, sw);
        }
    }

    @Unique
    private static void lv$launchFireworks(ServerWorld world, double x, double y, double z, int count) {
        for (int i = 0; i < count; i++) {
            double ox = (world.random.nextDouble() - 0.5) * 20;
            double oz = (world.random.nextDouble() - 0.5) * 20;

            // Build a random-color firework star
            NbtCompound star = new NbtCompound();
            star.putByte("Type", (byte) world.random.nextInt(4));
            star.putIntArray("Colors", new int[]{
                    0xFF0000 + world.random.nextInt(0xFFFFFF)
            });

            NbtList explosions = new NbtList();
            explosions.add(star);

            NbtCompound fw = new NbtCompound();
            fw.put("Explosions", explosions);
            fw.putByte("Flight", (byte) 1);

            NbtCompound tag = new NbtCompound();
            tag.put("Fireworks", fw);

            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.setNbt(tag);

            FireworkRocketEntity firework = new FireworkRocketEntity(
                    world, x + ox, y + 1, z + oz, rocket);
            world.spawnEntity(firework);
        }
    }
}
