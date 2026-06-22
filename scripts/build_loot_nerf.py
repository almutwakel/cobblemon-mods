#!/usr/bin/env python3
"""
Generates the `server-loot-nerf` datapack: overrides of RCT's generic wild-trainer
loot tables to retune drop rarity (move type gems -> Legendary, mint leaves -> Epic,
strip leveling/abusable items from Epic/Legendary).

Reads the originals from the vendored RCT reference and writes overrides into the repo.

Usage: python3 build_loot_nerf.py
"""
import json, os, copy

REF = "cobblemon-server/reference/rctmod/common/src/main/resources/data/rctmod/loot_table"
OUT = "datapacks/server-loot-nerf/data/rctmod/loot_table"

def load(rel):
    return json.load(open(os.path.join(REF, rel)))

def entries(tbl):
    return tbl["pools"][0]["entries"]

def named(es, *suffixes):
    """entries whose item name ends with any of the suffixes"""
    return [e for e in es if any(e.get("name","").endswith(s) for s in suffixes)]

def without(es, names):
    return [e for e in es if e.get("name") not in names]

def write(rel, tbl):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(tbl, f, indent=2); f.write("\n")
    print(f"  {rel}")

# --- harvest the entries we're moving (keep their set_count etc.) ---
TYPE_GEMS = named(entries(load("generic/uncommon/battle.json")),
                  "_gem")                              # 17 type gems (uncommon)
DRAGON_GEM = named(entries(load("generic/rare/battle.json")),
                   "dragon_gem")                       # dragon_gem (rare) -> legendary too
ALL_GEMS = TYPE_GEMS + DRAGON_GEM                      # 18 gems -> legendary
MINT_LEAVES = named(entries(load("generic/uncommon/nature.json")),
                    "_mint_leaf")                      # 6 mint leaves
print(f"moving {len(ALL_GEMS)} type gems -> legendary, {len(MINT_LEAVES)} mint leaves -> epic\n")

# ---------------- UNCOMMON: strip gems / mint leaves ----------------
t = load("generic/uncommon/battle.json")
t["pools"][0]["entries"] = [e for e in entries(t) if not e.get("name","").endswith("_gem")]
write("generic/uncommon/battle.json", t)

t = load("generic/uncommon/nature.json")
t["pools"][0]["entries"] = [e for e in entries(t) if not e.get("name","").endswith("_mint_leaf")]
write("generic/uncommon/nature.json", t)

# ---------------- RARE: strip dragon_gem (moves to legendary) ----------------
t = load("generic/rare/battle.json")
t["pools"][0]["entries"] = without(entries(t), {"cobblemon:dragon_gem"})
write("generic/rare/battle.json", t)

# ---------------- EPIC ----------------
t = load("generic/epic/medicine.json")
t["pools"][0]["entries"] = without(entries(t),
    {"cobblemon:exp_candy_m", "cobblemon:exp_candy_l"})
write("generic/epic/medicine.json", t)

t = load("generic/epic/diverse.json")
t["pools"][0]["entries"] = without(entries(t), {"minecraft:sniffer_egg"})
write("generic/epic/diverse.json", t)

t = load("generic/epic/nature.json")               # gain the mint leaves
entries(t).extend(copy.deepcopy(MINT_LEAVES))
write("generic/epic/nature.json", t)

# ---------------- LEGENDARY ----------------
t = load("generic/legendary/medicine.json")
t["pools"][0]["entries"] = without(entries(t), {"cobblemon:exp_candy_xl"})
write("generic/legendary/medicine.json", t)

t = load("generic/legendary/pokeballs.json")
t["pools"][0]["entries"] = without(entries(t), {"cobblemon:ancient_origin_ball"})
write("generic/legendary/pokeballs.json", t)

t = load("generic/legendary/archeology.json")
t["pools"][0]["entries"] = without(entries(t), {"cobblemon:ancient_origin_ball"})
write("generic/legendary/archeology.json", t)

t = load("generic/legendary/diverse.json")          # empty it -> drops nothing
t["pools"] = []
write("generic/legendary/diverse.json", t)

t = load("generic/legendary/battle.json")           # gain all 18 type gems
entries(t).extend(copy.deepcopy(ALL_GEMS))
write("generic/legendary/battle.json", t)

t = load("generic/legendary/masterball.json")       # strip master_ball everywhere
t["pools"] = []                                     # (boss Giovanni + unique/pokeballs ref this)
write("generic/legendary/masterball.json", t)

# ---------------- pack.mcmeta ----------------
mcmeta = {"pack": {"pack_format": 48,
    "description": "Retunes RCT wild-trainer loot (gems->Legendary, mints->Epic, strips abusable items)."}}
with open("datapacks/server-loot-nerf/pack.mcmeta", "w") as f:
    json.dump(mcmeta, f, indent=2); f.write("\n")
print("  pack.mcmeta")
