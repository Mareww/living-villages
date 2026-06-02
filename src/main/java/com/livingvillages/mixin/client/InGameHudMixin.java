package com.livingvillages.mixin.client;

import com.livingvillages.behavior.ReputationSystem;
import com.livingvillages.client.ReputationClientCache;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    private static final Identifier ICONS     = new Identifier("textures/gui/icons.png");
    private static final float      TEXT_SCALE = 0.75f;
    // Target bar dimensions — vanilla is 182×5
    private static final int        BAR_W      = 150;
    private static final int        BAR_H      = 5;

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void lv$onRenderXpBar(DrawContext context, int x, CallbackInfo ci) {
        if (!com.livingvillages.LivingVillagesConfig.get().reputationXpBar) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(mc.targetedEntity instanceof VillagerEntity villager)) return;

        int pts = ReputationClientCache.get(villager.getId());
        if (pts < 0) return;

        ci.cancel();

        int level   = Math.min(10, pts / 10);
        int color   = repColor(level);
        int scaledH = mc.getWindow().getScaledHeight();
        int cx      = mc.getWindow().getScaledWidth() / 2;
        int barY    = scaledH - 32 + 3;   // vanilla XP bar Y

        var    tr         = mc.textRenderer;
        String label      = ReputationSystem.LEVEL_NAMES[level];
        int    scaledLblW = (int)(tr.getWidth(label) * TEXT_SCALE);
        int    gap        = 4;

        // Centre label+bar together
        int totalW = scaledLblW + gap + BAR_W;
        int startX = cx - totalW / 2;
        int barX   = startX + scaledLblW + gap;

        // Scale factors: maps the vanilla 182×5 texture to BAR_W×BAR_H on screen
        float sx = (float) BAR_W / 182f;
        float sy = (float) BAR_H / 5f;

        // ── Background — full vanilla texture, scaled down ────────────────────
        context.getMatrices().push();
        context.getMatrices().translate(barX, barY, 0);
        context.getMatrices().scale(sx, sy, 1f);
        context.drawTexture(ICONS, 0, 0, 0, 64, 182, 5);
        context.getMatrices().pop();

        // ── Coloured fill — same scale, proportional width ────────────────────
        // Convert fill percentage back to vanilla texture width before scaling
        int filledTex = (int)(182 * pts / 100f);
        if (filledTex > 0) {
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >>  8) & 0xFF) / 255f;
            float b = ( color        & 0xFF) / 255f;
            RenderSystem.setShaderColor(r, g, b, 1f);
            context.getMatrices().push();
            context.getMatrices().translate(barX, barY, 0);
            context.getMatrices().scale(sx, sy, 1f);
            context.drawTexture(ICONS, 0, 0, 0, 69, filledTex, 5);
            context.getMatrices().pop();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        // ── Label — to the left, vertically centred with bar ─────────────────
        int labelY = barY - (int)((tr.fontHeight * TEXT_SCALE - BAR_H) / 2) - 1;

        // Transparent black background box — 1px bigger on each side, shifted 1px down
        context.fill(
                startX - 2,
                labelY + 1,
                startX + scaledLblW + 2,
                labelY + (int)(tr.fontHeight * TEXT_SCALE) + 2,
                0x80000000);

        context.getMatrices().push();
        context.getMatrices().translate(startX, labelY + 1, 0);
        context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        context.drawText(tr, label, 0, 0, color, false);
        context.getMatrices().pop();
    }

    private static int repColor(int level) {
        if (level < 3) return 0xFFE85030;
        if (level < 5) return 0xFF4ABA3C;
        if (level < 8) return 0xFF4090FF;
        return              0xFF00E0E0;
    }
}
