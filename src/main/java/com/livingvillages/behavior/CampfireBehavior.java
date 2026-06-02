package com.livingvillages.behavior;

import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Optional;

public class CampfireBehavior {

    private static final int COOLDOWN    = 600;
    private static final int SKIP_NIGHT  = 10001; // > full night window (10 000 ticks)
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END   = 23000L;

    // 8 seats 1 block from the campfire centre
    private static final int[][] SEAT_OFFSETS = {
        { 0, -1}, { 1, -1}, { 1,  0}, { 1,  1},
        { 0,  1}, {-1,  1}, {-1,  0}, {-1, -1}
    };

    public static void tick(VillagerEntity villager, ServerWorld world, IVillagerBehaviorState state) {
        if (!com.livingvillages.LivingVillagesConfig.get().campfireGathering) return;
        long timeOfDay = world.getTimeOfDay() % 24000L;
        boolean isNight = timeOfDay >= NIGHT_START && timeOfDay < NIGHT_END;

        if (!isNight) {
            if (state.livingvillages$getCampfireTarget() != null) {
                state.livingvillages$setCampfireTarget(null);
                state.livingvillages$setCampfireSeat(-1);
                villager.getNavigation().stop();
            }
            return;
        }

        int cd = state.livingvillages$getCampfireCooldown();
        if (cd > 0) {
            state.livingvillages$setCampfireCooldown(cd - 1);

            BlockPos campfire = state.livingvillages$getCampfireTarget();
            if (campfire != null) {
                int seatIdx = state.livingvillages$getCampfireSeat();
                BlockPos seat = seatIdx >= 0 ? getSeat(campfire, seatIdx) : null;

                boolean atSeat = seat != null && villager.squaredDistanceTo(
                        seat.getX() + 0.5, villager.getY(), seat.getZ() + 0.5) < 2.25;

                if (atSeat) {
                    // ── Seated ───────────────────────────────────────────────
                    villager.getNavigation().stop();
                    villager.velocityModified = true;

                    // During Q&A face the partner; otherwise always face the fire
                    VillagerEntity partner = CampfireConversation.getConversationPartner(
                            villager.getId(), world, world.getTime());
                    if (partner != null) {
                        villager.getLookControl().lookAt(partner, 30f, 30f);
                    } else {
                        villager.getLookControl().lookAt(
                                campfire.getX() + 0.5, campfire.getY() + 0.5, campfire.getZ() + 0.5);
                    }

                    // Occasional stargazing — the only visible animation on villagers is head movement
                    if (cd % 60 == 0 && world.random.nextInt(10) == 3) {
                        villager.getLookControl().lookAt(
                                villager.getX(), villager.getY() + 10.0, villager.getZ());
                    }
                    // Occasionally trigger a funny speech bubble conversation
                    CampfireConversation.tryTrigger(villager, world);

                    if (cd % 200 == 0) {
                        world.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                                SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL,
                                0.3f, 0.8f + world.random.nextFloat() * 0.2f);
                        world.spawnParticles(ParticleTypes.SMOKE,
                                villager.getX(), villager.getY() + 0.5, villager.getZ(),
                                1, 0.1, 0.1, 0.1, 0.01);
                        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                                villager.getX(), villager.getY() + 1.2, villager.getZ(),
                                2, 0.3, 0.2, 0.3, 0.0);
                    }
                } else {
                    // ── Walking to seat ──────────────────────────────────────
                    // Every second, physically scan all 8 seat positions and navigate
                    // to the nearest one that has nobody already in it.
                    // This is the source of truth — stored seat indices are only hints.
                    if (cd % 20 == 0) {
                        BlockPos target = findNearestFreeSeat(campfire, villager, world);
                        if (target != null) {
                            // Update stored seat to match what we are navigating to
                            for (int i = 0; i < SEAT_OFFSETS.length; i++) {
                                if (getSeat(campfire, i).equals(target)) {
                                    state.livingvillages$setCampfireSeat(i);
                                    break;
                                }
                            }
                            villager.getNavigation().startMovingTo(
                                    target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.5);
                        }
                    }
                }
            }
            return;
        }

        // ── Slow path: evening decision ───────────────────────────────────────────
        var brain = villager.getBrain();
        if (brain.hasActivity(Activity.PANIC)) {
            state.livingvillages$setCampfireCooldown(COOLDOWN);
            return;
        }

        if (state.livingvillages$getCampfireTarget() != null) {
            state.livingvillages$setCampfireCooldown(COOLDOWN);
            return;
        }

        // Split uses config campfire chance; remainder split between sleep and night owl
        float campfireP = com.livingvillages.LivingVillagesConfig.get().campfireChance / 100f;
        float r = world.random.nextFloat();
        float sleepBound = 1f - campfireP - 0.15f;
        float nightOwlBound = sleepBound + campfireP;
        if (r < sleepBound) {
            state.livingvillages$setCampfireCooldown(SKIP_NIGHT);
            return;
        }
        if (r < nightOwlBound) {
            // Campfire
        } else {
            // Night owl (15%)
            NightOwlTracker.register(villager.getId(), world);
            state.livingvillages$setCampfireCooldown(SKIP_NIGHT);
            return;
        }

        // roll == 0: try to attend campfire
        Optional<BlockPos> campfireOpt = findCampfire(villager, world);
        if (campfireOpt.isEmpty()) {
            // No campfire — become a night owl instead
            NightOwlTracker.register(villager.getId(), world);
            state.livingvillages$setCampfireCooldown(SKIP_NIGHT);
            return;
        }

        BlockPos campfire = campfireOpt.get();
        BlockPos initialSeat = findNearestFreeSeat(campfire, villager, world);
        if (initialSeat == null) {
            // All seats physically occupied
            state.livingvillages$setCampfireCooldown(SKIP_NIGHT);
            return;
        }

        // Store the initial seat index so the HEAD inject knows where to freeze us
        for (int i = 0; i < SEAT_OFFSETS.length; i++) {
            if (getSeat(campfire, i).equals(initialSeat)) {
                state.livingvillages$setCampfireSeat(i);
                break;
            }
        }

        state.livingvillages$setCampfireTarget(campfire);
        state.livingvillages$setCampfireCooldown(COOLDOWN);
    }

    /**
     * Scans all 8 seat positions and returns the nearest one that has no other
     * attending villager physically within 0.9 blocks of it.
     * This is the authoritative check — stored seat indices are only navigation hints.
     */
    private static BlockPos findNearestFreeSeat(BlockPos campfire, VillagerEntity villager, ServerWorld world) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int[] off : SEAT_OFFSETS) {
            BlockPos candidate = campfire.add(off[0], 0, off[1]);
            boolean occupied = !world.getEntitiesByClass(VillagerEntity.class,
                    new Box(candidate).expand(0.9),
                    v -> v != villager && v.isAlive()
                            && ((IVillagerBehaviorState) v).livingvillages$getCampfireTarget() != null).isEmpty();
            if (occupied) continue;

            double dist = villager.squaredDistanceTo(
                    candidate.getX() + 0.5, villager.getY(), candidate.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    public static BlockPos getSeat(BlockPos campfire, int seatIdx) {
        int[] off = SEAT_OFFSETS[seatIdx % SEAT_OFFSETS.length];
        return campfire.add(off[0], 0, off[1]);
    }

    private static Optional<BlockPos> findCampfire(VillagerEntity villager, ServerWorld world) {
        BlockPos center = villager.getBlockPos();
        int range = 64;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState bs = world.getBlockState(mutable);
                    // Accept any campfire in the #minecraft:campfires tag (covers modded ones).
                    // Fall back to CampfireBlock instanceof if the tag isn't populated.
                    boolean isCampfire = bs.isIn(net.minecraft.registry.tag.BlockTags.CAMPFIRES)
                            || bs.getBlock() instanceof CampfireBlock;
                    boolean isLit = !bs.contains(net.minecraft.state.property.Properties.LIT)
                            || bs.get(net.minecraft.state.property.Properties.LIT);
                    if (isCampfire && isLit) {
                        // Skip chimney campfires — they have walls on all sides.
                        // Outdoor gathering campfires need at least 3 open horizontal neighbours.
                        int openSides = 0;
                        for (net.minecraft.util.math.Direction d : new net.minecraft.util.math.Direction[]{
                                net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.SOUTH,
                                net.minecraft.util.math.Direction.EAST,  net.minecraft.util.math.Direction.WEST}) {
                            if (world.getBlockState(mutable.offset(d)).isAir()) openSides++;
                        }
                        if (openSides >= 3) return Optional.of(mutable.toImmutable());
                    }
                }
            }
        }
        return Optional.empty();
    }
}
