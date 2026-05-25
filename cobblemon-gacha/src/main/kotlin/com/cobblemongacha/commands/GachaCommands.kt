package com.cobblemongacha.commands

import com.cobblemongacha.CobblemonGacha
import com.cobblemongacha.announce.PullAnnouncer
import com.cobblemongacha.config.GachaConfig
import com.cobblemongacha.config.CrateCoord
import com.cobblemongacha.config.LootTableLoader
import com.cobblemongacha.data.KeyTier
import com.cobblemongacha.gui.OddsMenu
import com.cobblemongacha.gui.RollMenu
import com.cobblemongacha.item.KeyItems
import com.cobblemongacha.reward.RewardGranter
import com.cobblemongacha.reward.RewardRoller
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.BlockHitResult
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLPaths

object GachaCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("gacha")
                .executes { ctx -> showHelp(ctx.source, ctx.source.hasPermission(4)); 1 }
                .then(Commands.literal("help")
                    .executes { ctx -> showHelp(ctx.source, ctx.source.hasPermission(4)); 1 })
                .then(Commands.literal("version")
                    .executes { ctx ->
                        val v = ModList.get().getModContainerById(CobblemonGacha.MOD_ID)
                            .map { it.modInfo.version.toString() }.orElse("unknown")
                        ctx.source.sendSystemMessage(Component.literal("[Gacha] Cobblemon Gacha v$v"))
                        1
                    })
                .then(Commands.literal("odds")
                    .then(Commands.argument("tier", StringArgumentType.string())
                        .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                        .executes { ctx ->
                            val sp = ctx.source.playerOrException
                            val tier = KeyTier.fromKey(StringArgumentType.getString(ctx, "tier"))
                            if (tier == null) {
                                ctx.source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier"))
                                return@executes 0
                            }
                            val table = CobblemonGacha.tables[tier] ?: return@executes 0
                            OddsMenu.openFor(sp, tier, table); 1
                        })
                )
                .then(Commands.literal("admin")
                    .requires { it.hasPermission(4) }
                    .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("tier", StringArgumentType.string())
                                .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                                .executes { ctx -> adminGrant(ctx.source, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "tier"), 1) }
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                    .executes { ctx -> adminGrant(ctx.source, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "tier"), IntegerArgumentType.getInteger(ctx, "count")) })
                            )
                        )
                    )
                    .then(Commands.literal("setcrate")
                        .then(Commands.argument("tier", StringArgumentType.string())
                            .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                            .executes { ctx -> adminSetCrate(ctx.source, StringArgumentType.getString(ctx, "tier")) })
                    )
                    .then(Commands.literal("clearcrate")
                        .then(Commands.argument("tier", StringArgumentType.string())
                            .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                            .executes { ctx -> adminClearCrate(ctx.source, StringArgumentType.getString(ctx, "tier")) })
                    )
                    .then(Commands.literal("force")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("tier", StringArgumentType.string())
                                .suggests { _, b -> KeyTier.entries.forEach { b.suggest(it.key) }; b.buildFuture() }
                                .executes { ctx -> adminForce(ctx.source, EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "tier")) })
                        )
                    )
                    .then(Commands.literal("reload")
                        .executes { ctx -> adminReload(ctx.source) })
                    .then(Commands.literal("giveegg")
                        .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("tier", StringArgumentType.string())
                                .suggests { _, b ->
                                    listOf("common", "uncommon", "rare", "ultra_rare").forEach { b.suggest(it) }
                                    b.buildFuture()
                                }
                                .executes { ctx ->
                                    adminGiveEgg(
                                        ctx.source,
                                        EntityArgument.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "tier"),
                                        shiny = false, requireHa = false,
                                    )
                                }
                                .then(Commands.literal("shiny")
                                    .executes { ctx ->
                                        adminGiveEgg(
                                            ctx.source,
                                            EntityArgument.getPlayer(ctx, "player"),
                                            StringArgumentType.getString(ctx, "tier"),
                                            shiny = true, requireHa = false,
                                        )
                                    }
                                    .then(Commands.literal("ha")
                                        .executes { ctx ->
                                            adminGiveEgg(
                                                ctx.source,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "tier"),
                                                shiny = true, requireHa = true,
                                            )
                                        }))
                                .then(Commands.literal("ha")
                                    .executes { ctx ->
                                        adminGiveEgg(
                                            ctx.source,
                                            EntityArgument.getPlayer(ctx, "player"),
                                            StringArgumentType.getString(ctx, "tier"),
                                            shiny = false, requireHa = true,
                                        )
                                    })
                            )
                        )
                    )
                )
        )
    }

    private fun showHelp(source: CommandSourceStack, includeAdmin: Boolean) {
        val lines = mutableListOf(
            "§e[Gacha] §fCommands:",
            "§7  /gacha odds <common|rare|ultra> §f— preview the rewards in a box",
            "§7  /gacha version §f— mod version",
        )
        if (includeAdmin) {
            lines += listOf(
                "§e[Gacha] §fAdmin (op level 4):",
                "§7  /gacha admin grant <player> <tier> [count] §f— give keys",
                "§7  /gacha admin setcrate <tier> §f— bind your targeted block as that tier's crate",
                "§7  /gacha admin clearcrate <tier> §f— unbind a crate",
                "§7  /gacha admin force <player> <tier> §f— roll without consuming a key",
                "§7  /gacha admin reload §f— reload config + tables from disk",
                "§7  /gacha admin giveegg <player> <pool> [shiny] [ha] §f— grant a random species egg",
            )
        }
        lines.forEach { source.sendSystemMessage(Component.literal(it)) }
    }

    private fun adminGrant(source: CommandSourceStack, target: net.minecraft.server.level.ServerPlayer, tierStr: String, count: Int): Int {
        val tier = KeyTier.fromKey(tierStr) ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier: $tierStr")); return 0
        }
        val stack = KeyItems.build(tier, count)
        if (!target.inventory.add(stack)) target.drop(stack, false)
        target.sendSystemMessage(Component.literal("§a[Gacha] You received §6$count ${tier.displayName} Key${if (count == 1) "" else "s"} §afrom an admin"))
        source.sendSystemMessage(Component.literal("§a[Gacha] Gave ${target.name.string} $count ${tier.displayName} Key${if (count == 1) "" else "s"}"))
        return 1
    }

    private fun adminSetCrate(source: CommandSourceStack, tierStr: String): Int {
        val tier = KeyTier.fromKey(tierStr) ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier: $tierStr")); return 0
        }
        val player = source.player ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Must be run by a player (stand at the crate)")); return 0
        }
        val hit = player.pick(6.0, 0.0f, false)
        if (hit !is BlockHitResult || hit.type == HitResult.Type.MISS) {
            source.sendSystemMessage(Component.literal("§c[Gacha] Look at the crate block first (within 6 blocks)")); return 0
        }
        val pos = hit.blockPos
        val dim = player.serverLevel().dimension().location().toString()
        CobblemonGacha.config.setCrate(tier, CrateCoord(pos.x, pos.y, pos.z, dim))
        GachaConfig.save(FMLPaths.CONFIGDIR.get(), CobblemonGacha.config)
        source.sendSystemMessage(Component.literal("§a[Gacha] ${tier.displayName} crate bound to (${pos.x}, ${pos.y}, ${pos.z}) in $dim"))
        return 1
    }

    private fun adminClearCrate(source: CommandSourceStack, tierStr: String): Int {
        val tier = KeyTier.fromKey(tierStr) ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier: $tierStr")); return 0
        }
        CobblemonGacha.config.setCrate(tier, null)
        GachaConfig.save(FMLPaths.CONFIGDIR.get(), CobblemonGacha.config)
        source.sendSystemMessage(Component.literal("§a[Gacha] Cleared ${tier.displayName} crate binding"))
        return 1
    }

    private fun adminForce(source: CommandSourceStack, target: net.minecraft.server.level.ServerPlayer, tierStr: String): Int {
        val tier = KeyTier.fromKey(tierStr) ?: run {
            source.sendSystemMessage(Component.literal("§c[Gacha] Unknown tier: $tierStr")); return 0
        }
        val table = CobblemonGacha.tables[tier] ?: return 0
        RollMenu.openFor(target, tier, table, CobblemonGacha.config.crateOf(tier)?.let {
            net.minecraft.core.BlockPos(it.x, it.y, it.z)
        })
        source.sendSystemMessage(Component.literal("§a[Gacha] Forced ${tier.displayName} roll for ${target.name.string}"))
        return 1
    }

    private fun adminReload(source: CommandSourceStack): Int {
        val dir = FMLPaths.CONFIGDIR.get()
        CobblemonGacha.config = GachaConfig.load(dir)
        CobblemonGacha.tables = LootTableLoader.loadAll(dir)
        source.sendSystemMessage(Component.literal("§a[Gacha] Reloaded config + ${CobblemonGacha.tables.size} tables"))
        return 1
    }

    /**
     * Pick a species from the gacha's egg pool for [tierStr] (one of common/uncommon/rare/
     * ultra_rare; underscore or space tolerated), then dispatch Cobbreeding's `givepokemonegg`
     * with `min_perfect_ivs=2` baseline + optional `shiny=true` and `ha=yes`. Mirrors the same
     * dispatch path used during normal gacha pulls in `RewardGranter.dispatchEgg` so a quest
     * reward calling `gacha admin giveegg @s rare shiny ha` produces an egg indistinguishable
     * from a rare-tier gacha pull.
     */
    private fun adminGiveEgg(
        source: CommandSourceStack,
        target: net.minecraft.server.level.ServerPlayer,
        tierStr: String,
        shiny: Boolean,
        requireHa: Boolean,
    ): Int {
        val pools = CobblemonGacha.eggPools
        val species = pools.pick(tierStr, requireHiddenAbility = requireHa)
        if (species == null) {
            source.sendSystemMessage(Component.literal(
                "§c[Gacha] Egg pool '$tierStr' has no species" +
                    if (requireHa) " with Hidden Ability" else "" +
                    ". Valid tiers: common, uncommon, rare, ultra_rare"
            ))
            return 0
        }
        val args = buildList {
            add(species)
            add("min_perfect_ivs=2")
            if (shiny) add("shiny=true")
            if (requireHa) add("ha=yes")
        }.joinToString(" ")
        val cmd = "givepokemonegg ${target.gameProfile.name} $args"
        val src = target.server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
        target.server.commands.performPrefixedCommand(src, cmd)
        // Stamp the tier tag so cobblemon-bridge's timer hook picks the egg up. Without this
        // call, the egg falls back to Cobreeding's default ~10-minute hatch.
        RewardGranter.scheduleTagGrantedEgg(target, tierStr)

        val shinyTag = if (shiny) " §e✦ Shiny" else ""
        val haTag = if (requireHa) " §d(HA)" else ""
        val display = "$shinyTag §f${species.replaceFirstChar { it.uppercase() }} Egg$haTag"
        target.sendSystemMessage(Component.literal("§a[Gacha] You received a$display §a(Cobbreeding will deliver it)"))
        source.sendSystemMessage(Component.literal(
            "§a[Gacha] Gave ${target.name.string}: ${species} (tier=$tierStr, shiny=$shiny, ha=$requireHa)"
        ))
        return 1
    }
}
