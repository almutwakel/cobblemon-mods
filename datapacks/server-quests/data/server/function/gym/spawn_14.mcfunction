# Spawn Gym 14: Grant — Rock at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_14_grant.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.14,tag=!cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_14_grant
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_14_grant"}] add cobblemon_bridge.gym_id.14
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_14_grant"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§a✓ Spawned Gym 14: Grant — Rock","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_14"}]
