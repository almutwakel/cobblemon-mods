# Runs `as <player>` (one player at a time) when their `cq_hud_toggle` trigger has been fired.
#
# Trigger objectives accumulate the value the player wrote (default add 1). We don't care about
# the value — any non-zero score means "they hit the trigger". We flip the `cq_hud_off` tag and
# reset the score back to 0, then re-enable the trigger so the same player can toggle again.

# Flip the tag.
execute if entity @s[tag=cq_hud_off] run tag @s remove cq_hud_off
execute unless entity @s[tag=cq_hud_off] run tag @s add cq_hud_off

# Reset + re-enable.
scoreboard players set @s cq_hud_toggle 0
scoreboard players enable @s cq_hud_toggle

# Tell the player.
execute if entity @s[tag=cq_hud_off] run tellraw @s [{"text":"§7Quest HUD ","italic":false},{"text":"OFF","color":"red","bold":true},{"text":" — you'll still see chat updates on quest progress. Run §f/trigger cq_hud_toggle§r§7 to turn it back on."}]
execute unless entity @s[tag=cq_hud_off] run tellraw @s [{"text":"§7Quest HUD ","italic":false},{"text":"ON","color":"green","bold":true},{"text":" — current quest will appear above your hotbar."}]
