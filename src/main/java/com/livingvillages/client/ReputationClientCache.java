package com.livingvillages.client;

import java.util.HashMap;
import java.util.Map;

/** Stores reputation data received from the server, keyed by villager entity network ID. */
public class ReputationClientCache {
    // villager entity net ID → reputation points (0-100)
    private static final Map<Integer, Integer> DATA = new HashMap<>();

    public static void set(int entityId, int points) {
        DATA.put(entityId, points);
    }

    public static int get(int entityId) {
        return DATA.getOrDefault(entityId, -1); // -1 = no data
    }

    public static void clear() {
        DATA.clear();
    }
}
