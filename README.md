# KukeExpPond - 新一代的现代化 经验挂机池 插件

[English README](./README.en.md)

![bStats](https://bstats.org/signatures/bukkit/KukeExpPond.svg)

## 功能
- 支持多池管理（按世界与选区配置多个池）
- 支持瓶模式与直接模式经验发放
- 支持金币、点券与自定义指令奖励
- 支持每日重置与补偿重置（服务器错过预定时间时）
- 支持 ActionBar、BossBar、Title 展示
- 支持粒子特效，拥有沉浸真实的温泉效果
- 支持传送进入拦截与绕过权限
- 支持防结冰系统与调试日志开关
- 支持配置热重载与自动补全缺失项
- 支持从 Exppond-Next 导入池配置
- 支持 PlaceholderAPI 变量
- 兼容 1.8–1.21 服务端

## 指令与权限
| 指令 | 介绍 | 权限 |
| --- | --- | --- |
| `/kep help` | 显示帮助信息 | — |
| `/kep wand` | 获取区域选择工具（左键 `pos1`，右键 `pos2`） | `kukeexppond.admin` |
| `/kep create <池名>` | 以当前选区创建池，写入世界与坐标 | `kukeexppond.admin` |
| `/kep setregion <池名>` | 将当前选区应用到指定池的 `region` | `kukeexppond.admin` |
| `/kep list` | 查看所有已配置的池及模式/瓶状态 | — |
| `/kep info <池名>` | 查看指定池的世界、区域、模式、权限等详情 | — |
| `/kep reload` | 重载配置并重建奖励/经验/UI/特效任务 | `kukeexppond.admin` |
| `/kep updateconfig` | 自动检查并补全缺失的配置项 | `kukeexppond.admin` |
| `/kep importnext [override]` | 从 Exppond-Next 导入配置；`override` 表示覆盖同名池 | `kukeexppond.admin` |

> 提示：`wand` 需要在游戏内执行；`importnext` 需服务端存在 `plugins/Exppond-Next/config.yml`。`bypass` 绕过进入限制的权限节点为 `kukeexppond.bypass`。

## 使用示例
```bash
# 获取选择工具并创建名为 default 的池
/kep wand
/kep create default

# 将当前选区写入指定池
/kep setregion default

# 重载配置并重建任务
/kep reload

# 从 Exppond-Next 导入（覆盖同名池）
/kep importnext override
```

## config.yml 配置文件
```yaml
############################################
# KukeExpPond 配置文件 (1.8–1.21 兼容)
# 所有配置均可热重载。
#
# 变量占位：
#  - {player}: 玩家名
#  - {pond}: 池名称
#  - {money_today}/{money_max}: 今日/每日上限的金币
#  - {points_today}/{points_max}: 今日/每日上限的点券
#  - 其他 UI 项按实现映射
#
# 使用说明：
# - 该文件为默认配置示例，插件启动时会自动补全缺失项。
# - 修改后可执行 `/kep reload` 热重载；也可用 `/kep updateconfig` 自动补全缺失项。
# - 文本支持彩色代码（&a/&b 等），按服务端显示规则渲染。
############################################

# 配置文件版本（用于自动更新检测，缺失时会自动补全）
config_version: "1.2.0"

general:
  # 全局消息前缀
  prefix: "&7[&bKukeExpPond&7] "
  # 数据存储方式：yaml 或 sqlite（首版推荐 yaml）
  storage: yaml
  debug: false      # 调试日志：奖励计算、占位符取值、事件拦截
  # 全局每日重置时间（HH:mm），池级可覆盖；默认凌晨 00:00
  daily_reset_time: "00:00"
  # 全局时区（例如 Asia/Shanghai），池级可覆盖
  timezone: "Asia/Shanghai"
  # 防结冰系统配置
  ice_prevention:
    enable: true                      # 是否启用防结冰系统
    check_interval: 30                # 检查间隔（秒）
    convert_ice_to_water: true        # 是否将冰块转换为水
    debug_log: false                  # 是否记录防结冰调试日志
  version_mapping:
    # 粒子/音效的跨版本回退策略，仅示例（找不到枚举时使用这些回退）
    particle_fallback: CLOUD
    join_sound_fallback: ENTITY_EXPERIENCE_ORB_PICKUP
    leave_sound_fallback: BLOCK_WATER_AMBIENT

# 多池配置：键为池名
ponds:
  default:
    world: world
    region:
      # 选区两个点，支持跨版本；未设置时命令无法创建/启用
      pos1: { x: 0, y: 64, z: 0 }
      pos2: { x: 10, y: 70, z: 10 }
    permission:
      pond_permission: pond.default     # 进入权限节点（动态，按池自定义）
      pond_join: true                   # 无权限是否允许进入（true/false）
      bypass_permission: kukeexppond.bypass  # 可跳过限制（如传送阻止）
    mode:
      xp_mode: bottle                   # bottle 或 direct
    bottle:
      enable: true
      bottle_speed: 5                   # 每轮生成间隔（秒）
      bottle_count: 3                   # 每轮生成个数
      only_above_water: true            # 仅在水方块上方生成
      drop_height: 4.0                  # 瓶子生成相对高度（米），从玩家附近位置往上抬高后投掷
    reward:
      exp:
        enable: true
        speed: 10                      # 直接模式下每周期给予经验
        count: 5                       # 每周期经验点数
      money:
        enable: true
        speed: 10                       # 发放周期（秒）
        count: 5                        # 每周期金额
        max: 500                        # 每日上限
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
      async: true                         # 强制异步发放（必要）
      daily_reset_time: "04:00"          # 每日重置时间（HH:mm）
      timezone: "Asia/Shanghai"          # 时区
    ui:
      title:
        join_title: "&b欢迎来到经验温泉"
        join_subTitle: "&7放松身心，享受奖励"
        leave_title: "&7你离开了经验温泉"
        leave_subTitle: ""
        error_title: "&c无法进入"
        error_subTitle: "&7你没有权限"
        fadeIn: 10
        stay: 40
        fadeOut: 10
      actionbar:
        enable: true
        template: "&b{pond}&7: 金币 {money_today}/{money_max} | 点券 {points_today}/{points_max}"
      bossbar:
        enable: true
        color: BLUE
        style: SEGMENTED_10
        items: [MONEY_PROGRESS, POINTS_PROGRESS, ONLINE_IN_POND, NEXT_REWARD_COUNTDOWN]
      message:
        money: "&a获得金币 {amount} (今日 {money_today}/{money_max})"
        points: "&a获得点券 {amount} (今日 {points_today}/{points_max})"
        teleport: "&e请不要使用传送功能进入挂机池"
    sound:
      join_sound_id: ENTITY_PLAYER_LEVELUP
      join_sound_volume: 1.0
      join_sound_pitch: 1.2
      leave_sound_id: BLOCK_WATER_AMBIENT
      leave_sound_volume: 0.8
      leave_sound_pitch: 1.0
    effects:
      steam_hot_spring:           #  蒸汽特效
        enable: true
        rate: 10
        particle: CLOUD
      water_bubble:               #  气泡特效
        enable: true
        rate: 10
        particle: BUBBLE_COLUMN_UP
      smoke_bottom:               #  烟雾特效
        enable: true
        rate: 20
        particle: CAMPFIRE_COSY_SMOKE
      density: 1.0
    teleport:
      block_enter_by_teleport: true
      message: "&e请不要使用传送功能进入挂机池"
```

## PlaceholderAPI 变量
| 变量 | 说明 |
| --- | --- |
| `%kep_pond%` | 当前所在池名（为空表示不在池中） |
| `%kep_money_today%` | 当前池今日获得金币 |
| `%kep_money_max%` | 当前池每日金币上限 |
| `%kep_money_total%` | 累计获得金币总数 |
| `%kep_points_today%` | 当前池今日获得点券 |
| `%kep_points_max%` | 当前池每日点券上限 |
| `%kep_points_total%` | 累计获得点券总数 |
| `%kep_pond_<池名>%` | 指定池名（显式后缀） |
| `%kep_money_today_<池名>%` | 指定池的今日金币（显式后缀） |
| `%kep_points_today_<池名>%` | 指定池的今日点券（显式后缀） |
| `%kep_money_max_<池名>%` | 指定池的每日金币上限（显式后缀） |
| `%kep_points_max_<池名>%` | 指定池的每日点券上限（显式后缀） |

> 说明：显式后缀支持 `*_today_<池名>` 与 `*_max_<池名>`；`*_total` 不支持后缀。


## 插件截图
![screenshot](https://github.com/user-attachments/assets/83408843-b970-4474-8637-5865eaba800d)

## 反馈
- QQ 讨论群：`981954623`
- GitHub：`https://github.com/kukemc/KukeExpPond`
