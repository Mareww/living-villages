package com.livingvillages;

import com.livingvillages.behavior.AngerBehavior;
import com.livingvillages.behavior.BabyBehavior;
import com.livingvillages.behavior.GuardDialogueProcessor;
import com.livingvillages.behavior.CampfireConversation;
import com.livingvillages.behavior.GiftBehavior;
import com.livingvillages.behavior.ReputationSystem;
import com.livingvillages.duck.IVillagerBehaviorState;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import com.livingvillages.util.PlayerChestTracker;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class LivingVillages implements ModInitializer {
    public static final String MOD_ID = "livingvillages";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final String[] DEATH_LINES = {
        "No...", "Not again.", "Someone help!", "They got one of us.",
        "Stay back!", "Run!", "Is anyone else okay?!", "Oh no. Oh no no no.",
        "We lost someone.", "They're gone.", "This is not okay.",
        "Sound the bell!", "We need the golem!", "Everyone get inside!",
    };
    private static final String[] DEATH_LINES_BABY = {
        "What happened?!", "I'm scared.", "Is it over?", "Someone help!",
        "I want to go inside.", "Why did that happen?",
    };

    private static final String[] STEALING_LINES = {
        // Calling for help / running to protector
        "GOLEM! GOLEM, OVER HERE!", "Guards! To the storage!",
        "Someone get the golem!", "THIEF! GUARDS!",
        "The golem will deal with you!", "I'm getting the guards!",
        "To the iron golem — NOW!", "CALLING THE GOLEM!",
        "You'll regret that when the golem arrives.",
        // Direct confrontation
        "Thief!", "Stop that!", "Put it back!", "I saw that!",
        "Not on my watch!", "How dare you!", "That's not yours!",
        "Step away from there!", "Hey! That belongs to us!",
        "Don't touch that!", "Caught you!", "I knew it.",
        "Those are ours!", "That's village property!",
        "Put it down!", "We don't steal here.",
        "Everyone saw that.", "You are being watched.",
        "That is not a trade.", "Absolutely not.",
        "I will remember your face.", "You chose the wrong village.",
    };

    private static final String[] GRATEFUL_LINES = {
        "Thank you!", "You saved me!", "I owe you one.",
        "I won't forget this.", "You are a hero!", "Bless you!",
        "I thought that was it...", "Thank goodness you were here.",
    };

    @Override
    public void onInitialize() {
        LOGGER.info("Living Villages loaded.");
        LivingVillagesConfig.load();
        DialogueConfig.load();
        GuardDialogueProcessor.register(); // no-op if Guard Villagers not installed

        // Restore name tags after bubbles expire — runs across all worlds every tick
        // so it doesn't depend on a villager happening to tick in the right world.
        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getWorlds().forEach(CampfireConversation::restoreNames));

        // Hitting a villager: anger reaction + reputation loss (consolidated)
        // Using Fabric's event — VillagerEntity doesn't override damage() so a mixin would fail.

        // Reputation gain when player kills a mob near a villager
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (!(killer instanceof PlayerEntity)) return;
            PlayerEntity player = (PlayerEntity) killer;
            if (!(killed instanceof Monster)) return;
            // During an active raid, individual mob kills don't give rep — only raid victory does
            if (world instanceof ServerWorld && ((ServerWorld) world).getRaidAt(player.getBlockPos()) != null) return;
            // Find villagers within 10 blocks of the slain mob — the player protected them
            world.getEntitiesByClass(VillagerEntity.class,
                    killed.getBoundingBox().expand(10.0), v -> true).forEach(villager -> {
                ReputationSystem.addReputation(villager, player, 4);
                // Grateful bubble
                if (villager.getRandom().nextFloat() < 0.5f) {
                    CampfireConversation.spawnSpeech(villager,
                            GRATEFUL_LINES[villager.getRandom().nextInt(GRATEFUL_LINES.length)],
                            (net.minecraft.server.world.ServerWorld) world);
                }
            });
        });

        // Villager death reaction — nearby villagers say scared/mourning lines
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed) -> {
            if (!LivingVillagesConfig.get().deathReaction) return;
            if (!(killed instanceof VillagerEntity)) return;
            VillagerEntity deadVillager = (VillagerEntity) killed;
            if (!(world instanceof ServerWorld)) return;
            ServerWorld sw = (ServerWorld) world;
            String[] lines = deadVillager.isBaby() ? DEATH_LINES_BABY : DEATH_LINES;
            sw.getEntitiesByClass(VillagerEntity.class,
                    deadVillager.getBoundingBox().expand(12.0),
                    v -> v.isAlive() && v != deadVillager).forEach(witness -> {
                if (witness.getRandom().nextFloat() < 0.7f)
                    CampfireConversation.spawnSpeech(witness,
                            lines[witness.getRandom().nextInt(lines.length)], sw);
            });
        });

        // When a player breaks a chest they placed, stop tracking it
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            PlayerChestTracker.remove(pos);
            return true;
        });

        // Caught stealing: player opens a chest near a Stranger-reputation villager
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            var block = world.getBlockState(hitResult.getBlockPos()).getBlock();
            boolean isStorage = block instanceof AbstractChestBlock || block instanceof BarrelBlock;
            if (!isStorage) return ActionResult.PASS;
            // Skip chests the player themselves placed
            if (PlayerChestTracker.isPlayerChest(hitResult.getBlockPos())) return ActionResult.PASS;

            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

            // Villagers nearby react
            world.getEntitiesByClass(VillagerEntity.class,
                    new Box(hitResult.getBlockPos()).expand(8.0), v -> true).forEach(villager -> {
                if (villager.isBaby()) return; // babies don't react to stealing
                // Sleeping / napping villagers don't notice — check BEFORE reputation penalty
                if (villager.isSleeping()) return;
                if (((IVillagerBehaviorState) villager).livingvillages$getNapTimer() > 0) return;
                if (ReputationSystem.getLevel(villager.getUuid(), player.getUuid()) > 0) return;
                ReputationSystem.addReputation(villager, player, -10);
                // Shout the theft line then run to the nearest golem/guard
                CampfireConversation.spawnSpeech(villager,
                        STEALING_LINES[villager.getRandom().nextInt(STEALING_LINES.length)], sw);
                AngerBehavior.runToProtector(villager, player, sw);
            });

            // Guards nearby attack immediately if Guard Villagers is installed
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("guardvillagers")) {
                sw.getEntitiesByClass(net.minecraft.entity.mob.PathAwareEntity.class,
                        new Box(hitResult.getBlockPos()).expand(16.0), guard -> {
                            net.minecraft.util.Identifier id =
                                    net.minecraft.registry.Registries.ENTITY_TYPE.getId(guard.getType());
                            return id != null && "guardvillagers".equals(id.getNamespace());
                        }).forEach(guard -> {
                    // Set the thief as the guard's attack target
                    if (guard instanceof net.minecraft.entity.mob.MobEntity) {
                        ((net.minecraft.entity.mob.MobEntity) guard).setTarget(player);
                    }
                });
            }
            return ActionResult.PASS;
        });

        // Hitting a villager loses reputation with them
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof VillagerEntity villager)) return true;
            if (!(source.getAttacker() instanceof PlayerEntity player)) return true;
            if (!(entity.getWorld() instanceof ServerWorld world)) return true;
            // -8 rep per hit
            ReputationSystem.addReputation(villager, player, -15);
            AngerBehavior.onHit(villager, player, world, (IVillagerBehaviorState) villager);
            return true;
        });

        // Right-click a gift-bearing villager to accept the item.
        // Use the hand-item check (same as the mixin) — most reliable signal.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(entity instanceof VillagerEntity villager)) return ActionResult.PASS;

            // Always allow name tags through — they're applied before interactMob
            // so our blocking would prevent them from working.
            if (player.getStackInHand(hand).isOf(net.minecraft.item.Items.NAME_TAG)) {
                return ActionResult.PASS;
            }

            // Baby villager dismissal — right-click stops them following you
            if (villager.isBaby() && hand == Hand.MAIN_HAND
                    && !world.isClient() && world instanceof ServerWorld sw) {
                if (BabyBehavior.tryDismiss(villager, player, sw)) {
                    return ActionResult.SUCCESS;
                }
            }

            // Gift pickup — block trade screen on both sides when villager holds a gift.
            // Exception: fishing rod is NOT a gift, allow normal trading.
            if (!villager.getStackInHand(Hand.MAIN_HAND).isEmpty()
                    && !villager.getStackInHand(Hand.MAIN_HAND).isOf(net.minecraft.item.Items.FISHING_ROD)) {
                if (!world.isClient() && hand == Hand.MAIN_HAND
                        && world instanceof ServerWorld sw) {
                    GiftBehavior.tryAccept(villager, player, sw);
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                literal("lv")
                    .then(literal("clearbubbles")
                        .executes(LivingVillages::clearBubbles)
                    )
            )
        );
    }

    private static int clearBubbles(CommandContext<ServerCommandSource> ctx) {
        int removed = CampfireConversation.clearAll(ctx.getSource().getServer());
        ctx.getSource().sendFeedback(
            () -> Text.literal("[LivingVillages] Removed " + removed + " speech bubble(s)."),
            false
        );
        return removed;
    }
}
