package com.livingvillages;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            LivingVillagesConfig cfg = LivingVillagesConfig.get();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.literal("Living Villages"))
                    .setSavingRunnable(LivingVillagesConfig::save);

            ConfigEntryBuilder e = builder.entryBuilder();

            // ── Features ─────────────────────────────────────────────────────
            ConfigCategory features = builder.getOrCreateCategory(Text.literal("Features"));

            features.addEntry(e.startBooleanToggle(Text.literal("Ambient Chatter"), cfg.ambientChatter)
                    .setDefaultValue(true).setTooltip(Text.literal("Villagers occasionally say solo lines while wandering."))
                    .setSaveConsumer(v -> cfg.ambientChatter = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Greetings"), cfg.greetings)
                    .setDefaultValue(true).setTooltip(Text.literal("Villagers greet each other and the player once per day."))
                    .setSaveConsumer(v -> cfg.greetings = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Panic Bubbles"), cfg.panicBubbles)
                    .setDefaultValue(true).setTooltip(Text.literal("Villagers shout lines while fleeing from threats."))
                    .setSaveConsumer(v -> cfg.panicBubbles = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Rain Shelter"), cfg.rainShelter)
                    .setDefaultValue(true).setTooltip(Text.literal("Villagers run indoors when it rains."))
                    .setSaveConsumer(v -> cfg.rainShelter = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Campfire Gathering"), cfg.campfireGathering)
                    .setDefaultValue(true).setTooltip(Text.literal("Villagers gather around outdoor campfires at night."))
                    .setSaveConsumer(v -> cfg.campfireGathering = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Work Dialogue"), cfg.workDialogue)
                    .setDefaultValue(true).setTooltip(Text.literal("Villagers say profession-specific lines while working."))
                    .setSaveConsumer(v -> cfg.workDialogue = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Stare Reaction"), cfg.stareReaction)
                    .setDefaultValue(true).setTooltip(Text.literal("Villagers react when a player stares at them."))
                    .setSaveConsumer(v -> cfg.stareReaction = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Death Reaction"), cfg.deathReaction)
                    .setDefaultValue(true).setTooltip(Text.literal("Nearby villagers react when another villager dies."))
                    .setSaveConsumer(v -> cfg.deathReaction = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Anger / Flee System"), cfg.angerSystem)
                    .setDefaultValue(true).setTooltip(Text.literal("Villagers flee and call guards/golems when attacked."))
                    .setSaveConsumer(v -> cfg.angerSystem = v).build());

            features.addEntry(e.startBooleanToggle(Text.literal("Reputation XP Bar"), cfg.reputationXpBar)
                    .setDefaultValue(true).setTooltip(Text.literal("Replaces the XP bar with reputation level when looking at a villager."))
                    .setSaveConsumer(v -> cfg.reputationXpBar = v).build());

            // ── Baby Behaviour ────────────────────────────────────────────────
            ConfigCategory babies = builder.getOrCreateCategory(Text.literal("Baby Villagers"));

            babies.addEntry(e.startBooleanToggle(Text.literal("Baby Annoyance"), cfg.babyAnnoyance)
                    .setDefaultValue(true).setTooltip(Text.literal("Baby villagers follow players and say annoying things."))
                    .setSaveConsumer(v -> cfg.babyAnnoyance = v).build());

            babies.addEntry(e.startBooleanToggle(Text.literal("Baby Tag Game"), cfg.babyTagGame)
                    .setDefaultValue(true).setTooltip(Text.literal("Baby villagers play tag with each other."))
                    .setSaveConsumer(v -> cfg.babyTagGame = v).build());

            // ── Tuning ────────────────────────────────────────────────────────
            ConfigCategory tuning = builder.getOrCreateCategory(Text.literal("Tuning"));

            tuning.addEntry(e.startIntSlider(Text.literal("Stare Duration (seconds)"), cfg.stareDurationSeconds, 1, 10)
                    .setDefaultValue(2).setTooltip(Text.literal("How long a player must look at a villager before they react."))
                    .setSaveConsumer(v -> cfg.stareDurationSeconds = v).build());

            tuning.addEntry(e.startIntSlider(Text.literal("Campfire Chance (%)"), cfg.campfireChance, 0, 100)
                    .setDefaultValue(25).setTooltip(Text.literal("Chance (per villager per night) to attend the campfire."))
                    .setSaveConsumer(v -> cfg.campfireChance = v).build());

            return builder.build();
        };
    }
}
