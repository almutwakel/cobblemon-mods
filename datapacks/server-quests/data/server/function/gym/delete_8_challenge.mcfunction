# Despawn the CHALLENGE variant of Gym 8: Sabrina.
# Filters by both gym_id and gym_challenge tags so it only kills the challenge entity.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.8,tag=cobblemon_bridge.gym_challenge]
tellraw @s [{"text":"§7Killed any Challenge Gym 8 (Sabrina) entities.","italic":true}]
