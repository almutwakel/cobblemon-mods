# Reward for joining or founding a Minecolonies colony.
gacha admin giveegg @s common

playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true}, {"text": "Find a Home", "color": "white", "bold": true}, {"text": "\n§7Reward: §fCommon Egg", "bold": false}, {"text": "\n"}]
