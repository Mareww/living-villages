package com.livingvillages.behavior;

import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class AngerBehavior {

    private static final String[] PAIN_LINES = {
        "Ow!", "Hey!", "That hurt!", "Watch it!", "Rude!",
        "Excuse me?!", "Not okay!", "Ouch!", "What was that for?!",
        "I didn't deserve that.", "Seriously?!", "OW.",
        "That was uncalled for.", "I will remember this.",
    };

    private static final String[] FLEE_LINES = {
        "Someone help!", "GOLEM! GOLEM!", "Guards!",
        "Help me!", "Stop! Someone stop them!",
        "Get away from me!", "Back off!",
        "GOLEM, WHERE ARE YOU?!", "Somebody do something!",
        "They're attacking me!", "Call the golem!",
    };

    /** Called when a player hits the villager. */
    public static void onHit(VillagerEntity villager, PlayerEntity player,
                              ServerWorld world, IVillagerBehaviorState state) {
        if (!com.livingvillages.LivingVillagesConfig.get().angerSystem) return;
        CampfireConversation.spawnSpeech(villager,
                PAIN_LINES[world.random.nextInt(PAIN_LINES.length)], world);

        state.livingvillages$setAngryTarget(player.getUuid());
        state.livingvillages$setAngerCooldown(400);

        // Alert the protector and run toward it
        runToProtector(villager, player, world);

        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                villager.getX(), villager.getY() + 2.0, villager.getZ(),
                3, 0.3, 0.2, 0.3, 0.0);
    }

    /** Called every tick from VillagerBehaviorHandler. */
    public static void tick(VillagerEntity villager, ServerWorld world, IVillagerBehaviorState state) {
        int cd = state.livingvillages$getAngerCooldown();
        if (cd <= 0) return;

        state.livingvillages$setAngerCooldown(cd - 1);

        java.util.UUID targetUUID = state.livingvillages$getAngryTarget();
        if (targetUUID == null) return;

        PlayerEntity target = world.getPlayerByUuid(targetUUID);
        if (target == null || !target.isAlive()) {
            state.livingvillages$setAngryTarget(null);
            state.livingvillages$setAngerCooldown(0);
            return;
        }

        // Periodically re-run toward protector or flee if no protector
        if (cd % 40 == 0) {
            CampfireConversation.spawnSpeech(villager,
                    FLEE_LINES[world.random.nextInt(FLEE_LINES.length)], world);
            runToProtector(villager, target, world);
        }

        villager.getLookControl().lookAt(target, 30f, 30f);

        if (villager.squaredDistanceTo(target) > 196.0) {
            state.livingvillages$setAngryTarget(null);
            state.livingvillages$setAngerCooldown(0);
        }
    }

    /**
     * Alerts the nearest golem/guard and makes the villager RUN TOWARD them.
     * If none found, villager flees away from the player instead.
     */
    public static void runToProtector(VillagerEntity villager, PlayerEntity player, ServerWorld world) {
        Box range = new Box(villager.getBlockPos()).expand(32.0);

        // Find nearest golem
        net.minecraft.entity.Entity protector = world.getEntitiesByClass(
                IronGolemEntity.class, range, g -> true)
                .stream()
                .min(java.util.Comparator.comparingDouble(g -> g.squaredDistanceTo(villager)))
                .map(g -> (net.minecraft.entity.Entity) g)
                .orElse(null);

        // If no golem, try a guard
        if (protector == null && net.fabricmc.loader.api.FabricLoader.getInstance()
                .isModLoaded("guardvillagers")) {
            protector = world.getEntitiesByClass(
                    net.minecraft.entity.mob.PathAwareEntity.class, range, guard -> {
                        net.minecraft.util.Identifier id =
                                net.minecraft.registry.Registries.ENTITY_TYPE.getId(guard.getType());
                        return id != null && "guardvillagers".equals(id.getNamespace());
                    }).stream()
                    .min(java.util.Comparator.comparingDouble(g -> g.squaredDistanceTo(villager)))
                    .map(g -> (net.minecraft.entity.Entity) g)
                    .orElse(null);
        }

        if (protector != null) {
            // Alert the protector
            if (protector instanceof net.minecraft.entity.mob.MobEntity)
                ((net.minecraft.entity.mob.MobEntity) protector).setTarget(player);
            // Run TOWARD the protector
            villager.getBrain().remember(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new net.minecraft.entity.ai.brain.EntityLookTarget(protector, true),
                            0.85f, 2));
            world.playSound(null, protector.getX(), protector.getY(), protector.getZ(),
                    SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.NEUTRAL, 0.5f, 1.2f);
        } else {
            // No protector nearby — flee directly away from the attacker
            double dx = villager.getX() - player.getX();
            double dz = villager.getZ() - player.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                BlockPos fleePos = new BlockPos(
                        (int)(villager.getX() + dx / len * 10),
                        (int) villager.getY(),
                        (int)(villager.getZ() + dz / len * 10));
                villager.getBrain().remember(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new BlockPosLookTarget(fleePos), 0.8f, 1));
            }
        }
    }

    /** Finds the nearest iron golem OR guard and sets the player as its target. */
    private static void callGolem(VillagerEntity villager, PlayerEntity player, ServerWorld world) {
        Box range = new Box(villager.getBlockPos()).expand(32.0);

        // Try iron golem first
        world.getEntitiesByClass(IronGolemEntity.class, range, g -> true)
                .stream()
                .min(java.util.Comparator.comparingDouble(g -> g.squaredDistanceTo(villager)))
                .ifPresent(golem -> {
                    golem.setTarget(player);
                    world.playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                            SoundEvents.ENTITY_IRON_GOLEM_HURT, SoundCategory.NEUTRAL,
                            0.5f, 1.2f);
                });

        // Also alert any nearby guards (Guard Villagers mod)
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("guardvillagers")) {
            world.getEntitiesByClass(net.minecraft.entity.mob.PathAwareEntity.class, range, guard -> {
                net.minecraft.util.Identifier id =
                        net.minecraft.registry.Registries.ENTITY_TYPE.getId(guard.getType());
                return id != null && "guardvillagers".equals(id.getNamespace());
            }).forEach(guard -> {
                if (guard instanceof net.minecraft.entity.mob.MobEntity)
                    ((net.minecraft.entity.mob.MobEntity) guard).setTarget(player);
            });
        }
    }
}
