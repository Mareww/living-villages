package com.livingvillages.util;

import net.minecraft.util.math.BlockPos;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks block positions of chests/barrels placed by players this session.
 * Village-generated chests are never added here, so the stealing detection
 * can skip positions in this set.
 */
public class PlayerChestTracker {

    private static final Set<BlockPos> PLAYER_CHESTS = new HashSet<>();

    public static void add(BlockPos pos) {
        PLAYER_CHESTS.add(pos.toImmutable());
    }

    public static void remove(BlockPos pos) {
        PLAYER_CHESTS.remove(pos);
    }

    public static boolean isPlayerChest(BlockPos pos) {
        return PLAYER_CHESTS.contains(pos);
    }
}
