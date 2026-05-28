# Despawn the CHALLENGE variant of Gym 9: Drayden.
# Filters by both gym_id and gym_challenge tags so it only kills the challenge entity.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.9,tag=cobblemon_bridge.gym_challenge]
tellraw @s [{"text":"§7Killed any Challenge Gym 9 (Drayden) entities.","italic":true}]
