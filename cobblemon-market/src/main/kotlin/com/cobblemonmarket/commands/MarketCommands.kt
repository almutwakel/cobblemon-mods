package com.cobblemonmarket.commands

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.config.ItemConfig
import com.cobblemonmarket.config.MarketConfig
import com.cobblemonmarket.gui.ShopGui
import com.cobblemonmarket.pricing.PricingEngine
import com.cobblemonmarket.shop.ShopkeeperManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.math.BigDecimal
import java.util.UUID

object MarketCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("market")
                .then(Commands.literal("prices")
                    .executes { ctx ->
                        showPrices(ctx.source)
                        1
                    }
                )
                .then(Commands.literal("version")
                    .executes { ctx ->
                        val version = FabricLoader.getInstance().getModContainer(CobblemonMarket.MOD_ID)
                            .map { it.metadata.version.friendlyString }
                            .orElse("unknown")
                        ctx.source.sendSystemMessage(Component.literal("[Market] Cobblemon Market v$version"))
                        1
                    }
                )
                .then(Commands.literal("leaderboard")
                    .executes { ctx ->
                        showLeaderboard(ctx.source)
                        1
                    }
                )
                .then(Commands.literal("history")
                    .then(Commands.argument("item", StringArgumentType.greedyString())
                        .suggests { _, builder ->
                            CobblemonMarket.items.keys.forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            showHistory(ctx.source, StringArgumentType.getString(ctx, "item"))
                            1
                        }
                    )
                )
                .then(Commands.literal("npc")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val player = ctx.source.playerOrException
                                createNpc(player, StringArgumentType.getString(ctx, "name"))
                                1
                            }
                        )
                    )
                    .then(Commands.literal("remove")
                        .executes { ctx ->
                            removeNpc(ctx.source.playerOrException)
                            1
                        }
                    )
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("setfactor")
                        .then(Commands.argument("item", StringArgumentType.string())
                            .suggests { _, builder ->
                                CobblemonMarket.items.keys.forEach { builder.suggest(it) }
                                builder.buildFuture()
                            }
                            .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes { ctx ->
                                    val itemId = StringArgumentType.getString(ctx, "item")
                                    val value = DoubleArgumentType.getDouble(ctx, "value")
                                    setFactor(ctx.source, itemId, value)
                                    1
                                }
                            )
                        )
                    )
                    .then(Commands.literal("reload")
                        .executes { ctx ->
                            reload(ctx.source)
                            1
                        }
                    )
                )
        )
    }

    private fun showPrices(source: CommandSourceStack) {
        val items = CobblemonMarket.items
        val config = CobblemonMarket.config
        val store = CobblemonMarket.marketStore

        source.sendSystemMessage(Component.literal("[Market] === Current Prices ==="))

        for ((itemId, entry) in items) {
            val state = store.getOrCreate(itemId)
            val sellCount = state.transactions.count { it.type == "sell" }
            val buyCount = state.transactions.count { it.type == "buy" }

            val buyPrice = PricingEngine.buyPrice(entry.baseSellPrice, state.priceFactor, config.spreadBase)
            val sellPrice = PricingEngine.sellPrice(
                entry.baseSellPrice, state.priceFactor,
                sellCount, buyCount, config.spreadBase, config.spreadExtra
            )
            val factorPercent = (state.priceFactor * 100).toInt()

            source.sendSystemMessage(Component.literal(
                "  ${ShopGui.formatItemName(itemId)}: Sell $sellPrice | Buy $buyPrice ($factorPercent%)"
            ))
        }
    }

    private fun resolveItemId(input: String): String? {
        if (input in CobblemonMarket.items) return input
        // Try adding common namespaces
        for (ns in listOf("cobblemon", "minecraft")) {
            val full = "$ns:$input"
            if (full in CobblemonMarket.items) return full
        }
        return null
    }

    private fun showHistory(source: CommandSourceStack, itemId: String) {
        val resolved = resolveItemId(itemId)
        if (resolved == null) {
            source.sendSystemMessage(Component.literal("[Market] Unknown item: $itemId"))
            return
        }
        val itemId = resolved
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val sells = state.transactions.count { it.type == "sell" }
        val buys = state.transactions.count { it.type == "buy" }
        val total = state.transactions.size
        val factorPercent = (state.priceFactor * 100).toInt()

        source.sendSystemMessage(Component.literal("[Market] History for $itemId:"))
        source.sendSystemMessage(Component.literal("  Factor: $factorPercent%"))
        source.sendSystemMessage(Component.literal("  Last $total transactions: $sells sells, $buys buys"))
        source.sendSystemMessage(Component.literal(
            "  Skew: ${if (total < 2) "N/A" else "${"%.1f".format(sells.toDouble() / total * 100)}% sells"}"
        ))
    }

    private fun createNpc(player: ServerPlayer, name: String) {
        if (ShopkeeperManager.spawnShopkeeper(player, name)) {
            player.sendSystemMessage(Component.literal("[Market] Shopkeeper '$name' created."))
        } else {
            player.sendSystemMessage(Component.literal("[Market] Failed to create shopkeeper."))
        }
    }

    private fun removeNpc(player: ServerPlayer) {
        if (ShopkeeperManager.removeNearest(player)) {
            player.sendSystemMessage(Component.literal("[Market] Nearest shopkeeper removed."))
        } else {
            player.sendSystemMessage(Component.literal("[Market] No shopkeeper found within 5 blocks."))
        }
    }

    private fun setFactor(source: CommandSourceStack, itemId: String, value: Double) {
        if (itemId !in CobblemonMarket.items) {
            source.sendSystemMessage(Component.literal("[Market] Unknown item: $itemId"))
            return
        }
        CobblemonMarket.marketStore.setFactor(itemId, value)
        source.sendSystemMessage(Component.literal(
            "[Market] Set $itemId factor to ${(value * 100).toInt()}%"))
    }

    private fun showLeaderboard(source: CommandSourceStack) {
        val config = CobblemonMarket.config
        val knownUuids = CobblemonMarket.playerSpendStore.getAllKnownUuids()

        if (knownUuids.isEmpty()) {
            source.sendSystemMessage(Component.literal("[Market] No players have used the market yet."))
            return
        }

        val balances = mutableListOf<Triple<String, String, Int>>()
        for (uuidStr in knownUuids) {
            val spendData = CobblemonMarket.playerSpendStore.getAll()[uuidStr] ?: continue
            val balance = getBalanceForUuid(UUID.fromString(uuidStr))
            balances.add(Triple(uuidStr, spendData.name, balance))
        }

        balances.sortByDescending { it.third }

        source.sendSystemMessage(Component.literal("[Market] === Wealth Leaderboard ==="))
        val topN = balances.take(config.leaderboardSize)
        topN.forEachIndexed { i, (_, name, balance) ->
            source.sendSystemMessage(Component.literal(
                "  ${i + 1}. $name: $balance PokeDollars"
            ))
        }

        val player = source.player ?: return
        val playerUuid = player.uuid.toString()
        val playerIndex = balances.indexOfFirst { it.first == playerUuid }
        if (playerIndex >= config.leaderboardSize) {
            val (_, name, balance) = balances[playerIndex]
            source.sendSystemMessage(Component.literal("  ---"))
            source.sendSystemMessage(Component.literal(
                "  ${playerIndex + 1}. $name: $balance PokeDollars"
            ))
        }
    }

    private fun getBalanceForUuid(uuid: UUID): Int {
        return try {
            val loader = FabricLoader.getInstance()
            val entrypoints = loader.getEntrypointContainers("main", ModInitializer::class.java)
            val economyEntry = entrypoints.firstOrNull { it.provider.metadata.id == "cobblemon-economy" }
                ?: return 0
            val economyInstance = economyEntry.entrypoint
            val manager = economyInstance.javaClass.getMethod("getEconomyManager").invoke(economyInstance)
            val method = manager.javaClass.getMethod("getBalance", java.util.UUID::class.java)
            val balance = method.invoke(manager, uuid) as BigDecimal
            balance.toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun reload(source: CommandSourceStack) {
        val configDir = FabricLoader.getInstance().configDir
        CobblemonMarket.config = MarketConfig.load(configDir)
        CobblemonMarket.items = ItemConfig.load(configDir)
        source.sendSystemMessage(Component.literal(
            "[Market] Config reloaded. ${CobblemonMarket.items.size} items loaded."))
    }
}
