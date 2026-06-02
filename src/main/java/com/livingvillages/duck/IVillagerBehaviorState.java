package com.livingvillages.duck;

import net.minecraft.util.math.BlockPos;

public interface IVillagerBehaviorState {
    int livingvillages$getConversationCooldown();
    void livingvillages$setConversationCooldown(int cooldown);

    int livingvillages$getWeatherCooldown();
    void livingvillages$setWeatherCooldown(int cooldown);

    int livingvillages$getCampfireCooldown();
    void livingvillages$setCampfireCooldown(int cooldown);

    int livingvillages$getAmbientCooldown();
    void livingvillages$setAmbientCooldown(int cooldown);

    boolean livingvillages$isSeekingShelter();
    void livingvillages$setSeekingShelter(boolean seeking);

    boolean livingvillages$wasRaining();
    void livingvillages$setWasRaining(boolean raining);

    BlockPos livingvillages$getCampfireTarget();
    void livingvillages$setCampfireTarget(BlockPos pos);

    BlockPos livingvillages$getShelterTarget();
    void livingvillages$setShelterTarget(BlockPos pos);

    int livingvillages$getCelebrationTimer();
    void livingvillages$setCelebrationTimer(int timer);

    int livingvillages$getCampfireSeat();
    void livingvillages$setCampfireSeat(int seat);

    java.util.UUID livingvillages$getAngryTarget();
    void livingvillages$setAngryTarget(java.util.UUID uuid);

    int livingvillages$getAngerCooldown();
    void livingvillages$setAngerCooldown(int ticks);

    int livingvillages$getNapTimer();
    void livingvillages$setNapTimer(int ticks);
}
