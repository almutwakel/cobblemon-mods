# Spawn Gym 6: Roxie — Poison at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_06_roxie.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.6,tag=!cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_06_roxie
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_06_roxie"}] add cobblemon_bridge.gym_id.6
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_06_roxie"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§a✓ Spawned Gym 6: Roxie — Poison","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_6"}]
