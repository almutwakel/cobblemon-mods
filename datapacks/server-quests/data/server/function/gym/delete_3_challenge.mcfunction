# Despawn the CHALLENGE variant of Gym 3: Korrina.
# Filters by both gym_id and gym_challenge tags so it only kills the challenge entity.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.3,tag=cobblemon_bridge.gym_challenge]
tellraw @s [{"text":"§7Killed any Challenge Gym 3 (Korrina) entities.","italic":true}]
