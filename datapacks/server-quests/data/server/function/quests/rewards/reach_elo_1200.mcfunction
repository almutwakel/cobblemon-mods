# Reward for reaching 1200 ELO.
gacha admin giveegg @s uncommon

playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

tellraw @s [{"text": "\n§a§l[Quest Complete] ", "bold": true}, {"text": "Contender", "color": "white", "bold": true}, {"text": "\n§7Reward: §fUncommon Egg", "bold": false}, {"text": "\n§e► Next: ", "bold": false}, {"text": "Reach 1300 ELO\n", "color": "white", "bold": false}]
