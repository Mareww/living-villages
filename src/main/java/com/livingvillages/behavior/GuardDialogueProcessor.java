package com.livingvillages.behavior;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds patrol/combat/greeting speech bubbles to Guard Villagers entities.
 * Uses a world-tick event instead of a mixin to avoid intermediary mapping issues.
 */
public class GuardDialogueProcessor {

    private static final Map<Integer, Integer> DIALOGUE_COOLDOWN = new HashMap<>();
    private static final Map<Integer, Integer> GREET_COOLDOWN    = new HashMap<>();

    private static final String[] PATROL = {
        "On guard.", "All clear.", "Stay vigilant.", "Nothing suspicious.",
        "Keep moving, citizen.", "Eyes open.", "Quiet tonight.",
        "Another day, another watch.", "I have seen worse nights.",
        "The village is safe. For now.", "I never sleep. Ever.",
    };

    private static final String[] COMBAT = {
        "Halt!", "For the village!", "Surrender!", "Stop right there!",
        "You will regret this!", "Stand down!", "I am warning you!",
        "Not on my watch!", "Back away!", "HALT!",
        "You picked the wrong village.",
    };

    private static final String[] GREET = {
        "Good day, citizen.", "Keep out of trouble.", "Move along.",
        "I am watching.", "Evening.", "Welcome to the village.",
        "All quiet here.", "Carry on.", "State your business.",
    };

    public static void register() {
        if (!FabricLoader.getInstance().isModLoaded("guardvillagers")) return;

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            // Only run every 20 ticks (once per second) to avoid per-tick overhead.
            if (world.getTime() % 20 != 0) return;

            // Search only within 48 blocks of each online player — not the whole world.
            world.getPlayers().forEach(player -> {
                world.getEntitiesByClass(PathAwareEntity.class,
                        player.getBoundingBox().expand(48.0), e -> {
                            Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
                            return id != null && "guardvillagers".equals(id.getNamespace());
                        }).forEach(guard -> tickGuard(guard, world));
            });
        });
    }

    private static void tickGuard(PathAwareEntity guard, ServerWorld world) {
        if (!guard.isAlive()) return;

        int vid = guard.getId();
        long now = world.getTime();

        // Decrement cooldowns
        DIALOGUE_COOLDOWN.merge(vid, -1, Integer::sum);
        GREET_COOLDOWN.merge(vid, -1, Integer::sum);

        int dialogueCd = DIALOGUE_COOLDOWN.getOrDefault(vid, 0);
        int greetCd    = GREET_COOLDOWN.getOrDefault(vid, 0);

        // Combat shouts — every ~8 seconds while fighting
        // (cooldowns decrement once/sec since tickGuard runs every 20 ticks)
        LivingEntity target = guard.getTarget();
        if (target != null && dialogueCd <= 0
                && CampfireConversation.canSpeak(vid, now)) {
            DIALOGUE_COOLDOWN.put(vid, 8);
            CampfireConversation.spawnSpeechEntity(guard,
                    COMBAT[guard.getRandom().nextInt(COMBAT.length)], world);
        }

        // Patrol dialogue — ~1 in 90 seconds while idle = avg every 90 seconds
        if (target == null && dialogueCd <= 0
                && guard.getRandom().nextInt(90) == 0
                && CampfireConversation.canSpeak(vid, now)) {
            DIALOGUE_COOLDOWN.put(vid, 60);
            CampfireConversation.spawnSpeechEntity(guard,
                    PATROL[guard.getRandom().nextInt(PATROL.length)], world);
        }

        // Player greetings — once per 10 minutes (600 seconds)
        if (greetCd <= 0) {
            List<PlayerEntity> nearby = world.getEntitiesByClass(PlayerEntity.class,
                    guard.getBoundingBox().expand(4.0), p -> true);
            if (!nearby.isEmpty() && CampfireConversation.canSpeak(vid, now)) {
                GREET_COOLDOWN.put(vid, 600);
                guard.getLookControl().lookAt(nearby.get(0), 30f, 30f);
                CampfireConversation.spawnSpeechEntity(guard,
                        GREET[guard.getRandom().nextInt(GREET.length)], world);
            }
        }
    }
}
