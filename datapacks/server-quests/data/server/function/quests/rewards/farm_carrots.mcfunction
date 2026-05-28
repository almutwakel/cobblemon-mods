# Auto-generated.
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "Green Thumb", "color": "white", "bold": true},{"text": "\n§eGrow these and craft Great Balls (4 blue apricorns + 1 iron ingot).", "bold": false},{"text": "\n§7Reward: §f3 Blue Apricorn Sprouts", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Heal a Pok\u00e9mon\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_blue_apricorn_sprouts
schedule function server:quests/rewards/_finalize 20t append
