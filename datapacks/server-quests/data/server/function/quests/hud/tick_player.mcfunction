# Runs `as <player>` every ~30 ticks (~1.5s) for HUD-on players. Picks the LOWEST
# uncompleted mainline quest by walking top-down and using a per-tick `cq_hud_done`
# tag to ensure only one quest's actionbar fires.

tag @s remove cq_hud_done

# --- select_pokemon ---
execute if entity @s[tag=!cq_hud_done,advancements={server:select_pokemon=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Pick a Starter Pokémon","color":"white","bold":false},{"text":" §7— press §fP§7 to select your starter"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:select_pokemon=false}] run tag @s add cq_hud_done

# --- use_wild ---
execute if entity @s[tag=!cq_hud_done,advancements={server:use_wild=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Enter the Wilderness","color":"white","bold":false},{"text":" §7— type §f/wild§7 to teleport"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:use_wild=false}] run tag @s add cq_hud_done

# --- set_home ---
execute if entity @s[tag=!cq_hud_done,advancements={server:set_home=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Set a Home","color":"white","bold":false},{"text":" §7— §f/sethome§7 wherever you want to live"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:set_home=false}] run tag @s add cq_hud_done

# --- craft_pokeball ---
execute if entity @s[tag=!cq_hud_done,advancements={server:craft_pokeball=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Craft a Poké Ball","color":"white","bold":false},{"text":" §7— 4 red apricorns + 1 copper ingot"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:craft_pokeball=false}] run tag @s add cq_hud_done

# --- catch_pokemon ---
execute if entity @s[tag=!cq_hud_done,advancements={server:catch_pokemon=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Catch a Pokémon","color":"white","bold":false},{"text":" §7— right-click a wild mon holding a Poké Ball"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:catch_pokemon=false}] run tag @s add cq_hud_done

# --- farm_carrots ---
execute if entity @s[tag=!cq_hud_done,advancements={server:farm_carrots=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Stockpile 32 Carrots","color":"white","bold":false},{"text":" §7— plant carrots and harvest 32"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:farm_carrots=false}] run tag @s add cq_hud_done

# --- heal_pokemon ---
execute if entity @s[tag=!cq_hud_done,advancements={server:heal_pokemon=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Heal a Pokémon","color":"white","bold":false},{"text":" §7— feed a carrot or use a Poké Healer"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:heal_pokemon=false}] run tag @s add cq_hud_done

# --- beat_wild_trainer ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_wild_trainer=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Beat a Wild Trainer","color":"white","bold":false},{"text":" §7— wander; trainers spawn around you"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_wild_trainer=false}] run tag @s add cq_hud_done

# --- reach_party_level_20 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:reach_party_level_20=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Level 20 Trainer","color":"white","bold":false},{"text":" §7— train any party Pokémon to level 20 (your cap)"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:reach_party_level_20=false}] run tag @s add cq_hud_done

# --- beat_gym_1 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_1=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 1: Clay","color":"white","bold":false},{"text":" §7— find him at §f/warp gym1"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_1=false}] run tag @s add cq_hud_done

# --- reach_income_250 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:reach_income_250=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Reach $250","color":"white","bold":false},{"text":" §7— sell items on §f/market"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:reach_income_250=false}] run tag @s add cq_hud_done

# --- first_pvp_win ---
execute if entity @s[tag=!cq_hud_done,advancements={server:first_pvp_win=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Win a Ranked Battle","color":"white","bold":false},{"text":" §7— §f/challenge <player>"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:first_pvp_win=false}] run tag @s add cq_hud_done

# --- beat_gym_2 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_2=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 2: Gardenia","color":"white","bold":false},{"text":" §7— §f/warp gym2"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_2=false}] run tag @s add cq_hud_done

# --- beat_gym_3 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_3=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 3: Korrina","color":"white","bold":false},{"text":" §7— §f/warp gym3"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_3=false}] run tag @s add cq_hud_done

# --- beat_gym_4 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_4=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 4: Byron","color":"white","bold":false},{"text":" §7— §f/warp gym4"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_4=false}] run tag @s add cq_hud_done

# --- beat_gym_5 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_5=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 5: Blaine","color":"white","bold":false},{"text":" §7— §f/warp gym5"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_5=false}] run tag @s add cq_hud_done

# --- beat_gym_6 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_6=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 6: Roxie","color":"white","bold":false},{"text":" §7— §f/warp gym6"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_6=false}] run tag @s add cq_hud_done

# --- beat_gym_7 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_7=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 7: Crasher Wake","color":"white","bold":false},{"text":" §7— §f/warp gym7"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_7=false}] run tag @s add cq_hud_done

# --- beat_gym_8 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_8=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 8: Sabrina","color":"white","bold":false},{"text":" §7— §f/warp gym8"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_8=false}] run tag @s add cq_hud_done

# --- beat_gym_9 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_9=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 9: Drayden","color":"white","bold":false},{"text":" §7— §f/warp gym9"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_9=false}] run tag @s add cq_hud_done

# --- beat_gym_10 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_10=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 10: Morty","color":"white","bold":false},{"text":" §7— §f/warp gym10"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_10=false}] run tag @s add cq_hud_done

# --- beat_gym_11 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_11=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 11: Viola","color":"white","bold":false},{"text":" §7— §f/warp gym11"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_11=false}] run tag @s add cq_hud_done

# --- beat_gym_12 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_12=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 12: Cheren","color":"white","bold":false},{"text":" §7— §f/warp gym12"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_12=false}] run tag @s add cq_hud_done

# --- beat_gym_13 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_13=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 13: Lt. Surge","color":"white","bold":false},{"text":" §7— §f/warp gym13"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_13=false}] run tag @s add cq_hud_done

# --- beat_gym_14 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_14=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 14: Grant","color":"white","bold":false},{"text":" §7— §f/warp gym14"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_14=false}] run tag @s add cq_hud_done

# --- beat_gym_15 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_15=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 15: Skyla","color":"white","bold":false},{"text":" §7— §f/warp gym15"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_15=false}] run tag @s add cq_hud_done

# --- beat_gym_16 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_16=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 16: Brycen","color":"white","bold":false},{"text":" §7— §f/warp gym16"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_16=false}] run tag @s add cq_hud_done

# --- beat_gym_17 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_17=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 17: Valerie","color":"white","bold":false},{"text":" §7— §f/warp gym17"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_17=false}] run tag @s add cq_hud_done

# --- beat_gym_18 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_18=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 18: Marnie","color":"white","bold":false},{"text":" §7— §f/warp gym18"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_18=false}] run tag @s add cq_hud_done

# --- beat_gym_19 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_19=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 19: Oak","color":"white","bold":false},{"text":" §7— §f/warp gym19"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_19=false}] run tag @s add cq_hud_done

# --- beat_gym_20 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_20=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Elite Four 1: Lorelei","color":"white","bold":false},{"text":" §7— §f/warp gym20"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_20=false}] run tag @s add cq_hud_done

# --- beat_gym_21 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_21=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Elite Four 2: Cynthia","color":"white","bold":false},{"text":" §7— §f/warp gym21"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_21=false}] run tag @s add cq_hud_done

# --- beat_gym_22 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_22=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Elite Four 3: Agatha","color":"white","bold":false},{"text":" §7— §f/warp gym22"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_22=false}] run tag @s add cq_hud_done

# --- beat_gym_23 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_23=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Elite Four 4: Lance","color":"white","bold":false},{"text":" §7— §f/warp gym23"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_23=false}] run tag @s add cq_hud_done

# --- beat_gym_24 ---
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_24=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Champion","color":"white","bold":false},{"text":" §7— §f/warp gym24"}]
execute if entity @s[tag=!cq_hud_done,advancements={server:beat_gym_24=false}] run tag @s add cq_hud_done

# --- All mainline complete ---
execute if entity @s[tag=!cq_hud_done] run title @s actionbar [{"text":"§a✓ All mainline quests complete!  ","bold":true},{"text":"§7Try /quests list for branch tracks (ELO, income, colony).","color":"gray","bold":false}]
