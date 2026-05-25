package com.cobblemonbridge.commands

import com.cobblemonbridge.CobblemonBridge
import com.cobblemonbridge.economy.EconomyBridge
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Short aliases for the most-used sub-commands across our in-house mods. Players don't have to
 * remember which mod owns which command — they just type `/buy`, `/challenge`, etc. Each alias
 * resolves to the upstream command via `performPrefixedCommand` so we don't duplicate
 * permission/validation logic.
 *
 * NeoEssentials's economy commands (`/balance`, `/pay`, `/baltop`) are disabled in
 * `config/neoessentials/config.json` because they use NE's separate balance, not the cobbledollar
 * balance the rest of the server runs on. We replace `/pay` and add `/money` here, both backed
 * by cobblemon-economy.
 */
object CommandAliases {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        registerMarketAliases(dispatcher)
        registerRankedAliases(dispatcher)
        registerEconomyAliases(dispatcher)
    }

    // ─── /market shortcuts ─────────────────────────────────────────────────
    // /buy and /sell were retired — trades now happen exclusively at the shopkeeper NPC
    // (`/function server:market/spawn_npc`). /prices stays as a read-only alias since it
    // doesn't transact.
    private fun registerMarketAliases(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // /prices — full lineup, or one item's history
        dispatcher.register(
            Commands.literal("prices")
                .executes { ctx -> forward(ctx.source, "market prices") }
                .then(Commands.argument("item", StringArgumentType.greedyString())
                    .executes { ctx ->
                        val item = StringArgumentType.getString(ctx, "item")
                        forward(ctx.source, "market price $item")
                    }
                )
        )
    }

    // ─── /ranked shortcuts ─────────────────────────────────────────────────
    private fun registerRankedAliases(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // /challenge <player>
        dispatcher.register(
            Commands.literal("challenge")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes { ctx ->
                        val target = EntityArgument.getPlayer(ctx, "player")
                        forward(ctx.source, "ranked challenge ${target.gameProfile.name}")
                    }
                )
        )
        dispatcher.register(Commands.literal("accept").executes { ctx -> forward(ctx.source, "ranked accept") })
        dispatcher.register(Commands.literal("decline").executes { ctx -> forward(ctx.source, "ranked decline") })
        // /stats [player]
        dispatcher.register(
            Commands.literal("stats")
                .executes { ctx -> forward(ctx.source, "ranked stats") }
                .then(Commands.argument("player", StringArgumentType.string())
                    .executes { ctx ->
                        val name = StringArgumentType.getString(ctx, "player")
                        forward(ctx.source, "ranked stats $name")
                    }
                )
        )
    }

    // ─── /money + /pay (cobblemon-economy backed) ──────────────────────────
    private fun registerEconomyAliases(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // /money — show your own balance
        dispatcher.register(
            Commands.literal("money")
                .executes { ctx ->
                    val player = ctx.source.player ?: return@executes 0
                    showBalance(player, player.uuid, "Your")
                    1
                }
                .then(Commands.argument("player", EntityArgument.player())
                    .requires { it.hasPermission(2) }  // peeking at other players requires perms
                    .executes { ctx ->
                        val player = ctx.source.player ?: return@executes 0
                        val target = EntityArgument.getPlayer(ctx, "player")
                        showBalance(player, target.uuid, "${target.gameProfile.name}'s")
                        1
                    }
                )
        )
        // /balance alias for /money — players reach for it instinctively
        dispatcher.register(
            Commands.literal("balance")
                .executes { ctx ->
                    val player = ctx.source.player ?: return@executes 0
                    showBalance(player, player.uuid, "Your")
                    1
                }
        )
        // /pay <player> <amount> — cobbledollars transfer
        dispatcher.register(
            Commands.literal("pay")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes { ctx ->
                            val sender = ctx.source.player ?: return@executes 0
                            val target = EntityArgument.getPlayer(ctx, "player")
                            val amount = IntegerArgumentType.getInteger(ctx, "amount")
                            handlePay(sender, target, amount)
                            1
                        }
                    )
                )
        )
    }

    private fun showBalance(viewer: ServerPlayer, target: UUID, who: String) {
        val balance = EconomyBridge.getBalance(target)
        viewer.sendSystemMessage(Component.literal("§6$who balance: §a$$balance"))
    }

    private fun handlePay(sender: ServerPlayer, target: ServerPlayer, amount: Int) {
        if (sender.uuid == target.uuid) {
            sender.sendSystemMessage(Component.literal("§c[Pay] You can't pay yourself."))
            return
        }
        val senderBalance = EconomyBridge.getBalance(sender.uuid)
        if (senderBalance < amount) {
            sender.sendSystemMessage(Component.literal("§c[Pay] Insufficient funds — have §6$$senderBalance§c, need §6$$amount§c."))
            return
        }
        if (!EconomyBridge.withdraw(sender.uuid, amount)) {
            sender.sendSystemMessage(Component.literal("§c[Pay] Withdrawal failed. Try again."))
            return
        }
        EconomyBridge.deposit(target.uuid, amount)
        sender.sendSystemMessage(Component.literal("§a[Pay] §fSent §6$$amount§f to §e${target.gameProfile.name}§f."))
        target.sendSystemMessage(Component.literal("§a[Pay] §fReceived §6$$amount§f from §e${sender.gameProfile.name}§f."))
        CobblemonBridge.logger.info(
            "/pay: {} -> {} for {}",
            sender.gameProfile.name, target.gameProfile.name, amount,
        )
    }

    /** Run [command] (without leading slash) with the player's source. Returns 1 on success. */
    private fun forward(source: CommandSourceStack, command: String): Int {
        val player = source.player ?: return 0
        val src = source.withPermission(4).withSuppressedOutput()
        player.server.commands.performPrefixedCommand(src, command)
        return 1
    }
}
