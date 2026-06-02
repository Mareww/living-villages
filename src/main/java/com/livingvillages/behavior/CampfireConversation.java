package com.livingvillages.behavior;

import com.livingvillages.DialogueConfig;
import com.livingvillages.duck.IVillagerBehaviorState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.VillagerProfession;

import java.util.*;

public class CampfireConversation {

    // ── Built-in arrays always used — config only wins if it has MORE lines (user added custom ones) ──
    private static String[][] cfgTalks()      { var c=DialogueConfig.get().universalTalks;      return c!=null&&c.length>TALKS.length?c:TALKS; }
    private static String[]   cfgSolo()       { var c=DialogueConfig.get().universalSolo;       return c!=null&&c.length>SOLO.length?c:SOLO; }
    private static String[]   cfgOneSided()   { var c=DialogueConfig.get().universalOneSided;   return c!=null&&c.length>ONE_SIDED.length?c:ONE_SIDED; }
    private static String[]   cfgPanic()      { var c=DialogueConfig.get().panicLines;          return c!=null&&c.length>PANIC_LINES.length?c:PANIC_LINES; }
    private static String[][] cfgRoamTalks()  { var c=DialogueConfig.get().roamTalks;           return c!=null&&c.length>ROAM_TALKS.length?c:ROAM_TALKS; }
    private static String[]   cfgRoamOne()    { var c=DialogueConfig.get().roamOneSided;        return c!=null&&c.length>ROAM_ONE_SIDED.length?c:ROAM_ONE_SIDED; }
    private static String[]   cfgJoin()       { var c=DialogueConfig.get().joinLines;           return c!=null&&c.length>0?c:JOIN_LINES_DEFAULT; }
    private static String[]   cfgHums()       { var c=DialogueConfig.get().hums;                return c!=null&&c.length>0?c:HUMS_DEFAULT; }

    private static final String[] HUMS_DEFAULT = {
        "Hm hm hm...", "La la la...", "Hmmmm...", "Tra la la...", "Da da da...",
        "Mm mm mm...", "La la... hmm.", "Hmm hmm hmm...", "Da dum da dum...", "La la la la la...",
    };
    private static final String[] JOIN_LINES_DEFAULT = {
        "I heard that.", "Exactly.", "Go on.", "I was just thinking that.",
        "Well said.", "Couldn't agree more.", "Interesting.", "Tell me more.",
        "I was there for that.", "Speaking of which...", "And then what?",
    };

    // ── Built-in universal lines (default fallback) ───────────────────────────
    private static final String[][] TALKS = {
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
        {"Have you ever met an illager?", "Once. He tried to trade me bad luck."},
        {"Why do they always target the beds?", "Because they know."},
        {"What do you do when you are not trading?", "This. Exactly this."},
        {"Do you think we will ever have peace?", "Define peace."},
        {"Someone keeps placing torches upside down.", "That is me. Do not ask."},
        {"The ravine behind the village keeps getting bigger.", "It was always that big."},
        {"I heard thunder last night.", "That was not thunder."},
        {"Did you see the lightning strike the hill?", "It happens every other week now."},
        {"The nitwit waved at me this morning.", "What did you do?"},
        {"I counted my emeralds again.", "Were there enough?"},
        {"Do you ever wonder what is beyond the village?", "More village, probably."},
        {"A bat flew into my house.", "Did you catch it?"},
        {"The well is empty again.", "Steve was thirsty."},
        // NEW
        {"What do you think happens when we sleep?", "Nothing good, based on the phantoms."},
        {"Do golems have feelings?", "He sat with a flower for an hour. You tell me."},
        {"I think the illagers are getting organised.", "They were always organised. We weren't."},
        {"Someone moved my bed last night.", "Where did you wake up? / In the field. With the cows."},
        {"I had a trade go wrong today.", "Define wrong. / Define trade."},
        {"Have you ever been to another village?", "Once. It was also on fire."},
        {"Do you think the raids will ever stop?", "Do you think the sun will stop rising?"},
        {"What would you do with a thousand emeralds?", "Sleep. Actual, uninterrupted sleep."},
        {"I think the golem is lonely.", "He has flowers. More than most of us."},
        {"Do you remember the last quiet week?", "I have a vague memory of one. Years ago."},
        {"Someone built a house out of wool last week.", "Is it still there? / It was wool."},
        {"What is the point of the bell?", "It tells us something bad is happening. / We know that already."},
        {"I dreamed I was a creeper.", "How did it feel? / Explosive."},
        {"Do you think zombies know what they are?", "I think they're past caring."},
        {"The nether still scares me.", "It should scare everyone."},
        {"I saw a piglin once. Just once.", "What happened? / That was enough."},
        {"What's the longest raid you've survived?", "They blur together after a while."},
        {"I think the pillagers are jealous of us.", "Of what exactly? / Of everything exactly."},
        {"Have you ever seen a stronghold?", "I've seen the hole Steve dug to find one."},
        {"Do we choose our professions or do they choose us?", "I didn't choose the barrel. The barrel chose me."},
        {"I wonder what the golem dreams about.", "Flowers probably. And us, hopefully."},
        {"What would this village be without the golem?", "Gone. Probably gone."},
        {"The sky looked strange last night.", "It always looks strange now. We adjusted."},
        {"Have you ever just... not traded?", "Once. It felt wrong for a week."},
        {"I feel like the village is shrinking.", "Or we're getting bigger. / We're not getting bigger."},
        {"Someone asked me what year it is.", "What did you say? / I counted the raids."},
        {"The children grow up fast here.", "Everything moves fast here."},
        {"I saw a skeleton reading a sign yesterday.", "Could it read? / It was confused. Same as us."},
        {"If you could change one thing about the village...", "One thing? / Fair point."},
        {"The crops grew without rain this week.", "Don't question it. / I'm absolutely questioning it."},
        {"What happens to the golems when they are old?", "I don't know. I've never seen an old one."},
        {"Do you think there are villages underground?", "Probably. And they're wondering about us."},
        {"I tried to befriend a bat.", "How did that go? / It bit me and flew away. So. Mixed."},
        {"Have you noticed the moon looks different lately?", "I try not to look at it too long."},
        // Additional lines
        {"What is your earliest memory?", "Smoke. Then bells. Then more smoke."},
        {"Do you think the raids are personal?", "At this point, yes. Very personal."},
        {"I found a map with no name on it.", "Where did it lead? / Nowhere I recognized. Everywhere I feared."},
        {"A spider stared at me for ten minutes today.", "What did you do? / I stared back. Neither of us blinked."},
        {"Have you ever traded with someone you regretted?", "Every third customer."},
        {"What do you think about at night?", "Mostly nothing. Which is loud in its own way."},
        {"Someone left food outside my door.", "Who? / I didn't check. I ate it."},
        {"The forest sounds different after midnight.", "I know. I've stopped investigating."},
        {"I fell asleep at my stall today.", "Did anyone notice? / Everyone noticed. Nobody said anything."},
        {"Do you think the golem gets bored?", "I think the golem has a patience we cannot comprehend."},
        {"I told someone a secret and they told everyone.", "What was the secret? / If I tell you it'll happen again."},
        {"A rabbit followed me home.", "What did you do? / Named it. Complicated things."},
        {"The fog this morning was wrong.", "Wrong how? / Just wrong. You know the kind."},
        {"Is there a house you've always been curious about?", "The one at the edge. Nobody goes near it. / Good reason for that."},
        {"Someone built a path to nowhere.", "Maybe it leads somewhere they haven't told us."},
        {"What would you plant if you could plant anything?", "Something that doesn't need rain or raids to survive."},
        {"I counted the emeralds in my inventory today.", "Were there more or fewer than you expected? / Somehow both."},
        {"The bell rang and nothing happened.", "What did you do? / I sat very still for twenty minutes."},
        {"I had the same dream three nights in a row.", "What happened in it? / Nothing. Absolute silence. That's what scared me."},
        {"Do you remember the last trader who passed through?", "The one with the llamas? / The llamas were better company."},
        {"Someone painted something on my door.", "What was it? / A fish. No explanation. Still there."},
        {"I watched the golem stand in the same spot for four hours.", "Was he okay? / He seemed fine. Better than most of us."},
        {"The new path they built doesn't go anywhere useful.", "Most paths don't. That's not why we make them."},
        {"I feel like this village is watching us right now.", "The village or something in it? / Let's not find out."},
    };

    private static final String[] SOLO = {
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
        "If the fire goes out, I'm going to bed.",
        "I should do this more often. I never do.",
        "The shadows look friendlier from this side of the fire.",
        "One day I'll build a bigger campfire. One day.",
        "Everything is fine. Probably fine. Mostly fine.",
        "The night shift at the campfire. Nobody asked for it. Here I am.",
        // NEW
        "The village looks smaller at night. More manageable.",
        "I've been here my whole life. Strange to think about.",
        "A quiet night is still a good night.",
        "The fire doesn't ask for anything. I appreciate that.",
        "I wonder if the golem gets cold.",
        "Somewhere, there is a raid happening. Not here. For now.",
        "I made a decision today. I don't remember what it was.",
        "The sky is doing something interesting. No threats yet.",
        "I should sleep. I won't sleep.",
        "The world is very large and we are very small and this campfire is just right.",
        "I have questions. The fire doesn't answer. Neither does anyone else.",
        "Tomorrow will be another day. That's either good or bad.",
        "I've traded 200 emeralds worth of things this week. Nobody said thank you.",
        "The grass grows back. That's something.",
        "I've never left the village for longer than a day. That seems wrong somehow.",
        "There was a raid here once that lasted three days. Nobody talks about that one.",
        "The bell doesn't ring for nothing. I should be grateful for silent nights.",
        "I caught myself smiling at a flower today. Don't tell anyone.",
        "The golem is still out there somewhere. Good.",
        "One more night survived. Tomorrow we do it again.",
        "I don't know what I'd do without this campfire. I know what I'd do without the raids.",
        "Somewhere, someone is having a worse night. Somewhere, someone isn't.",
        "The stars don't care about any of this. I find that comforting.",
        "Simple things. Fire. Warmth. Nobody trying to kill me. Good evening.",
        // More solo lines
        "I've been here a long time. The village has changed. I haven't decided if that's good.",
        "The fire doesn't care what I did today. That's a comfort.",
        "Some nights everything feels possible. Tonight is not that night.",
        "I keep meaning to fix that fence. The fence keeps not caring.",
        "A good fire and no questions. Perfect.",
        "I wonder if the other villages are doing this right now. Sitting by their fires.",
        "The embers are the best part. Nobody agrees with me on that.",
        "I should have said something different this morning. Oh well.",
        "Nobody raided tonight. I'll take it.",
        "The golem is out there somewhere. That helps.",
        "Three trades, two conversations, one near-death experience. Normal day.",
        "The smell of the fire is going to be in my clothes for days. I don't mind.",
        "Sometimes the village feels very small. Tonight it feels exactly right.",
        "I started to count my worries and fell asleep. Good method.",
        "Whatever happens tomorrow, tonight was fine.",
        "I saw something moving in the tree line. I'm choosing not to pursue it.",
        "I've never left. Some days that seems brave. Others it just seems like fact.",
        "Fire, stars, no immediate danger. These are the good times.",
        "I had a good idea today. I wrote it down. I can't read my own handwriting.",
        "The shadow of the golem passed by an hour ago. He's making his rounds. Good.",
        "The village breathes at night. I can hear it.",
        "I've survived everything this world has thrown at me so far. Interesting streak.",
        "I keep thinking I hear my name. Nobody's calling.",
        "The nights here are actually beautiful if you don't think too hard.",
    };

    // ── Profession-specific Q&A pools ─────────────────────────────────────────
    private static final Map<VillagerProfession, String[][]> PROF_TALKS = new HashMap<>();
    private static final Map<VillagerProfession, String[]>   PROF_SOLO  = new HashMap<>();

    // ── Work-context lines — said while actively doing the job ────────────────
    private static final Map<VillagerProfession, String[]> PROF_WORK = new HashMap<>();

