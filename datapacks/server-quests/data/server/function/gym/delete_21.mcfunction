# Despawn Gym 21: Cynthia — E4 #2. Only kills entities carrying our gym_id tag, so it
# won't touch other RCT trainers that happen to be nearby.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.21]
tellraw @s [{"text":"§7Killed any Gym 21 (Cynthia — E4 #2) entities.","italic":true}]
