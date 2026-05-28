playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "Into the Wild", "color": "white", "bold": true},{"text": "\n§7Reward: §f3 Raw Copper", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Set a Home (/sethome)\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_raw_copper
schedule function server:quests/rewards/_finalize 20t append
