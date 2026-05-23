# Awarded by cobblemon-bridge PartyLevelHook when any party Pokémon hits level 15.
gacha admin giveegg @s uncommon

playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

tellraw @s [{"text":"\n§a§l[Quest Complete] ","bold":true},{"text":"Level 15 Trainer","color":"white","bold":true},{"text":"\n§7Reward: §fUncommon Egg","bold":false},{"text":"\n"}]
