# Awarded by cobblemon-bridge when the player runs /sethome (NeoEssentials hook).
# Until that hook lands, ops grant manually:
#   /advancement grant <player> only server:set_home done
gacha admin giveegg @s common

playsound minecraft:entity.player.levelup player @s ~ ~ ~ 0.8 1.2

tellraw @s [{"text":"\n§a§l[Quest Complete] ","bold":true},{"text":"A Place to Crash","color":"white","bold":true},{"text":"\n§7Reward: §fCommon Egg","bold":false},{"text":"\n§e► Tip: ","bold":false},{"text":"§7Use §f/home <name>§7 to teleport back, or §f/tpa <player>§7 to visit someone\n","bold":false}]
