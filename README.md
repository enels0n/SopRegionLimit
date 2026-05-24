# SopRegionLimit

`SopRegionLimit` is a WorldGuard helper plugin for limiting player region creation.

It intercepts WorldGuard claim/remove commands, validates WorldEdit selections, applies per-group and per-world limits, and can automatically clean up inactive regions.

## Requirements

- `Java 8+`
- `Paper/Spigot` on any server version supported by `SopLib`
- [SopLib](https://github.com/enels0n/SopLib)
- [WorldEdit](https://modrinth.com/plugin/worldedit) or [FastAsyncWorldEdit](https://modrinth.com/plugin/fastasyncworldedit)
- [WorldGuard](https://modrinth.com/plugin/worldguard)
- [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi/versions) is optional and is only used inside plugin messages

## Version Model

`SopRegionLimit` is intentionally compiled against `Spigot 1.16.5` and kept compatible with `Java 8+`.

The goal is that gameplay plugins do not need their own rebuild every time a new Minecraft version appears.  
Runtime compatibility is delegated to `SopLib`.

In practice that means:

- if a new server version is supported by `SopLib`
- and the plugin itself does not use version-specific NMS/API directly
- then `SopRegionLimit` should continue to work without a dedicated version update

So the effective support policy is:

- compile target: `1.16.5` / `Java 8+`
- runtime support: whatever versions are currently supported by `SopLib`

## What It Does

- Intercepts WorldGuard region claim commands:
  - `/rg claim <name>`
  - `/region claim <name>`
  - `/regions claim <name>`
- Intercepts WorldGuard region delete commands:
  - `/rg remove <name>`
  - `/rg delete <name>`
  - `/rg del <name>`
  - `/rg rem <name>`
- Limits region creation by:
  - max region count
  - min/max X size
  - min/max Y size
  - min/max Z size
  - per-group rules
  - per-world overrides
- Supports two claim modes:
  - `in-global` for top-level regions
  - `in-own-region` for child regions inside a player-owned parent region
- Can auto-apply default WorldGuard flags to:
  - top-level regions
  - child regions
- Can auto-expand Y when a selection is too short
- Can remove expired regions based on owner/member last played time

## Commands

- `/sopregionlimit reload`
- Alias: `/sregionlimit reload`

## Permissions

- `sopregionlimit.admin`
  - Allows `/sopregionlimit reload`
- `sopregionlimit.bypass`
  - Bypasses remove ownership checks
- `sopregionlimit.bypass`
  - Also bypasses claim overlap/count/size checks in claim flow
- `sopregionlimit.vertical-expand.bypass`
  - Prevents forced vertical auto expansion when `vertical-expand` is enabled for the matched group

Group permissions are resolved dynamically from config:

- `sopregionlimit.in-global.<group>`
- `sopregionlimit.in-own-region.<group>`

## Claim Behavior

When a player runs a claim command:

1. The plugin reads the current WorldEdit selection.
2. Only cuboid selections are allowed.
3. The selection cannot touch another player's region unless the player has bypass.
4. The plugin detects whether the region is:
   - a top-level claim in global space
   - or a child claim inside a player-owned parent region
5. Matching limits are resolved from config.
6. If allowed, the region is created and linked with internal flags:
   - `rl-childs`
   - `rl-parent`

Child region ids are created as:

```text
<parent>_<name>
```

Top-level region ids are created as:

```text
<name>
```

All ids are lowercased by the plugin.

## Config Overview

Main sections in `config.yml`:

- `debug`
- `cleaner`
- `limit`
- `limit-per-world`
- `messages`

### Cleaner

```yml
cleaner:
  enabled: true
  period: 3600
  expire-time-days: 30
  log-in-console: true
```

- `period` is in seconds
- `expire-time-days` checks `OfflinePlayer#getLastPlayed()`
- if all owners and members are inactive longer than the threshold, the region is removed

Filter options:

```yml
cleaner:
  filter:
    ignore-without-owners: true
    ignore-worlds: []
    ignore-regions:
      - "__global__"
```

### Global Limits

```yml
limit:
  autoexpand: true
  autoexpand-down: 15
  in-global-default-flags:
    pvp: DENY
  in-own-region-default-flags: {}
  in-global:
    default:
      max-count: 1
      vertical-expand: false
      x-min: 15
      x-max: 40
      y-min: 15
      y-max: 40
      z-min: 15
      z-max: 40
```

Supported fields per group:

- `max-count`
- `vertical-expand`
- `x-min`
- `x-max`
- `y-min`
- `y-max`
- `z-min`
- `z-max`

### Per-World Overrides

```yml
limit-per-world:
  in-global:
    world:
      default:
        max-count: 2
        x-max: 60
```

If a per-world group section exists, it overrides the matching global group section.

## Example Group Setup

```yml
limit:
  in-global:
    default:
      max-count: 1
      x-min: 15
      x-max: 40
      y-min: 15
      y-max: 40
      z-min: 15
      z-max: 40
      vertical-expand: false
    vip:
      max-count: 3
      x-min: 15
      x-max: 50
      y-min: 15
      y-max: 50
      z-min: 15
      z-max: 50
      vertical-expand: false
```

Then grant:

```text
sopregionlimit.in-global.vip
```

## PlaceholderAPI

The plugin does not register its own placeholders.

`PlaceholderAPI` is only used when resolving text inside `messages.*`, so you can safely use normal PAPI placeholders there for players.

## Notes

- Claim validation is done by intercepting player WG commands, not by replacing WorldGuard itself.
- Region names must match:

```text
[a-zA-Z0-9_]{3,20}
```

- `__global__` cannot be used as a custom region name.
- Non-cuboid WorldEdit selections are rejected.