    static {
        PROF_WORK.put(VillagerProfession.FARMER, new String[] {
            "Come on, grow...", "More wheat. Always more wheat.",
            "The soil is good today.", "Don't you dare rain.",
            "64 wheat. 64.", "Beets again. Why do I grow beets.",
            "These carrots won't harvest themselves.", "I can feel the rain coming.",
            "Good soil. Good crops. Good day.", "Almost full. Almost.",
        });
        PROF_WORK.put(VillagerProfession.FISHERMAN, new String[] {
            "Come on... bite already.", "The water looks promising today.",
            "I can wait. I've been waiting for years.",
            "Still... still... still...", "The fish are watching me. I can feel it.",
            "Any moment now.", "Patience. This is patience.",
            "One good catch. Just one.", "Don't even move.",
            "The line moved. False alarm.", "I see you down there.",
            "Nothing. Absolutely nothing.", "Worth it. Eventually.",
        });
        PROF_WORK.put(VillagerProfession.LIBRARIAN, new String[] {
            "Where did I put that book...", "Fascinating. Truly.",
            "This enchantment... unusual.", "I've read this before. I'll read it again.",
            "Shh. I'm working.", "The knowledge is in here somewhere.",
            "Cross-referencing...", "Interesting. Very interesting.",
            "Don't interrupt a librarian.", "Page 47 again. Always page 47.",
        });
        PROF_WORK.put(VillagerProfession.ARMORER, new String[] {
            "Hold still, you piece of iron.", "Almost perfect. Almost.",
            "The forge is hot today. Good.", "Chestplate number twelve.",
            "Strike while the iron is hot.", "This one's for a raid. I can tell.",
            "Beautiful. Nobody will appreciate it.", "Harder. Faster.",
        });
        PROF_WORK.put(VillagerProfession.WEAPONSMITH, new String[] {
            "Sharper. It needs to be sharper.", "This edge could split a hair.",
            "A blade worthy of a hero. Sold for three emeralds.",
            "Strike true.", "Perfect balance. Nobody will notice.",
            "The metal speaks to me today.", "One more pass on the grindstone.",
        });
        PROF_WORK.put(VillagerProfession.TOOLSMITH, new String[] {
            "This pickaxe will outlast its owner.", "Perfect angle.",
            "Steve will break this in an hour. I know it.",
            "The handle has to feel right.", "Tap. Tap. Tap.",
            "A craftsman's work is never done.", "Close. Very close.",
        });
        PROF_WORK.put(VillagerProfession.CLERIC, new String[] {
            "The potion is almost ready...", "Nether wart. Check. Blaze powder. Check.",
            "This will help someone. Hopefully.",
            "Weakness first. Then the golden apple.", "Bubble bubble.",
            "The undead fear this brew. As they should.",
            "Careful. Careful. One mistake and it's over.",
            "A good cleric saves lives. That is the job.",
            "Hold still... almost cured... there.",
            "The screaming means it's working.", "Another soul returned.",
        });
        PROF_WORK.put(VillagerProfession.BUTCHER, new String[] {
            "Clean cut. Every time.", "The smoker needs more wood.",
            "A good piece of meat. Honest work.", "Don't look at me like that.",
            "Smells good. Smells really good.", "This one's for the feast.",
            "Best cut I've made all week.",
        });
        PROF_WORK.put(VillagerProfession.SHEPHERD, new String[] {
            "Hold still... almost done.", "The loom has a rhythm to it.",
            "Blue today. Definitely blue.", "Shearing is an art. Not everyone agrees.",
            "Wool wool wool wool wool.", "This pattern came to me in a dream.",
            "The sheep are cooperative today. Rare.",
        });
        PROF_WORK.put(VillagerProfession.FLETCHER, new String[] {
            "Straight. It has to be straight.", "Feathers aligned. Good.",
            "Number 48. 16 more to go.", "This one will fly true. I know it.",
            "The grain of the wood matters. It always matters.",
            "Don't rush a fletcher.", "Every arrow is a promise.",
        });
        PROF_WORK.put(VillagerProfession.CARTOGRAPHER, new String[] {
            "The scale is off. Again.", "This path needs a name.",
            "I've been to this place. In my head.",
            "North. Always start with north.", "The ink needs to dry.",
            "This map will save someone's life one day.",
            "Every blank space is a question I haven't answered yet.",
        });
        PROF_WORK.put(VillagerProfession.LEATHERWORKER, new String[] {
            "The dye isn't setting right...", "Soak longer. Always soak longer.",
            "This saddle will outlast any horse.", "Smooth. It has to be smooth.",
            "The smell grows on you. Literally.", "Good hide. Good day.",
        });
        PROF_WORK.put(VillagerProfession.MASON, new String[] {
            "One more cut.", "The chisel knows what it wants.",
            "Stone doesn't lie.", "Tap. Listen. Tap again.",
            "200 bricks. Still not enough.",
            "Perfect edge. Nobody will see it underground but I will know.",
            "The grain runs this way. Always this way.",
        });
    }

    // Work lines for mod professions keyed by registry name string
    private static final Map<String, String[]> PROF_WORK_NAMED = new HashMap<>();
    static {
        PROF_WORK_NAMED.put("beekeeper", new String[] {
            "Don't anger them...", "The bees are busy today. Good.",
            "Careful. They sting.", "Honey's ready. Almost.",
            "The hive is healthy.", "Smoke first. Always smoke first.",
            "They know me by now. I think.", "A good hive is a happy hive.",
            "Don't swat. Never swat.", "The queen is calm today.",
        });
    }

    // ── Hangout-specific Q&A — used when two villagers walk together ──────────
    private static final String[][] HANGOUT_TALKS = {
        {"Where are you going?", "Anywhere that isn't here. Want to come?"},
        {"I needed to get out.", "The village or... / Both."},
        {"Good timing. I was going to walk alone.", "You were always going to walk with me."},
        {"How was your day?", "Long. Shorter now."},
        {"I have been thinking.", "Is that wise? / Probably not."},
        {"Do you ever feel like we're being watched?", "By whom? / The whole forest."},
        {"Where do you want to go?", "Somewhere without a bell."},
        {"We should do this more often.", "We say that every time. / And every time we mean it."},
        {"Have you heard the news?", "Which news? There's always news. / The bad kind."},
        {"I forget how big the village is until I walk it.", "It gets bigger every raid. / No it doesn't. / You're right. It doesn't."},
        {"What are you thinking about?", "Nothing. Which is rare. Enjoy it with me."},
        {"I saw a fox today.", "Was it doing fox things? / Just watching. Judging probably."},
        {"The evenings are different when you're not working.", "They're longer. / They're better."},
        {"I ran into the nitwit earlier.", "How was that? / He waved. I waved. We went separate ways. Good meeting."},
        {"Do you remember when there were fewer of us?", "I try not to count."},
        {"The golem followed me for a bit this morning.", "He does that. / It's comforting. / It is."},
        {"I think I've been trading too much.", "Is that possible? / Today it felt possible."},
        {"Raid season will be here soon.", "We'll survive it. / We always do."},
        {"I had a dream about a village with no emeralds.", "Nightmare? / Strangely peaceful."},
        {"Something feels different tonight.", "Good different or bad different? / Ask me tomorrow."},
        {"I like walking without a destination.", "You have a destination. / Do I? / You always do."},
        {"Do you think the golem knows our names?", "I think he knows more than we give him credit for."},
        {"This is the best part of the day.", "Walking? / You. Walking is secondary."},
        {"Tell me something I don't know.", "The well runs deeper than anyone admits. / That's concerning. / Welcome to this village."},
        {"What do you actually want?", "Right now? Just this."},
        {"Do you remember who built the old watchtower?", "It fell down before I could ask. / Same."},
        {"I used to be afraid of the dark.", "And now? / Now I'm afraid of the things in it. Progress."},
        {"You've been quiet today.", "I've been thinking loudly. / About? / Still thinking."},
        {"I got a good trade today.", "Tell me. / I traded stress for this walk. / Fair exchange."},
        {"I think the village is alive.", "What do you mean? / It breathes. It grows. It survives."},
        {"How long have we known each other?", "Long enough that I can walk beside you without talking. / And yet here we are."},
    };

    private static final Map<Integer, Integer> HANGOUT_IDX = new HashMap<>();

    /**
     * Start a group conversation among 3–4 nearby villagers.
     * Picks a pre-written thread; villagers take turns in round-robin order.
     * Each bubble appears only after the previous one fully disappears.
     */
    public static void tryGroupConversation(VillagerEntity initiator, ServerWorld world) {
        long now = world.getTime();

        // Area cooldown — one group conv per area per 5 minutes
        long areaKey = campfireKey(initiator.getBlockPos());
        if (GROUP_COOLDOWN.getOrDefault(areaKey, 0L) > now) return;
        if (VILLAGER_BUBBLE.getOrDefault(initiator.getId(), 0L) > now) return;

        // Gather 2–3 additional villagers nearby (total group 3–4)
        java.util.List<VillagerEntity> candidates = world.getEntitiesByClass(
                VillagerEntity.class, initiator.getBoundingBox().expand(6.0),
                v -> v != initiator && v.isAlive() && !v.isBaby() && !v.isSleeping()
                        && !GROUP_FROZEN.containsKey(v.getId())
                        && VILLAGER_BUBBLE.getOrDefault(v.getId(), 0L) <= now
                        && getConversationPartner(v.getId(), world, now) == null);

        if (candidates.isEmpty()) return;

        int groupSize = Math.min(4, 1 + candidates.size());
        java.util.List<VillagerEntity> group = new java.util.ArrayList<>();
        group.add(initiator);
        for (int i = 0; i < groupSize - 1; i++) group.add(candidates.get(i));

        // Pick a random thread
        String[] thread = GROUP_THREADS[world.random.nextInt(GROUP_THREADS.length)];
        int lineCount = Math.min(thread.length, 4 + world.random.nextInt(2)); // 4–5 lines max

        // Schedule lines sequentially — each fires after the previous bubble expires
        int duration = 130;
        int gap      = 15;
        long tick    = now;

        for (int i = 0; i < lineCount; i++) {
            VillagerEntity speaker = group.get(i % group.size());
            int textIdx = GROUP_LINE_TEXTS.size();
            GROUP_LINE_TEXTS.add(thread[i]);
            GROUP_PENDING_LINES.add(new long[]{ tick, speaker.getId(), textIdx });
            tick += duration + gap;
        }

        // Freeze all participants until last line expires
        long endTick = tick + 20L;
        for (VillagerEntity v : group) GROUP_FROZEN.put(v.getId(), endTick);
        GROUP_COOLDOWN.put(areaKey, endTick + 3000L); // 2.5 min area cooldown
    }

    /** Trigger a hangout-specific Q&A between two walking villagers. */
    public static void tryHangoutTalk(VillagerEntity speaker, VillagerEntity listener, ServerWorld world) {
        // Must be close and both idle before starting a conversation
        if (speaker.squaredDistanceTo(listener) > 9.0) return; // > 3 blocks apart
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(speaker.getId(), 0L) > now) return;
        if (VILLAGER_BUBBLE.getOrDefault(listener.getId(), 0L) > now) return;

        int idx = nextIdx(HANGOUT_IDX, speaker.getId(), HANGOUT_TALKS.length);
        int duration = 130;
        long gap = 25L;

        // Stop both and face each other for the conversation
        speaker.getNavigation().stop();
        listener.getNavigation().stop();
        speaker.getBrain().forget(MemoryModuleType.WALK_TARGET);
        listener.getBrain().forget(MemoryModuleType.WALK_TARGET);
        speaker.getLookControl().lookAt(listener.getX(), listener.getEyeY(), listener.getZ());
        listener.getLookControl().lookAt(speaker.getX(), speaker.getEyeY(), speaker.getZ());

        spawnBubble(speaker, HANGOUT_TALKS[idx][0], duration, world, now);
        // poolFlag=3 tells the listener-response step to use HANGOUT_TALKS
        PENDING.add(new long[]{ now + duration + gap, listener.getId(), 3, idx, duration });

