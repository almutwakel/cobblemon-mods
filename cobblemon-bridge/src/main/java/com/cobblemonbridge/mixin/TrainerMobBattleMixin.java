package com.cobblemonbridge.mixin;

import com.cobblemonbridge.battle.GymBattleGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gate gym battles at the {@code TrainerMob.startBattleWith(Player)} entry — the choke point
 * for both interact paths:
 *   <ul>
 *     <li>Right-click via {@code EntityInteract} → RCT's interact handler → startBattleWith</li>
 *     <li>Force-battle-on-sight → AI directly calls startBattleWith (skips EntityInteract!)</li>
 *   </ul>
 *
 * The {@code @SubscribeEvent} handlers in {@code GymPrereqHook} / {@code GymBattleAdjustHook} /
 * {@code E4GauntletHook} miss the force-battle path entirely. Without this Mixin, a wandering
 * gym leader who locks eyes with the player will start a battle even when the player hasn't
 * beaten the prereq gym, and the asymmetric downlevel won't apply.
 *
 * Cancellation is signaled by {@code GymBattleGate.beforeStartBattle} returning false; we then
 * call {@code ci.cancel()} so the rest of startBattleWith doesn't run.
 *
 * <p>{@code remap = false} because RCTmod is a NeoForge mod (Mojmap names at runtime).
 */
@Mixin(targets = "com.gitlab.srcmc.rctmod.world.entities.TrainerMob", remap = false)
public class TrainerMobBattleMixin {

    @Inject(
        method = "startBattleWith(Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void cobblemonbridge$gateGymBattle(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        Entity self = (Entity)(Object)this;
        if (!GymBattleGate.INSTANCE.beforeStartBattle(self, serverPlayer)) {
            ci.cancel();
        }
    }
}
