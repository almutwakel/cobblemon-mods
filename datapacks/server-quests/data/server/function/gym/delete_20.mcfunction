# Despawn Gym 20: Lorelei — E4 #1. Only kills entities carrying our gym_id tag, so it
# won't touch other RCT trainers that happen to be nearby.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.20]
tellraw @s [{"text":"§7Killed any Gym 20 (Lorelei — E4 #1) entities.","italic":true}]
