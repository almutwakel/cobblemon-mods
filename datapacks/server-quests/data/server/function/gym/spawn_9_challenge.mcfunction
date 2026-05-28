# Spawn Challenge Gym 9: Drayden — Dragon at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_09_drayden_challenge.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.9,tag=cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_09_drayden_challenge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_09_drayden_challenge"}] add cobblemon_bridge.gym_id.9
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_09_drayden_challenge"}] add cobblemon_bridge.gym_challenge
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_09_drayden_challenge"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§d✓ Spawned Challenge Gym 9: Drayden — Dragon","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_9_challenge"}]
