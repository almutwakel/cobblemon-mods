# Spawn Gym 22: Agatha — E4 #3 at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_22_agatha.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.22,tag=!cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_22_agatha
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_22_agatha"}] add cobblemon_bridge.gym_id.22
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_22_agatha"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§a✓ Spawned Gym 22: Agatha — E4 #3","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_22"}]
