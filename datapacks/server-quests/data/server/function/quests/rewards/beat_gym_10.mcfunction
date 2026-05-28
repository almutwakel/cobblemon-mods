# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=10.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Gym Defeated] ","bold":true},{"text":"Gym 10: Morty — Ghost","color":"white","bold":true},{"text":"\n§7Reward: §fUltra Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Tackle rotating gyms 11–18, Oak (gym 19), or the Elite Four (gym 20+)\n","color":"white","bold":false}]
tag @s add cq_reward_key_ultra_1
schedule function server:quests/rewards/_finalize 20t append
