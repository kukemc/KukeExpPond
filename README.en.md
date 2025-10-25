# KukeExpPond - Multi-pond XP Hot Spring & Rewards Plugin

[中文说明 / Chinese README](./README.md)

![bStats](https://bstats.org/signatures/bukkit/KukeExpPond.svg)

## Features
- Multiple ponds with world/region configuration
- Bottle and direct XP modes
- Rewards via money, points, and custom commands
- Daily reset with compensation when server misses schedule
- ActionBar, BossBar, and Title display
- Teleport enter blocking with bypass permission
- Ice prevention and debug logging
- Hot-reload and auto config update
- Import from Exppond-Next
- PlaceholderAPI variables
- Compatible with 1.8–1.21 servers

## PlaceholderAPI Variables
| Variable | Description |
| --- | --- |
| `%kep_pond%` | Current pond name (empty if not in a pond) |
| `%kep_money_today%` | Money earned today in the current pond |
| `%kep_money_max%` | Daily money cap in the current pond |
| `%kep_money_total%` | Total accumulated money |
| `%kep_points_today%` | Points earned today in the current pond |
| `%kep_points_max%` | Daily points cap in the current pond |
| `%kep_points_total%` | Total accumulated points |
| `%kep_pond_<pond>%` | Explicit suffix: name of the specified pond |
| `%kep_money_today_<pond>%` | Explicit suffix: money today for the specified pond |
| `%kep_points_today_<pond>%` | Explicit suffix: points today for the specified pond |
| `%kep_money_max_<pond>%` | Explicit suffix: daily money cap for the specified pond |
| `%kep_points_max_<pond>%` | Explicit suffix: daily points cap for the specified pond |

> Note: Explicit suffix supports `*_today_<pond>` and `*_max_<pond>`; `*_total` does not support suffixes.


## Commands & Permissions
| Command | Description | Permission |
| --- | --- | --- |
| `/kep help` | Show help | — |
| `/kep wand` | Get region selection wand (`pos1` left, `pos2` right) | `kukeexppond.admin` |
| `/kep create <pond>` | Create pond from current selection | `kukeexppond.admin` |
| `/kep setregion <pond>` | Apply current selection to pond `region` | `kukeexppond.admin` |
| `/kep list` | List configured ponds | — |
| `/kep info <pond>` | Show details of the pond | — |
| `/kep reload` | Reload config and rebuild tasks | `kukeexppond.admin` |
| `/kep updateconfig` | Fill missing config keys automatically | `kukeexppond.admin` |
| `/kep importnext [override]` | Import from Exppond-Next; `override` replaces existing | `kukeexppond.admin` |

> Tip: `wand` must be used in-game; `importnext` requires `plugins/Exppond-Next/config.yml`. Bypass permission: `kukeexppond.bypass`.

## Example Usage
```bash
/kep wand
/kep create default
/kep setregion default
/kep reload
/kep importnext override
```

## config.yml Example (excerpt)
```yaml
config_version: "1.2.0"

general:
  prefix: "&7[&bKukeExpPond&7] "
  storage: yaml
  debug: false
  daily_reset_time: "00:00"
  timezone: "Asia/Shanghai"
  ice_prevention:
    enable: true
    check_interval: 30
    convert_ice_to_water: true
    debug_log: false

ponds:
  default:
    world: world
    region:
      pos1: { x: 0, y: 64, z: 0 }
      pos2: { x: 10, y: 70, z: 10 }
    permission:
      pond_permission: pond.default
      pond_join: true
      bypass_permission: kukeexppond.bypass
    mode:
      xp_mode: bottle
    bottle:
      enable: true
      bottle_speed: 5
      bottle_count: 3
      only_above_water: true
      drop_height: 4.0
    reward:
      exp:
        enable: true
        speed: 10
        count: 5
      money:
        enable: true
        speed: 10
        count: 5
        max: 500
      points:
        enable: false
        speed: 15
        count: 2
        max: 200
      command:
        enable: false
        speed: 30
        commands:
          - "say Congrats {player}"
      async: true
      daily_reset_time: "04:00"
      timezone: "Asia/Shanghai"
    ui:
      actionbar:
        enable: true
        template: "&b{pond}&7: Money {money_today}/{money_max} | Points {points_today}/{points_max}"
      bossbar:
        enable: true
        color: BLUE
        style: SEGMENTED_10
        items: [MONEY_PROGRESS, POINTS_PROGRESS, ONLINE_IN_POND, NEXT_REWARD_COUNTDOWN]
    teleport:
      block_enter_by_teleport: true
      message: "&ePlease do not enter the pond via teleport"
```

## Feedback
- QQ Group: `981954623`
- GitHub: `https://github.com/kukemc/KukeExpPond`