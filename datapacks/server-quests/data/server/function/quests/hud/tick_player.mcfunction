# Runs `as <player>` every ~30 ticks (~1.5s) for HUD-on players. Decides which quest is
# "currently active" by walking the linear chain top-down. The first incomplete line matches;
# subsequent lines are mutually exclusive via `advancements={prev=true,...=false}` selectors.
# After reach_income_100, the chain diverges into three parallel tracks (income / ELO / gyms)
# which surface in /quests list rather than the action bar.

# --- 1. Catch a Pokémon ------------------------------------------------------
execute if entity @s[advancements={server:catch_pokemon=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Catch a Pokémon","color":"white","bold":false},{"text":" §7— right-click a wild mon holding a Poké Ball"}]

# --- 2. Craft a Poké Ball ----------------------------------------------------
execute if entity @s[advancements={server:catch_pokemon=true,server:craft_pokeball=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Craft a Poké Ball","color":"white","bold":false},{"text":" §7— red apricorn + sticks on a crafting table"}]

# --- 3. Set a home -----------------------------------------------------------
execute if entity @s[advancements={server:craft_pokeball=true,server:set_home=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Set a Home","color":"white","bold":false},{"text":" §7— §f/sethome§7 wherever you want to live"}]

# --- 4. Farm carrots ---------------------------------------------------------
execute if entity @s[advancements={server:set_home=true,server:farm_carrots=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Stockpile 32 Carrots","color":"white","bold":false},{"text":" §7— find a village or plant your own"}]

# --- 5. Beat a wild trainer --------------------------------------------------
execute if entity @s[advancements={server:farm_carrots=true,server:beat_wild_trainer=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Beat a Wild Trainer","color":"white","bold":false},{"text":" §7— wander; trainers spawn around you"}]

# --- 6. Reach party level 15 -------------------------------------------------
execute if entity @s[advancements={server:beat_wild_trainer=true,server:reach_party_level_15=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Level 15 Trainer","color":"white","bold":false},{"text":" §7— train any party Pokémon to level 15"}]

# --- 7. Beat Gym 1 (Clay) ----------------------------------------------------
execute if entity @s[advancements={server:reach_party_level_15=true,server:beat_gym_1=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 1: Clay","color":"white","bold":false},{"text":" §7— find him at §f/warp gym1"}]

# --- 8. First ranked PvP win -------------------------------------------------
execute if entity @s[advancements={server:beat_gym_1=true,server:first_pvp_win=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Win a Ranked Battle","color":"white","bold":false},{"text":" §7— §f/ranked challenge <player>"}]

# --- 9. Reach $100 (last mainline before branching) --------------------------
execute if entity @s[advancements={server:first_pvp_win=true,server:reach_income_100=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Reach $100","color":"white","bold":false},{"text":" §7— sell items at the market"}]

# --- Tail — point at /quests list for the branching tracks -----------------
execute if entity @s[advancements={server:reach_income_100=true,server:reach_elo_1100=false,server:reach_income_1000=false,server:beat_gym_2=false}] run title @s actionbar [{"text":"§a✓ Main quests done!  ","bold":true},{"text":"§7Branching tracks unlocked — open §f/quests list","color":"gray","bold":false}]
