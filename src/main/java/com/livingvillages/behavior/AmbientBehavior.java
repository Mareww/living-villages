package com.livingvillages.behavior;

import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AmbientBehavior {

    private static final int COOLDOWN_MIN = 600;
    private static final int COOLDOWN_MAX = 1200;

    // Stare detection — villager notices a player looking at them for 2+ seconds
    // villager net ID → [player net ID, consecutive stare ticks, cooldown-until tick]
    private static final Map<Integer, long[]> STARE = new HashMap<>();

    private static final String[] STARE_LINES = {
        "What are you looking at?", "Can I help you?",
        "Something on my face?", "...Hello?",
        "I can see you staring.", "Is there something you need?",
        "Yes, I'm a villager.", "Are you lost?",
        "Awkward.", "Is this about the emeralds?",
        "You're staring.", "I'm trying to work here.",
        "Do I know you?", "Can I... help you?",
        "Eyes forward, please.", "Take a picture.",
        "I don't bite. Usually.",
    };

    // Post-work nap: 2–3 minutes (2400–3600 ticks), triggered once after work ends
    private static final long NAP_START   = 9200L;  // just after work ends
    private static final long NAP_END     = 12000L; // before evening
    private static final int  NAP_MIN     = 600;   // 30 real seconds
    private static final int  NAP_MAX     = 1200;  // 60 real seconds

    public static void tick(VillagerEntity villager, ServerWorld world, IVillagerBehaviorState state) {
        // ── Stare detection ───────────────────────────────────────────────────
        int vid = villager.getId();
        long now = world.getTime();
        long[] stare = STARE.get(vid);
        long cooldownUntil = stare != null ? stare[2] : 0L;

        // Prune stale STARE entries every ~5 minutes to prevent unbounded growth
        if (now % 6000 == vid % 6000) STARE.entrySet().removeIf(e -> {
            net.minecraft.entity.Entity ent = world.getEntityById((int) e.getKey().intValue());
            return ent == null || !ent.isAlive();
        });

        // Only run raycast-heavy stare check every 5 ticks to reduce server load
        if (com.livingvillages.LivingVillagesConfig.get().stareReaction
                && cooldownUntil <= now && !villager.isSleeping() && state.livingvillages$getNapTimer() == 0
                && now % 5 == vid % 5) {
            PlayerEntity looker = null;
            double best = Double.MAX_VALUE;
            for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class,
                    villager.getBoundingBox().expand(4.0), pl -> !pl.isSpectator())) {
                Vec3d eye = p.getEyePos();
                Vec3d toVillager = villager.getEyePos().subtract(eye);
                double dist = toVillager.length();
                if (dist < 0.5 || dist > 4.0) continue;
                // Check line of sight — don't react through walls
                if (!villager.canSee(p)) continue;
                double dot = p.getRotationVector().normalize().dotProduct(toVillager.normalize());
                if (dot > 0.97 && dist < best) { best = dist; looker = p; }
            }

            int staringPlayer = stare != null ? (int) stare[0] : -1;
            int stareTicks    = stare != null ? (int) stare[1] : 0;

            if (looker != null) {
                if (looker.getId() == staringPlayer) {
                    stareTicks++;
                } else {
                    stareTicks = 1;
                    staringPlayer = looker.getId();
                }
                STARE.put(vid, new long[]{ staringPlayer, stareTicks, cooldownUntil });

                int stareThreshold = Math.max(1, com.livingvillages.LivingVillagesConfig.get().stareDurationSeconds * 4);
                if (stareTicks >= stareThreshold) {
                    // Villager reacts and looks back at the player
                    villager.getLookControl().lookAt(looker, 30f, 30f);
                    CampfireConversation.spawnSpeech(villager,
                            STARE_LINES[world.random.nextInt(STARE_LINES.length)], world);
                    // Cooldown: won't react again for 30 seconds
                    STARE.put(vid, new long[]{ -1, 0, now + 600L });
                }
            } else {
                // Nobody looking — reset counter
                if (stare != null) STARE.put(vid, new long[]{ -1, 0, cooldownUntil });
            }
        }

        // ── Nap system ────────────────────────────────────────────────────────
        int napTimer = state.livingvillages$getNapTimer();
        long tod = world.getTimeOfDay() % 24000L;

        if (napTimer > 0) {
            // Post-work rest: aimless slow wandering, no bed involvement
            state.livingvillages$setNapTimer(napTimer - 1);
            villager.getBrain().forget(MemoryModuleType.INTERACTION_TARGET);
            // Every 5 seconds pick a new nearby destination at reduced speed
            if (napTimer % 100 == 0) {
                double rx = (world.random.nextDouble() - 0.5) * 10;
                double rz = (world.random.nextDouble() - 0.5) * 10;
                BlockPos dest = villager.getBlockPos().add((int) rx, 0, (int) rz);
                villager.getBrain().remember(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosLookTarget(dest), 0.3f, 2)); // 0.3 = slow drift
            }
            return;
        }

        // Chance to start a nap when entering post-work window (15% per check)
        if (tod >= NAP_START && tod < NAP_END
                && napTimer == 0
                && !villager.isBaby()
                && !villager.getBrain().hasActivity(Activity.PANIC)
                && !villager.getBrain().hasActivity(Activity.REST)
                && world.random.nextFloat() < 0.15f) {
            int duration = NAP_MIN + world.random.nextInt(NAP_MAX - NAP_MIN);
            state.livingvillages$setNapTimer(duration);
            return;
        }
        int cd = state.livingvillages$getAmbientCooldown();
        if (cd > 0) {
            state.livingvillages$setAmbientCooldown(cd - 1);
            return;
        }
        state.livingvillages$setAmbientCooldown(COOLDOWN_MIN + world.random.nextInt(COOLDOWN_MAX - COOLDOWN_MIN));

        // Campfire-sitters have their own animation system
        if (state.livingvillages$getCampfireTarget() != null) return;

        // Very rare: villager hums while walking (1 in 15 ambient triggers ≈ once per ~10 min)
        if (world.random.nextInt(15) == 0) {
            CampfireConversation.spawnHum(villager, world);
            return;
        }

        int action = world.random.nextInt(10);

        if (action < 4) {
            double dx = (world.random.nextDouble() - 0.5) * 12.0;
            double dz = (world.random.nextDouble() - 0.5) * 12.0;
            villager.getLookControl().lookAt(
                    villager.getX() + dx, villager.getY(), villager.getZ() + dz);
        } else if (action < 7) {
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    villager.getX(), villager.getY() + 1.8, villager.getZ(),
                    5, 0.3, 0.3, 0.3, 0.0);
            world.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                    SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL,
                    0.4f, 1.1f + world.random.nextFloat() * 0.2f);
        } else if (action < 9) {
            world.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                    SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL,
                    0.25f, 0.7f + world.random.nextFloat() * 0.3f);
        } else {
            List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class,
                    villager.getBoundingBox().expand(8.0),
                    e -> !(e instanceof VillagerEntity) && e != villager && e.isAlive()
                            && (!(e instanceof PlayerEntity) || villager.getCustomer() == e));
            if (!nearby.isEmpty()) {
                LivingEntity target = nearby.get(world.random.nextInt(nearby.size()));
                villager.getLookControl().lookAt(target, 30.0f, 30.0f);
            }
        }
    }
}
