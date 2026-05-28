# Awarded by cobblemon-bridge GymDefeatHook for gym_id=10 challenge variant.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 0.9
tellraw @s [{"text":"\n§5§l[Challenge Defeated] ","bold":true},{"text":"Gym 10: Morty — Challenge","color":"light_purple","bold":true},{"text":"\n§7Reward: §fUltra Key","bold":false},{"text":"\n"}]
tag @s add cq_reward_key_ultra_1
schedule function server:quests/rewards/_finalize 20t append
