package com.livingvillages.behavior;

import com.livingvillages.behavior.AngerBehavior;
import com.livingvillages.behavior.BabyBehavior;
import com.livingvillages.behavior.NightOwlTracker;
import com.livingvillages.behavior.GiftBehavior;
import com.livingvillages.behavior.ReputationSystem;
import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

public class VillagerBehaviorHandler {
    public static void tick(VillagerEntity villager, ServerWorld world) {
        IVillagerBehaviorState state = (IVillagerBehaviorState) villager;
        suppressCampfireInteraction(villager, state, world);
        CampfireConversation.tick(world); // deduplicated inside — safe to call per villager

        // Freeze villagers during active Q&A so they stay still and face each other
        long tickNow = world.getTime();

        // Freeze villagers participating in a group conversation thread
        if (CampfireConversation.isInGroupConversation(villager.getId(), tickNow)) {
            villager.getNavigation().stop();
            villager.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
            villager.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET);
            return;
        }

        VillagerEntity conversationPartner = CampfireConversation.getConversationPartner(
                villager.getId(), world, tickNow);
        if (conversationPartner != null) {
            // Clear any INTERACTION_TARGET set by vanilla Meet task or a third villager
            // — we only look at our conversation partner while mid-conversation
            var interactionMem = villager.getBrain()
                    .getOptionalRegisteredMemory(net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET);
            if (interactionMem.isPresent() && interactionMem.get() != conversationPartner)
                villager.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET);
            // Look at partner's eyes, not their feet
            villager.getLookControl().lookAt(
                    conversationPartner.getX(),
                    conversationPartner.getEyeY(),
                    conversationPartner.getZ());

