# Spawn the market shopkeeper at the function caller's position.
# Uses a vanilla Villager (rather than a Cobblemon NPC) because cobblemon:npc requires an
# NPCClass that isn't registered until after datapacks parse — using /spawnnpc inside a
# function breaks reload. The Villager is tagged `cobblemon_bridge.market_vendor` so
# cobblemon-market's EntityInteract handler recognizes the right-click. Invulnerable +
# PersistenceRequired + NoAI + Silent so it stays put and is unkillable.

kill @e[type=minecraft:villager,tag=cobblemon_bridge.market_vendor]
summon minecraft:villager ~ ~ ~ {Tags:["cobblemon_bridge.market_vendor"],Invulnerable:1b,PersistenceRequired:1b,NoAI:1b,Silent:1b,CustomName:'{"text":"Shopkeeper","color":"green","bold":true}',CustomNameVisible:1b,VillagerData:{type:"minecraft:plains",profession:"minecraft:librarian",level:5},Offers:{Recipes:[]}}
tellraw @s [{"text":"§a✓ Spawned Market Shopkeeper","bold":true},{"text":"\n§7Right-click to open the market. Delete: §f/function server:market/delete_npc"}]
