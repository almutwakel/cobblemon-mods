# Spawn Gym 18: Marnie — Dark at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_18_marnie.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.18,tag=!cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_18_marnie
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_18_marnie"}] add cobblemon_bridge.gym_id.18
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_18_marnie"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§a✓ Spawned Gym 18: Marnie — Dark","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_18"}]
