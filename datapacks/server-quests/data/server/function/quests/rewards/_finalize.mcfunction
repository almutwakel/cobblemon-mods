# Delayed reward dispatcher. Each per-quest reward function tags the player and schedules this
# 20 ticks later, so "[Quest Complete]" chat fires first, then the actual grant ~1s later.
#
# Tag naming: cq_reward_<kind>_<spec>
#   - egg: tier from common/uncommon/rare/ultra_rare
#   - key: tier + count
#   - item: a per-quest bundle key

# ─── Egg rewards ─────────────────────────────────────────────────────────
execute as @a[tag=cq_reward_egg_common] at @s run gacha admin giveegg @s common
tag @a[tag=cq_reward_egg_common] remove cq_reward_egg_common

execute as @a[tag=cq_reward_egg_uncommon] at @s run gacha admin giveegg @s uncommon
tag @a[tag=cq_reward_egg_uncommon] remove cq_reward_egg_uncommon

# ─── Key rewards (count always 1 for current quest layout) ──────────────
execute as @a[tag=cq_reward_key_common_1] at @s run gacha admin grant @s common 1
tag @a[tag=cq_reward_key_common_1] remove cq_reward_key_common_1

execute as @a[tag=cq_reward_key_uncommon_1] at @s run gacha admin grant @s uncommon 1
tag @a[tag=cq_reward_key_uncommon_1] remove cq_reward_key_uncommon_1

execute as @a[tag=cq_reward_key_rare_1] at @s run gacha admin grant @s rare 1
tag @a[tag=cq_reward_key_rare_1] remove cq_reward_key_rare_1

execute as @a[tag=cq_reward_key_ultra_1] at @s run gacha admin grant @s ultra 1
tag @a[tag=cq_reward_key_ultra_1] remove cq_reward_key_ultra_1

# ─── Item bundles per quest ─────────────────────────────────────────────
# select_pokemon: 10 Poké Balls + Pokédex
execute as @a[tag=cq_reward_item_starter_balls] at @s run give @s cobblemon:poke_ball 10
tag @a[tag=cq_reward_item_starter_balls] remove cq_reward_item_starter_balls

# use_wild: 3 raw copper for early smelting
execute as @a[tag=cq_reward_item_raw_copper] at @s run give @s minecraft:raw_copper 3
tag @a[tag=cq_reward_item_raw_copper] remove cq_reward_item_raw_copper

# set_home: 3 red apricorn sprouts so the player has stock for crafting Poké Balls next
execute as @a[tag=cq_reward_item_apricorn_sprouts] at @s run give @s cobblemon:red_apricorn_seed 3
tag @a[tag=cq_reward_item_apricorn_sprouts] remove cq_reward_item_apricorn_sprouts

# craft_pokeball: 5 great balls
execute as @a[tag=cq_reward_item_great_balls_craft] at @s run give @s cobblemon:great_ball 5
tag @a[tag=cq_reward_item_great_balls_craft] remove cq_reward_item_great_balls_craft

# catch_pokemon: 3 carrots — flavor "feed your new mon"
execute as @a[tag=cq_reward_item_carrots_craft] at @s run give @s minecraft:carrot 3
tag @a[tag=cq_reward_item_carrots_craft] remove cq_reward_item_carrots_craft

# heal_pokemon: revive
execute as @a[tag=cq_reward_item_revive] at @s run give @s cobblemon:revive 1
tag @a[tag=cq_reward_item_revive] remove cq_reward_item_revive

# beat_wild_trainer: extra storage for the exploration phase
execute as @a[tag=cq_reward_item_backpack] at @s run give @s sophisticatedbackpacks:backpack 1
tag @a[tag=cq_reward_item_backpack] remove cq_reward_item_backpack

# reach_income_250 — Pocket Change: Pasture Block (gates Cobbleworkers automation)
execute as @a[tag=cq_reward_item_pasture_block] at @s run give @s cobblemon:pasture 1
tag @a[tag=cq_reward_item_pasture_block] remove cq_reward_item_pasture_block

# reach_income_1000 — Founding Fortune: Minecolonies supply camp
execute as @a[tag=cq_reward_item_supply_camp] at @s run give @s minecolonies:supplycampdeployer 1
tag @a[tag=cq_reward_item_supply_camp] remove cq_reward_item_supply_camp

# join_colony: Poké Healer block for the colony's center
execute as @a[tag=cq_reward_item_pokehealer] at @s run give @s cobblemon:healing_machine 1
tag @a[tag=cq_reward_item_pokehealer] remove cq_reward_item_pokehealer

# farm_carrots: 3 blue apricorn sprouts → unlocks Great Ball crafting
execute as @a[tag=cq_reward_item_blue_apricorn_sprouts] at @s run give @s cobblemon:blue_apricorn_seed 3
tag @a[tag=cq_reward_item_blue_apricorn_sprouts] remove cq_reward_item_blue_apricorn_sprouts

# first_pvp_win: better balls for the next catch run
execute as @a[tag=cq_reward_item_great_balls] at @s run give @s cobblemon:great_ball 5
tag @a[tag=cq_reward_item_great_balls] remove cq_reward_item_great_balls

# Income milestones: rare candies (level boost) / master ball at higher tiers
execute as @a[tag=cq_reward_item_rare_candy] at @s run give @s cobblemon:rare_candy 1
tag @a[tag=cq_reward_item_rare_candy] remove cq_reward_item_rare_candy

execute as @a[tag=cq_reward_item_master_ball] at @s run give @s cobblemon:master_ball 1
tag @a[tag=cq_reward_item_master_ball] remove cq_reward_item_master_ball

# ELO milestones: progressive battle kit
execute as @a[tag=cq_reward_item_ranked_starter] at @s run give @s cobblemon:great_ball 1
execute as @a[tag=cq_reward_item_ranked_starter] at @s run give @s cobblemon:super_potion 1
tag @a[tag=cq_reward_item_ranked_starter] remove cq_reward_item_ranked_starter

execute as @a[tag=cq_reward_item_ranked_mid] at @s run give @s cobblemon:ultra_ball 1
execute as @a[tag=cq_reward_item_ranked_mid] at @s run give @s cobblemon:hyper_potion 1
tag @a[tag=cq_reward_item_ranked_mid] remove cq_reward_item_ranked_mid
