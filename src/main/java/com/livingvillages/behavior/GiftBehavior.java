package com.livingvillages.behavior;

import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.village.VillagerProfession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GiftBehavior {

    private static final Map<UUID, GiftOffer>   PENDING       = new HashMap<>();
    private static final Map<String, Long>      COOLDOWN      = new HashMap<>();
    // Last enchantment given per librarian — excluded from next pick to avoid repeats
    private static final Map<UUID, Enchantment> LAST_ENCHANT  = new HashMap<>();

    private static final String[] OFFER_PHRASES = {
        "Here, take this.", "This is for you.", "A gift, from me.",
        "You deserve it.", "Consider it a thank you.",
        "For everything you have done.", "I insist.", "Keep it.",
        "You earned this.", "A small token.",
    };

    private static final String[] FOLLOW_PHRASES = {
        "Take this!", "Please, take it.", "It's for you!",
        "I've been looking for you.", "Here, take it!",
        "Don't leave me hanging!", "It belongs to you.",
        "Just take it already!", "Please!",
    };

    private record GiftOffer(UUID playerUuid, ItemStack item, long expiresAt) {}

    // ── Query helpers ─────────────────────────────────────────────────────────

    /** True if this villager currently has a pending gift offer for the given player. */
    public static boolean hasPendingGiftFor(VillagerEntity villager, PlayerEntity player) {
        GiftOffer offer = PENDING.get(villager.getUuid());
        return offer != null && offer.playerUuid().equals(player.getUuid());
    }

    // ── Accept (right-click) ──────────────────────────────────────────────────

    public static boolean tryAccept(VillagerEntity villager, PlayerEntity player, ServerWorld world) {
        GiftOffer offer = PENDING.get(villager.getUuid());
        if (offer == null || !offer.playerUuid().equals(player.getUuid())) return false;

        ItemStack gift = offer.item();
        if (!player.getInventory().insertStack(gift)) player.dropItem(gift, false);

        PENDING.remove(villager.getUuid());
        villager.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        CampfireConversation.spawnSpeech(villager, "Enjoy!", world);
        return true;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void tick(VillagerEntity villager, ServerWorld world) {
        UUID vid = villager.getUuid();
        long now = world.getTime();

        GiftOffer offer = PENDING.get(vid);
        if (offer != null) {
            // Expired — give up
            if (now > offer.expiresAt()) {
                cancelOffer(villager, vid);
                return;
            }
            PlayerEntity target = world.getPlayerByUuid(offer.playerUuid());
            if (target == null || !target.isAlive()) {
                cancelOffer(villager, vid);
                return;
            }

            // Re-set hand item every tick — vanilla Brain tasks clear it otherwise
            villager.setStackInHand(Hand.MAIN_HAND, offer.item().copy());

            var brain = villager.getBrain();
            if (!brain.hasActivity(Activity.PANIC) && !brain.hasActivity(Activity.REST)) {
                // Pin WALK_TARGET every tick so Brain can never drop it
                brain.remember(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityLookTarget(target, true), 0.85f, 1));
                villager.getLookControl().lookAt(target, 30f, 30f);
            }

            // Show follow messages every ~6 seconds when the player is close
            if (now % 120 == (villager.getId() % 120)
                    && villager.squaredDistanceTo(target) < 64.0) {
                CampfireConversation.spawnSpeech(villager,
                        FOLLOW_PHRASES[villager.getRandom().nextInt(FOLLOW_PHRASES.length)], world);
            }
            return;
        }

        // ── Try to create a new offer ─────────────────────────────────────────
        var players = world.getEntitiesByClass(PlayerEntity.class,
                villager.getBoundingBox().expand(12.0), p -> true);
        if (players.isEmpty()) return;

        for (PlayerEntity player : players) {
            int level = ReputationSystem.getLevel(vid, player.getUuid());
            if (level < 8) continue;

            String coolKey = vid + "|" + player.getUuid();
            if (COOLDOWN.getOrDefault(coolKey, 0L) > now) continue;
            if (villager.getRandom().nextInt(800) != 0) continue;

            ItemStack gift = pickGift(villager.getVillagerData().getProfession(), level, villager);
            if (gift.isEmpty()) continue;

            COOLDOWN.put(coolKey, now + 6000L);
            PENDING.put(vid, new GiftOffer(player.getUuid(), gift, now + 6000L));
            villager.setStackInHand(Hand.MAIN_HAND, gift.copy());
            CampfireConversation.spawnSpeech(villager,
                    OFFER_PHRASES[villager.getRandom().nextInt(OFFER_PHRASES.length)], world);
            break;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void cancelOffer(VillagerEntity villager, UUID vid) {
        PENDING.remove(vid);
        villager.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static ItemStack applyRarity(ItemStack stack, int tier) {
        String[] labels   = { "§7Common",  "§6Uncommon",  "§b★ Rare" };
        String   label    = labels[Math.min(tier - 1, labels.length - 1)];
        // Add lore line showing rarity
        var nbt  = stack.getOrCreateNbt();
        if (!nbt.contains("display")) nbt.put("display", new net.minecraft.nbt.NbtCompound());
        var display = nbt.getCompound("display");
        var lore    = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(label).formatted(Formatting.ITALIC))));
        display.put("Lore", lore);
        return stack;
    }

    // Enchantment pools by tier (1=Beloved, 2=Champion, 3=Legend)
    private static final Enchantment[][] BOOK_ENCHANTS = {
        { Enchantments.SHARPNESS, Enchantments.PROTECTION, Enchantments.EFFICIENCY,
          Enchantments.FEATHER_FALLING, Enchantments.FLAME, Enchantments.PUNCH },
        { Enchantments.FIRE_ASPECT, Enchantments.LOOTING, Enchantments.FORTUNE,
          Enchantments.RESPIRATION, Enchantments.THORNS, Enchantments.KNOCKBACK },
        { Enchantments.MENDING, Enchantments.UNBREAKING, Enchantments.POWER,
          Enchantments.SWEEPING, Enchantments.SILK_TOUCH },
    };

    private static ItemStack pickGift(VillagerProfession prof, int level, VillagerEntity v) {
        int tier = level - 7; // 1, 2, or 3
        Item item; int count = 1;

        if (prof == VillagerProfession.LIBRARIAN) {
            if (tier >= 2) {
                Enchantment[] pool = BOOK_ENCHANTS[Math.min(tier - 1, BOOK_ENCHANTS.length - 1)];
                Enchantment last = LAST_ENCHANT.get(v.getUuid());
                Enchantment enchant;
                int tries = 0;
                do { enchant = pool[v.getRandom().nextInt(pool.length)]; tries++; }
                while (enchant == last && tries < pool.length);
                LAST_ENCHANT.put(v.getUuid(), enchant);
                int enchLevel = Math.min(tier >= 3 ? 2 : 1, enchant.getMaxLevel());
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                EnchantedBookItem.addEnchantment(book, new EnchantmentLevelEntry(enchant, enchLevel));
                return applyRarity(book, tier);
            }
            item = Items.BOOK;
        }
        // Tier 1 → common item  |  Tier 2 → better item  |  Tier 3 → clearly distinct/rare item
        else if (prof == VillagerProfession.FARMER)        { item = tier==3?Items.ENCHANTED_GOLDEN_APPLE : tier==2?Items.GOLDEN_APPLE  : Items.BREAD;           count = tier==1?2:1; }
        else if (prof == VillagerProfession.FISHERMAN)     { item = tier==3?Items.PUFFERFISH               : tier==2?Items.SALMON        : Items.COD;             count = tier<=2?2:1; }
        else if (prof == VillagerProfession.ARMORER)       { item = tier==3?Items.DIAMOND_CHESTPLATE        : tier==2?Items.IRON_CHESTPLATE: Items.IRON_INGOT;    count = tier==1?4:1; }
        else if (prof == VillagerProfession.WEAPONSMITH)   { item = tier==3?Items.DIAMOND_SWORD             : tier==2?Items.IRON_SWORD    : Items.IRON_INGOT;     count = tier==1?4:1; }
        else if (prof == VillagerProfession.TOOLSMITH)     { item = tier==3?Items.DIAMOND_PICKAXE           : tier==2?Items.IRON_PICKAXE  : Items.IRON_INGOT;    count = tier==1?4:1; }
        else if (prof == VillagerProfession.CLERIC)        { item = tier==3?Items.GHAST_TEAR                : tier==2?Items.EXPERIENCE_BOTTLE: Items.REDSTONE;   count = tier==1?4 : tier==2?3:1; }
        else if (prof == VillagerProfession.BUTCHER)       { item = tier==3?Items.COOKED_BEEF               : tier==2?Items.COOKED_BEEF   : Items.COOKED_CHICKEN; count = tier==3?6 : tier==2?3:2; }
        else if (prof == VillagerProfession.SHEPHERD)      { item = tier==3?Items.WHITE_BED                 : tier==2?Items.WHITE_WOOL    : Items.WHITE_WOOL;    count = tier==1?3 : tier==2?6:1; }
        else if (prof == VillagerProfession.FLETCHER)      { item = tier==3?Items.CROSSBOW                  : tier==2?Items.BOW           : Items.ARROW;          count = tier==1?8:1; }
        else if (prof == VillagerProfession.CARTOGRAPHER)  { item = tier==3?Items.FILLED_MAP                : tier==2?Items.COMPASS       : Items.PAPER;          count = tier==1?4:1; }
        else if (prof == VillagerProfession.LEATHERWORKER) { item = tier==3?Items.SADDLE                    : tier==2?Items.LEATHER_CHESTPLATE: Items.LEATHER;  count = tier==1?4:1; }
        else if (prof == VillagerProfession.MASON)         { item = tier==3?Items.DIAMOND                   : tier==2?Items.STONE_BRICKS  : Items.BRICK;          count = tier==3?2 : tier==2?8:8; }
        else { item = tier==3?Items.EMERALD                                   : tier==2?Items.EMERALD       : Items.BREAD;                  count = tier==3?3 : tier==2?1:2; }
        return applyRarity(new ItemStack(item, count), tier);
    }
}
