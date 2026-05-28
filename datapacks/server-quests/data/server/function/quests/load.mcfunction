# Runs once per world load. Sets up the persistent scoreboard objectives the HUD needs.
#
# cq_hud_toggle: a `trigger` objective. Players run `/trigger cq_hud_toggle` to opt the HUD
#   on/off. Score 0 = on (default), score 1 = off. Trigger objectives are the only way non-op
#   players can mutate scoreboard state via commands.
#
# cq_hud_tick: a dummy objective. Players' scores increment every tick while online. We use it
#   to throttle the action-bar refresh to every 30 ticks (~1.5s) so we're not spamming on every
#   server tick.

scoreboard objectives add cq_hud_toggle trigger
scoreboard objectives add cq_hud_tick dummy

# Enforce keepInventory on every world load — re-asserts even if an op flipped it off.
gamerule keepInventory true

# Make the trigger visible-and-usable for everyone already online.
scoreboard players enable @a cq_hud_toggle
