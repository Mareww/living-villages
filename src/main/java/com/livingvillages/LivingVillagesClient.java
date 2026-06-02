package com.livingvillages;

import com.livingvillages.client.ReputationClientCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

public class LivingVillagesClient implements ClientModInitializer {

    public static final Identifier REPUTATION_PACKET = new Identifier("livingvillages", "reputation");

    @Override
    public void onInitializeClient() {
        // Receive reputation data from server: [entityId (int), points (int)]
        ClientPlayNetworking.registerGlobalReceiver(REPUTATION_PACKET, (client, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            int points   = buf.readInt();
            client.execute(() -> ReputationClientCache.set(entityId, points));
        });
    }
}
