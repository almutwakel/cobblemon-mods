package com.cobblemonbridge.quests

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemonbridge.CobblemonBridge
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.Items
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

/**
 * Awards `server:heal_pokemon` ("Nurse Joy") when the player first interacts with
 * healing mechanics: feeding a carrot to a Pokémon or using a Poké Healer block.
 */
object HealQuestHook {

    private const val ADVANCEMENT_ID = "server:heal_pokemon"
    private val HEALING_MACHINE_ID = ResourceLocation.fromNamespaceAndPath("cobblemon", "healing_machine")

    /** Carrot + Pokémon entity → award quest. */
    @SubscribeEvent
    fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (event.level.isClientSide) return
        if (event.hand != InteractionHand.MAIN_HAND) return
        val player = event.entity as? ServerPlayer ?: return
        if (event.target !is PokemonEntity) return
        if (player.mainHandItem.item != Items.CARROT) return

        val awarded = QuestAdvancements.award(player, ADVANCEMENT_ID)
        if (awarded) {
            CobblemonBridge.logger.info("cobblemon-bridge: awarded {} to {} (carrot heal)",
                ADVANCEMENT_ID, player.gameProfile.name)
        }
    }

    /** Right-click Poké Healer block → award quest. */
    @SubscribeEvent
    fun onUseBlock(event: PlayerInteractEvent.RightClickBlock) {
        if (event.level.isClientSide) return
        val player = event.entity as? ServerPlayer ?: return
        val block = event.level.getBlockState(event.pos).block
        val blockId = BuiltInRegistries.BLOCK.getKey(block)
        if (blockId != HEALING_MACHINE_ID) return

        val awarded = QuestAdvancements.award(player, ADVANCEMENT_ID)
        if (awarded) {
            CobblemonBridge.logger.info("cobblemon-bridge: awarded {} to {} (pokehealer)",
                ADVANCEMENT_ID, player.gameProfile.name)
        }
    }
}
