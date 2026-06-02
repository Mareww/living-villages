package com.livingvillages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads dialogue lines from config/livingvillages/dialogue.json.
 * On first run the file is created with the built-in defaults so players can edit it.
 */
public class DialogueConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data INSTANCE = null;

    public static Data get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        Path dir  = FabricLoader.getInstance().getConfigDir().resolve("livingvillages");
        Path file = dir.resolve("dialogue.json");
        try {
            Files.createDirectories(dir);
            if (!Files.exists(file)) {
                // First run — write defaults so the user can see and edit them
                try (Writer w = Files.newBufferedWriter(file)) {
                    GSON.toJson(Data.defaults(), w);
                }
                LivingVillages.LOGGER.info("[LivingVillages] Created default dialogue.json in config/livingvillages/");
            }
            try (Reader r = Files.newBufferedReader(file)) {
                INSTANCE = GSON.fromJson(r, Data.class);
            }
            if (INSTANCE == null) INSTANCE = Data.defaults();
        } catch (IOException | JsonParseException e) {
            LivingVillages.LOGGER.error("[LivingVillages] Failed to load dialogue.json, using built-in defaults", e);
            INSTANCE = Data.defaults();
        }
    }

    /** POJO that Gson serialises to/from JSON. */
    public static class Data {
        public String[][] universalTalks;
        public String[]   universalSolo;
        public String[]   universalOneSided;
        public String[]   panicLines;
        public String[]   hums;
        public String[]   roamOneSided;
        public String[][] roamTalks;
        public String[]   joinLines;

        // Profession-specific
        public String[][] farmerTalks;    public String[] farmerSolo;
        public String[][] fishermanTalks; public String[] fishermanSolo;
        public String[][] librarianTalks; public String[] librarianSolo;
        public String[][] armorerTalks;   public String[] armorerSolo;
        public String[][] weaponsmithTalks; public String[] weaponsmithSolo;
        public String[][] toolsmithTalks;  public String[] toolsmithSolo;
        public String[][] clericTalks;    public String[] clericSolo;
        public String[][] butcherTalks;   public String[] butcherSolo;
        public String[][] shepherdTalks;  public String[] shepherdSolo;
        public String[][] fletcherTalks;  public String[] fletcherSolo;
        public String[][] cartographerTalks; public String[] cartographerSolo;
        public String[][] leatherworkerTalks; public String[] leatherworkerSolo;
        public String[][] masonTalks;     public String[] masonSolo;

        static Data defaults() {
            Data d = new Data();
            d.universalTalks = new String[][] {
                {"Have you ever wondered why we rebuild after every raid?", "It builds character. And walls."},
                {"Do you think Steve is okay?", "He dug straight down again. So... no."},
                {"The iron golem won't look at me anymore.", "Nothing. That is the problem."},
                {"The phantom keeps following me.", "When did you last sleep?"},
                {"Do you think we get paid enough?", "We get paid?"},
                {"Do you think the fire is listening to us?", "Yes. And it is not impressed."},
                {"This is my favourite part of the day.", "Nothing explodes at campfires."},
                {"I feel like the fire understands me.", "That is concerning."},
                {"Do you ever think about leaving the village?", "Every time there is a raid."},
                {"I had a dream there were no raids.", "Quiet. Too quiet."},
                {"The golem smiled at a flower for 3 hours.", "He is the most stable one here."},
                {"I waved at a creeper. It waved back.", "Then I ran."},
                {"A zombie knocked on my door last night.", "I hope you didn't open it."},
                {"Have you seen the new house they built?", "The one already on fire? Yes."},
                {"Do you trust the nitwit?", "I trust nobody. Especially not the nitwit."},
                {"How many raids have we survived?", "Not enough to stop counting."},
                {"The bell rang again.", "Already? I just sat down."},
                {"Why do they always target the beds?", "Because they know."},
                {"What do you do when you are not trading?", "This. Exactly this."},
                {"Do you think we will ever have peace?", "Define peace."},
                {"Someone keeps placing torches upside down.", "That is me. Do not ask."},
                {"The ravine behind the village keeps getting bigger.", "It was always that big."},
                {"I heard thunder last night.", "That was not thunder."},
                {"The nitwit waved at me this morning.", "What did you do?"},
                {"Do you ever wonder what is beyond the village?", "More village, probably."},
                {"A bat flew into my house.", "Did you catch it?"},
                {"The well is empty again.", "Steve was thirsty."},
            };
            d.universalSolo = new String[] {
                "The fire is warm tonight...",
                "I could watch this forever.",
                "No raids. No trades. Just fire.",
                "Something about fire makes everything feel okay.",
                "The smoke smells like home.",
                "I think the fire is judging me.",
                "The fire crackles. I feel understood.",
                "Perfect evening. Unless a creeper shows up.",
                "Warmth. Actual warmth. Not emerald warmth.",
                "The golem should see this. He can never sit though.",
                "Sometimes I talk to the fire. Better listener than most.",
                "The flames look different every time. Like clouds, but hot.",
                "I wonder what the golem thinks about at night.",
                "No one raids a campfire. They respect it.",
                "Five minutes. Just five minutes of quiet.",
                "The stars are out. That means no phantoms. Hopefully.",
                "This village is strange. I live here anyway.",
                "I count the raids like seasons now.",
                "Everything is fine. Probably fine. Mostly fine.",
                "The shadows look friendlier from this side of the fire.",
            };
            d.universalOneSided = new String[] {
                "I once traded with a zombie by accident. Long story.",
                "The golem winked at me today. I think.",
                "We should do this more often.",
                "Does anyone else feel like the raids are getting personal?",
                "I miss the old days. Whatever those were.",
                "You know what I realized today? Nothing. Absolutely nothing.",
                "I have 47 emeralds. That is all I have.",
                "Sometimes I think the creepers are not trying to kill us. Sometimes.",
                "I named my bed Gertrude. She burned down.",
                "My house has four walls. Three of them are mine.",
                "The librarian gave me a book called How To Say No. It cost 4 emeralds.",
                "I have been thinking. It has been going poorly.",
                "If I had to describe this village in one word... it would be Ours.",
                "Nobody asked but the potatoes are doing well.",
                "I started a journal. Day 1: nothing happened. Day 2: same.",
                "The stars look different on this side of the village.",
                "Three raids this month. I am starting to take it personally.",
                "My trades are fair. The emerald disagrees.",
                "The golem is staring at the moon again. He does that.",
                "Someone needs to fix that fence. Not me though.",
                "A bat landed on my shoulder this morning. We had a moment.",
                "The raid horn sounds different when you hear it alone.",
                "Funny how the fire looks smaller when you have problems.",
                "Sometimes a good fire is the only meeting that matters.",
            };
            d.panicLines = new String[] {
                "RUN!", "NOT AGAIN.", "WHY.", "I JUST SAT DOWN.",
                "EVERY TIME.", "OH NO OH NO OH NO", "WHAT IS THAT.",
                "I QUIT.", "WHERE IS THE GOLEM.", "SOMEONE HELP.",
                "THEY FOUND ME.", "I KNEW IT.", "MY EMERALDS!",
                "NOT THE WHEAT!", "THIS IS WHY I HAVE TRUST ISSUES.",
                "AGAIN?!", "IT'S FINE. IT'S NOT FINE.", "NOT TODAY.",
                "I JUST REBUILT THAT WALL.", "ABSOLUTELY NOT.",
            };
            d.hums = new String[] {
                "Hm hm hm...", "La la la...", "Hmmmm...",
                "Tra la la...", "Da da da...", "Mm mm mm...",
                "La la... hmm.", "Hmm hmm hmm...", "Da dum da dum...",
                "La la la la la...",
            };
            d.roamTalks = new String[][] {
                {"Beautiful day.", "I suppose."},
                {"Have you heard any news?", "Only bad ones."},
                {"Busy today?", "Always."},
                {"Did you sleep well?", "Better than usual."},
                {"Watch where you're going!", "You walked into me."},
                {"How's the family?", "Existing."},
                {"Seen anything unusual?", "Define unusual."},
                {"Trade is slow today.", "Trade is always slow."},
                {"Good to see you.", "Likewise."},
                {"The golem is acting up again.", "It stood there. Aggressively."},
                {"Long day.", "They all are."},
                {"You look tired.", "I am tired."},
                {"Where are you headed?", "Away from here, briefly."},
                {"Did you hear that?", "I hear everything."},
                {"Something is moving in the treeline.", "I saw it too. Still watching."},
            };
            d.roamOneSided = new String[] {
                "Don't mind me.", "Just passing through.", "Lovely day.",
                "Carry on.", "Nothing to see here.", "Still here? Good.",
                "Almost done for the day.", "Watch your step.", "Excuse me. Busy.",
                "Off to something important. Probably.", "Good. You're alive.",
            };
            d.joinLines = new String[] {
                "I heard that.", "Exactly.", "Go on.", "I was just thinking that.",
                "Well said.", "Couldn't agree more.", "Interesting.", "Tell me more.",
                "I was there for that.", "Speaking of which...", "And then what?",
            };
            // Profession pools use built-in defaults — config only overrides if keys present
            d.farmerTalks    = new String[0][]; d.farmerSolo    = new String[0];
            d.fishermanTalks = new String[0][]; d.fishermanSolo = new String[0];
            d.librarianTalks = new String[0][]; d.librarianSolo = new String[0];
            d.armorerTalks   = new String[0][]; d.armorerSolo   = new String[0];
            d.weaponsmithTalks  = new String[0][]; d.weaponsmithSolo  = new String[0];
            d.toolsmithTalks    = new String[0][]; d.toolsmithSolo    = new String[0];
            d.clericTalks    = new String[0][]; d.clericSolo    = new String[0];
            d.butcherTalks   = new String[0][]; d.butcherSolo   = new String[0];
            d.shepherdTalks  = new String[0][]; d.shepherdSolo  = new String[0];
            d.fletcherTalks  = new String[0][]; d.fletcherSolo  = new String[0];
            d.cartographerTalks   = new String[0][]; d.cartographerSolo   = new String[0];
            d.leatherworkerTalks  = new String[0][]; d.leatherworkerSolo  = new String[0];
            d.masonTalks     = new String[0][]; d.masonSolo     = new String[0];
            return d;
        }
    }
}
