# Auto-generated.
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1.0 1.2
tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true},{"text": "Trained Up", "color": "white", "bold": true},{"text": "\n§eYou've brought a Pokémon to your level cap! Gym 1 will bring any higher-level Pokémon to this level.", "bold": false},{"text": "\n§7Reward: §fSophisticated Backpack", "bold": false},{"text": "\n§e► Next: ", "bold": false},{"text": "Defeat Gym 1: Clay\n", "color": "white", "bold": false}]
tag @s add cq_reward_item_backpack
schedule function server:quests/rewards/_finalize 20t append
