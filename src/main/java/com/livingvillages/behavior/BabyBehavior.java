package com.livingvillages.behavior;

import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BabyBehavior {

    private static final String[] LINES = {
        "Play with me!", "Chase me!", "Tag! You're it!", "Look at me!",
        "Watch this!", "Hello! Hello! Hello!", "Come here!", "Wait! Wait!",
        "Can you jump?", "Be my friend?", "You're funny looking.",
        "My mum says don't talk to strangers.", "Hi! Hi! Hi!",
        "Look, I can run!", "Pick me up!", "Trade? No? Play!",
        "Ooh, a traveller!", "Are you lost?", "Again! Again!",
        "I can do a flip! ...Maybe.", "You smell like adventure.",
        "Will you be here tomorrow?", "I found a bug! Want to see?",
        "Race you to the well!", "What is that thing you're holding?",
    };

    private static final String[] THUNDER_LINES = {
        "Mama!", "It's so loud!", "I'm scared!", "Ahhhh!",
        "Make it stop!", "I don't like this...", "Help!",
        "Someone hold me!", "What IS that?!", "Too loud!!",
        "I want to go home.", "Please stop...",
    };

    // ── Tag game lines ────────────────────────────────────────────────────────
    private static final String[] TAG_IT_LINES = {
        "Tag! You're it!", "Come back!", "I'm gonna get you!",
        "You can't run forever!", "Gotcha!", "Ha! Tag!",
    };
    private static final String[] TAG_RUN_LINES = {
        "You'll never catch me!", "Nope!", "Bye!", "Too slow!",
        "Never!", "Not today!", "Wheee!",
    };

    private static final Map<Integer, Long>     COOLDOWN         = new HashMap<>();
    private static final Map<Integer, Long>     THUNDER_COOLDOWN = new HashMap<>();
    private static final Map<Integer, BlockPos> SHELTER          = new HashMap<>();
    // "babyUUID|playerUUID" → Minecraft day when player dismissed the baby.
    // UUID keys + day numbers survive server restarts.
    private static final Map<String, Long>      DISMISSED        = new HashMap<>();

    private static final String[] DISMISS_LINES = {
        "Fine...", "Okay, okay.", "I'll leave you alone.",
        "You're no fun.", "Maybe later then.", "Hmph.",
        "I was just trying to be friendly.", "...Bye.",
    };

    /** Called when a player right-clicks a baby. Dismisses if following OR approaching. */
    public static boolean tryDismiss(VillagerEntity baby, PlayerEntity player,
                                      ServerWorld world) {
        if (!baby.isBaby()) return false;
        int bid = baby.getId();

        // Only dismiss if this baby is actively following this specific player
        long[] follow = FOLLOW_STATE.get(bid);
        if (follow == null || (int) follow[0] != player.getId()) return false;

        FOLLOW_STATE.remove(bid);
        String dismissKey = baby.getUuid() + "|" + player.getUuid();
        DISMISSED.put(dismissKey, world.getTime() / 24000L);
        COOLDOWN.put(bid, world.getTime() + 1200L);
        // Force-clear any active bubble so the dismiss line always shows
        CampfireConversation.forceClearBubble(baby);
        CampfireConversation.spawnSpeech(baby,
                DISMISS_LINES[baby.getRandom().nextInt(DISMISS_LINES.length)], world);
        return true;
    }
    // baby net ID → [player net ID, expiry tick, next bubble tick]
    private static final Map<Integer, long[]>   FOLLOW_STATE     = new HashMap<>();

    private static final String[] FOLLOW_LINES = {
        "Play with me!", "Hey! Hey! Hey!", "Wait up!", "Look at me!",
        "Are you ignoring me?", "Hello?", "Over here!", "I'm right here!",
        "You can't escape!", "Come ON.", "Please?", "Pick me up!",
        "I'm not going away.", "Notice me!", "Hi! Hi! Hi!",
    };

    // Tag game: "it" baby ID → target baby ID
    private static final Map<Integer, Integer>  TAG_IT           = new HashMap<>();
    // Immunity: prevents instant re-tagging after being caught
    private static final Map<Integer, Long>     TAG_COOLDOWN     = new HashMap<>();
    // Game expiry: "it" baby ID → tick when the game ends
    private static final Map<Integer, Long>     TAG_EXPIRY       = new HashMap<>();

    public static void tick(VillagerEntity villager, ServerWorld world) {
        if (!com.livingvillages.LivingVillagesConfig.get().babyAnnoyance
                && !com.livingvillages.LivingVillagesConfig.get().babyTagGame) return;
        if (!villager.isBaby()) return;

        long now          = world.getTime();
        long staggered    = now + villager.getId();

        // ── Thunderstorm behaviour ────────────────────────────────────────────
        if (world.isThundering()) {
            villager.hurtTime = 5;

            BlockPos shelter = SHELTER.get(villager.getId());
            if (shelter == null) {
                shelter = findHouseShelter(villager, world);
                if (shelter != null) SHELTER.put(villager.getId(), shelter);
            }

            boolean inside = shelter != null
                    && villager.squaredDistanceTo(
                            shelter.getX() + 0.5, villager.getY(),
                            shelter.getZ() + 0.5) < 3.0;

            if (inside) {
                villager.getNavigation().stop();
                villager.setVelocity(0, villager.getVelocity().y, 0);
                villager.velocityModified = true;
                closeNearbyDoors(villager, world);
            } else if (shelter != null) {
                villager.getBrain().remember(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new net.minecraft.entity.ai.brain.BlockPosLookTarget(shelter), 0.9f, 0));
            } else {
                if (staggered % 4 == 0) {
                    double j = 0.045;
                    villager.setVelocity(
                            (world.random.nextDouble() - 0.5) * j,
                            villager.getVelocity().y,
                            (world.random.nextDouble() - 0.5) * j);
                    villager.velocityModified = true;
                }
            }

            if (staggered % 8 == 0)
                world.spawnParticles(ParticleTypes.SPLASH,
                        villager.getX(), villager.getY() + 0.9, villager.getZ(),
                        2, 0.15, 0.15, 0.15, 0.02);
            if (staggered % 70 == 0 && world.random.nextBoolean())
                world.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                        SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.NEUTRAL,
                        0.35f, 1.6f + world.random.nextFloat() * 0.3f);
            if (THUNDER_COOLDOWN.getOrDefault(villager.getId(), 0L) <= now
                    && world.random.nextInt(300) == 0) {
                THUNDER_COOLDOWN.put(villager.getId(), now + 400L);
                CampfireConversation.spawnSpeech(villager,
                        THUNDER_LINES[world.random.nextInt(THUNDER_LINES.length)], world);
            }
            return;
        }

        if (SHELTER.containsKey(villager.getId())) {
            SHELTER.remove(villager.getId());
        }

        // ── Tag game with nearby babies (every 5 ticks) ──────────────────────
        if (staggered % 5 == 0) {
            List<VillagerEntity> nearbyBabies = world.getEntitiesByClass(VillagerEntity.class,
                    villager.getBoundingBox().expand(12.0),
                    v -> v != villager && v.isAlive() && v.isBaby());
            if (!nearbyBabies.isEmpty()) {
                tickTag(villager, nearbyBabies, world, now);
            }
        }

        // ── Player-annoy behaviour ────────────────────────────────────────────
        // Skip if already playing tag
        if (TAG_IT.containsKey(villager.getId())) return;

        int vid = villager.getId();

        // ── Active follow state ───────────────────────────────────────────────
        long[] follow = FOLLOW_STATE.get(vid);
        if (follow != null) {
            if (now > follow[1]) {
                FOLLOW_STATE.remove(vid); // timed out
            } else {
                var followPlayer = world.getEntityById((int) follow[0]);
                if (!(followPlayer instanceof PlayerEntity fp)
                        || villager.squaredDistanceTo(fp) > 225.0) { // > 15 blocks → give up
                    FOLLOW_STATE.remove(vid);
                    COOLDOWN.put(vid, now + 600L);
                } else {
                    // Target 2 blocks ahead of where the player is facing so the
                    // baby keeps cutting in front
                    var look = fp.getRotationVector().normalize();
                    net.minecraft.util.math.BlockPos aheadPos = BlockPos.ofFloored(
                            fp.getX() + look.x * 2,
                            fp.getY(),
                            fp.getZ() + look.z * 2);
                    villager.getBrain().remember(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(new net.minecraft.entity.ai.brain.BlockPosLookTarget(aheadPos), 1.1f, 0));
                    villager.getLookControl().lookAt(fp, 30f, 30f);

                    // Occasional jump to be extra annoying
                    if (now % 40 == (vid % 40) && villager.isOnGround()
                            && villager.getRandom().nextFloat() < 0.3f) {
                        villager.setVelocity(villager.getVelocity().x, 0.38, villager.getVelocity().z);
                        villager.velocityModified = true;
                    }

                    // Bubble every 5 seconds
                    if (now >= follow[2] && CampfireConversation.canSpeak(vid, now)) {
                        follow[2] = now + 100L;
                        CampfireConversation.spawnSpeech(villager,
                                FOLLOW_LINES[villager.getRandom().nextInt(FOLLOW_LINES.length)], world);
                    }
                }
            }
            return;
        }

        // ── Start annoying (trigger) ──────────────────────────────────────────
        if (COOLDOWN.getOrDefault(vid, 0L) > now) return;
        if (villager.getRandom().nextInt(100) != 0) return;

        List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class,
                villager.getBoundingBox().expand(12.0), p -> true);
        if (players.isEmpty()) return;

        // Pick a player that hasn't dismissed this baby today
        PlayerEntity player = null;
        long today = world.getTime() / 24000L;
        for (PlayerEntity p : players) {
            String dk = villager.getUuid() + "|" + p.getUuid();
            if (DISMISSED.getOrDefault(dk, -1L) < today) { player = p; break; }
        }
        if (player == null) return; // all nearby players dismissed this baby today

        // Enter follow state: [player net ID, expiry tick, next bubble tick]
        FOLLOW_STATE.put(vid, new long[]{player.getId(), now + 600L, now});
        CampfireConversation.spawnSpeech(villager,
                LINES[villager.getRandom().nextInt(LINES.length)], world);
    }

    // ── Tag game logic ────────────────────────────────────────────────────────

    private static void tickTag(VillagerEntity villager, List<VillagerEntity> babies,
                                  ServerWorld world, long now) {
        int vid = villager.getId();

        // ── Check end conditions ──────────────────────────────────────────────
        long timeOfDay = world.getTimeOfDay() % 24000L;
        boolean isNight = timeOfDay >= 13000L;

        if (TAG_IT.containsKey(vid)) {
            long expiry = TAG_EXPIRY.getOrDefault(vid, Long.MAX_VALUE);
            boolean timedOut   = now >= expiry;
            boolean tooFar     = babies.stream().allMatch(b ->
                    b.squaredDistanceTo(villager) > 144.0); // > 12 blocks
            if (timedOut || tooFar || isNight) {
                TAG_IT.remove(vid);
                TAG_EXPIRY.remove(vid);
                return;
            }
        }

        // If this baby is "it", chase the target
        Integer targetId = TAG_IT.get(vid);
        if (targetId != null) {
            VillagerEntity target = babies.stream()
                    .filter(b -> b.getId() == targetId).findFirst().orElse(null);
            if (target == null) {
                TAG_IT.remove(vid);
                TAG_EXPIRY.remove(vid);
                return;
            }

            // Chase at speed
            villager.getBrain().remember(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityLookTarget(target, true), 1.1f, 0));
            villager.getLookControl().lookAt(target, 30f, 30f);

            // Occasional taunt bubble
            if (now % 80 == (vid % 80) && CampfireConversation.canSpeak(vid, now)) {
                CampfireConversation.spawnSpeech(villager,
                        TAG_IT_LINES[villager.getRandom().nextInt(TAG_IT_LINES.length)], world);
            }

            // Tagged! Transfer "it"
            if (villager.squaredDistanceTo(target) < 2.25
                    && TAG_COOLDOWN.getOrDefault(vid, 0L) <= now) {
                TAG_COOLDOWN.put(vid, now + 60L);
                TAG_IT.remove(vid);
                TAG_EXPIRY.remove(vid);
                // Carry the remaining game time over to the new "it"
                VillagerEntity newTarget = babies.stream()
                        .filter(b -> b.getId() != targetId)
                        .findFirst().orElse(villager);
                TAG_IT.put(targetId, newTarget.getId());
                TAG_EXPIRY.put(targetId, now + 2400L); // reset 2-min window on each tag
                CampfireConversation.spawnSpeech(target,
                        TAG_IT_LINES[world.random.nextInt(TAG_IT_LINES.length)], world);
            }
            return;
        }

        // Check if this baby is being chased — run away!
        boolean beingChased = TAG_IT.values().stream().anyMatch(id -> id == vid);
        if (beingChased) {
            // Run away from the chaser
            TAG_IT.entrySet().stream()
                    .filter(e -> e.getValue() == vid)
                    .findFirst().ifPresent(e -> {
                VillagerEntity chaser = babies.stream()
                        .filter(b -> b.getId() == e.getKey()).findFirst().orElse(null);
                if (chaser != null) {
                    double distSqToChaser = villager.squaredDistanceTo(chaser);
                    // Only flee if chaser is within 8 blocks — once far enough, stop.
                    // This keeps the game within the village; babies can't run forever.
                    if (distSqToChaser < 64.0) {
                        double dx = villager.getX() - chaser.getX();
                        double dz = villager.getZ() - chaser.getZ();
                        double len = Math.sqrt(dx * dx + dz * dz);
                        if (len > 0.01) {
                            BlockPos flee = BlockPos.ofFloored(
                                    villager.getX() + dx / len * 4,
                                    villager.getY(),
                                    villager.getZ() + dz / len * 4);
                            villager.getBrain().remember(MemoryModuleType.WALK_TARGET,
                                    new WalkTarget(new net.minecraft.entity.ai.brain.BlockPosLookTarget(flee), 1.1f, 0));
                        }
                    } else {
                        // Far enough — stop and let the chaser close the gap
                        villager.getNavigation().stop();
                    }
                    if (now % 100 == (vid % 100) && CampfireConversation.canSpeak(vid, now)) {
                        CampfireConversation.spawnSpeech(villager,
                                TAG_RUN_LINES[villager.getRandom().nextInt(TAG_RUN_LINES.length)], world);
                    }
                }
            });
            return;
        }

        // No one is "it" near this baby — maybe start a game (rare, only daytime)
        if (TAG_IT.isEmpty() && !isNight && villager.getRandom().nextInt(600) == 0) {
            VillagerEntity target = babies.get(world.random.nextInt(babies.size()));
            TAG_IT.put(vid, target.getId());
            TAG_EXPIRY.put(vid, now + 2400L); // 2-minute game
            CampfireConversation.spawnSpeech(villager, "Tag! You're it!", world);
        }
    }

    private static void closeNearbyDoors(VillagerEntity villager, ServerWorld world) {
        BlockPos center = villager.getBlockPos();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = center.add(dx, dy, dz);
            var bs = world.getBlockState(pos);
            if (!(bs.getBlock() instanceof DoorBlock)) continue;
            if (bs.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) continue;
            if (!bs.get(Properties.OPEN)) continue;
            world.setBlockState(pos, bs.with(Properties.OPEN, false), 10);
            var ubs = world.getBlockState(pos.up());
            if (ubs.contains(Properties.OPEN)) world.setBlockState(pos.up(), ubs.with(Properties.OPEN, false), 10);
            world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1f, 1f);
        }
    }

    private static BlockPos findHouseShelter(VillagerEntity villager, ServerWorld world) {
        BlockPos origin = villager.getBlockPos();
        for (int r = 1; r <= 24; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos doorPos = origin.add(dx, dy, dz);
                    var doorState = world.getBlockState(doorPos);
                    if (!(doorState.getBlock() instanceof DoorBlock)) continue;
                    if (doorState.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) continue;
                    Direction facing = doorState.get(Properties.HORIZONTAL_FACING);
                    for (Direction inward : new Direction[]{facing, facing.getOpposite()}) {
                        if (world.isSkyVisible(doorPos.offset(inward, 1))) continue;
                        if (villager.getNavigation().findPathTo(doorPos.offset(inward.getOpposite(), 1), 1) == null) continue;
                        BlockPos best = null;
                        for (int depth = 2; depth <= 6; depth++) {
                            BlockPos c = doorPos.offset(inward, depth);
                            if (world.getBlockState(c).isAir() && world.getBlockState(c.up()).isAir()
                                    && !world.isSkyVisible(c)) best = c;
                            else break;
                        }
                        if (best != null) return best;
                    }
                }
            }
        }
        return null;
    }
}