        // Add 60 extra ticks so the freeze outlasts the bubble + name-tag restore
        long faceEnd = now + duration + gap + duration + 60L;
        CONVERSATIONS.put(speaker.getId(),  new long[]{ listener.getId(), faceEnd });
        CONVERSATIONS.put(listener.getId(), new long[]{ speaker.getId(),  faceEnd });
    }

    // ── Nitwit lines ──────────────────────────────────────────────────────────
    static {
        PROF_SOLO.put(net.minecraft.village.VillagerProfession.NITWIT, new String[] {
            "...", "Hmm.", "Yes.", "Oh.",
            "I am here.", "Good.", "Okay.",
            "...........", "Yep.", "Sure.",
            "I am doing something.", "This is a place.",
            "I was just... yes.", "Indeed.",
        });
        PROF_TALKS.put(net.minecraft.village.VillagerProfession.NITWIT, new String[][] {
            {"What do you do all day?", "Things."},
            {"Do you have a job?", "I have... purposes."},
            {"Are you okay?", "Yes. Why."},
            {"What are you thinking about?", "..."},
            {"You seem lost.", "I know where I am. / I think."},
        });
    }

    /** Returns a random work-context line for the given profession, or null if none exists. */
    public static String getWorkLine(VillagerProfession prof, net.minecraft.util.math.random.Random rng) {
        String[] lines = PROF_WORK.get(prof);
        if (lines != null && lines.length > 0) return lines[rng.nextInt(lines.length)];
        // Fallback: check by registry name for mod professions
        String profName = net.minecraft.registry.Registries.VILLAGER_PROFESSION
                .getId(prof) != null
                ? net.minecraft.registry.Registries.VILLAGER_PROFESSION.getId(prof).getPath()
                : null;
        if (profName != null) {
            String[] namedLines = PROF_WORK_NAMED.get(profName);
            if (namedLines != null && namedLines.length > 0)
                return namedLines[rng.nextInt(namedLines.length)];
        }
        return null;
    }

    static {
        // FARMER
        PROF_TALKS.put(VillagerProfession.FARMER, new String[][] {
            {"I harvested 64 wheat today.", "You could stop at some point."},
            {"The crows got to my carrots again.", "Did you try a scarecrow?"},
            {"I think the wheat is sentient.", "It grows when you are not looking."},
            {"I planted beets today.", "Already told everyone."},
            {"My potatoes keep disappearing.", "Did you check Steve's inventory?"},
            {"Rain is good for the crops.", "Rain is good for nothing else."},
            {"I found a pumpkin the size of my house.", "What did you do with it?"},
            {"The soil is tired today.", "You or the soil?"},
            {"I tried composting today.", "How did it go?"},
            {"The wheat grew overnight.", "You're welcome. I sang to it."},
            {"A bee helped me today.", "Did you thank it?"},
            {"My melon farm keeps getting raided.", "By pillagers? / By Steve."},
            {"I grew a golden carrot by accident.", "How? / I had a lot of gold."},
            {"The frost killed my crops.", "Again? / Every single year."},
            {"Someone stole from my farm.", "What did you do? / I moved the farm."},
            {"I think my hoe is enchanted.", "Did you enchant it? / No. That is the problem."},
            {"I planted flowers between the wheat rows.", "Why? / For the bees. And me."},
            {"My scarecrow scared me this morning.", "Was it the face? / It waved."},
            {"I found a skeleton in my field.", "Farming hazard. / I planted around it."},
            {"The pumpkins are ready.", "Finally. / They were ready last week. I was not."},
        });
        PROF_SOLO.put(VillagerProfession.FARMER, new String[] {
            "The crops do not grow as fast as they used to.",
            "I planted beets. Do not tell anyone.",
            "64 wheat. Again. Every single day.",
            "The soil looks tired. I understand.",
            "Rain tomorrow. I can feel it in the carrots.",
            "I talk to the crops. They do not talk back. Progress.",
            "Good harvest today. I will not jinx it by saying more.",
            "Something ate my beetroot. Something with emeralds.",
            "The composter smells like home now.",
            "Sun, soil, seeds. Simple. I like simple.",
            "My field at sunset is the best part of this job.",
            "I named the largest pumpkin Gerald.",
            "The cows keep wandering into the melon patch.",
            "I counted my seeds three times. Lost count twice.",
        });

        // FISHERMAN
        PROF_TALKS.put(VillagerProfession.FISHERMAN, new String[][] {
            {"I caught nothing again today.", "Third week in a row."},
            {"I think I caught a boot.", "Was it a good boot?"},
            {"The fish know when I am coming.", "I wish I knew how."},
            {"My rod broke. Again.", "How does that keep happening?"},
            {"I may have fished up a saddle.", "From where?"},
            {"The river is lower than usual.", "Did you check Steve's bucket?"},
            {"I once caught a fish that looked at me.", "What kind of look?"},
            {"Fishing teaches patience.", "Does it work?"},
            {"I had a good spot and then it rained.", "Rain improves fishing. / Not for me."},
            {"The water was beautiful today.", "Did you catch anything? / I forgot to fish."},
            {"I caught a salmon the size of my arm.", "What did you do with it? / Held it for a while."},
            {"I think there are no fish left.", "Did you check deeper? / I don't go deeper."},
            {"The bobber went under and I panicked.", "Did you reel it in? / I dropped the rod."},
            {"I gave a fish to the golem.", "What did he do? / Stared at it for an hour."},
            {"My best catch ever was a name tag.", "What did it say? / Steve."},
            {"I fish here every day.", "Any luck? / Luck has nothing to do with it. / So no luck. / Correct."},
            {"The barrel is full of cod.", "Great. / I hate cod."},
            {"I caught an enchanted rod today.", "In the water? / Apparently."},
        });
        PROF_SOLO.put(VillagerProfession.FISHERMAN, new String[] {
            "The fish were uncooperative today.",
            "I stood in rain for 4 hours. Caught a stick.",
            "The fish know. They always know.",
            "Fishing is patience. I have none.",
            "The water was calm today. I was not.",
            "I fished up a name tag once. It said Steve.",
            "Watching the bobber is the only rest I get.",
            "The river gives what it wants. Today it gave nothing.",
            "Someday I will catch something worth bragging about.",
            "The line was taut. Then it wasn't. I don't want to talk about it.",
            "Clear skies and a good current. Perfect day to catch nothing.",
            "I think the fish have meetings about me.",
            "My barrel smells like success. Or cod. Same thing.",
        });

        // LIBRARIAN
        PROF_TALKS.put(VillagerProfession.LIBRARIAN, new String[][] {
            {"I have read every book in the village.", "We need more books."},
            {"A creeper destroyed half my collection.", "The important ones, I assume."},
            {"Knowledge is priceless.", "You charge for it though."},
            {"I wrote a book today.", "About writing books?"},
            {"Someone returned a book with pages missing.", "The ones with answers."},
            {"A child asked me what enchanting does.", "What did you say?"},
            {"I found an error in a book.", "What kind of error?"},
            {"The shelves are full again.", "Good. Make more shelves."},
            {"Someone wants Efficiency V.", "Already? / They had Efficiency IV yesterday."},
            {"I enchanted a book that scared me.", "What was on it? / I didn't read it."},
            {"The lectern speaks to me sometimes.", "It doesn't have a mouth. / Exactly."},
            {"Reading by firelight is dangerous.", "For your eyes? / For my attention span."},
            {"I have a book about everything except raids.", "Write one. / I'm on chapter one."},
            {"A child borrowed my rarest book.", "Did they return it? / They are using it as a sled."},
            {"I tried to organize by topic.", "And? / They reorganized themselves. Overnight."},
            {"I found a book in a chest that doesn't exist.", "What was inside? / More books."},
            {"The enchanting table hums at night.", "Does it bother you? / Only when it stops."},
            {"I lost my glasses. Found them in a book.", "How? / I was using them as a bookmark."},
        });
        PROF_SOLO.put(VillagerProfession.LIBRARIAN, new String[] {
            "I should write a book about this campfire.",
            "The flames remind me of chapter seven.",
            "50 bookshelves and still not enough.",
            "Knowledge is power. Power costs emeralds.",
            "I have read everything twice. Starting again.",
            "A good book is worth more than a raid. Almost.",
            "The enchanting table and I have an understanding.",
            "I shelved 40 books today. Unshelved 41.",
            "Someone dog-eared a page. I am still not over it.",
            "The best enchantment I have is quiet. Very rare.",
            "Books don't run away. I appreciate that.",
            "I read until the fire went out. Found more wood.",
        });

        // ARMORER
        PROF_TALKS.put(VillagerProfession.ARMORER, new String[][] {
            {"I forged the finest armor today.", "Did anyone buy it? / For 2 emeralds. Yes."},
            {"Iron prices are rising again.", "Everything rises except my sales."},
            {"Someone wants leather armor.", "From the armorer? / I said the same thing."},
            {"A helmet came back dented.", "From battle? / From Steve sitting on it."},
            {"I made a full set of iron armor.", "Who bought it? / Nobody. It's on my wall."},
            {"The blast furnace makes everything faster.", "And louder. / Worth it."},
            {"Someone wants diamond armor.", "Do you have diamonds? / They assumed I did."},
            {"My best chestplate went for 5 emeralds.", "That's it? / That was the negotiated price. I started at 4."},
            {"I tried to make armor for the golem.", "Did it fit? / He refused to try it on."},
            {"Someone brought back my armor after a raid.", "In how many pieces? / Technically still armor."},
        });
        PROF_SOLO.put(VillagerProfession.ARMORER, new String[] {
            "Forged 12 chestplates today. Sold one.",
            "The anvil sounds like music to me.",
            "Iron doesn't lie. People do.",
            "Good armor. Nobody appreciates good armor.",
            "One day someone will pay what it's worth.",
            "The heat from the furnace feels like a reward.",
            "A well-made helmet. Nobody looks inside a well-made helmet.",
            "I test every plate myself. My arms are very tired.",
        });

        // WEAPONSMITH
        PROF_TALKS.put(VillagerProfession.WEAPONSMITH, new String[][] {
            {"I forged a sword that could end wars.", "Did it? / I sold it for 3 emeralds."},
            {"Someone wanted a wooden sword.", "How old are they? / Old enough to know better."},
            {"The swords sell faster than I can make them.", "Raiders? / Steve. Definitely Steve."},
            {"I made an axe so sharp it scares me.", "What did you do? / Sold it. Obviously."},
            {"I named a blade once.", "What happened to it? / It was bought by the nitwit."},
            {"Someone wants a sword with Looting III.", "Can you make that? / I can make the sword."},
            {"A good sword should feel like part of your arm.", "How would you know? / I make the arms."},
            {"My best work gets traded away.", "That's the job. / The job is wrong."},
            {"I sharpened the same blade three times today.", "For whom? / The same person. Three times."},
            {"Someone returned a sword broken in half.", "In battle? / Against a stone wall. Horizontally."},
        });
        PROF_SOLO.put(VillagerProfession.WEAPONSMITH, new String[] {
            "Every sword has a story. This one has several.",
            "I made something beautiful today and sold it for nothing.",
            "Steel doesn't argue back. I prefer steel.",
            "One day I'll keep one of my own swords.",
            "The grindstone and I have spent a lot of time together.",
            "A blade that fits the hand perfectly. Three emeralds.",
            "I hear battles in my sleep now. Just the metal.",
            "My finest work left this morning. For seven emeralds and a fish.",
        });

        // TOOLSMITH
        PROF_TALKS.put(VillagerProfession.TOOLSMITH, new String[][] {
            {"I made the perfect pickaxe today.", "Did Steve take it? / Within minutes."},
            {"A shovel came back broken.", "How? / Do not ask."},
            {"Tools should last forever.", "Correct. They don't."},
            {"Someone wants an enchanted hoe.", "Is that a threat? / Just a trade."},
            {"I made a pickaxe with Fortune III.", "How much did it sell for? / Not enough."},
            {"A hammer came back with gem fragments on it.", "Where was it used? / I don't want to know."},
            {"Someone wants a tool for every job.", "Make more? / I told them that. They agreed."},
            {"My tools are better than any Steve makes.", "Has Steve made tools? / He made one. Once."},
            {"The smithing table is my favorite thing.", "More than the grindstone? / It doesn't argue."},
            {"I tested every axe by chopping something.", "What? / Nothing I'm admitting to."},
        });
        PROF_SOLO.put(VillagerProfession.TOOLSMITH, new String[] {
            "The right tool makes everything easier. I still have the wrong tools.",
            "Crafted a perfect hoe today. Nobody noticed.",
            "Tools outlast their owners. Usually.",
            "Iron ore is getting harder to find. Ironic.",
            "There is no problem a good pickaxe cannot make worse.",
            "My hands know the shape of the handles now without looking.",
            "I fix things for people who break things. Good business.",
        });

        // CLERIC
        PROF_TALKS.put(VillagerProfession.CLERIC, new String[][] {
            {"I brewed a weakness potion today.", "For whom? / Myself. Long story."},
            {"The undead don't fear me anymore.", "Why not? / We've been through too much."},
            {"I traded a zombie villager today.", "How did it go? / Awkward. Very awkward."},
            {"Someone wants a regeneration potion.", "What did they do? / I didn't ask."},
            // Healing / curing context
            {"I cured a zombie villager this morning.", "Did it work? / Eventually. After screaming."},
            {"The golden apple supply is getting low.", "For cures? / I'm trying not to think about it."},
            {"Another villager was converted last night.", "Did you cure them? / They are fine now. Mostly."},
            {"I gave a regeneration potion to the golem.", "What happened? / He looked confused. Then fine."},
            {"Someone needed splash healing today.", "Were they hurt badly? / They fell off a block."},
            {"The smell after a zombie cure never leaves.", "How long does it linger? / Ask me in a week."},
            {"A zombie nearly bit me during a cure.", "What did you do? / I backed up and said some words."},
            {"I keep potions for the worst nights.", "Do those nights come? / They always come."},
            {"I healed someone who didn't say thank you.", "What did you do? / Saved the potion. Next time."},
            {"Someone asked me to cure their friend.", "Was the friend a zombie? / That's what I asked."},
            {"I ran out of nether wart.", "What happens then? / People stay unhealed. I don't like it."},
            {"The brewing stand hums when I'm not looking.", "Is it a good hum? / It's a purposeful hum."},
            {"Someone brought me a zombie they claimed was their uncle.", "Was it? / The eyes said yes."},
            {"I carry healing potions everywhere now.", "Why? / Because raids don't schedule themselves."},
        });
        PROF_SOLO.put(VillagerProfession.CLERIC, new String[] {
            "The nether wart grows well this season.",
            "Potions of weakness. The irony is not lost on me.",
            "I see the undead too clearly. Occupational hazard.",
            "Fire and I have an understanding.",
            "Three cures this week. Four next week if the moon is wrong.",
            "The golden apple costs too much. The alternative costs more.",
            "I brewed through the night. Everyone is alive. For now.",
            "Healing takes time. I do not always have time.",
            "The screaming during a cure always surprises me. Even now.",
            "I keep a potion of regeneration for myself. Just in case.",
            "Another soul returned from the undead today. A good day.",
            "The brewing stand is always warm. It comforts me.",
            "Sometimes I think the undead are just confused. Then they bite.",
            "My job is to bring people back. Nobody thanks me for it. I don't mind.",
        });

        // BUTCHER
        PROF_TALKS.put(VillagerProfession.BUTCHER, new String[][] {
            {"A cow looked at me today.", "How? / Knowingly."},
            {"I ran out of beef again.", "Who keeps buying it? / Steve. Probably Steve."},
            {"The chickens are organizing.", "What do you mean? / They face the same direction now."},
            {"Someone wanted raw rabbit.", "Did you have it? / I had questions first."},
            {"The smoker was going all day.", "Good business? / Good smell. The business was fine."},
            {"I smoked the best porkchop of my life today.", "Who bought it? / I ate it. Sorry."},
            {"A pig followed me home again.", "Third time this week? / Fourth."},
            {"My knives are sharper than the weaponsmith's.", "Does he know? / He does now."},
            {"Someone wanted rabbit stew.", "Did you have rabbits? / I had stew. They walked away."},
            {"The golem watched me work all morning.", "Did he say anything? / He nodded once."},
        });
        PROF_SOLO.put(VillagerProfession.BUTCHER, new String[] {
            "A pig followed me home. I let it.",
            "The animals look at me differently now.",
            "Smoked meat smells better over a campfire.",
            "A cow blinked at me. I blinked back.",
            "The smoker runs all night. I find it comforting.",
            "Good cut, clean work. That's all I ask.",
            "Someone said I was good at my job today. I appreciated it.",
            "The smell of smoked porkchop follows me everywhere. I don't mind.",
        });

        // SHEPHERD
        PROF_TALKS.put(VillagerProfession.SHEPHERD, new String[][] {
            {"I need more wool.", "How much do you have? / Not enough. Never enough."},
            {"A sheep followed me home.", "Did you keep it? / It has its own bed now."},
            {"I dyed a sheep purple today.", "Why? / I had purple dye."},
            {"The sheep escaped again.", "All of them? / Just the clever ones."},
            {"I ran out of red dye.", "That's the most popular color? / By far."},
            {"I made a carpet pattern no one has seen before.", "Who asked for it? / Nobody yet."},
            {"The loom jammed again.", "Did you fix it? / I kicked it. It works now."},
            {"A sheep walked into my bedroom at night.", "What did you do? / We talked about it."},
            {"I counted wool until I fell asleep.", "How high did you get? / 128. Then morning."},
            {"Someone wants a full carpet in every color.", "Every color? / They said it is for a boat."},
        });
        PROF_SOLO.put(VillagerProfession.SHEPHERD, new String[] {
            "Wool. Always more wool.",
            "The sheep are judging me by the fire tonight.",
            "I counted 47 sheep to sleep. Then I lost count.",
            "Carpets are underrated. I make carpets.",
            "A sheep named itself. I did not argue.",
            "The loom at night sounds like breathing.",
            "Colorful wool. It is the small joys.",
            "My flock is small but opinionated.",
        });

        // FLETCHER
        PROF_TALKS.put(VillagerProfession.FLETCHER, new String[][] {
            {"Someone ordered 64 arrows.", "Every day. Same person."},
            {"I'm running out of feathers.", "Chickens? / They know."},
            {"I made a crossbow today.", "It fired backwards. Once."},
            {"Arrows are faster than emeralds.", "What does that mean? / I'm still figuring it out."},
            {"I balanced an arrow on one finger today.", "For how long? / Long enough."},
            {"My best crossbow sold for 4 emeralds.", "You okay? / I will be."},
            {"Someone wanted tipped arrows.", "With what? / They didn't specify. That worried me."},
            {"I made 200 arrows before noon.", "Who ordered that many? / Nobody. I got carried away."},
            {"A child tried to buy one arrow.", "Did you sell it? / I sold a full stack. They were determined."},
            {"The feathers were wet today.", "From rain? / From a very startled chicken."},
        });
        PROF_SOLO.put(VillagerProfession.FLETCHER, new String[] {
            "64 arrows a day. Where do they all go.",
            "Feathers are harder to find than you'd think.",
            "A fletcher's work is never done. Literally.",
            "I aim for quality. Sometimes I miss.",
            "A straight arrow is its own reward.",
            "I count feathers in my sleep now.",
            "The best shot I never took was with my own crossbow.",
            "Arrows that fly true make me unreasonably proud.",
        });

        // CARTOGRAPHER
        PROF_TALKS.put(VillagerProfession.CARTOGRAPHER, new String[][] {
            {"I mapped the whole village today.", "How big is it? / Smaller than I thought."},
            {"I keep finding places that shouldn't exist.", "What do you do? / I map them anyway."},
            {"I made a treasure map.", "Where does it lead? / I forgot."},
            {"The world is bigger than the map.", "Reassuring or terrifying? / Yes."},
            {"I found a structure not on any map.", "What was it? / It was a map. Of more structures."},
            {"My quill broke mid-map.", "What did you do? / Used a fish bone. Don't tell anyone."},
            {"Someone bought a map that leads here.", "To the village? / To this exact campfire."},
            {"I charted the river today.", "It moved. / Yes. That happens more than you'd think."},
            {"The ocean monument is smaller than I drew.", "Did you fix the map? / I fixed the monument. In my head."},
            {"A traveler told me about a city under the sea.", "Did you map it? / I mapped the idea of it."},
        });
        PROF_SOLO.put(VillagerProfession.CARTOGRAPHER, new String[] {
            "There are places on my map that don't exist yet.",
            "I mapped this campfire. Scale: too small.",
            "Every path leads somewhere. Eventually.",
            "The edge of the map is not the edge of the world. Probably.",
            "I drew a road today that nobody walks yet.",
            "North is always north. That is the most reliable thing I know.",
            "The blank spaces on my maps are the most honest parts.",
            "I have mapped 14 villages. This one remains the strangest.",
        });

        // LEATHERWORKER
        PROF_TALKS.put(VillagerProfession.LEATHERWORKER, new String[][] {
            {"Leather prices are impossible.", "What happened? / Steve. As usual."},
            {"Someone wants all colors of leather armor.", "Simultaneously? / That was my question."},
            {"I made saddles all day.", "Anyone need one? / Not one person."},
            {"The smell of leather never leaves.", "Is that good? / I don't know anymore."},
            {"I dyed a helmet seventeen times.", "Why? / The customer kept changing their mind."},
            {"A horse came to me directly.", "To buy? / To model. I think."},
            {"I tanned hide all morning.", "How does it feel? / Like morning. But louder."},
            {"Someone wanted a blue tunic.", "Do you make tunics? / I made a start."},
            {"The cauldron ran out of dye mid-project.", "What color were you making? / The perfect color. Gone."},
            {"Leather armor saved someone in a raid.", "Who said that? / They did. While buying more."},
        });
        PROF_SOLO.put(VillagerProfession.LEATHERWORKER, new String[] {
            "Leather dyes better by firelight.",
            "I made 12 saddles today. Not one horse in the village.",
            "The smell follows me home every night.",
            "A good hide is worth more than people know.",
            "Soft leather, clean cut. That's the job.",
            "The cauldron bubbles all day. I find it peaceful.",
            "I tried a new dye today. It matched something I saw in a dream.",
            "Nobody notices the craft. They notice the color. Fair enough.",
        });

        // MASON
        PROF_TALKS.put(VillagerProfession.MASON, new String[][] {
            {"I cut 200 stones today.", "It's never enough."},
            {"Someone wants stone bricks.", "How many? / All of them."},
            {"The quarry is deeper than expected.", "How deep? / Still going."},
            {"Stone doesn't argue. I respect that.", "Unlike who? / Everyone."},
            {"I found a fossil in the stone today.", "What did you do with it? / Cut it. Then felt bad."},
            {"The chiseling took all morning.", "What were you making? / A mistake I am fixing."},
            {"Someone wants polished andesite.", "All of it? / They are specific people."},
            {"I built a wall in an hour.", "Is that fast? / For stone. Yes."},
            {"My best work is underground.", "Why underground? / Nobody argues with underground."},
            {"The quarry flooded overnight.", "What did you do? / I made a waterfall feature. Intentional."},
        });
        PROF_SOLO.put(VillagerProfession.MASON, new String[] {
            "200 stones. Still not enough.",
            "The quarry goes deeper every time I look.",
            "Stone is honest. It doesn't change.",
            "I like things that don't move. I am a mason.",
            "A perfectly cut brick is as close to peace as I get.",
            "The chisel and I have worked out our differences.",
            "Stone remembers the shape you give it. I like that.",
            "My hands are rough. My work is not.",
        });
    }

    // ── State tracking ─────────────────────────────────────────────────────────
    // ── Trade reaction lines ──────────────────────────────────────────────────
    private static final String[] TRADE_CELEBRATE = {
        "Thank you!", "Come back anytime!", "A pleasure!", "Excellent choice!",
        "Always happy to trade!", "My finest work!", "Pleasure doing business!",
        "You have good taste!", "Business is good today!", "A fine transaction!",
        "Thank you, traveller!", "Wonderful!", "I hope you enjoy it!",
        "Tell your friends!", "Best customer today.", "Made my day!",
    };
    private static final String[] TRADE_RELUCTANT = {
        "I suppose...", "Hmm. Fine.", "Very well.", "If you insist.",
        "This barely covers my costs.", "You drive a hard bargain.",
        "Well... alright.", "I... suppose that works.",
        "My generosity will be the end of me.", "Fine. Take it.",
        "I won't be doing that again.", "Next time it costs more.",
    };
    private static final String[] TRADE_FUNNY = {
        "I can't believe I just did that.", "My accountant will hear about this.",
        "Please don't come back. ...Actually, do come back.",
        "The emerald told me to say yes.", "I immediately regret this.",
        "Tell no one.", "This never happened.",
    };

    // ── Panic phrases (generic) ───────────────────────────────────────────────
    private static final String[] PANIC_LINES = {
        "RUN!", "NOT AGAIN.", "WHY.", "I JUST SAT DOWN.",
        "EVERY TIME.", "OH NO OH NO OH NO", "WHAT IS THAT.",
        "I QUIT.", "WHERE IS THE GOLEM.", "SOMEONE HELP.",
        "THEY FOUND ME.", "I KNEW IT.", "MY EMERALDS!",
        "NOT THE WHEAT!", "THIS IS WHY I HAVE TRUST ISSUES.",
        "AGAIN?!", "IT'S FINE. IT'S NOT FINE.", "NOT TODAY.",
        "I JUST REBUILT THAT WALL.", "ABSOLUTELY NOT.",
    };

    // ── Raid-specific panic phrases ────────────────────────────────────────────
    private static final String[] RAID_PANIC_LINES = {
        "RAID!", "PILLAGERS!", "THEY'RE HERE!", "HELP US!",
        "NOT A RAID AGAIN!", "THE BANNER! THEY HAVE THE BANNER!",
        "EVERYONE RUN!", "CALL THE GOLEM!", "WE'RE UNDER ATTACK!",
        "I KNEW THIS WOULD HAPPEN.", "NOT TODAY NOT TODAY NOT TODAY.",
        "WHERE DO I GO?!", "PROTECT THE CHILDREN!", "BARRICADE THE DOORS!",
        "THIS IS THE THIRD ONE THIS WEEK.", "I HATE PILLAGERS.",
        "SOMEONE DO SOMETHING!", "MY HOUSE AGAIN?!",
    };

    // ── Raid victory celebration phrases ──────────────────────────────────────
    public static final String[] RAID_VICTORY_LINES = {
        "We did it!", "They're gone!", "WE WON!", "Finally...",
        "Is it over? It's over!", "Thank goodness.", "WE SURVIVED!",
        "The village stands!", "Never doubted us.", "Ha! Take that!",
        "That was too close.", "Someone rebuild the fence. Again.",
        "I need to sit down.", "We defended our home!", "FOR THE VILLAGE!",
    };

    // ── Raid loss / mourning phrases ──────────────────────────────────────────
    public static final String[] RAID_LOSS_LINES = {
        "It's over...", "We lost.", "They won.",
        "I knew we couldn't hold them.", "What do we do now?",
        "Everything is gone.", "Someone get help...", "Not again...",
    };

    // villager net ID → tick their panic bubble expires (prevents spam)
    private static final Map<Integer, Long> PANIC_BUBBLE = new HashMap<>();

    // Per-villager cycling index so lines don't repeat until the full pool is used
    private static final Map<Integer, Integer> SOLO_IDX     = new HashMap<>();
    private static final Map<Integer, Integer> ONE_SIDED_IDX = new HashMap<>();
    private static final Map<Integer, Integer> TALKS_IDX    = new HashMap<>();

    // Separate cooldown for work-context lines so they're never blocked by ambient/greetings
    private static final Map<Integer, Long> WORK_BUBBLE = new HashMap<>();

    /**
     * General chatter — say a solo or one-sided line while wandering.
     * Prefers profession-specific lines; skips if a bubble is already active.
     */
    public static void tryChatter(VillagerEntity villager, ServerWorld world) {
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(villager.getId(), 0L) > now) return;

        // Babies always use their own age-appropriate lines
        if (villager.isBaby()) {
            int idx = nextIdx(SOLO_IDX, villager.getId(), BABY_CHATTER.length);
            spawnBubble(villager, BABY_CHATTER[idx], 80, world, now);
            return;
        }

        VillagerProfession prof = villager.getVillagerData().getProfession();
        String line;
        if (world.random.nextFloat() < 0.6f) {
            String[] soloPool = PROF_SOLO.getOrDefault(prof, new String[0]);
            if (soloPool.length == 0) soloPool = cfgSolo();
            int idx = nextIdx(SOLO_IDX, villager.getId(), soloPool.length);
            line = soloPool[idx];
        } else {
            String[] ons = cfgOneSided();
            int idx = nextIdx(ONE_SIDED_IDX, villager.getId(), ons.length);
            line = ons[idx];
        }
        spawnBubble(villager, line, 80, world, now);
    }

    /** Show a work-context line — uses its own cooldown, ignores the general bubble cooldown. */
    public static void spawnWorkSpeech(VillagerEntity villager, String text, ServerWorld world) {
        long now = world.getTime();
        if (WORK_BUBBLE.getOrDefault(villager.getId(), 0L) > now) return;
        WORK_BUBBLE.put(villager.getId(), now + 1800L); // 90s before next work line
        spawnBubble(villager, text, 120, world, now); // slightly longer display
    }

    /** Advance a cycling index through a pool, returning the next entry.
     *  On first use, starts at a random offset so villagers don't all say the same line. */
    private static int nextIdx(Map<Integer, Integer> map, int villagerNetId, int poolSize) {
        int current = map.getOrDefault(villagerNetId, Integer.MIN_VALUE);
        int idx;
        if (current == Integer.MIN_VALUE) {
            // First use — scatter starting position using villager ID as seed
            idx = (int)(Math.abs(villagerNetId * 2654435761L) % poolSize);
        } else {
            idx = (current + 1) % poolSize;
        }
        map.put(villagerNetId, idx);
        return idx;
    }

    // ── Campfire one-sided remarks (said to the group, no reply expected) ───────
    private static final String[] ONE_SIDED = {
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
        "I once saw Steve eat a raw potato. We do not talk about it.",
        "Three raids this month. I am starting to take it personally.",
        "My trades are fair. The emerald disagrees.",
        "The golem is staring at the moon again. He does that.",
        "I counted the torches in the village today. Too few.",
        "Someone needs to fix that fence. Not me though.",
        "A bat landed on my shoulder this morning. We had a moment.",
        "I once got lost leaving my own house. The fog was thick.",
        "The raid horn sounds different when you hear it alone.",
        "I have rebuilt this house four times. I know every wall.",
        "Funny how the fire looks smaller when you have problems.",
        "My neighbour planted a cactus facing my door. I respect it.",
        "I still have not found my other boot. This is year two.",
        "Sometimes a good fire is the only meeting that matters.",
        // More one-sided remarks
        "I once found a chest with nothing in it. Somehow that was worse than no chest.",
        "My bed moved three inches to the left last week. Nobody admits it.",
        "The nitwit has been waving at the same tree for a week. The tree hasn't waved back. Yet.",
        "A pillager made eye contact with me before the raid. Very personal.",
        "I made a deal once where I came out ahead. I've been suspicious ever since.",
        "The well makes a different sound at night. I have stopped listening.",
        "Someone rearranged my tools while I was working. I rearranged them back. War.",
        "I keep a list of things I said wrong. It's a long list.",
        "The torch by my door has been burning for two years. Not once has it gone out. Suspicious.",
        "A chicken followed me into my house last week. I let it. We needed the company.",
        "I named a cloud today. Gerald. He's gone now. Gone too soon.",
        "My inventory has been full for three weeks. I just keep holding things.",
        "I trained for a raid that never came. Felt ready. Then annoyed. Then ready again.",
        "I have seventeen emeralds and no idea what I'm saving them for.",
        "A drowned waved at me from underwater. I chose to believe it was friendly.",
        "My door creaks differently depending on the time of day. I've started charting it.",
        "I saw a sheep jump over a fence once. Just once. It never did it again. Neither did I.",
        "The stars spell something out there. I haven't figured it out. I'm scared to.",
        "I repaired something today that I knew I'd break again tomorrow. Progress.",
        "Someone wrote 'thank you' in the dirt outside my house. Nobody admits it. I'm keeping it.",
        "I dropped my best item in the dark once. Found it three days later. Never spoke of it again.",
        "The golem sneezed yesterday. I'm choosing to believe that's normal.",
        "I counted everyone in the village. Then I counted again. Different number. I'm ignoring this.",
        "I gave someone directions once and they went the right way. My finest moment.",
        "A bat flew into my house every night for a week. I think it lives there now. We don't talk.",
    };

    // ── Freeroam conversation lines (quick passing exchanges) ─────────────────
    // poolFlag = 2 in PENDING
    private static final String[][] ROAM_TALKS = {
        {"Beautiful day.", "I suppose."},
        {"Have you heard any news?", "Only bad ones."},
        {"Busy today?", "When am I not?"},
        {"Did you sleep well?", "Better than usual."},
        {"Watch where you're going!", "You walked into me."},
        {"How's the family?", "Existing. Loudly."},
        {"Seen anything unusual?", "Define unusual."},
        {"Trade is slow today.", "Trade is always slow until it isn't."},
        {"Good to see you.", "Likewise. Genuinely."},
        {"The golem is acting up again.", "It stood there. Aggressively."},
        {"Any raids coming?", "There's always a raid coming."},
        {"Nice day for a walk.", "Every day is the same walk. Still nice though."},
        {"Long day.", "They all are. This one more than most."},
        {"Did you hear that?", "I hear everything. I choose what to acknowledge."},
        {"Have you eaten?", "I forget what eating is for leisure."},
        {"Something is moving in the treeline.", "I saw it too. Still watching."},
        {"Where are you headed?", "Somewhere. Hopefully better than here."},
        {"You look tired.", "I am tired. I've been tired since the third raid."},
        {"Have you seen the nitwit lately?", "He waved at a fence post this morning. Confidently."},
        {"I lost my trade list.", "Was it important? / Everything I do is important."},
        {"Do you trust the new arrival?", "I trust nobody new until the second raid."},
        {"Did you hear about the golem last night?", "He moved a flower. Deliberately."},
        {"Someone left their door open.", "Which one? / The one facing the forest."},
        {"I need to talk to someone.", "Here I am. / That's what I was afraid of."},
        {"Good morning.", "Is it? / Not yet. But it could be."},
        {"Have you traded today?", "Three times. Once well."},
        {"Something smells like rain.", "That's me. I was outside. / That tracks."},
        {"The path was muddy this morning.", "It's always muddy. / This was different muddy."},
        {"I fixed my fence.", "The raiders will appreciate that. / That is not funny. / I know."},
        {"Do you think we'll get a new neighbour?", "Someone always moves in after a raid."},
        {"What did you have for breakfast?", "Nothing. I keep forgetting breakfast exists."},
        {"The golem looked at me.", "What kind of look? / A knowing look. / That's troubling."},
        {"Is it just me or is it louder today?", "It's just you. / Then why does everyone look nervous?"},
        {"I traded badly this morning.", "How badly? / I don't want to discuss it."},
        {"Have you spoken to the new arrival?", "Briefly. They have opinions. / Of course they do."},
        {"Something fell in the night.", "What was it? / I'm going to say it was nothing."},
        {"You're heading the right way.", "Am I? / For once, yes."},
        {"I ran out of materials again.", "Already? / I didn't have many to start."},
        {"The path splits here.", "Which way are you going? / The one that doesn't end in a hole."},
        {"Did you see the lightning last night?", "I saw it. I heard it. I didn't enjoy it."},
        {"Can I ask you something?", "You already are. / Fair. Then yes."},
        {"I need to be somewhere.", "We all do. That's the problem."},
        {"How do you stay so calm?", "Practice. Also ignorance. Mostly ignorance."},
        {"Something is following me.", "Probably just your shadow. / My shadow doesn't breathe."},
        {"I almost got lost today.", "Almost? / The path found me again. Embarrassing for both of us."},
        {"It's going to be a long day.", "When isn't it? / Today I know it for a fact."},
        {"I had a plan this morning.", "What happened? / It met reality. They didn't get along."},
        {"Strange morning.", "Strange is generous. / Yes. Too generous."},
        {"Do you ever get the feeling something is about to happen?", "Constantly. That's just living here."},
        {"I saw something shiny in the well.", "Leave it there. / Already done."},
        {"The golem moved today.", "Where? / That's what concerns me."},
    };

    // ── Freeroam one-sided passing remarks ────────────────────────────────────
    private static final String[] ROAM_ONE_SIDED = {
        "Don't mind me.",
        "Just passing through.",
        "Lovely day.",
        "Carry on.",
        "Nothing to see here.",
        "Hard at work, I see.",
        "Still here? Good.",
        "Almost done for the day.",
        "The usual, the usual.",
        "Watch your step.",
        "Excuse me. Busy.",
        "Off to something important. Probably.",
        "Good. You're alive.",
        "Keep it up.",
        "Don't stop on my account.",
        "Just thinking. Don't worry about it.",
        "Back in a moment. Possibly.",
        "If you need me I'll be over there.",
        "The golem nodded at me. I took that as encouragement.",
        "I have a plan. It may not work. But I have it.",
        "One errand. Just the one. That's the plan.",
        "I know where I'm going. Mostly.",
        "Busy. But the good kind.",
        "Things to do. People to avoid. The usual.",
        "I've had worse days. Not many, but some.",
        "Not my problem. But I'm thinking about it.",
        "I'll figure it out. Probably.",
        "One step at a time. Very small steps.",
        "I was going somewhere. I'll remember what.",
        "Things could be worse. Don't ask me how.",
        "Not late. Early for something else.",
        "Making progress. On what, I haven't decided.",
        "I'm fine. Don't ask follow-up questions.",
        "Best I can do under the circumstances.",
        "Still standing. That's the goal.",
        "Could be going better. Could be going worse. Going.",
        "I have a plan. It's flexible.",
        "You didn't see me. I wasn't here.",
        "On my way. Eventually.",
        "Thinking. Don't disturb.",
        "I've survived worse odds than this.",
        "I'll deal with it later. Later has not arrived.",
        "Moving forward. Sideways sometimes. Mostly forward.",
        "It's fine. I've decided it's fine.",
    };

    // ── Greeting lines by time of day ─────────────────────────────────────────
    // Villager greeting another villager
    private static final String[] GREET_VIL_MORNING   = {"Good morning!", "Morning!", "Rise and grind...", "Another day."};
    private static final String[] GREET_VIL_AFTERNOON  = {"Good afternoon.", "Afternoon.", "Still going?", "Halfway there."};
    private static final String[] GREET_VIL_EVENING    = {"Good evening.", "Evening.", "Long day, huh?", "Almost done."};
    private static final String[] GREET_VIL_NIGHT      = {"You're up late.", "Can't sleep either?", "Quiet night.", "Good night."};

    // Villager greeting a player
    private static final String[] GREET_PLR_MORNING    = {"Good morning, traveller!", "Morning, stranger!", "Up early, I see.", "Welcome, traveller."};
    private static final String[] GREET_PLR_AFTERNOON  = {"Good afternoon.", "Safe travels.", "Need anything?", "Lovely day, isn't it?"};
    private static final String[] GREET_PLR_EVENING    = {"Good evening.", "Heading somewhere?", "Evening, traveller.", "Getting late."};
    private static final String[] GREET_PLR_NIGHT      = {"Bit late to be wandering.", "Be careful out there.", "You're up late, traveller.", "Quiet night, isn't it?"};

    // (greeterId << 32) | targetId → next allowed greeting tick (villager↔villager pairs)
    // player entity ID → tick when any villager may greet them again (one greeting per player per day)
    private static final Map<java.util.UUID, Long> PLAYER_GREETED = new HashMap<>();
    // villager entity ID → tick when they can be greeted again (one greeting per villager per day)
    private static final Map<java.util.UUID, Long> VILLAGER_GREETED = new HashMap<>();

    // villager net ID → tick when their name-tag bubble should be cleared
    private static final Map<Integer, Long> NAME_BUBBLE = new HashMap<>();
    // villager net ID → original custom name before a bubble overwrote it
    private static final Map<Integer, net.minecraft.text.Text> STORED_NAMES = new HashMap<>();
    // villager net ID → original customNameVisible state (so hover-only names stay hover-only)
    private static final Map<Integer, Boolean> STORED_NAME_VISIBLE = new HashMap<>();

    // ── Group conversation threads ────────────────────────────────────────────
    // Each thread is an array of lines; villagers take turns in round-robin order.
    static final String[][] GROUP_THREADS = {
        // The raid
        {"Did you sleep at all last night?", "Between the horn and the noise, no.", "I gave up sleeping. I just wait now.", "That is not better."},
        // The golem
        {"I think the golem is getting smarter.", "What makes you say that?", "He moved his flower to get better light.", "He has been doing that for weeks. We just noticed."},
        // The traveller
        {"The traveller was here again.", "What did they want?", "Looked at everything. Bought nothing. Left.", "Classic."},
        // Village size
        {"The village feels smaller lately.", "It is the same size.", "That is what I mean.", "Ah."},
        // The nitwit
        {"What does the nitwit do all day?", "They wave at things.", "What things?", "Everything. Without exception."},
        // Philosophy
        {"Do you ever wonder if we chose this?", "Chose what?", "All of it. The raids. The trades. The bell.", "I try not to think about the bell."},
        // Emeralds
        {"How many emeralds do you have?", "That is personal.", "So. A lot then.", "That is also personal."},
        // The well
        {"The well makes a sound at night.", "What kind of sound?", "The kind I have decided to ignore.", "Wise."},
        // The new build
        {"Did you see what they built on the hill?", "I saw it.", "What do you think?", "More confidence than sense."},
        // Trade regret
        {"I made a trade today I regret.", "Already?", "The moment I agreed to it.", "How quickly can we forget this happened?"},
        // Old days
        {"Was it always like this?", "Raids and bells and running?", "Yes. Always like this.", "I was hoping you would say no."},
        // The fence
        {"Someone broke my fence again.", "Which section?", "The part facing the forest.", "That part always goes first."},
        // Future
        {"What do you want. Eventually.", "Fewer raids. More sleep. Better prices.", "That is everyone's answer.", "Does that make it wrong?"},
        // Night sounds
        {"Something was moving outside last night.", "Did you look?", "No.", "Good decision."},
        // Professions
        {"Do you ever wish you had a different job?", "Constantly.", "What would you do?", "Something that did not involve this."},
        // The bell
        {"Who rings the bell?", "It rings itself. Apparently.", "That is not how bells work.", "Tell the bell."},
        // The sky
        {"The sky looked strange this morning.", "Strange how?", "Like something was watching.", "That is just the phantom. Ignore it."},
        // Village breathing
        {"This village has a sound at night.", "Like what?", "Like it is breathing. Quietly surviving.", "I have noticed that too."},
        // Weather moods
        {"I feel rain coming.", "I feel everything coming. I have felt it for months.", "Is that exhausting?", "Very. Yes."},
        // Dreams
        {"I had a dream last night.", "Good or bad?", "I was somewhere quiet.", "That explains why you look confused."},
        // Sleep
        {"I slept through the bell last night.", "How?", "I have trained myself.", "Teach me."},
        // The golem flower
        {"The golem left a flower outside my door.", "I did not think he moved that far.", "Neither did I.", "Maybe he likes you."},
        // Lost item
        {"Has anyone seen a blue pot?", "What kind of pot?", "The kind I cannot find.", "That describes most pots."},
        // Repairs
        {"I fixed something today.", "What?", "My opinion of Mondays.", "And?", "Still bad. But marginally."},
        // Counting
        {"I counted the houses today.", "How many?", "More than yesterday.", "That does not make sense.", "The village grows. I counted."},
    };

    // Group conversation system — [showAtTick, villagerId, lineText as hash key]
    // We store pending group lines similarly to Q&A PENDING
    private static final List<long[]> GROUP_PENDING_LINES = new java.util.ArrayList<>();
    // villager ID → group conversation end tick (freeze them while group talks)
    private static final Map<Integer, Long> GROUP_FROZEN = new HashMap<>();
    // Cooldown so group conversations don't spam: last group conv end tick per area
    private static final Map<Long, Long> GROUP_COOLDOWN = new HashMap<>();
    // Stored group line text by index — avoids putting strings in long[]
    private static final List<String> GROUP_LINE_TEXTS = new java.util.ArrayList<>();

    private static final List<long[]> PENDING = new ArrayList<>();
    private static final Map<Integer, Long> ACTIVE = new HashMap<>();
    private static final Map<Integer, Long> VILLAGER_BUBBLE = new HashMap<>();
    private static final Map<Long, Long>    CAMPFIRE_COOLDOWN = new HashMap<>();
    private static final Map<Integer, long[]> CONVERSATIONS = new HashMap<>();
    // pair key (min_id * 1e6 + max_id) → tick when this pair may talk again
    private static final Map<Long, Long> TALKED_RECENTLY = new HashMap<>();
    // joiner villager ID → [speakerA ID, speakerB ID, timeoutTick]
    private static final Map<Integer, long[]> PENDING_JOINERS = new HashMap<>();
    private static long lastCleanupTick = -1;

    /** Returns true if this villager is mid-group-conversation and should be frozen. */
    public static boolean isInGroupConversation(int villagerNetId, long now) {
        return GROUP_FROZEN.getOrDefault(villagerNetId, 0L) > now;
    }

    /** Returns true if these two villagers talked recently and should wait before talking again. */
    public static boolean talkedRecently(int idA, int idB, long now) {
        long key = (long) Math.min(idA, idB) * 1_000_000L + Math.max(idA, idB);
        return TALKED_RECENTLY.getOrDefault(key, 0L) > now;
    }

    private static void markTalkedRecently(int idA, int idB, long now) {
        long key = (long) Math.min(idA, idB) * 1_000_000L + Math.max(idA, idB);
        TALKED_RECENTLY.put(key, now + 3600L); // 3-minute cooldown before same pair talks again
    }

    // Stores the talk index AND which pool it came from (0=universal, 1=profession)
    // so the answer can be fetched from the right pool.
    // PENDING entry: [showTick, listenerNetId, poolFlag, talkIdx, durationTicks]
    // poolFlag: 0 = TALKS, 1 = profession-specific pool of speaker

    /** Deduplicated global tick. */
    public static void tick(ServerWorld world) {
        long now = world.getTime();
        if (now == lastCleanupTick) return;
        lastCleanupTick = now;

        PENDING.removeIf(p -> {
            if (now < p[0]) return false;
            int listenerId = (int) p[1];
            int poolFlag   = (int) p[2];
            var entity = world.getEntityById(listenerId);
            if (entity instanceof VillagerEntity v
                    && VILLAGER_BUBBLE.getOrDefault(listenerId, 0L) <= now) {
                if (poolFlag == 4) {
                    // poolFlag=4: reply text is in GROUP_LINE_TEXTS at index p[3]
                    int textIdx = (int) p[3];
                    if (textIdx < GROUP_LINE_TEXTS.size()) {
                        spawnBubble(v, GROUP_LINE_TEXTS.get(textIdx), (int) p[4], world, now);
                    }
                } else {
                    String[][] pool = selectPool(v.getVillagerData().getProfession(), poolFlag);
                    int idx = (int) p[3];
                    if (idx < pool.length) {
                        spawnBubble(v, pool[idx][1], (int) p[4], world, now);
                    }
                }
            }
            return true;
        });

        // NAME_BUBBLE expiry and restoration are fully handled by restoreNames()
        // which runs every server tick via ServerTickEvents. Nothing to do here.

        VILLAGER_BUBBLE.entrySet().removeIf(e -> now >= e.getValue());
        CAMPFIRE_COOLDOWN.entrySet().removeIf(e -> now >= e.getValue());
        GROUP_COOLDOWN.entrySet().removeIf(e -> now >= e.getValue());
        GROUP_FROZEN.entrySet().removeIf(e -> now >= e.getValue());

        // Process group conversation pending lines — show each only after previous bubble
        GROUP_PENDING_LINES.removeIf(entry -> {
            long showAt = entry[0];
            if (now < showAt) return false;
            int vid     = (int) entry[1];
            int textIdx = (int) entry[2];
            var entity  = world.getEntityById(vid);
            if (entity instanceof VillagerEntity v && textIdx < GROUP_LINE_TEXTS.size()) {
                spawnBubble(v, GROUP_LINE_TEXTS.get(textIdx), 130, world, now);
            }
            return true; // always remove processed entries
        });
        // Prune GROUP_LINE_TEXTS when no pending lines reference it — prevent unbounded growth
        if (GROUP_PENDING_LINES.isEmpty() && !GROUP_LINE_TEXTS.isEmpty()) {
            GROUP_LINE_TEXTS.clear();
        }
        // When a conversation expires: clear INTERACTION_TARGET, record cooldown,
        // and nudge each villager away from their partner so they visibly "leave".
        // Collect pairs that just expired so we can try a joiner after cleanup
        List<int[]> expiredPairs = new java.util.ArrayList<>();
        CONVERSATIONS.entrySet().removeIf(e -> {
            if (now < e.getValue()[1]) return false;
            int vid = e.getKey();
            int partnerId = (int) e.getValue()[0];
            markTalkedRecently(vid, partnerId, now);

            // Record once per pair (lower ID wins) for joiner check below
            if (vid < partnerId) expiredPairs.add(new int[]{vid, partnerId});

            var entity = world.getEntityById(vid);
            var partner = world.getEntityById(partnerId);
            if (entity instanceof VillagerEntity v) {
                v.getBrain().forget(MemoryModuleType.INTERACTION_TARGET);
                ((com.livingvillages.duck.IVillagerBehaviorState) v)
                        .livingvillages$setConversationCooldown(200 + world.random.nextInt(200));
                v.getBrain().forget(MemoryModuleType.LOOK_TARGET);
                if (partner != null) {
                    double dx = v.getX() - partner.getX();
                    double dz = v.getZ() - partner.getZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0.1) {
                        double farDist = 8.0 + world.random.nextDouble() * 6.0;
                        net.minecraft.util.math.BlockPos away = new net.minecraft.util.math.BlockPos(
                                (int)(v.getX() + dx / len * farDist),
                                (int) v.getY(),
                                (int)(v.getZ() + dz / len * farDist));
                        v.getBrain().remember(MemoryModuleType.WALK_TARGET,
                                new WalkTarget(new net.minecraft.entity.ai.brain.BlockPosLookTarget(away), 0.55f, 2));
                        v.getBrain().forget(MemoryModuleType.INTERACTION_TARGET);
                    }
                }
            }
            return true;
        });
        // After cleanup: for each pair that just finished, give a nearby bystander a chance to jump in
        for (int[] pair : expiredPairs) {
            tryJoinerPickup(pair[0], pair[1], world, now);
        }

        TALKED_RECENTLY.entrySet().removeIf(e -> now >= e.getValue());
        PANIC_BUBBLE.entrySet().removeIf(e -> now >= e.getValue());
        PENDING_JOINERS.entrySet().removeIf(e -> {
            long[] d = e.getValue();
            if (now >= d[2]) return true; // timed out
            var a = world.getEntityById((int) d[0]);
            var b = world.getEntityById((int) d[1]);
            return (!(a instanceof net.minecraft.entity.Entity) || !a.isAlive())
                || (!(b instanceof net.minecraft.entity.Entity) || !b.isAlive());
        });

        // Praise: villagers walk toward player and show bubbles after raid victory
        PRAISE_STATE.entrySet().removeIf(e -> {
            long[] data = e.getValue();
            if (now >= data[1]) return true; // expired
            var entity = world.getEntityById(e.getKey());
            if (!(entity instanceof VillagerEntity v)) return true;
            var player = world.getEntityById((int) data[0]);
            if (!(player instanceof net.minecraft.entity.player.PlayerEntity p)) return true;

            // Walk toward player
            v.getBrain().remember(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityLookTarget(p, true), 0.6f, 2));
            v.getLookControl().lookAt(p, 30f, 30f);

            // Show praise bubble every 5 seconds
            if (now % 100 == (e.getKey() % 100)
                    && VILLAGER_BUBBLE.getOrDefault(v.getId(), 0L) <= now) {
                spawnBubble(v, PRAISE_LINES[v.getRandom().nextInt(PRAISE_LINES.length)], 80, world, now);
            }
            return false;
        });
        // PLAYER_GREETED / VILLAGER_GREETED use day numbers — no expiry cleanup needed.

        // Process bystander join-in lines
        JOINER_LINES.entrySet().removeIf(e -> {
            Object[] data = e.getValue();
            if (now < (long) data[1]) return false;
            var entity = world.getEntityById(e.getKey());
            if (entity instanceof VillagerEntity v
                    && VILLAGER_BUBBLE.getOrDefault(v.getId(), 0L) <= now) {
                spawnBubble(v, (String) data[0], (int) data[2], world, now);
            }
            return true;
        });
    }

    // Baby-specific general chatter (no trading references)
    private static final String[] BABY_CHATTER = {
        "I found a bug.", "The sky is very high today.",
        "I made a noise.", "That cloud looks like a sheep.",
        "My feet hurt.", "I counted to ten. Lost count.",
        "I think the golem saw me.", "It is very big outside.",
        "I wonder what is over there.", "I dropped something.",
        "Something moved in the grass.", "I am not tired at all.",
        "The ground is different here.", "I like this spot.",
        "I heard a sound.", "The sun is looking at me.",
        "I found a stick. It is mine now.", "That was not there before.",
        "I am going very fast.", "Nobody is watching. Good.",
        "I could stay here forever. Maybe just a bit.",
        "The grass is wet. Noted.", "I want to see what's over the hill.",
        "I made a new plan. It involves running.",
        "That flower was there yesterday too. We are friends.",
        "The golem is very loud when he walks.", "I was never scared.",
        "Something smells interesting.", "I am exploring. Professionally.",
    };

    // Baby-specific campfire solo lines
    private static final String[] BABY_CAMPFIRE = {
        "The fire is big.", "It's warm here.", "I like this.",
        "Is the golem scared of fire too?", "Why does smoke go up?",
        "I counted the flames. Lost count.", "Nobody is running. Good.",
        "The sparks look like tiny stars.", "This is my favourite place.",
        "If I sit very still nothing bad happens.",
    };

    /** Called from CampfireBehavior when a villager is seated. */
    public static void tryTrigger(VillagerEntity speaker, ServerWorld world) {
        if (speaker.getRandom().nextInt(200) != 0) return; // slightly less frequent = less chatty

        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(speaker.getId(), 0L) > now) return;

        BlockPos campfire = ((IVillagerBehaviorState) speaker).livingvillages$getCampfireTarget();
        if (campfire == null) return;

        long campfireKey = campfireKey(campfire);
        if (CAMPFIRE_COOLDOWN.getOrDefault(campfireKey, 0L) > now) return;

        VillagerProfession prof = speaker.getVillagerData().getProfession();
        int duration = 120;
        long gap = 25L;
        long conversationEnd = now + duration + gap + duration + 100L;

        float roll = speaker.getRandom().nextFloat();

        if (roll < 0.30f || speaker.isBaby()) {
            // Solo campfire musing — babies get their own lines, adults get profession-specific
            String[] soloPool;
            if (speaker.isBaby()) {
                soloPool = BABY_CAMPFIRE;
            } else {
                soloPool = PROF_SOLO.getOrDefault(prof, new String[0]);
                if (soloPool.length == 0) soloPool = cfgSolo();
            }
            CAMPFIRE_COOLDOWN.put(campfireKey, now + duration + 100L);
            int si = nextIdx(SOLO_IDX, speaker.getId(), soloPool.length);
            spawnBubble(speaker, soloPool[si], duration, world, now);
            return;
        }

        if (roll < 0.45f) {
            // One-sided remark — cycle through so all lines get used
            CAMPFIRE_COOLDOWN.put(campfireKey, now + duration + 100L);
            String[] ons = cfgOneSided();
            int oi = nextIdx(ONE_SIDED_IDX, speaker.getId(), ons.length);
            spawnBubble(speaker, ons[oi], duration, world, now);
            return;
        }

        List<VillagerEntity> coSitters = world.getEntitiesByClass(VillagerEntity.class,
                speaker.getBoundingBox().expand(8.0),
                v -> v != speaker && v.isAlive() && !v.isSleeping()
                        && campfire.equals(((IVillagerBehaviorState) v).livingvillages$getCampfireTarget()));

        if (coSitters.isEmpty()) {
            CAMPFIRE_COOLDOWN.put(campfireKey, now + duration + 100L);
            String[] ons = cfgOneSided();
            int oi = nextIdx(ONE_SIDED_IDX, speaker.getId(), ons.length);
            spawnBubble(speaker, ons[oi], duration, world, now);
            return;
        }

        // Profession-specific pool preferred (85%), universal fallback only if no prof pool
        String[][] profPool = PROF_TALKS.get(prof);
        boolean useProfPool = profPool != null && profPool.length > 0
                && speaker.getRandom().nextFloat() < 0.85f;
        int poolFlag = useProfPool ? 1 : 0;
        String[][] pool = useProfPool ? profPool : cfgTalks();

        VillagerEntity listener = coSitters.get(speaker.getRandom().nextInt(coSitters.size()));
        // Cycle through the pool so all lines are used before repeating
        int idx = nextIdx(TALKS_IDX, speaker.getId(), pool.length);

        CAMPFIRE_COOLDOWN.put(campfireKey, conversationEnd);

        long faceEnd = now + duration + gap + duration;
        CONVERSATIONS.put(speaker.getId(),  new long[]{listener.getId(), faceEnd});
        CONVERSATIONS.put(listener.getId(), new long[]{speaker.getId(),  faceEnd});

        spawnBubble(speaker, pool[idx][0], duration, world, now);
        // [showTick, listenerNetId, poolFlag, talkIdx, duration]
        PENDING.add(new long[]{now + duration + gap, listener.getId(), poolFlag, idx, duration});
    }

    private static String[][] selectPool(VillagerProfession listenerProf, int poolFlag) {
        if (poolFlag == 0) return cfgTalks();
        if (poolFlag == 2) return cfgRoamTalks();
        if (poolFlag == 3) return HANGOUT_TALKS;
        // poolFlag == 4: reply text is in GROUP_LINE_TEXTS, handled separately in PENDING processing
        if (poolFlag == 4) return new String[0][]; // sentinel — never used directly
        String[][] p = PROF_TALKS.get(listenerProf);
        return (p != null && p.length > 0) ? p : cfgTalks();
    }

    /**
     * Called every tick for panicking villagers. Occasionally spawns a short
     * funny bubble above their head as they flee.
     */
    public static void tryPanic(VillagerEntity villager, ServerWorld world) {
        if (villager.getRandom().nextInt(80) != 0) return;

        long now = world.getTime();
        if (PANIC_BUBBLE.getOrDefault(villager.getId(), 0L) > now) return;

        int duration = 40;
        // Use raid-specific lines if there's an active raid nearby
        boolean inRaid = world.getRaidAt(villager.getBlockPos()) != null;
        String[] pool = inRaid ? RAID_PANIC_LINES : cfgPanic();
        String line = pool[villager.getRandom().nextInt(pool.length)];
        PANIC_BUBBLE.put(villager.getId(), now + duration);
        spawnBubble(villager, line, duration, world, now);
    }

    /** Returns the villager this entity should face during Q&A, or null to face the fire. */
    public static VillagerEntity getConversationPartner(int villagerNetId, ServerWorld world, long now) {
        long[] entry = CONVERSATIONS.get(villagerNetId);
        if (entry == null || now >= entry[1]) return null;
        var entity = world.getEntityById((int) entry[0]);
        return entity instanceof VillagerEntity v ? v : null;
    }

    // joiner villager net ID → [line text, showTick, durationTicks]
    private static final Map<Integer, Object[]> JOINER_LINES = new HashMap<>();

    // ── Raid praise state ─────────────────────────────────────────────────────
    // villager net ID → [target player net ID, expiry tick]
    private static final Map<Integer, long[]> PRAISE_STATE = new HashMap<>();

    private static final String[] PRAISE_LINES = {
        "You did it!", "Our hero!", "Thank you!", "We are safe because of you!",
        "You are a legend!", "THANK YOU!", "Bless you, traveller!",
        "You saved our village!", "We owe you everything!",
        "Come back anytime, hero!", "The village stands because of you!",
        "You are always welcome here!", "We will not forget this.",
        "Three cheers for the hero!", "Words cannot express our gratitude.",
        "You are one of us now!", "We are forever in your debt.",
    };

    public static String[] getJoinLines() { return cfgJoin(); }

    /** Returns true if this villager has no active bubble (can say something new). */
    public static boolean canSpeak(int villagerNetId, long now) {
        return VILLAGER_BUBBLE.getOrDefault(villagerNetId, 0L) <= now;
    }

    /**
     * Called every server tick across all worlds — restores real custom names on entities
     * whose bubble has expired. Runs server-wide so it never misses a world.
     */
    public static void restoreNames(ServerWorld world) {
        long now = world.getTime();
        // Single loop: process every expired NAME_BUBBLE entry.
        NAME_BUBBLE.entrySet().removeIf(e -> {
            if (now < e.getValue()) return false; // not expired yet

            int id = e.getKey();
            var entity = world.getEntityById(id);
            if (entity == null) return false; // entity in a different world — try next tick

            Text original = STORED_NAMES.remove(id);
            Boolean wasVisible = STORED_NAME_VISIBLE.remove(id);
            if (original != null) {
                // Restore the original name AND its original hover-only/always state
                entity.setCustomName(original);
                entity.setCustomNameVisible(wasVisible != null ? wasVisible : false);
            } else {
                // No saved name — clear bubble if it's still showing ours
                Text cur = entity.getCustomName();
                if (cur != null && cur.getString().startsWith("\"")) {
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                }
            }
            return true;
        });
    }

    /** Force-clears the active bubble on an entity so the next spawnSpeech always shows. */
    public static void forceClearBubble(VillagerEntity villager) {
        int id = villager.getId();
        VILLAGER_BUBBLE.remove(id);
        NAME_BUBBLE.remove(id);
        // Restore real name if stored, otherwise clear
        net.minecraft.text.Text original = STORED_NAMES.remove(id);
        if (original != null) {
            villager.setCustomName(original);
            villager.setCustomNameVisible(true);
        } else {
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
        }
    }

    /** Spawn a bubble on a random villager from the given list. */
    public static void spawnForGroup(java.util.List<VillagerEntity> villagers,
                                      String[] pool, ServerWorld world) {
        if (villagers.isEmpty()) return;
        long now = world.getTime();
        // Stagger: each villager has a random chance and independent cooldown
        for (VillagerEntity v : villagers) {
            if (v.getRandom().nextFloat() < 0.6f
                    && VILLAGER_BUBBLE.getOrDefault(v.getId(), 0L) <= now) {
                spawnBubble(v, pool[v.getRandom().nextInt(pool.length)], 100, world, now);
            }
        }
    }

    /** Generic one-off bubble for any villager. */
    public static void spawnSpeech(VillagerEntity villager, String text, ServerWorld world) {
        spawnSpeechEntity(villager, text, world);
    }

    /** Generic one-off bubble for ANY living entity (guards, etc.). */
    public static void spawnSpeechEntity(net.minecraft.entity.LivingEntity entity, String text, ServerWorld world) {
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(entity.getId(), 0L) > now) return;
        int id = entity.getId();
        if (!STORED_NAMES.containsKey(id)) {
            net.minecraft.text.Text current = entity.getCustomName();
            if (current != null && !current.getString().startsWith("\"")) {
                STORED_NAMES.put(id, current);
                STORED_NAME_VISIBLE.put(id, entity.isCustomNameVisible());
            }
        }
        VILLAGER_BUBBLE.put(id, now + 80);
        NAME_BUBBLE.put(id, now + 80);
        // Strip " / context" notation — only show the final part after the last slash
        String display = text.contains(" / ") ? text.substring(text.lastIndexOf(" / ") + 3) : text;
        entity.setCustomName(net.minecraft.text.Text.literal("\"" + display + "\""));
        entity.setCustomNameVisible(true); // bubble is always visible while active
    }

    /** Reaction bubble after completing a trade. */
    public static void spawnTradeBubble(VillagerEntity villager, ServerWorld world) {
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(villager.getId(), 0L) > now) return;
        float roll = villager.getRandom().nextFloat();
        String[] pool = roll < 0.05f ? TRADE_FUNNY
                : roll < 0.40f ? TRADE_RELUCTANT
                : TRADE_CELEBRATE;
        spawnBubble(villager, pool[villager.getRandom().nextInt(pool.length)], 80, world, now);
    }

    /** Short hum/lyric bubble — appears above a walking villager, lasts 2 seconds. */
    /** Start post-raid praise — villager walks toward player and praises for 90 seconds. */
    public static void startPraise(VillagerEntity villager, net.minecraft.entity.player.PlayerEntity player,
                                    ServerWorld world) {
        long now = world.getTime();
        PRAISE_STATE.put(villager.getId(), new long[]{player.getId(), now + 1800L});
    }

    public static void spawnHum(VillagerEntity villager, ServerWorld world) {
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(villager.getId(), 0L) > now) return;
        String[] h = cfgHums();
        spawnBubble(villager, h[villager.getRandom().nextInt(h.length)], 40, world, now);
    }

    /** Picks a nearby bystander to walk over and join the A-B conversation organically. */
    public static void tryScheduleJoiner(VillagerEntity speaker, VillagerEntity target, ServerWorld world) {
        if (world.random.nextFloat() > 0.4f) return;
        long now = world.getTime();
        List<VillagerEntity> bystanders = world.getEntitiesByClass(VillagerEntity.class,
                speaker.getBoundingBox().expand(12.0),
                v -> v != speaker && v != target && v.isAlive() && !v.isBaby() && !v.isSleeping()
                        && ((IVillagerBehaviorState) v).livingvillages$getCampfireTarget() == null
                        && !PENDING_JOINERS.containsKey(v.getId())
                        && getConversationPartner(v.getId(), world, now) == null
                        && !isInGroupConversation(v.getId(), now));
        if (bystanders.isEmpty()) return;
        VillagerEntity joiner = bystanders.get(world.random.nextInt(bystanders.size()));
        PENDING_JOINERS.put(joiner.getId(), new long[]{speaker.getId(), target.getId(), now + 600L});
    }

    /** Returns joiner data for this villager, or null if they are not a pending joiner. */
    public static long[] getJoinerData(int villagerNetId) {
        return PENDING_JOINERS.get(villagerNetId);
    }

    /** Cancels this villager's pending joiner status. */
    public static void removeJoiner(int villagerNetId) {
        PENDING_JOINERS.remove(villagerNetId);
    }

    /** C arrived and A-B finished — start the group thread with C speaking first. */
    public static void triggerJoinerGroup(VillagerEntity joiner, VillagerEntity speakerA,
                                           VillagerEntity speakerB, ServerWorld world) {
        long now = world.getTime();
        if (isInGroupConversation(joiner.getId(), now)
                || isInGroupConversation(speakerA.getId(), now)
                || isInGroupConversation(speakerB.getId(), now)) return;

        long areaKey = campfireKey(joiner.getBlockPos());
        if (GROUP_COOLDOWN.getOrDefault(areaKey, 0L) > now) return;

        String[] thread = GROUP_THREADS[world.random.nextInt(GROUP_THREADS.length)];
        int lineCount = Math.min(thread.length, 4 + world.random.nextInt(2));

        java.util.List<VillagerEntity> group = new java.util.ArrayList<>();
        group.add(joiner); group.add(speakerA); group.add(speakerB);

        // Override any walk-away impulse CONVERSATIONS removal may have just set
        for (VillagerEntity v : group) {
            v.getNavigation().stop();
            v.getBrain().forget(MemoryModuleType.WALK_TARGET);
        }

        int duration = 130;
        int gap = 15;
        long tick = now;
        for (int i = 0; i < lineCount; i++) {
            VillagerEntity speaker = group.get(i % group.size());
            int textIdx = GROUP_LINE_TEXTS.size();
            GROUP_LINE_TEXTS.add(thread[i]);
            GROUP_PENDING_LINES.add(new long[]{tick, speaker.getId(), textIdx});
            tick += duration + gap;
        }

        long endTick = tick + 20L;
        for (VillagerEntity v : group) GROUP_FROZEN.put(v.getId(), endTick);
        GROUP_COOLDOWN.put(areaKey, endTick + 3000L);
    }

    /**
     * After A-B finish talking, scan nearby villagers — if one is within 6 blocks
     * and hasn't talked to either recently, they jump in and start a new exchange.
     * 40% chance. This creates the organic trio/chain effect without any navigation.
     */
    private static void tryJoinerPickup(int vidA, int vidB, ServerWorld world, long now) {
        if (world.random.nextFloat() > 0.4f) return;

        var entityA = world.getEntityById(vidA);
        var entityB = world.getEntityById(vidB);
        if (!(entityA instanceof VillagerEntity vA) || !vA.isAlive()) return;
        if (!(entityB instanceof VillagerEntity vB) || !vB.isAlive()) return;

        double midX = (vA.getX() + vB.getX()) / 2.0;
        double midY = (vA.getY() + vB.getY()) / 2.0;
        double midZ = (vA.getZ() + vB.getZ()) / 2.0;

        List<VillagerEntity> bystanders = world.getEntitiesByClass(VillagerEntity.class,
                new net.minecraft.util.math.Box(midX - 6, midY - 2, midZ - 6,
                                                midX + 6, midY + 2, midZ + 6),
                v -> v != vA && v != vB && v.isAlive() && !v.isBaby() && !v.isSleeping()
                        && ((IVillagerBehaviorState) v).livingvillages$getCampfireTarget() == null
                        && CONVERSATIONS.get(v.getId()) == null
                        && !isInGroupConversation(v.getId(), now)
                        && VILLAGER_BUBBLE.getOrDefault(v.getId(), 0L) <= now);

        if (bystanders.isEmpty()) return;

        VillagerEntity joiner = bystanders.get(world.random.nextInt(bystanders.size()));

        // Pick whichever of A or B the joiner hasn't spoken to recently
        VillagerEntity target;
        boolean recentA = talkedRecently(joiner.getId(), vA.getId(), now);
        boolean recentB = talkedRecently(joiner.getId(), vB.getId(), now);
        if (recentA && recentB) return; // joiner knows both too well right now
        target = recentA ? vB : vA;

        // Face each other and start a fresh roam exchange
        joiner.getLookControl().lookAt(target.getX(), target.getEyeY(), target.getZ());
        target.getLookControl().lookAt(joiner.getX(), joiner.getEyeY(), joiner.getZ());
        spawnRoamBubbles(joiner, target, world);
    }

    /** Schedules a bystander to join in ~5 seconds after the main exchange. */
    public static void scheduleJoin(VillagerEntity joiner, String line, ServerWorld world) {
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(joiner.getId(), 0L) > now) return;
        JOINER_LINES.put(joiner.getId(), new Object[]{line, now + 100L, 70});
    }

    /** Spawns a Q&A from a specific dialogue pool (adult↔baby, baby↔baby, etc.). */
    public static void spawnSpecialBubbles(VillagerEntity speaker, VillagerEntity target,
                                            String[][] pool, ServerWorld world) {
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(speaker.getId(), 0L) > now) return;
        if (VILLAGER_BUBBLE.getOrDefault(target.getId(), 0L) > now) return;

        int idx = speaker.getRandom().nextInt(pool.length);
        int duration = 80;
        long gap = 25L;
        spawnBubble(speaker, pool[idx][0], duration, world, now);
        // Store the actual reply text so PENDING can find it without hitting the wrong pool
        int replyTextIdx = GROUP_LINE_TEXTS.size();
        GROUP_LINE_TEXTS.add(pool[idx][1]);
        // Use poolFlag=4 as a sentinel meaning "use GROUP_LINE_TEXTS[replyTextIdx]"
        PENDING.add(new long[]{now + duration + gap, target.getId(), 4, replyTextIdx, duration});
        long specFaceEnd = now + duration + gap + duration + 60L;
        CONVERSATIONS.put(speaker.getId(), new long[]{ target.getId(), specFaceEnd });
        CONVERSATIONS.put(target.getId(), new long[]{ speaker.getId(), specFaceEnd });
    }

    /** Spawns a freeroam conversation bubble between two passing villagers. */
    public static void spawnRoamBubbles(VillagerEntity speaker, VillagerEntity target, ServerWorld world) {
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(speaker.getId(), 0L) > now) return;

        int duration = 100; // 5 seconds — enough time to read the line
        long gap = 25L;    // 1.25 s clear pause before reply

        // 35% chance: one-sided remark, no reply
        if (speaker.getRandom().nextFloat() < 0.35f) {
            String[] ro = cfgRoamOne();
            spawnBubble(speaker, ro[speaker.getRandom().nextInt(ro.length)], duration, world, now);
            return;
        }

        // 65%: Q&A exchange
        if (VILLAGER_BUBBLE.getOrDefault(target.getId(), 0L) > now) return;
        String[][] rt = cfgRoamTalks();
        int idx = nextIdx(TALKS_IDX, speaker.getId(), rt.length);
        spawnBubble(speaker, rt[idx][0], duration, world, now);
        // poolFlag = 2 (ROAM_TALKS)
        long roamGap = 25L;
        PENDING.add(new long[]{now + duration + roamGap, target.getId(), 2, idx, duration});
        // Freeze both so they stay face-to-face until the reply bubble disappears
        long roamFaceEnd = now + duration + roamGap + duration + 60L;
        CONVERSATIONS.put(speaker.getId(), new long[]{ target.getId(), roamFaceEnd });
        CONVERSATIONS.put(target.getId(), new long[]{ speaker.getId(), roamFaceEnd });
    }

    /**
     * Greeting system — villagers say hello to nearby villagers and players
     * once per game-day. Uses time-of-day to pick the right phrase.
     */
    public static void tryGreet(VillagerEntity villager, ServerWorld world) {
        if (!com.livingvillages.LivingVillagesConfig.get().greetings) return;
        if (villager.isSleeping()) return;
        if (((com.livingvillages.duck.IVillagerBehaviorState) villager).livingvillages$getNapTimer() > 0) return;
        long now = world.getTime();
        if (VILLAGER_BUBBLE.getOrDefault(villager.getId(), 0L) > now) return;

        long timeOfDay = world.getTimeOfDay() % 24000L;
        String[] villagerPool = greetPool(timeOfDay, false);
        String[] playerPool   = greetPool(timeOfDay, true);

        // Scan nearby entities within 4 blocks
        var nearby = world.getEntitiesByClass(LivingEntity.class,
                villager.getBoundingBox().expand(4.0),
                e -> e != villager && e.isAlive()
                        && (e instanceof VillagerEntity || e instanceof PlayerEntity));
        if (nearby.isEmpty()) return;

        // Minecraft day derived from world time — persists across server restarts
        long today = world.getTime() / 24000L;

        for (var target : nearby) {
            if (target instanceof PlayerEntity player) {
                // UUID survives restarts; day number matches world clock which also survives
                if (PLAYER_GREETED.getOrDefault(player.getUuid(), -1L) >= today) continue;
                PLAYER_GREETED.put(player.getUuid(), today);
            } else if (target instanceof VillagerEntity tv) {
                if (VILLAGER_GREETED.getOrDefault(tv.getUuid(), -1L) >= today) continue;
                if (villager.getRandom().nextInt(60) != 0) continue;
                VILLAGER_GREETED.put(tv.getUuid(), today);
                VILLAGER_GREETED.put(villager.getUuid(), today);
            } else {
                continue;
            }

            String[] pool = (target instanceof PlayerEntity) ? playerPool : villagerPool;
            String phrase = pool[villager.getRandom().nextInt(pool.length)];

            // Look at the target AND hold the Brain LOOK_TARGET so it persists for the greeting
            villager.getLookControl().lookAt((Entity) target, 30f, 30f);
            villager.getBrain().remember(MemoryModuleType.LOOK_TARGET,
                    new net.minecraft.entity.ai.brain.EntityLookTarget((Entity) target, true));
            spawnBubble(villager, phrase, 60, world, now);
            return;
        }
    }

    private static String[] greetPool(long timeOfDay, boolean forPlayer) {
        if (timeOfDay < 6000L)  return forPlayer ? GREET_PLR_MORNING   : GREET_VIL_MORNING;
        if (timeOfDay < 12000L) return forPlayer ? GREET_PLR_AFTERNOON  : GREET_VIL_AFTERNOON;
        if (timeOfDay < 13000L) return forPlayer ? GREET_PLR_EVENING    : GREET_VIL_EVENING;
        return                         forPlayer ? GREET_PLR_NIGHT      : GREET_VIL_NIGHT;
    }

    private static String greetKey(java.util.UUID greeter, java.util.UUID target) {
        return greeter + "|" + target;
    }

    /** Clears all active speech bubbles (name tags) and resets all state. */
    public static int clearAll(net.minecraft.server.MinecraftServer server) {
        int removed = 0;
        for (var world : server.getWorlds()) {
            // Clear custom-name bubbles from all tracked villagers
            for (int id : NAME_BUBBLE.keySet()) {
                var entity = world.getEntityById(id);
                if (entity instanceof VillagerEntity v) {
                    v.setCustomName(null);
                    v.setCustomNameVisible(false);
                    removed++;
                }
            }
            // Also remove any leftover ArmorStand bubbles from older sessions
            var toDiscard = new ArrayList<net.minecraft.entity.Entity>();
            for (var entity : world.iterateEntities()) {
                if (entity instanceof ArmorStandEntity stand
                        && stand.isInvisible() && stand.isCustomNameVisible()
                        && stand.hasNoGravity() && stand.isInvulnerable()) {
                    toDiscard.add(stand);
                }
            }
            for (var e : toDiscard) { e.discard(); removed++; }
        }
        ACTIVE.clear(); NAME_BUBBLE.clear(); PENDING.clear(); VILLAGER_BUBBLE.clear();
        CAMPFIRE_COOLDOWN.clear(); CONVERSATIONS.clear();
        PANIC_BUBBLE.clear(); JOINER_LINES.clear(); PRAISE_STATE.clear();
        lastCleanupTick = -1;
        return removed;
    }

    private static long campfireKey(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ ((long) pos.getZ() << 16) ^ pos.getY();
    }

    // Uses the villager's own name tag so the bubble always follows them.
    // If the entity already has a real custom name (name tag), saves it and schedules
    // a restoration so the name reappears cleanly after the bubble.
    private static void spawnBubble(VillagerEntity villager, String text,
                                     int duration, ServerWorld world, long now) {
        int id = villager.getId();
        Text current = villager.getCustomName();
        boolean hasRealName = current != null && !current.getString().startsWith("\"");
        if (hasRealName && !STORED_NAMES.containsKey(id)) {
            STORED_NAMES.put(id, current);
            STORED_NAME_VISIBLE.put(id, villager.isCustomNameVisible());
        }
        VILLAGER_BUBBLE.put(id, now + duration);
        NAME_BUBBLE.put(id, now + duration);
        String display = text.contains(" / ") ? text.substring(text.lastIndexOf(" / ") + 3) : text;
        villager.setCustomName(Text.literal("\"" + display + "\""));
        villager.setCustomNameVisible(true);
    }
}
