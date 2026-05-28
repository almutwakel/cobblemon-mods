# Despawn Gym 23: Lance — E4 #4. Only kills entities carrying our gym_id tag, so it
# won't touch other RCT trainers that happen to be nearby.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.23]
tellraw @s [{"text":"§7Killed any Gym 23 (Lance — E4 #4) entities.","italic":true}]
