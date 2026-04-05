package com.cobblemonmarket.shop

import com.cobblemonmarket.CobblemonMarket
import com.cobblemonmarket.gui.ShopGui
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.npc.Villager
import net.minecraft.world.entity.npc.VillagerProfession
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class ShopkeeperData(
    val uuid: String,
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val world: String
)

object ShopkeeperManager {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val shopkeeperUuids: MutableSet<UUID> = mutableSetOf()
    private val shopkeepers: MutableList<ShopkeeperData> = mutableListOf()
    private lateinit var configDir: Path

    fun init(configDir: Path) {
        this.configDir = configDir
        loadShopkeepers()
        registerInteractionHandler()
    }

    fun spawnShopkeeper(player: ServerPlayer, name: String): Boolean {
        val level = player.serverLevel()
        val villager = Villager(EntityType.VILLAGER, level)
        villager.setPos(player.x, player.y, player.z)
        villager.customName = Component.literal(name)
        villager.isCustomNameVisible = true
        villager.isNoAi = true
        villager.isInvulnerable = true
        villager.isSilent = true
        villager.villagerData = villager.villagerData
            .setProfession(VillagerProfession.LIBRARIAN)

        if (!level.addFreshEntity(villager)) {
            return false
        }

        val data = ShopkeeperData(
            uuid = villager.uuid.toString(),
            name = name,
            x = player.x, y = player.y, z = player.z,
            world = level.dimension().location().toString()
        )
        shopkeepers.add(data)
        shopkeeperUuids.add(villager.uuid)
        saveShopkeepers()
        return true
    }

    fun removeNearest(player: ServerPlayer, radius: Double = 5.0): Boolean {
        val level = player.serverLevel()
        val nearby = level.getEntitiesOfClass(
            Villager::class.java,
            player.boundingBox.inflate(radius)
        ) { shopkeeperUuids.contains(it.uuid) }

        val nearest = nearby.minByOrNull { it.distanceTo(player) } ?: return false
        shopkeeperUuids.remove(nearest.uuid)
        shopkeepers.removeAll { it.uuid == nearest.uuid.toString() }
        nearest.discard()
        saveShopkeepers()
        return true
    }

    private fun registerInteractionHandler() {
        UseEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            if (world.isClientSide) return@register InteractionResult.PASS
            if (entity !is Villager) return@register InteractionResult.PASS
            if (!shopkeeperUuids.contains(entity.uuid)) return@register InteractionResult.PASS

            val serverPlayer = player as? ServerPlayer ?: return@register InteractionResult.PASS
            ShopGui(serverPlayer).open()
            InteractionResult.SUCCESS
        }
    }

    private fun loadShopkeepers() {
        val file = configDir.resolve("cobblemon-market").resolve("shopkeepers.json")
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableList<ShopkeeperData>>() {}.type
            val loaded: MutableList<ShopkeeperData> = gson.fromJson(file.readText(), type)
            shopkeepers.clear()
            shopkeepers.addAll(loaded)
            shopkeeperUuids.clear()
            shopkeepers.forEach { shopkeeperUuids.add(UUID.fromString(it.uuid)) }
        } catch (e: Exception) {
            CobblemonMarket.logger.error("Failed to load shopkeepers", e)
        }
    }

    private fun saveShopkeepers() {
        val dir = configDir.resolve("cobblemon-market")
        dir.createDirectories()
        dir.resolve("shopkeepers.json").writeText(gson.toJson(shopkeepers))
    }
}
