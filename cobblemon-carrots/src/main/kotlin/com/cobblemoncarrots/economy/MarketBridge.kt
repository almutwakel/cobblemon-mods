package com.cobblemoncarrots.economy

import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reflection bridge into cobblemon-market for live carrot pricing.
 *
 * The healer asks two questions at prompt time — "what's the current per-carrot buy price?"
 * and "how many carrots does the market have in stock?" — and a third at confirm time:
 * "purchase N carrots at market price, deducting from the player's balance and the market's
 * stock atomically." Items aren't delivered to the inventory; the caller (the healer) consumes
 * them directly into a pokemon.heal().
 *
 * Why reflection: keeps this mod independent of cobblemon-market at compile time. If the market
 * mod is absent or its API shifts, [available] returns false and the healer falls back to its
 * flat `carrotPrice` config value.
 */
object MarketBridge {

    private val log = LoggerFactory.getLogger("cobblemon-carrots/market")
    private const val MARKET_CLASS = "com.cobblemonmarket.CobblemonMarket"
    private const val TRADEOPS_CLASS = "com.cobblemonmarket.economy.TradeOps"
    private const val PRICING_CLASS = "com.cobblemonmarket.pricing.PricingEngine"
    const val CARROT_ITEM_ID = "minecraft:carrot"

    @Volatile private var resolved: Boolean = false
    @Volatile private var unavailable: Boolean = false

    // Reflected handles — populated by [resolve], all non-null when [available] is true.
    private var itemsField: Field? = null              // CobblemonMarket.items: Map<String, ItemEntry>
    private var marketStoreField: Field? = null         // CobblemonMarket.marketStore: MarketStore
    private var entryBaseBuyPrice: Method? = null       // ItemEntry.getBaseBuyPrice(): Int
    private var entryBaseStock: Method? = null          // ItemEntry.getBaseStock(): Int
    private var entryElasticity: Method? = null         // ItemEntry.getElasticity(): Double
    private var storeGetOrCreate: Method? = null        // MarketStore.getOrCreate(String): ItemState
    private var stateStock: Method? = null              // ItemState.getStock(): Double
    private var pricingBuyPrice: Method? = null         // PricingEngine.buyPrice(Int, Double, Int, Double): Int
    private var tradeOpsInstance: Any? = null           // TradeOps singleton (object)
    private var tradeOpsBuyForConsumption: Method? = null
    private var tradeResultSuccess: Class<*>? = null
    private var tradeResultOutOfStock: Class<*>? = null
    private var tradeResultInsufficientBalance: Class<*>? = null
    private val warnedOnce = AtomicBoolean(false)

    private fun resolve(): Boolean {
        if (resolved) return !unavailable
        synchronized(this) {
            if (resolved) return !unavailable
            try {
                val market = Class.forName(MARKET_CLASS)
                itemsField = market.getDeclaredField("items").apply { isAccessible = true }
                marketStoreField = market.getDeclaredField("marketStore").apply { isAccessible = true }

                val itemEntryClass = Class.forName("com.cobblemonmarket.config.ItemEntry")
                entryBaseBuyPrice = itemEntryClass.getMethod("getBaseBuyPrice")
                entryBaseStock = itemEntryClass.getMethod("getBaseStock")
                entryElasticity = itemEntryClass.getMethod("getElasticity")

                val storeClass = Class.forName("com.cobblemonmarket.data.MarketStore")
                storeGetOrCreate = storeClass.getMethod("getOrCreate", String::class.java)

                val stateClass = Class.forName("com.cobblemonmarket.data.ItemState")
                stateStock = stateClass.getMethod("getStock")

                val pricing = Class.forName(PRICING_CLASS)
                pricingBuyPrice = pricing.getMethod(
                    "buyPrice",
                    Int::class.javaPrimitiveType, Double::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Double::class.javaPrimitiveType,
                )

                val tradeOps = Class.forName(TRADEOPS_CLASS)
                tradeOpsInstance = tradeOps.getField("INSTANCE").get(null)
                tradeOpsBuyForConsumption = tradeOps.getMethod(
                    "buyForConsumption",
                    ServerPlayer::class.java, String::class.java, Int::class.javaPrimitiveType,
                )

                tradeResultSuccess = Class.forName("com.cobblemonmarket.economy.TradeResult\$Success")
                tradeResultOutOfStock = Class.forName("com.cobblemonmarket.economy.TradeResult\$OutOfStock")
                tradeResultInsufficientBalance = Class.forName("com.cobblemonmarket.economy.TradeResult\$InsufficientBalance")
                resolved = true
                unavailable = false
                log.info("cobblemon-market bridge resolved — healer will use live carrot pricing")
                return true
            } catch (e: ClassNotFoundException) {
                warnOnce("cobblemon-market not loaded — healer falls back to flat carrotPrice config")
            } catch (e: Throwable) {
                warnOnce("cobblemon-market reflection failed: ${e.javaClass.simpleName}: ${e.message}")
            }
            resolved = true
            unavailable = true
            return false
        }
    }

