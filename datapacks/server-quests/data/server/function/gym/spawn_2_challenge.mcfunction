# Spawn Challenge Gym 2: Gardenia — Grass at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_02_gardenia_challenge.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.2,tag=cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_02_gardenia_challenge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_02_gardenia_challenge"}] add cobblemon_bridge.gym_id.2
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_02_gardenia_challenge"}] add cobblemon_bridge.gym_challenge
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_02_gardenia_challenge"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§d✓ Spawned Challenge Gym 2: Gardenia — Grass","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_2_challenge"}]
