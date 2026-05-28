# Despawn the CHALLENGE variant of Gym 7: Crasher Wake.
# Filters by both gym_id and gym_challenge tags so it only kills the challenge entity.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.7,tag=cobblemon_bridge.gym_challenge]
tellraw @s [{"text":"§7Killed any Challenge Gym 7 (Crasher Wake) entities.","italic":true}]
