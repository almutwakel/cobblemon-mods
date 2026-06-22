#!/usr/bin/env python3
"""
Wires the custom RCT boss trainers into a dedicated `server_bosses` series so they
stop polluting every other series' card list, and gives them a linear progression
chain for the Trainer Card.

Usage:
    python3 apply_server_bosses_series.py <datapack_rctmod_dir>
    e.g. python3 apply_server_bosses_series.py datapacks/server-gyms/data/rctmod

Idempotent — safe to re-run. Skips legacy gym_11..gym_19 and the native BDSP
gym_leader_* overrides (which already belong to the bdsp series).
"""
import json, sys, os

SERIES_ID = "server_bosses"

# Main progression: linear, each requires the previous (always-accessible once unlocked).
PROGRESSION = [
    "gym_01_ground", "gym_02_grass", "gym_03_fighting", "gym_04_steel",
    "gym_05_fire", "gym_06_electric", "gym_07_water", "gym_08_psychic",
    "gym_09_dragon", "gym_10_ghost",
]
# Elite Four + Champion: continues the chain after the 10 gyms.
E4 = ["gym_20_alder", "gym_21_cynthia", "gym_22_ash", "gym_23_lance", "gym_24_n"]
CHAIN = PROGRESSION + E4  # one linear line: gym_01 -> ... -> gym_10 -> E4 -> N

# requiredDefeats per chain node (bare filenames, OR-within-set / AND-across-sets).
CHAIN_REQ = {tid: ([] if i == 0 else [[CHAIN[i - 1]]]) for i, tid in enumerate(CHAIN)}


def series_file(root):
    return os.path.join(root, "series", f"{SERIES_ID}.json")


def write_series(root):
    os.makedirs(os.path.dirname(series_file(root)), exist_ok=True)
    data = {
        "title": {"literal": "Server Bosses"},
        "description": {"literal": "Gym Leaders, the Battle Tower, and the Elite Four."},
        "difficulty": 8,
        "requiredSeries": [],
        # Series-local cap floor. Must be <= the lowest trainer level (gym_01 = 15) so the
        # progress graph prints each trainer's REAL level (max(initialLevelCap, trainerLevel))
        # instead of the global 200, and so the per-player cap tracks progression (15 -> 70)
        # — which keeps the next key trainer inside the spawn window (min(cap, party)).
        # allowOverLeveling=true still lets players level past this freely; this only governs
        # spawn-matching + graph display for players who are IN this series.
        "initialLevelCap": 15,
    }
    with open(series_file(root), "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"  series  -> {series_file(root)}")


def classify(name):
    """Return (should_edit, required_defeats, optional) for a mob filename stem."""
    if name in CHAIN_REQ:
        return True, CHAIN_REQ[name], False          # gyms 1-10 + E4: core, ordered
    if name.startswith("bt_"):
        # Both normal and challenge battle-tower entries are optional and independently
        # accessible — challenge does NOT require beating the normal version first.
        return True, [], True
    return False, None, None                            # legacy gym_11-19, gym_leader_*: skip


def main(root):
    mobs = os.path.join(root, "mobs", "trainers", "single")
    if not os.path.isdir(mobs):
        sys.exit(f"not found: {mobs}")

    write_series(root)

    edited = skipped = 0
    for fn in sorted(os.listdir(mobs)):
        if not fn.endswith(".json"):
            continue
        name = fn[:-5]
        do, req, optional = classify(name)
        if not do:
            skipped += 1
            continue
        path = os.path.join(mobs, fn)
        d = json.load(open(path))
        d["series"] = [SERIES_ID]
        d["requiredDefeats"] = req
        d["optional"] = optional
        with open(path, "w") as f:
            json.dump(d, f, indent=2)
            f.write("\n")
        edited += 1

    print(f"  edited {edited} mob files, skipped {skipped} (legacy + bdsp leaders)")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(os.path.abspath(sys.argv[1]))
