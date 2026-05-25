package com.cobblemonbridge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Neutralizes RCTmod's "your party is over the level cap" battle gate.
 *
 * <p>{@code TrainerMob.canBattleAgainst} refuses the battle if
 * {@code TrainerManager.getPlayerLevel(player) > playerData.getLevelCap()}. On our server the
 * level cap is policy-driven by gym progression, and we already downlevel the player's team
 * inside {@code GymBattleAdjustHook} at battle start — so the gate is redundant and only blocks
 * players whose collection has outgrown their gym count.
 *
 * <p>We use MixinExtras' {@code @ModifyExpressionValue} to rewrite just the int return value
 * of the {@code getPlayerLevel} call inside {@code canBattleAgainst} to 0, making the
 * comparison always false. This works without RCTmod on our compile classpath — unlike
 * {@code @Redirect}, the handler signature only needs the return type, not the receiver type.
 *
 * <p>Scope: only the invocation inside {@code canBattleAgainst}. Other callers of
 * {@code getPlayerLevel} (spawn weighting, level-cap progression, etc.) are unaffected.
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob", remap = false)
public class TrainerMobMixin {

    @ModifyExpressionValue(
        method = "canBattleAgainst",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gitlab/srcmc/rctmod/api/service/TrainerManager;getPlayerLevel(Lnet/minecraft/world/entity/player/Player;)I"
        )
    )
    private int cobblemonbridge$bypassLevelCapGate(int originalPlayerLevel) {
        return 0;
    }
}
