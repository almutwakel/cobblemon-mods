# Reward for reaching $100.
gacha admin giveegg @s common

playsound minecraft:entity.player.levelup player @s ~ ~ ~ 0.8 1.2

tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true}, {"text": "Pocket Change", "color": "white", "bold": true}, {"text": "\n§7Reward: §fCommon Egg", "bold": false}, {"text": "\n§e► Next: ", "bold": false}, {"text": "Reach $1,000\n", "color": "white", "bold": false}]
