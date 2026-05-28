# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=23.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Elite Four #4] ","bold":true},{"text":"Elite Four #4: Lance","color":"white","bold":true},{"text":"\n§7Reward: §fUltra Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Face the Champion (gym 24)\n","color":"white","bold":false}]
tag @s add cq_reward_key_ultra_1
schedule function server:quests/rewards/_finalize 20t append
