# Runs every server tick (registered via `minecraft:tick` function tag).
#
# Two responsibilities:
#   1. Drain any `/trigger cq_hud_toggle` activations: if a player has score 1, flip their tag
#      and reset the trigger so they can run it again later.
#   2. Throttle: bump every online player's cq_hud_tick score; only when the score % 30 == 0
#      do we run the per-player HUD refresh (action bar). That's ~1.5s cadence.

# --- Handle opt-out toggles --------------------------------------------------
execute as @a[scores={cq_hud_toggle=1..}] run function server:quests/hud/handle_toggle

# --- Throttle the HUD refresh to every 30 ticks (1.5s) ----------------------
scoreboard players add @a cq_hud_tick 1

execute as @a[scores={cq_hud_tick=30..},tag=!cq_hud_off] run function server:quests/hud/tick_player

# Reset the tick counter for anyone who hit 30 this tick (so we count fresh next round).
scoreboard players set @a[scores={cq_hud_tick=30..}] cq_hud_tick 0

# Make sure new joiners get the trigger usable. `scoreboard players enable` is idempotent on
# already-enabled players, so running it cheaply each tick covers anyone who joined since load.
scoreboard players enable @a cq_hud_toggle
