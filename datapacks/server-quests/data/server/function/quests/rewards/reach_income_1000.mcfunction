# Reward for reaching $1,000.
gacha admin giveegg @s uncommon

playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true}, {"text": "Petty Cash", "color": "white", "bold": true}, {"text": "\n§7Reward: §fUncommon Egg", "bold": false}, {"text": "\n§e► Next: ", "bold": false}, {"text": "Reach $10,000\n", "color": "white", "bold": false}]
