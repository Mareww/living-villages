package com.livingvillages.behavior;

import com.livingvillages.LivingVillagesClient;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.*;

public class ReputationSystem {

    public static final String[] LEVEL_NAMES = {
        "Stranger", "Acquaintance", "Familiar", "Friendly", "Liked",
        "Respected", "Trusted", "Admired", "Beloved", "Honored", "Exalted"
    };

    // villagerUUID → {playerUUID → points 0–100}
    private static final Map<UUID, Map<UUID, Integer>> REP = new HashMap<>();

    // ── Getters ───────────────────────────────────────────────────────────────

    public static int getPoints(UUID villager, UUID player) {
        return REP.getOrDefault(villager, Map.of()).getOrDefault(player, 0);
    }

    public static int getLevel(UUID villager, UUID player) {
        return Math.min(10, getPoints(villager, player) / 10);
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public static void addReputation(VillagerEntity villager, PlayerEntity player, int amount) {
        UUID vid = villager.getUuid();
        UUID pid = player.getUuid();
        REP.computeIfAbsent(vid, k -> new HashMap<>())
           .merge(pid, amount, (a, b) -> Math.min(100, Math.max(0, a + b)));
    }

    // ── Tick — send reputation to nearby players for XP bar display ───────────

    public static void tick(VillagerEntity villager, ServerWorld world) {
        // Send reputation packets every 40 ticks (2 seconds) — not every tick.
        // Stagger by entity ID so not all villagers send on the same tick.
        if ((world.getTime() + villager.getId()) % 40 != 0) return;

        UUID vid = villager.getUuid();
        world.getEntitiesByClass(ServerPlayerEntity.class,
                villager.getBoundingBox().expand(5.0), p -> true).forEach(sp -> {
            var buf = PacketByteBufs.create();
            buf.writeInt(villager.getId());
            buf.writeInt(getPoints(vid, sp.getUuid()));
            ServerPlayNetworking.send(sp, LivingVillagesClient.REPUTATION_PACKET, buf);
        });
    }

    // ── NBT persistence ───────────────────────────────────────────────────────

    public static void writeNbt(VillagerEntity villager, net.minecraft.nbt.NbtCompound nbt) {
        Map<UUID, Integer> data = REP.get(villager.getUuid());
        if (data == null || data.isEmpty()) return;
        var compound = new net.minecraft.nbt.NbtCompound();
        data.forEach((playerUuid, points) ->
                compound.putInt(playerUuid.toString(), points));
        nbt.put("LVReputation", compound);
    }

    public static void readNbt(VillagerEntity villager, net.minecraft.nbt.NbtCompound nbt) {
        if (!nbt.contains("LVReputation")) return;
        var compound = nbt.getCompound("LVReputation");
        Map<UUID, Integer> data = REP.computeIfAbsent(villager.getUuid(), k -> new HashMap<>());
        for (String key : compound.getKeys()) {
            try { data.put(UUID.fromString(key), compound.getInt(key)); }
            catch (IllegalArgumentException ignored) {}
        }
    }
}
