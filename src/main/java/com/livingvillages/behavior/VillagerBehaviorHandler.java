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

        // Joiner approach — walk toward the A-B midpoint, wait, then trigger group thread
        long[] joinerData = CampfireConversation.getJoinerData(villager.getId());
        if (joinerData != null) {
            var entityA = world.getEntityById((int) joinerData[0]);
            var entityB = world.getEntityById((int) joinerData[1]);
            VillagerEntity speakerA = (entityA instanceof VillagerEntity) ? (VillagerEntity) entityA : null;
            VillagerEntity speakerB = (entityB instanceof VillagerEntity) ? (VillagerEntity) entityB : null;

            if (speakerA == null || !speakerA.isAlive() || speakerB == null || !speakerB.isAlive()
                    || villager.isSleeping()) {
                CampfireConversation.removeJoiner(villager.getId());
                // fall through to normal behavior below
            } else {
                double midX = (speakerA.getX() + speakerB.getX()) / 2.0;
                double midY =  speakerA.getY();
                double midZ = (speakerA.getZ() + speakerB.getZ()) / 2.0;

                // Stand 1.8 blocks perpendicular to A-B — triangle formation
                double abX = speakerB.getX() - speakerA.getX();
                double abZ = speakerB.getZ() - speakerA.getZ();
                double abLen = Math.sqrt(abX * abX + abZ * abZ);
                double targetX = midX, targetZ = midZ;
                if (abLen > 0.1) {
                    double perpX = -abZ / abLen;
                    double perpZ =  abX / abLen;
                    double dot = (villager.getX() - midX) * perpX + (villager.getZ() - midZ) * perpZ;
                    double side = dot >= 0 ? 1.8 : -1.8;
                    targetX = midX + perpX * side;
                    targetZ = midZ + perpZ * side;
                }

                double dx = villager.getX() - targetX;
                double dz = villager.getZ() - targetZ;
                if (dx * dx + dz * dz > 4.0) {
                    // Still walking — set nav target toward triangle slot
                    net.minecraft.util.math.BlockPos dest = new net.minecraft.util.math.BlockPos(
                            (int) targetX, (int) midY, (int) targetZ);
                    villager.getBrain().remember(
                            net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET,
                            new net.minecraft.entity.ai.brain.WalkTarget(
                                    new net.minecraft.entity.ai.brain.BlockPosLookTarget(dest), 0.5f, 1));
                    villager.getLookControl().lookAt(midX, midY + 1.6, midZ);
                } else {
                    // Arrived — hold position and watch the conversation
                    villager.getNavigation().stop();
                    villager.getBrain().forget(
                            net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
                    villager.getLookControl().lookAt(midX, midY + 1.6, midZ);

                    // Once A-B are done, step in and start the group thread
                    boolean convActive = CampfireConversation.getConversationPartner(
                            speakerA.getId(), world, tickNow) != null;
                    if (!convActive) {
                        CampfireConversation.removeJoiner(villager.getId());
                        CampfireConversation.triggerJoinerGroup(villager, speakerA, speakerB, world);
                    }
                }
                return;
            }
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

            // Kill horizontal velocity every tick — prevents being drifted by passing entities
            net.minecraft.util.math.Vec3d vel = villager.getVelocity();
            if (Math.abs(vel.x) > 0.005 || Math.abs(vel.z) > 0.005) {
                villager.setVelocity(0, vel.y, 0);
                villager.velocityModified = true;
            }

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
                // Good position — stop
                villager.getNavigation().stop();
                villager.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
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

        // Anti-bell-clustering: wipe MEETING_POINT during IDLE so the vanilla
        // VillagerWanderAroundTask has no bell to gravitate toward.
        if (villager.getBrain().hasActivity(net.minecraft.entity.ai.brain.Activity.IDLE)
                && state.livingvillages$getCampfireTarget() == null
                && !villager.isSleeping()) {
            villager.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.MEETING_POINT);
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
