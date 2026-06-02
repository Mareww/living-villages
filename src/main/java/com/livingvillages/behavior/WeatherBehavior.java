package com.livingvillages.behavior;

import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

import java.util.Optional;

public class WeatherBehavior {

    private static final int COOLDOWN = 40;
    private static final int RETRY_TICKS = 400;

    public static void tick(VillagerEntity villager, ServerWorld world, IVillagerBehaviorState state) {
        if (!com.livingvillages.LivingVillagesConfig.get().rainShelter) return;
        // ── FAST PATH (every tick) ─────────────────────────────────────────────────

        // Celebration: jump + particles for ~5 seconds after rain stops.
        // Timer only counts while the villager is outdoors.
        int celebTimer = state.livingvillages$getCelebrationTimer();
        if (celebTimer > 0 && isExposedToSky(world, villager.getBlockPos())) {
            state.livingvillages$setCelebrationTimer(celebTimer - 1);
            if (celebTimer % 15 == 0) {
                villager.setVelocity(villager.getVelocity().x, 0.42, villager.getVelocity().z);
                villager.velocityModified = true;
                world.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                        SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL,
                        0.6f, 1.3f + world.random.nextFloat() * 0.2f);
            }
            if (celebTimer % 4 == 0) {
                world.spawnParticles(ParticleTypes.NOTE,
                        villager.getX(), villager.getY() + 2.1, villager.getZ(),
                        1, 0.4, 0.1, 0.4, 0.0);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        villager.getX(), villager.getY() + 1.6, villager.getZ(),
                        2, 0.4, 0.3, 0.4, 0.0);
            }
        }

        // Keep WALK_TARGET pinned every tick while seeking shelter.
        if (state.livingvillages$isSeekingShelter()) {
            var brain = villager.getBrain();
            if (!world.isRaining() || brain.hasActivity(Activity.PANIC) || brain.hasActivity(Activity.REST)) {
                clearShelterState(villager, state);
            } else {
                BlockPos target = state.livingvillages$getShelterTarget();
                if (target != null) {
                    brain.remember(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(new BlockPosLookTarget(target), 0.8f, 1));
                }
            }
        }

        // ── SLOW PATH (every 40 ticks) ────────────────────────────────────────────
        int cd = state.livingvillages$getWeatherCooldown();
        if (cd > 0) {
            state.livingvillages$setWeatherCooldown(cd - 1);
            return;
        }
        state.livingvillages$setWeatherCooldown(COOLDOWN);

        // Dry biomes (desert, savanna, badlands) never get rain
        Biome.Precipitation precip = world.getBiome(villager.getBlockPos())
                .value().getPrecipitation(villager.getBlockPos());
        if (precip == Biome.Precipitation.NONE) return;

        // During a raid, let vanilla PANIC AI handle movement — don't fight it
        if (world.getRaidAt(villager.getBlockPos()) != null) {
            clearShelterState(villager, state);
            return;
        }

        boolean raining = world.isRaining();
        boolean wasRaining = state.livingvillages$wasRaining();

        if (raining) {
            // Inside — close any open doors nearby (vanilla CloseDoorTask won't fire
            // because our WALK_TARGET pin keeps the villager away from the door)
            if (state.livingvillages$isSeekingShelter() && !isExposedToSky(world, villager.getBlockPos())) {
                closeNearbyDoors(villager, world);
            }

            var brain = villager.getBrain();
            if (!brain.hasActivity(Activity.PANIC) && !brain.hasActivity(Activity.REST)
                    && isExposedToSky(world, villager.getBlockPos())) {

                if (!state.livingvillages$isSeekingShelter()) {
                    // First exposure to rain — react and search
                    world.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                            SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.NEUTRAL,
                            0.3f, 1.2f + world.random.nextFloat() * 0.2f);
                    world.spawnParticles(ParticleTypes.SPLASH,
                            villager.getX(), villager.getY() + 1.5, villager.getZ(),
                            6, 0.3, 0.2, 0.3, 0.05);
                    state.livingvillages$setSeekingShelter(true);
                    BlockPos shelter = findShelter(villager, world).orElseGet(
                            () -> homeFallback(villager, world)); // always have somewhere to go
                    state.livingvillages$setShelterTarget(shelter);

                } else {
                    BlockPos target = state.livingvillages$getShelterTarget();
                    // Retry every slow-path tick when homeless (target null), otherwise stagger
                    long stagger = (world.getTime() + villager.getId()) % RETRY_TICKS;
                    boolean shouldRetry = target == null || stagger == 0;
                    if (shouldRetry && (target == null
                            || villager.getNavigation().findPathTo(target, 0) == null)) {
                        state.livingvillages$setShelterTarget(null);
                        BlockPos shelter = findShelter(villager, world)
                                .orElseGet(() -> homeFallback(villager, world));
                        // Last resort: search wider with relaxed path check
                        if (shelter == null) shelter = lastResortShelter(villager, world);
                        state.livingvillages$setShelterTarget(shelter);
                    }
                }
            }
        } else if (wasRaining) {
            clearShelterState(villager, state);
            state.livingvillages$setCelebrationTimer(100);
            villager.getLookControl().lookAt(villager.getX(), villager.getY() + 10.0, villager.getZ());
        }

        state.livingvillages$setWasRaining(raining);
    }

    // Uses WORLD_SURFACE heightmap which includes leaf blocks.
    // world.isSkyVisible() uses MOTION_BLOCKING_NO_LEAVES, so trees don't count — this does.
    private static boolean isExposedToSky(ServerWorld world, BlockPos pos) {
        return world.getTopY(Heightmap.Type.WORLD_SURFACE, pos.getX(), pos.getZ()) <= pos.getY();
    }

    private static void clearShelterState(VillagerEntity villager, IVillagerBehaviorState state) {
        state.livingvillages$setSeekingShelter(false);
        state.livingvillages$setShelterTarget(null);
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
    }

    private static Optional<BlockPos> findShelter(VillagerEntity villager, ServerWorld world) {
        // Priority 1: own bed — vanilla navigation handles doors perfectly
        Optional<GlobalPos> homeMemory = villager.getBrain()
                .getOptionalRegisteredMemory(MemoryModuleType.HOME);
        if (homeMemory.isPresent()
                && homeMemory.get().getDimension().equals(world.getRegistryKey())) {
            return Optional.of(homeMemory.get().getPos());
        }

        // Priority 2: nearest accessible house (homeless villager)
        Optional<BlockPos> house = findHouseShelter(villager, world);
        if (house.isPresent()) return house;

        // Priority 3: any covered spot — tree canopy, cliff, overhang
        return findGenericShelter(villager, world);
    }

    /** Last resort: walk toward the villager's home bed position (vanilla gets them inside). */
    private static BlockPos homeFallback(VillagerEntity villager, ServerWorld world) {
        return villager.getBrain()
                .getOptionalRegisteredMemory(MemoryModuleType.HOME)
                .filter(g -> g.getDimension().equals(world.getRegistryKey()))
                .map(GlobalPos::getPos)
                .orElse(null); // if truly homeless, stays null (standing still, unavoidable)
    }

    private static Optional<BlockPos> findHouseShelter(VillagerEntity villager, ServerWorld world) {
        BlockPos origin = villager.getBlockPos();
        int pathChecks = 0;

        for (int r = 1; r <= 24; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos doorPos = origin.add(dx, dy, dz);
                        var doorState = world.getBlockState(doorPos);
                        if (!(doorState.getBlock() instanceof DoorBlock)) continue;
                        if (doorState.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) continue;

                        Direction facing = doorState.get(Properties.HORIZONTAL_FACING);
                        for (Direction inward : new Direction[]{facing, facing.getOpposite()}) {
                            if (isExposedToSky(world, doorPos.offset(inward, 1))) continue;

                            BlockPos approach = doorPos.offset(inward.getOpposite(), 1);
                            if (villager.getNavigation().findPathTo(approach, 1) == null) {
                                if (++pathChecks >= 20) return Optional.empty(); // raised from 10→20
                                continue;
                            }

                            for (int depth = 2; depth <= 4; depth++) {
                                BlockPos candidate = doorPos.offset(inward, depth);
                                if (isSheltered(world, candidate)) return Optional.of(candidate);
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findGenericShelter(VillagerEntity villager, ServerWorld world) {
        BlockPos origin = villager.getBlockPos();
        int pathChecks = 0;

        for (int r = 1; r <= 32; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos candidate = origin.add(dx, dy, dz);
                        if (!isSheltered(world, candidate)) continue;
                        if (villager.getNavigation().findPathTo(candidate, 0) != null) {
                            return Optional.of(candidate);
                        }
                        // Raise cap so sparse forest canopy doesn't exhaust checks too early
                        if (++pathChecks >= 20) return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isSheltered(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isAir()
                && world.getBlockState(pos.up()).isAir()
                && !isExposedToSky(world, pos);
    }

    /** Wider search with no path validation — finds any non-sky-exposed air block up to 48 blocks away. */
    private static BlockPos lastResortShelter(VillagerEntity villager, ServerWorld world) {
        BlockPos origin = villager.getBlockPos();
        for (int r = 1; r <= 48; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = -1; dy <= 2; dy++) {
                        BlockPos candidate = origin.add(dx, dy, dz);
                        if (isSheltered(world, candidate)) return candidate;
                    }
                }
            }
        }
        return null;
    }

    private static void closeNearbyDoors(VillagerEntity villager, ServerWorld world) {
        BlockPos center = villager.getBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    var bs = world.getBlockState(pos);
                    if (!(bs.getBlock() instanceof DoorBlock)) continue;
                    if (bs.get(Properties.DOUBLE_BLOCK_HALF) != DoubleBlockHalf.LOWER) continue;
                    if (!bs.get(Properties.OPEN)) continue;
                    world.setBlockState(pos, bs.with(Properties.OPEN, false), 10);
                    BlockPos upper = pos.up();
                    var ubs = world.getBlockState(upper);
                    if (ubs.contains(Properties.OPEN)) {
                        world.setBlockState(upper, ubs.with(Properties.OPEN, false), 10);
                    }
                    world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE,
                            SoundCategory.BLOCKS, 1.0f, 1.0f);
                }
            }
        }
    }
}
