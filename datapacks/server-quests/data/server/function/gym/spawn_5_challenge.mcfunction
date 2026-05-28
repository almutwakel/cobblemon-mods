# Spawn Challenge Gym 5: Blaine — Fire at the function caller's position.
# Trainer lives in the rctmod namespace (data/rctmod/trainers/gym_05_blaine_challenge.json) — bare name
# is enough for the summon command. TrainerId NBT is stored as just the path.

kill @e[type=rctmod:trainer,tag=cobblemon_bridge.gym_id.5,tag=cobblemon_bridge.gym_challenge]
execute at @s run rctmod trainer summon_persistent gym_05_blaine_challenge
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_05_blaine_challenge"}] add cobblemon_bridge.gym_id.5
execute at @s run tag @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_05_blaine_challenge"}] add cobblemon_bridge.gym_challenge
execute at @s run data merge entity @e[type=rctmod:trainer,distance=..10,limit=1,sort=nearest,nbt={TrainerId:"gym_05_blaine_challenge"}] {Invulnerable:1b,PersistenceRequired:1b}
tellraw @s [{"text":"§d✓ Spawned Challenge Gym 5: Blaine — Fire","bold":true},{"text":"\n§7Delete: §f/function server:gym/delete_5_challenge"}]
