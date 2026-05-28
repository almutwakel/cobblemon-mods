# Despawn Gym 17: Valerie — Fairy. Only kills entities carrying our gym_id tag, so it
# won't touch other RCT trainers that happen to be nearby.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.17]
tellraw @s [{"text":"§7Killed any Gym 17 (Valerie — Fairy) entities.","italic":true}]
