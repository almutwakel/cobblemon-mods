# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=24.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Champion] ","bold":true},{"text":"Champion","color":"white","bold":true},{"text":"\n§7Reward: §fUltra Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"You ARE the Champion. Congrats.\n","color":"white","bold":false}]
tag @s add cq_reward_key_ultra_1
schedule function server:quests/rewards/_finalize 20t append
