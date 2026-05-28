# Auto-generated. Awarded by cobblemon-bridge GymDefeatHook for gym_id=4.
playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1.0 1.0
tellraw @s [{"text":"\n§6§l[Gym Defeated] ","bold":true},{"text":"Gym 4: Byron — Steel","color":"white","bold":true},{"text":"\n§7Reward: §fRare Key","bold":false},{"text":"\n§e► Next: ","bold":false},{"text":"Gym 5: Blaine §7(/warp gym5)\n","color":"white","bold":false}]
tag @s add cq_reward_key_rare_1
schedule function server:quests/rewards/_finalize 20t append