            double dist = villager.distanceTo(conversationPartner);
            if (dist > 2.2) {
                // Pushed too far — walk back to face-to-face distance (triggers on even small pushes)
                villager.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,
                        new net.minecraft.entity.ai.brain.WalkTarget(
                                new net.minecraft.entity.ai.brain.EntityLookTarget(conversationPartner, true),
                                0.5f, 1));
            } else if (dist < 1.2) {
                // Too close — back up
                double dx = villager.getX() - conversationPartner.getX();
                double dz = villager.getZ() - conversationPartner.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 0) {
                    net.minecraft.util.math.BlockPos backPos = new net.minecraft.util.math.BlockPos(
                            (int)(villager.getX() + dx / len * 1.5),
                            (int) villager.getY(),
                            (int)(villager.getZ() + dz / len * 1.5));
                    villager.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,
                            new net.minecraft.entity.ai.brain.WalkTarget(
                                    new net.minecraft.entity.ai.brain.BlockPosLookTarget(backPos), 0.35f, 1));
                }
            } else {
                // Good position — stop and resist further pushes by zeroing horizontal velocity
                villager.getNavigation().stop();
                villager.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
                net.minecraft.util.math.Vec3d vel = villager.getVelocity();
                if (Math.abs(vel.x) > 0.01 || Math.abs(vel.z) > 0.01) {
                    villager.setVelocity(0, vel.y, 0); // kill horizontal drift from pushes
                    villager.velocityModified = true;
                }
            }
            return;
        }

        // Panic speech bubbles — funny exclamations while fleeing
        if (villager.getBrain().hasActivity(Activity.PANIC)) {
            CampfireConversation.tryPanic(villager, world);
        }

        // Greeting system — hello to passing villagers and players (once per day)
        CampfireConversation.tryGreet(villager, world);

        // Baby villager annoyance behavior
        BabyBehavior.tick(villager, world);

        // Night owl — stay awake, suppress bed navigation
        NightOwlTracker.tick(villager, world);

        // Reputation boss bar + gift giving
        ReputationSystem.tick(villager, world);
        GiftBehavior.tick(villager, world);

        // Enforce hover-only name tags — name tag mods force customNameVisible=true every tick.
        // We override it back to false so names only show when the player looks at the villager.
        // Exception: our speech bubbles (names starting with '"') should stay visible.
        {
            net.minecraft.text.Text vName = villager.getCustomName();
            if (vName != null && !vName.getString().startsWith("\"")
                    && villager.isCustomNameVisible()) {
                villager.setCustomNameVisible(false);
            }
        }

        // Anti-bell-clustering: nudge idle villagers away from the meeting point.
        // Runs AFTER conversation freeze (which returns early), so it never fires mid-conversation.
        // Every ~2 minutes per villager, with 50% chance = avg once per 4 minutes.
        if (villager.getBrain().hasActivity(net.minecraft.entity.ai.brain.Activity.IDLE)
                && state.livingvillages$getCampfireTarget() == null
                && !villager.isSleeping()
                && !CampfireConversation.isInGroupConversation(villager.getId(), tickNow)
                && (tickNow + villager.getId()) % 2400 == 0
                && world.random.nextFloat() < 0.5f
                && villager.getBrain().getOptionalRegisteredMemory(
                        net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET).isEmpty()) {
            double rx = (world.random.nextDouble() - 0.5) * 20;
            double rz = (world.random.nextDouble() - 0.5) * 20;
            net.minecraft.util.math.BlockPos wander = villager.getBlockPos().add((int) rx, 0, (int) rz);
            villager.getBrain().remember(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.entity.ai.brain.WalkTarget(
                            new net.minecraft.entity.ai.brain.BlockPosLookTarget(wander), 0.4f, 4));
        }

        // Group conversation — when 3+ villagers are nearby, occasionally start a thread
        if ((tickNow + villager.getId()) % 2400 == 0
                && world.random.nextFloat() < 0.35f
                && !villager.isBaby() && !villager.isSleeping()
                && state.livingvillages$getCampfireTarget() == null
                && conversationPartner == null
                && !CampfireConversation.isInGroupConversation(villager.getId(), tickNow)) {
            long nearby = world.getEntitiesByClass(net.minecraft.entity.passive.VillagerEntity.class,
                    villager.getBoundingBox().expand(6.0),
                    v -> v != villager && v.isAlive() && !v.isBaby()).stream().count();
            if (nearby >= 2) CampfireConversation.tryGroupConversation(villager, world);
        }

        // General chatter — every ~30 seconds say a solo or one-sided line
        if (com.livingvillages.LivingVillagesConfig.get().ambientChatter
                && (tickNow + villager.getId()) % 1200 == 0
                && !villager.isSleeping()
                && state.livingvillages$getCampfireTarget() == null // campfire has its own system
                && state.livingvillages$getNapTimer() == 0
                && !villager.getBrain().hasActivity(net.minecraft.entity.ai.brain.Activity.PANIC)
                // Skip if already mid-conversation with another villager
                && villager.getBrain().getOptionalRegisteredMemory(
                        net.minecraft.entity.ai.brain.MemoryModuleType.INTERACTION_TARGET).isEmpty()
                && world.random.nextFloat() < 0.5f) {
            CampfireConversation.tryChatter(villager, world);
        }

        // Work-context dialogue — uses its own cooldown so ambient/greeting never blocks it
        if (com.livingvillages.LivingVillagesConfig.get().workDialogue
                && villager.getBrain().hasActivity(net.minecraft.entity.ai.brain.Activity.WORK)) {
            long now = world.getTime();
            // Stagger per villager: fires roughly every 90s, offset by entity ID
            if ((now + villager.getId()) % 1800 == 0 && world.random.nextFloat() < 0.7f) {
                String line = CampfireConversation.getWorkLine(
                        villager.getVillagerData().getProfession(), world.random);
                if (line != null)
                    CampfireConversation.spawnWorkSpeech(villager, line, world);
            }
        }

        AngerBehavior.tick(villager, world, state);
        ConversationBehavior.tick(villager, world, state);
        WeatherBehavior.tick(villager, world, state);
        CampfireBehavior.tick(villager, world, state);
        AmbientBehavior.tick(villager, world, state);
    }

    private static void suppressCampfireInteraction(VillagerEntity villager,
                                                     IVillagerBehaviorState state,
                                                     ServerWorld world) {
        var brain = villager.getBrain();
        var myCampfire = state.livingvillages$getCampfireTarget();

        if (myCampfire != null) {
            // If something is attacking the villager, release them from the campfire
            // so vanilla PANIC can take over and they can actually flee.
            if (brain.hasActivity(Activity.PANIC)) {
                state.livingvillages$setCampfireTarget(null);
                state.livingvillages$setCampfireSeat(-1);
                // Don't block navigation — let PANIC flee freely
                return;
            }

            // Campfire attendee (walking or seated): block all Brain-driven interaction
            brain.forget(MemoryModuleType.WALK_TARGET);
            brain.forget(MemoryModuleType.INTERACTION_TARGET);
            return;
        }

        // ── Non-attendee ─────────────────────────────────────────────────────────

        // 1. Cancel if INTERACTION_TARGET is a campfire attendee
        brain.getOptionalRegisteredMemory(MemoryModuleType.INTERACTION_TARGET).ifPresent(target -> {
            if (target instanceof VillagerEntity tv
                    && ((IVillagerBehaviorState) tv).livingvillages$getCampfireTarget() != null) {
                brain.forget(MemoryModuleType.INTERACTION_TARGET);
                brain.forget(MemoryModuleType.WALK_TARGET);
                villager.getNavigation().stop();
            }
        });

        // 2. Cancel if the active navigation path ends near a campfire attendee.
        //    The vanilla Meet task calls startMovingTo() directly — so even after we
        //    clear INTERACTION_TARGET, a stale path can still carry the villager forward.
        var nav = villager.getNavigation();
        if (!nav.isIdle()) {
            var path = nav.getCurrentPath();
            if (path != null) {
                PathNode end = path.getEnd();
                if (end != null) {
                    // isEmpty() = no campfire sitters near the path end → safe to proceed
                    // !isEmpty() = there ARE sitters → cancel the path so we don't push them
                    boolean endsNearSitter = !world.getEntitiesByClass(VillagerEntity.class,
                            new Box(end.x - 2, end.y - 1, end.z - 2,
                                    end.x + 2, end.y + 1, end.z + 2),
                            v -> v != villager && v.isAlive()
                                    && ((IVillagerBehaviorState) v).livingvillages$getCampfireTarget() != null)
                            .isEmpty(); // true = sitters found
                    if (endsNearSitter) {
                        brain.forget(MemoryModuleType.WALK_TARGET);
                        nav.stop();
                    }
                }
            }
        }
    }
}
