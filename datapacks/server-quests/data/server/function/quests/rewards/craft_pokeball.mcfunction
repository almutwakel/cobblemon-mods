playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "Catch 'em", "color": "white", "bold": true},{"text": "\n§7Reward: §f5 Great Balls", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Catch a Pokémon\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_great_balls_craft
schedule function server:quests/rewards/_finalize 20t append
