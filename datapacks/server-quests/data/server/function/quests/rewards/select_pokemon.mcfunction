# Reward for picking your starter Pokémon — 10 Poké Balls + a Pokédex.
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true}, {"text": "Pick a Starter", "color": "white", "bold": true}, {"text": "\n§7Reward: §f10 Poké Balls", "bold": false}, {"text": "\n§e► Next: ", "bold": false}, {"text": "Enter the Wilderness (/wild)\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_starter_balls
schedule function server:quests/rewards/_finalize 20t append
