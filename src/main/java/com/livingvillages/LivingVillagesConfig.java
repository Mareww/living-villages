package com.livingvillages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod-wide config — saved to config/livingvillages/config.json.
 * All fields are public static so behaviors can read them cheaply.
 */
public class LivingVillagesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LivingVillagesConfig INSTANCE = new LivingVillagesConfig();

    // ── Feature toggles ───────────────────────────────────────────────────────
    public boolean babyAnnoyance    = true;
    public boolean babyTagGame      = true;
    public boolean stareReaction    = true;
    public boolean rainShelter      = true;
    public boolean campfireGathering = true;
    public boolean reputationXpBar  = true;
    public boolean deathReaction    = true;
    public boolean workDialogue     = true;
    public boolean angerSystem      = true;  // flee-to-golem on hit
    public boolean ambientChatter   = true;  // periodic solo/one-sided bubbles
    public boolean greetings        = true;
    public boolean panicBubbles     = true;

    // ── Tuning values ─────────────────────────────────────────────────────────
    /** Seconds of eye contact before a villager reacts (1-10). */
    public int stareDurationSeconds = 2;
    /** Chance (0-100) a villager attends the campfire at night. */
    public int campfireChance = 25;

    // ── Static accessors (shorthand for behaviors) ────────────────────────────
    public static LivingVillagesConfig get() { return INSTANCE; }

    public static void load() {
        Path file = configFile();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    LivingVillagesConfig loaded = GSON.fromJson(r, LivingVillagesConfig.class);
                    if (loaded != null) INSTANCE = loaded;
                }
            }
            save(); // write any missing fields
        } catch (IOException e) {
            LivingVillages.LOGGER.warn("[LivingVillages] Could not load config, using defaults", e);
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(configFile())) {
            GSON.toJson(INSTANCE, w);
        } catch (IOException e) {
            LivingVillages.LOGGER.warn("[LivingVillages] Could not save config", e);
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("livingvillages").resolve("config.json");
    }
}
