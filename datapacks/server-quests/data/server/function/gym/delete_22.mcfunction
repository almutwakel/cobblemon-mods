# Despawn Gym 22: Agatha — E4 #3. Only kills entities carrying our gym_id tag, so it
# won't touch other RCT trainers that happen to be nearby.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.22]
tellraw @s [{"text":"§7Killed any Gym 22 (Agatha — E4 #3) entities.","italic":true}]
