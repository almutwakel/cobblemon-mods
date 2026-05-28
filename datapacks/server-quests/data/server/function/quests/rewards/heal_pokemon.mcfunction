playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "Nurse Joy", "color": "white", "bold": true},{"text": "\n§7Reward: §fRevive", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Beat a Wild Trainer\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_revive
schedule function server:quests/rewards/_finalize 20t append