    fun available(): Boolean = resolve()

    /** Current effective buy price per carrot, computed from current stock. */
    fun getCarrotBuyPrice(): Int? {
        if (!resolve()) return null
        return try {
            val entry = entryFor(CARROT_ITEM_ID) ?: return null
            val state = stateFor(CARROT_ITEM_ID) ?: return null
            val stock = stateStock!!.invoke(state) as Double
            val basePrice = entryBaseBuyPrice!!.invoke(entry) as Int
            val baseStock = entryBaseStock!!.invoke(entry) as Int
            val elasticity = entryElasticity!!.invoke(entry) as Double
            pricingBuyPrice!!.invoke(null, basePrice, stock, baseStock, elasticity) as Int
        } catch (e: Throwable) { log.error("getCarrotBuyPrice failed", e); null }
    }

    /** Floor of current stock (whole units the market can sell right now). */
    fun getCarrotStock(): Int? {
        if (!resolve()) return null
        return try {
            val state = stateFor(CARROT_ITEM_ID) ?: return null
            (stateStock!!.invoke(state) as Double).toInt()
        } catch (e: Throwable) { log.error("getCarrotStock failed", e); null }
    }

    sealed class BuyResult {
        data class Success(val totalCost: Int) : BuyResult()
        data class OutOfStock(val available: Int) : BuyResult()
        data class InsufficientBalance(val have: Int, val need: Int) : BuyResult()
        object Unknown : BuyResult()
    }

    /**
     * Atomically purchases [qty] carrots from the market for [player]. Withdraws money and
     * decrements stock; does NOT add items to the inventory (caller consumes them immediately).
     */
    fun buyCarrots(player: ServerPlayer, qty: Int): BuyResult {
        if (qty <= 0) return BuyResult.Success(0)
        if (!resolve()) return BuyResult.Unknown
        return try {
            val result = tradeOpsBuyForConsumption!!.invoke(tradeOpsInstance, player, CARROT_ITEM_ID, qty)
            when {
                tradeResultSuccess!!.isInstance(result) -> {
                    val totalPrice = result.javaClass.getMethod("getTotalPrice").invoke(result) as Int
                    BuyResult.Success(totalPrice)
                }
                tradeResultOutOfStock!!.isInstance(result) -> {
                    val available = result.javaClass.getMethod("getAvailable").invoke(result) as Int
                    BuyResult.OutOfStock(available)
                }
                tradeResultInsufficientBalance!!.isInstance(result) -> {
                    val have = result.javaClass.getMethod("getHave").invoke(result) as Int
                    val need = result.javaClass.getMethod("getNeed").invoke(result) as Int
                    BuyResult.InsufficientBalance(have, need)
                }
                else -> BuyResult.Unknown
            }
        } catch (e: Throwable) { log.error("buyCarrots failed", e); BuyResult.Unknown }
    }

    private fun entryFor(itemId: String): Any? {
        @Suppress("UNCHECKED_CAST")
        val items = itemsField!!.get(null) as Map<String, Any>
        return items[itemId]
    }

    private fun stateFor(itemId: String): Any? {
        val store = marketStoreField!!.get(null)
        return storeGetOrCreate!!.invoke(store, itemId)
    }

    private fun warnOnce(msg: String) {
        if (warnedOnce.compareAndSet(false, true)) log.warn(msg)
    }
}
