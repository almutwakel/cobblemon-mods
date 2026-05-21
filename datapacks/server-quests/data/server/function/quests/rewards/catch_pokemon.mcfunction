# Reward for catching a Pokémon.
gacha admin giveegg @s common

playsound minecraft:entity.player.levelup player @s ~ ~ ~ 0.8 1.2

tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true}, {"text": "Gotta Catch One", "color": "white", "bold": true}, {"text": "\n§7Reward: §fCommon Egg", "bold": false}, {"text": "\n§e► Next: ", "bold": false}, {"text": "Stockpile 32 carrots\n", "color": "white", "bold": false}]
