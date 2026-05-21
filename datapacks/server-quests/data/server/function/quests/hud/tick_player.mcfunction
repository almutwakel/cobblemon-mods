# Runs `as <player>` every ~30 ticks (~1.5s) for HUD-on players. Decides which quest is
# "currently active" by walking the LINEAR quest chain top-down. Parallel side quests
# (reach_income_*, reach_elo_*, join_colony) intentionally don't appear in the HUD — they
# show up in the L-key advancement tree and announce via toast/chat on grant. Focusing the
# HUD on the linear chain keeps the one-line action bar coherent.
#
# Cascade order (linear chain): craft_pokeball → catch_pokemon → farm_carrots →
#   beat_gym_1 → first_pvp_win → reach_elo_1100
#
# After 1100 ELO, the linear chain is "complete" and the HUD goes silent (the tail goal
# `reach_income_1000` is shown as a dangling carrot — see the final line). Players who want
# to keep climbing the ELO ladder check the L tree.

# --- 1. Craft a Poké Ball ----------------------------------------------------
execute if entity @s[advancements={server:craft_pokeball=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Craft a Poké Ball","color":"white","bold":false},{"text":" §7— surround a §fred apricorn§7 with sticks on a crafting table"}]

# --- 2. Catch a Pokémon ------------------------------------------------------
execute if entity @s[advancements={server:craft_pokeball=true,server:catch_pokemon=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Catch a Pokémon","color":"white","bold":false},{"text":" §7— right-click a wild mon while holding a Poké Ball"}]

# --- 3. Farm carrots ---------------------------------------------------------
execute if entity @s[advancements={server:craft_pokeball=true,server:catch_pokemon=true,server:farm_carrots=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Stockpile 32 Carrots","color":"white","bold":false},{"text":" §7— find a village or plant your own"}]

# --- 4. Beat Gym 1 (Misty) ---------------------------------------------------
execute if entity @s[advancements={server:craft_pokeball=true,server:catch_pokemon=true,server:farm_carrots=true,server:beat_gym_1=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Defeat Gym 1: Misty","color":"white","bold":false},{"text":" §7— find her at §f/warp gym1§7 and challenge her"}]

# --- 5. First ranked PvP win --------------------------------------------------
execute if entity @s[advancements={server:beat_gym_1=true,server:first_pvp_win=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Win a Ranked Battle","color":"white","bold":false},{"text":" §7— §f/ranked challenge <player>§7"}]

# --- 6. ELO 1100 (current ladder goal) ---------------------------------------
execute if entity @s[advancements={server:first_pvp_win=true,server:reach_elo_1100=false}] run title @s actionbar [{"text":"§e★ Quest: ","bold":true},{"text":"Reach 1100 ELO","color":"white","bold":false},{"text":" §7— keep winning Ranked"}]

# --- Tail (linear chain complete) — point at side goals ---------------------
execute if entity @s[advancements={server:reach_elo_1100=true,server:reach_income_1000=false}] run title @s actionbar [{"text":"§a✓ Linear quests done!  ","bold":true},{"text":"Side goal: ","color":"gray","bold":false},{"text":"Reach $1,000","color":"white","bold":true}]
