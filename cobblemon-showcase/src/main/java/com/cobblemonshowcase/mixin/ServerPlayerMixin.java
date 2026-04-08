package com.cobblemonshowcase.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"), cancellable = true)
    private void cobblemonShowcase$decorateChat(PlayerChatMessage message, CallbackInfo ci) {
        ServerPlayer sender = this.player;
        var server = sender.getServer();
        if (server == null) return;

        String senderName = sender.getName().getString();
        MutableComponent nameComponent = Component.literal("<" + senderName + ">")
            .withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/showcase " + senderName))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to view profile")))
            );

        Component content = message.decoratedContent();
        MutableComponent fullMessage = Component.empty()
            .append(nameComponent)
            .append(Component.literal(" "))
            .append(content);

        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            recipient.sendSystemMessage(fullMessage);
        }

        ci.cancel();
    }
}
