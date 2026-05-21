# Reward for crafting a Poké Ball.
gacha admin giveegg @s common

playsound minecraft:entity.player.levelup player @s ~ ~ ~ 0.8 1.2

tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true}, {"text": "Craft 'em", "color": "white", "bold": true}, {"text": "\n§7Reward: §fCommon Egg", "bold": false}, {"text": "\n§e► Next: ", "bold": false}, {"text": "Catch a Pokémon\n", "color": "white", "bold": false}]
