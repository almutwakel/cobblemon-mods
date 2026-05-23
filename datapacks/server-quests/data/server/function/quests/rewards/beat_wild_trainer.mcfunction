# Awarded by cobblemon-bridge when the player wins against any RCT trainer NPC that isn't a
# gym leader (gym defeats route to beat_gym_<N> instead).
gacha admin giveegg @s uncommon

playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0

tellraw @s [{"text":"\n§a§l[Quest Complete] ","bold":true},{"text":"First Trainer Down","color":"white","bold":true},{"text":"\n§7Reward: §fUncommon Egg","bold":false},{"text":"\n"}]
