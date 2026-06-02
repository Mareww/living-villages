package com.livingvillages.behavior;

import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks villagers who chose to stay awake at night instead of sleeping.
 * Prevents their Brain from navigating to bed while letting normal
 * socialising (wander, meet, conversation) run freely.
 */
public class NightOwlTracker {

    private static final long NIGHT_END = 23000L;

    // villager net ID → tick when night owl state expires (next dawn)
    private static final Map<Integer, Long> OWLS = new HashMap<>();

    private static final String[] NIGHT_WANDER_LINES = {
        "Couldn't sleep.", "The night is nice.", "Just needed some air.",
        "Quiet out here.", "I like it at night.", "Can't sleep anyway.",
        "The stars are out.", "Everyone else is asleep.",
        "Just me and the golem tonight.", "Night walks are underrated.",
        "I had a thought. Still thinking it.",
    };

    public static void register(int villagerNetId, ServerWorld world) {
        long timeOfDay = world.getTimeOfDay() % 24000L;
        // Expires at the next dawn (23000 ticks into current day)
        long currentDay = world.getTimeOfDay() / 24000L;
        long expiry = (currentDay + 1) * 24000L - (24000L - NIGHT_END);
        if (timeOfDay < NIGHT_END) expiry = currentDay * 24000L + NIGHT_END;
        OWLS.put(villagerNetId, expiry);
    }

    public static boolean isNightOwl(int villagerNetId) {
        return OWLS.containsKey(villagerNetId);
    }

    /** Called every tick from VillagerBehaviorHandler for night owl villagers. */
    public static void tick(VillagerEntity villager, ServerWorld world) {
        int vid = villager.getId();
        Long expiry = OWLS.get(vid);
        if (expiry == null) return;

        long absTime = world.getTimeOfDay();
        if (absTime >= expiry) {
            OWLS.remove(vid); // dawn — go to sleep now
            return;
        }

        // Prevent REST activity from navigating the villager to bed.
        // We clear the Brain's WALK_TARGET only when REST is trying to use it
        // for bed-navigation — MEET/IDLE socialising is still free to set it.
        var brain = villager.getBrain();
        if (brain.hasActivity(net.minecraft.entity.ai.brain.Activity.REST)) {
            brain.forget(MemoryModuleType.WALK_TARGET);
        }

        // Occasional wandering night monologue
        long now = world.getTime();
        if (now % 400 == (vid % 400) && villager.getRandom().nextInt(3) == 0) {
            CampfireConversation.spawnSpeech(villager,
                    NIGHT_WANDER_LINES[villager.getRandom().nextInt(NIGHT_WANDER_LINES.length)],
                    world);
        }
    }
}
