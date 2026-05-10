package com.cobblemonmarket.commands

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.config.ItemConfig
import com.cobblemonmarket.config.MarketConfig
import com.cobblemonmarket.economy.EconomyBridge
import com.cobblemonmarket.data.Candle
import com.cobblemonmarket.data.PriceHistory
import java.time.ZoneId
import com.cobblemonmarket.economy.TradeOps
import com.cobblemonmarket.economy.TradeResult
import com.cobblemonmarket.gui.ShopMenuProvider
import com.cobblemonmarket.pricing.PricingEngine
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths
import java.util.UUID

object MarketCommands {

    /** Max number of candles shown in `/market price`; older candles are dropped from the head. */
    private const val CANDLE_LIMIT = 30

    /** Vertical resolution of the candlestick chart, in chat lines. */
    private const val CANDLE_ROWS = 6

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("market")
                .executes { ctx ->
                    showHelp(ctx.source, ctx.source.hasPermission(4))
                    1
                }
                .then(Commands.literal("help")
                    .executes { ctx ->
                        showHelp(ctx.source, ctx.source.hasPermission(4))
                        1
                    }
                )
                .then(Commands.literal("prices")
                    .executes { ctx ->
                        showPrices(ctx.source)
                        1
                    }
                )
                .then(Commands.literal("version")
                    .executes { ctx ->
                        val version = ModList.get().getModContainerById(CobblemonMarket.MOD_ID)
                            .map { it.modInfo.version.toString() }
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
                            // Suggest short names so users don't have to type colons (which
                            // Brigadier StringArgumentType.string() rejects unquoted).
                            // resolveItemId() handles the short→full mapping bidirectionally.
                            CobblemonMarket.items.keys.forEach {
                                builder.suggest(it.substringAfterLast(':'))
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            showHistory(ctx.source, StringArgumentType.getString(ctx, "item"))
                            1
                        }
                    )
                )
                .then(Commands.literal("price")
                    .then(Commands.argument("item", StringArgumentType.greedyString())
                        .suggests { _, builder ->
                            CobblemonMarket.items.keys.forEach {
                                builder.suggest(it.substringAfterLast(':'))
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            showPriceChart(ctx.source, StringArgumentType.getString(ctx, "item"))
                            1
                        }
                    )
                )
                .then(Commands.literal("open")
                    .executes { ctx ->
                        ShopMenuProvider.open(ctx.source.playerOrException)
                        1
                    }
                )
                .then(Commands.literal("buy")
                    .then(Commands.argument("item", StringArgumentType.string())
                        .suggests { _, builder ->
                            // Suggest short names so users don't have to type colons (which
                            // Brigadier StringArgumentType.string() rejects unquoted).
                            // resolveItemId() handles the short→full mapping bidirectionally.
                            CobblemonMarket.items.keys.forEach {
                                builder.suggest(it.substringAfterLast(':'))
                            }
                            builder.buildFuture()
                        }
                        .then(Commands.argument("qty", IntegerArgumentType.integer(1, 1024))
                            .executes { ctx ->
                                val sp = ctx.source.playerOrException
                                val itemId = resolveItemId(StringArgumentType.getString(ctx, "item"))
                                val qty = IntegerArgumentType.getInteger(ctx, "qty")
                                if (itemId == null) {
                                    ctx.source.sendSystemMessage(Component.literal("§c[Market] Unknown item"))
                                    return@executes 0
                                }
                                reportTrade(ctx.source, "BUY", sp, itemId, qty, TradeOps.buy(sp, itemId, qty))
                            }
                        )
                    )
                )
                .then(Commands.literal("sell")
                    .then(Commands.argument("item", StringArgumentType.string())
                        .suggests { _, builder ->
                            // Suggest short names so users don't have to type colons (which
                            // Brigadier StringArgumentType.string() rejects unquoted).
                            // resolveItemId() handles the short→full mapping bidirectionally.
                            CobblemonMarket.items.keys.forEach {
                                builder.suggest(it.substringAfterLast(':'))
                            }
                            builder.buildFuture()
                        }
                        .then(Commands.argument("qty", IntegerArgumentType.integer(1, 1024))
                            .executes { ctx ->
                                val sp = ctx.source.playerOrException
                                val itemId = resolveItemId(StringArgumentType.getString(ctx, "item"))
                                val qty = IntegerArgumentType.getInteger(ctx, "qty")
                                if (itemId == null) {
                                    ctx.source.sendSystemMessage(Component.literal("§c[Market] Unknown item"))
                                    return@executes 0
                                }
                                reportTrade(ctx.source, "SELL", sp, itemId, qty, TradeOps.sell(sp, itemId, qty))
                            }
                        )
                    )
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("trade")
                        .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.literal("buy")
                                .then(Commands.argument("item", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        CobblemonMarket.items.keys.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .then(Commands.argument("qty", IntegerArgumentType.integer(1, 1024))
                                        .executes { ctx ->
                                            val target = EntityArgument.getPlayer(ctx, "target")
                                            val itemId = resolveItemId(StringArgumentType.getString(ctx, "item"))
                                            val qty = IntegerArgumentType.getInteger(ctx, "qty")
                                            if (itemId == null) {
                                                ctx.source.sendSystemMessage(Component.literal("§c[Market] Unknown item"))
                                                return@executes 0
                                            }
                                            reportTrade(ctx.source, "BUY (admin for ${target.name.string})", target, itemId, qty, TradeOps.buy(target, itemId, qty))
                                        }
                                    )
                                )
                            )
                            .then(Commands.literal("sell")
                                .then(Commands.argument("item", StringArgumentType.string())
                                    .suggests { _, builder ->
                                        CobblemonMarket.items.keys.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .then(Commands.argument("qty", IntegerArgumentType.integer(1, 1024))
                                        .executes { ctx ->
                                            val target = EntityArgument.getPlayer(ctx, "target")
                                            val itemId = resolveItemId(StringArgumentType.getString(ctx, "item"))
                                            val qty = IntegerArgumentType.getInteger(ctx, "qty")
                                            if (itemId == null) {
                                                ctx.source.sendSystemMessage(Component.literal("§c[Market] Unknown item"))
                                                return@executes 0
                                            }
                                            reportTrade(ctx.source, "SELL (admin for ${target.name.string})", target, itemId, qty, TradeOps.sell(target, itemId, qty))
                                        }
                                    )
                                )
                            )
                        )
                    )
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
                "  ${formatItemName(itemId)}: Sell $sellPrice | Buy $buyPrice ($factorPercent%)"
            ))
        }
    }

    /**
     * Resolves a user-typed item identifier to its full namespaced form.
     *
     * Accepts (case-insensitive, underscores/hyphens optional):
     *   "cobblemon:rare_candy", "rare_candy", "rarecandy", "RareCandy", "RARE_CANDY"  →  "cobblemon:rare_candy"
     *
     * Brigadier's StringArgumentType.string() rejects unquoted colons, so the typical
     * command path is short-form (no colon) anyway — the namespaced form still works
     * if quoted: /market buy "cobblemon:rare_candy" 5.
     */
    private fun resolveItemId(input: String): String? {
        val items = CobblemonMarket.items.keys
        if (input in items) return input
        val normalized = normalizeItemKey(input)
        return items.firstOrNull { id ->
            normalizeItemKey(id) == normalized || normalizeItemKey(id.substringAfterLast(':')) == normalized
        }
    }

    private fun normalizeItemKey(s: String): String =
        s.lowercase().replace("_", "").replace("-", "").replace(" ", "")

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

    private fun formatItemName(itemId: String): String =
        itemId.substringAfterLast(':').split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    /**
     * Renders a multi-line candlestick chart of the price history for one item.
     *
     * One candle per "transaction group" (consecutive trades by the same player on the
     * same calendar day). Open = price right before the group, Close = price right after.
     * Wick covers the high/low touched during the group. Green when close > open (price
     * rose; usually buys), red when close < open (usually sells), grey when unchanged.
     *
     * Below the candles, a single-line volume sparkline shows total qty per candle. The
     * legend underneath gives min/max/last and aggregate buy/sell volume.
     */
    private fun showPriceChart(source: CommandSourceStack, rawItemId: String) {
        val itemId = resolveItemId(rawItemId)
        if (itemId == null) {
            source.sendSystemMessage(Component.literal("§c[Market] Unknown item: $rawItemId"))
            return
        }
        val state = CobblemonMarket.marketStore.getOrCreate(itemId)
        val history = state.priceHistory
        val displayName = formatItemName(itemId)
        if (history.isEmpty()) {
            source.sendSystemMessage(Component.literal(
                "§e[Market] §f$displayName §7— no trades recorded yet."))
            return
        }

        val zone = try {
            ZoneId.of(CobblemonMarket.config.priceHistoryTimeZone)
        } catch (e: Exception) {
            CobblemonMarket.logger.warn(
                "Invalid priceHistoryTimeZone '{}'; falling back to system default",
                CobblemonMarket.config.priceHistoryTimeZone
            )
            ZoneId.systemDefault()
        }
        val candles = PriceHistory.groupIntoCandles(history, zone)
        val displayed = if (candles.size > CANDLE_LIMIT) candles.subList(candles.size - CANDLE_LIMIT, candles.size) else candles
        val factorPct = (state.priceFactor * 100).toInt()

        source.sendSystemMessage(Component.literal(
            "§e[Market] §f$displayName §7— ${candles.size} candle${if (candles.size == 1) "" else "s"} (${history.size} trades), factor §f$factorPct%"))

        for (line in renderCandleChart(displayed)) {
            source.sendSystemMessage(Component.literal(line))
        }

        // Aggregate stats from full history (not just displayed window).
        var totalQty = 0; var bought = 0; var minPrice = Int.MAX_VALUE; var maxPrice = Int.MIN_VALUE
        for (t in history) {
            totalQty += t.quantity
            if (t.type == "buy") bought += t.quantity
            if (t.pricePerUnit < minPrice) minPrice = t.pricePerUnit
            if (t.pricePerUnit > maxPrice) maxPrice = t.pricePerUnit
        }
        val sold = totalQty - bought
        val last = history.last().pricePerUnit
        source.sendSystemMessage(Component.literal(
            "§7Min §c\$$minPrice§7  Max §a\$$maxPrice§7  Last §f\$$last"))
        source.sendSystemMessage(Component.literal(
            "§7Volume §f$totalQty units §8(§a$bought bought§8 / §c$sold sold§8)"))
    }

    /**
     * Builds the chat-line representation of [candles] as CANDLE_ROWS rows of candle
     * bodies/wicks plus one row of volume bars. Returns the lines top-down.
     *
     * Each candle is one column wide. Body is `█`, wick is `│`, empty is space. Color
     * codes are emitted before each character so colors can mix within a row.
     */
    private fun renderCandleChart(candles: List<Candle>): List<String> {
        if (candles.isEmpty()) return listOf("§7(no candles yet)")

        var globalHigh = Int.MIN_VALUE
        var globalLow = Int.MAX_VALUE
        var maxVolume = 0
        for (c in candles) {
            if (c.high > globalHigh) globalHigh = c.high
            if (c.low < globalLow) globalLow = c.low
            if (c.volume > maxVolume) maxVolume = c.volume
        }
        val priceRange = (globalHigh - globalLow).coerceAtLeast(1)

        // Each row 0..CANDLE_ROWS-1 covers a slice of the price range, top first.
        // For row r, slice is [globalHigh - (r+1)*step, globalHigh - r*step].
        val step = priceRange.toDouble() / CANDLE_ROWS
        val out = mutableListOf<String>()
        for (row in 0 until CANDLE_ROWS) {
            val rowTop = globalHigh - row * step
            val rowBot = globalHigh - (row + 1) * step
            val sb = StringBuilder()
            for (c in candles) {
                val color = when {
                    c.close > c.open -> "§a"   // up — green
                    c.close < c.open -> "§c"   // down — red
                    else -> "§7"               // flat — grey
                }
                val bodyHi = maxOf(c.open, c.close).toDouble()
                val bodyLo = minOf(c.open, c.close).toDouble()
                val char = when {
                    rowTop < c.low.toDouble() -> ' '          // entirely below this candle's range
                    rowBot > c.high.toDouble() -> ' '         // entirely above this candle's range
                    rowTop >= bodyLo && rowBot <= bodyHi -> '█' // overlaps body → solid
                    else -> '│'                                // overlaps wick only
                }
                sb.append(color).append(char)
            }
            out.add(sb.toString())
        }

        // Visual gap between the candles and the volume row so the volume bars
        // don't get visually mistaken for the lowest candle row. Two blank rows
        // is enough on most chat clients to read as separation.
        out.add("§r")
        out.add("§r")

        // Volume sparkline — one block per candle, height proportional to qty.
        val blocks = "▁▂▃▄▅▆▇█"
        val volSb = StringBuilder("§7")
        val volMax = maxVolume.coerceAtLeast(1)
        for (c in candles) {
            val level = ((c.volume.toDouble() / volMax) * (blocks.length - 1)).toInt().coerceIn(0, blocks.length - 1)
            volSb.append(blocks[level])
        }
        out.add(volSb.toString())

        return out
    }

    private fun showHelp(source: CommandSourceStack, includeAdmin: Boolean) {
        val lines = mutableListOf(
            "§e[Market] §fCommands:",
            "§7  /market prices §f— show current buy/sell prices for all items",
            "§7  /market price <item> §f— price-history chart for one item",
            "§7  /market history <item> §f— recent price-movement summary for one item",
            "§7  /market leaderboard §f— top wealth (PokeDollars) on the server",
            "§7  /market open §f— open the chest UI (in-game player only)",
            "§7  /market buy <item> <qty> §f— buy via command (in-game player)",
            "§7  /market sell <item> <qty> §f— sell via command (in-game player)",
            "§7  /market version §f— show mod version",
        )
        if (includeAdmin) {
            lines += listOf(
                "§e[Market] §fAdmin (op level 4):",
                "§7  /market admin trade <player> buy|sell <item> <qty> §f— drive a trade for a target player (console-friendly)",
                "§7  /market admin setfactor <item> <0.0-1.0> §f— override price factor",
                "§7  /market admin reload §f— reload config + items.json from disk",
            )
        }
        lines += "§8Tip: item names accept short forms — \"rare_candy\", \"rarecandy\", \"RareCandy\" all work."
        lines.forEach { source.sendSystemMessage(Component.literal(it)) }
    }

    /**
     * Renders a TradeResult into chat for the command source. Returns 1 on success, 0 on failure
     * (Brigadier convention for command return codes).
     *
     * Sent to BOTH the source (e.g., the server console) and the affected player so admin trades
     * are visible to the target without needing the source to relay manually.
     */
    private fun reportTrade(
        source: CommandSourceStack,
        label: String,
        target: ServerPlayer,
        itemId: String,
        qty: Int,
        result: TradeResult,
    ): Int {
        val itemName = formatItemName(itemId)
        return when (result) {
            is TradeResult.Success -> {
                val factorPct = (result.newFactor * 100).toInt()
                val msg = Component.literal("§a[Market] $label $qty× $itemName for $${result.totalPrice} (factor → $factorPct%)")
                source.sendSystemMessage(msg)
                if (target.uuid != source.player?.uuid) target.sendSystemMessage(msg)
                1
            }
            is TradeResult.InsufficientBalance -> {
                source.sendSystemMessage(Component.literal("§c[Market] Insufficient balance for ${target.name.string}: have $${result.have}, need $${result.need}"))
                0
            }
            is TradeResult.InsufficientItems -> {
                source.sendSystemMessage(Component.literal("§c[Market] ${target.name.string} only has ${result.have}× ${formatItemName(result.itemId)} (need ${result.need})"))
                0
            }
            TradeResult.NoInventorySpace -> {
                source.sendSystemMessage(Component.literal("§c[Market] ${target.name.string} has no inventory space"))
                0
            }
            is TradeResult.UnknownItem -> {
                source.sendSystemMessage(Component.literal("§c[Market] Unknown item: ${result.itemId}"))
                0
            }
            TradeResult.EconomyFailed -> {
                source.sendSystemMessage(Component.literal("§c[Market] Cobblemon Economy unavailable"))
                0
            }
        }
    }

    private fun setFactor(source: CommandSourceStack, rawItemId: String, value: Double) {
        val itemId = resolveItemId(rawItemId) ?: run {
            source.sendSystemMessage(Component.literal("§c[Market] Unknown item: $rawItemId"))
            return
        }
        CobblemonMarket.marketStore.setFactor(itemId, value)
        source.sendSystemMessage(Component.literal(
            "§a[Market] Set ${formatItemName(itemId)} factor to ${(value * 100).toInt()}%"))
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

        // Comparator SAM (invokedynamic) instead of Kotlin's `sortByDescending` — the latter's
        // `$$inlined$` helper class fails to load under NeoForge + Sinytra Connector
        // (see cobblemon-ranked EloStore.getLeaderboard for the same fix).
        balances.sortWith(Comparator { a, b -> b.third.compareTo(a.third) })

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

    private fun getBalanceForUuid(uuid: UUID): Int = EconomyBridge.getBalance(uuid)

    private fun reload(source: CommandSourceStack) {
        val configDir = FMLPaths.CONFIGDIR.get()
        CobblemonMarket.config = MarketConfig.load(configDir)
        CobblemonMarket.items = ItemConfig.load(configDir)
        source.sendSystemMessage(Component.literal(
            "[Market] Config reloaded. ${CobblemonMarket.items.size} items loaded."))
    }
}
