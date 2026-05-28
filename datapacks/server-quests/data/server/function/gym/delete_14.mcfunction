# Despawn Gym 14: Grant — Rock. Only kills entities carrying our gym_id tag, so it
# won't touch other RCT trainers that happen to be nearby.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.14]
tellraw @s [{"text":"§7Killed any Gym 14 (Grant — Rock) entities.","italic":true}]
