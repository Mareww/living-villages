package com.livingvillages.behavior;

import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class ConversationBehavior {

    // Cooldown prevents spamming during a long meeting — allows one exchange per meeting session.
    private static final int COOLDOWN_MIN = 80;
    private static final int COOLDOWN_MAX = 180;

    // Join lines are now in DialogueConfig — fetched via CampfireConversation.cfgJoin()

    // Adult speaking to a baby
    private static final String[][] ADULT_TO_BABY = {
        {"Stay close, little one.", "Okay!"},
        {"Have you eaten?", "I ate five carrots!"},
        {"Don't wander too far.", "But I want to explore!"},
        {"Be careful out there.", "I'm always careful!"},
        {"How was your day?", "I found a bug!"},
        {"You're growing so fast.", "I know! I'm almost this tall!"},
        {"Did you do your chores?", "...mostly."},
        {"Stay away from the well.", "How do you know about that?"},
    };

    // Baby speaking to an adult
    private static final String[][] BABY_TO_ADULT = {
        {"Can we play?", "Maybe later. I have trades."},
        {"Tell me a story!", "Once there was a creeper..."},
        {"Are you the strongest here?", "...No. That's the golem."},
        {"What do you do all day?", "Trade. Worry. Trade again."},
        {"I want to be a librarian!", "You can't even read yet."},
        {"Will there be another raid?", "Let's hope not."},
    };

    // Baby speaking to another baby
    private static final String[][] BABY_TO_BABY = {
        {"Race you to the well!", "You always win though."},
        {"Wanna trade sticks?", "I have sixteen sticks!"},
        {"I saw a witch yesterday.", "Was she scary? / Very."},
        {"Tag! You're it!", "That's not fair I wasn't ready!"},
        {"My dad has a cool hat.", "My mum has a better one."},
        {"Do you think monsters are real?", "I've seen three today."},
        {"Let's dig a hole!", "We're not allowed to."},
    };

    public static void tick(VillagerEntity villager, ServerWorld world, IVillagerBehaviorState state) {
        int cd = state.livingvillages$getConversationCooldown();
        if (cd > 0) {
            state.livingvillages$setConversationCooldown(cd - 1);
            return;
        }

        // Sleeping villagers neither speak nor are spoken to
        if (villager.isSleeping()) return;

        var brain = villager.getBrain();
        if (brain.hasActivity(Activity.WORK) || brain.hasActivity(Activity.REST)
                || brain.hasActivity(Activity.PANIC)) return;

        // Campfire and fishing villagers handle their own interactions
        BlockPos myCampfire = state.livingvillages$getCampfireTarget();
        if (myCampfire != null) return;

        // Only fire when the villager is in an active vanilla meeting (INTERACTION_TARGET set).
        // This is exactly the "face to face" state — no meeting, no bubble.
        var interactionMemory = brain.getOptionalRegisteredMemory(MemoryModuleType.INTERACTION_TARGET);
        if (interactionMemory.isEmpty()) return;
        if (!(interactionMemory.get() instanceof VillagerEntity target)) return;
        if (!target.isAlive()) return;
        if (target.isSleeping()) return;
        if (((IVillagerBehaviorState) target).livingvillages$getCampfireTarget() != null) return;
        // Don't interrupt — if target is already in a conversation with someone else, skip
        if (CampfireConversation.getConversationPartner(target.getId(), world, world.getTime()) != null) return;
        // Also skip if the speaker themselves are already conversing with someone else
        if (CampfireConversation.getConversationPartner(villager.getId(), world, world.getTime()) != null) return;
        // Don't start a conversation if they're too far apart — bubbles would look disconnected
        if (villager.squaredDistanceTo(target) > 16.0) return; // > 4 blocks
        // Don't talk to the same villager again too soon
        if (CampfireConversation.talkedRecently(villager.getId(), target.getId(), world.getTime())) return;

        // If either villager is still walking, stop them and wait a moment before speaking.
        // This ensures they're face-to-face and stationary before the bubble appears.
        boolean villagerMoving = !villager.getNavigation().isIdle();
        boolean targetMoving   = !target.getNavigation().isIdle();
        if (villagerMoving || targetMoving) {
            villager.getNavigation().stop();
            target.getNavigation().stop();
            villager.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
            target.getBrain().forget(net.minecraft.entity.ai.brain.MemoryModuleType.WALK_TARGET);
            villager.getLookControl().lookAt(target.getX(), target.getEyeY(), target.getZ());
            target.getLookControl().lookAt(villager.getX(), villager.getEyeY(), villager.getZ());
            // Short settling delay — come back in ~0.5 s when they've turned to face each other
            state.livingvillages$setConversationCooldown(10);
            return;
        }

        state.livingvillages$setConversationCooldown(
                COOLDOWN_MIN + world.random.nextInt(COOLDOWN_MAX - COOLDOWN_MIN));

        // Already stopped — face each other and speak
        villager.getLookControl().lookAt(target.getX(), target.getEyeY(), target.getZ());
        target.getLookControl().lookAt(villager.getX(), villager.getEyeY(), villager.getZ());

        world.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL,
                0.5f, 0.9f + world.random.nextFloat() * 0.2f);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                villager.getX(), villager.getY() + 2.0, villager.getZ(),
                3, 0.2, 0.1, 0.2, 0.0);

        // Use age-specific dialogue if either party is a baby
        boolean speakerBaby = villager.isBaby();
        boolean targetBaby  = target.isBaby();
        if (speakerBaby && targetBaby) {
            CampfireConversation.spawnSpecialBubbles(villager, target,
                    BABY_TO_BABY, world);
        } else if (!speakerBaby && targetBaby) {
            CampfireConversation.spawnSpecialBubbles(villager, target,
                    ADULT_TO_BABY, world);
        } else if (speakerBaby && !targetBaby) {
            CampfireConversation.spawnSpecialBubbles(villager, target,
                    BABY_TO_ADULT, world);
        } else {
            CampfireConversation.spawnRoamBubbles(villager, target, world);
        }

        // Chance a nearby bystander notices and walks over to form a trio
        CampfireConversation.tryScheduleJoiner(villager, target, world);
    }
}
