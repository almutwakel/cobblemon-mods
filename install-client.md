# Installing the Cobblemon Server Modpack

Everything you need to join the server: NeoForge, Cobblemon, Minecolonies, QoL / performance mods, and our in-repo gym-leader mod — all in one `.mrpack` file.

## Prerequisites

- A legitimate Minecraft Java Edition account (Mojang/Microsoft login)
- **Java 21** — required by NeoForge 1.21.1
  - macOS: `brew install openjdk@21`
  - Windows/Linux: [Adoptium Temurin 21](https://adoptium.net/)

## Step 1 — Install Prism Launcher

Prism Launcher is a free, open-source Minecraft launcher that handles multiple instances cleanly and supports `.mrpack` imports natively.

Download: https://prismlauncher.org/download/

Install and launch it once. On first run it'll ask for:
- Your Microsoft account (sign in)
- The Java 21 install you set up above — point it at that JDK when prompted

## Step 2 — Import the modpack

1. Grab most recent `Cobblemon Server-x.x.x.mrpack` from [here](https://drive.google.com/drive/folders/1fdQb5aa_VfR7ZUAul_NZGYkoS6GfHzBX?usp=sharing).
2. In Prism: **Drag .mrpack into the client**.
3. Name the instance (e.g. "Cobblemon Server") and click **OK**.
4. Prism downloads NeoForge 1.21.1 + 36 mods from Modrinth/CurseForge. Takes 2–5 minutes depending on connection.

## Step 3 — Launch and connect

1. Double-click the instance → Minecraft launches with all mods loaded.
2. **Multiplayer → Add Server**
   - Server Name: `Cobblemon Server`
   - Server Address: `108.21.168.120`
3. Join.

## Updating the pack

When a new `.mrpack` is released:
1. In Prism, right-click your instance → **Edit**.
2. **Version → Reinstall from zip** → pick the new `.mrpack`.

Your worlds, screenshots, and settings are preserved; only the mods are swapped.

## Troubleshooting

- **"Java not found" on launch** — Prism → **Settings → Java** → point to your Java 21 JDK.
- **Black screen / instant crash** — check Prism's **Logs** tab. 90% of issues on launch are a mismatched Java version or outdated graphics driver.
- **"Outdated server" on connect** — the pack's Minecraft/NeoForge version doesn't match the server. Make sure you're on the latest `.mrpack`.
- **Mods not loading in-game** — verify Prism launched the instance (not vanilla): main menu should say `NeoForge 21.1.227` in the bottom-left, and the **Mods** button should show 36+ entries.

## What's in the pack

See `README.md` at the repo root for the full mod list with descriptions. Short version:
- **Cobblemon** core + Mega Showdown + Cobblemon Ranked + CobbleFurnies + Cobblemon Integrations
- **Minecolonies** and its libraries (colony/NPC substrate for gym leaders)
- **Serene Seasons** + **Enhanced Celestials** + **Terralith** (worldgen / time-of-year variation)
- **Waystones**, **WorldEdit**
- Perf/ops: Lithium, FerriteCore, ModernFix, Spark, Sodium, ImmediatelyFast, EntityCulling, Fast IP Ping
- QoL: Xaero's Minimap + World Map, JEI
- **cobblemon-npc** (in-repo) — gym-leader progression, profession-based teams, signature Pokemon, trainer records
