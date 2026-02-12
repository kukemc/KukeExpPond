package kuke.kukeExpPond.ui;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.pond.Pond;
import kuke.kukeExpPond.pond.PondManager;
import kuke.kukeExpPond.player.PlayerStateManager;
import kuke.kukeExpPond.storage.DataStore;
import kuke.kukeExpPond.util.ActionBarUtil;
import kuke.kukeExpPond.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Periodic UI updater for ActionBar and BossBar.
 * - Runs on main thread; reads config each tick for hot-reload friendliness.
 * - BossBar API available 1.9+; fallback to ActionBar on legacy.
 */
public class UiManager {
    private final KukeExpPond plugin;
    private final PondManager pondManager;
    private final PlayerStateManager states;
    private final DataStore dataStore;
    private final ActionBarUtil actionBar;
    private final ChatUtil chat;

    private BukkitTask task;

    // BossBar support (1.9+).
    // Map<PlayerUUID, Map<BarKey, BossBarObject>>
    private final Map<UUID, Map<String, Object>> activeBossBars = new HashMap<>();
    private boolean bossApiChecked = false;
    private boolean bossApiAvailable = false;

    public UiManager(KukeExpPond plugin, PondManager pondManager, PlayerStateManager states, DataStore dataStore) {
        this.plugin = plugin;
        this.pondManager = pondManager;
        this.states = states;
        this.dataStore = dataStore;
        this.actionBar = new ActionBarUtil(plugin);
        this.chat = new ChatUtil(plugin);
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                updateUi();
            }
        }, 20L, 20L); // every second
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // Cleanup bossbars
        removeAllBossBars();
    }

    public void rebuild() {
        start();
    }

    private void updateUi() {
        checkBossBarApi();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            String pondName = states.getCurrentPond(id);
            if (pondName == null) {
                // Outside pond: remove bossbar if exists
                removePlayerBossBars(id);
                continue;
            }
            Pond pond = pondManager.getByName(pondName);
            if (pond == null) {
                removePlayerBossBars(id);
                continue;
            }

            // ActionBar
            if (plugin.getConfig().getBoolean("ponds." + pondName + ".ui.actionbar.enable", true)) {
                String tpl = plugin.getConfig().getString("ponds." + pondName + ".ui.actionbar.template", "&b{pond}&7: 金币 {money_today}/{money_max} | 点券 {points_today}/{points_max}");
                String msg = formatString(tpl, p, pondName);
                actionBar.send(p, msg);
            }

            // BossBar (if API available)
            if (bossApiAvailable && plugin.getConfig().getBoolean("ponds." + pondName + ".ui.bossbar.enable", true)) {
                ensureAndUpdateBossBars(p, pondName);
            } else {
                // Fallback: ensure no stale bossbar
                removePlayerBossBars(id);
            }
        }
    }

    private String formatString(String tpl, Player p, String pondName) {
        if (tpl == null || tpl.isEmpty()) return "";
        
        int moneyToday = dataStore.getDailyMoney(p.getUniqueId(), pondName);
        int pointsToday = dataStore.getDailyPoints(p.getUniqueId(), pondName);
        int commandToday = dataStore.getDailyCommand(p.getUniqueId(), pondName);
        int moneyMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.money.max", 0);
        int pointsMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.points.max", 0);
        int commandMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.command.max", 0);
        int onlineInPond = countPlayersInPond(pondName);
        
        int nextRemain = 0;
        int moneyRemain = 0;
        int pointsRemain = 0;
        int commandRemain = 0;
        
        try {
            if (plugin.getRewardManager() != null) {
                nextRemain = plugin.getRewardManager().getNextRemainingSeconds(pondName);
                moneyRemain = plugin.getRewardManager().getMoneyRemainingSeconds(pondName);
                pointsRemain = plugin.getRewardManager().getPointsRemainingSeconds(pondName);
                commandRemain = plugin.getRewardManager().getCommandRemainingSeconds(pondName);
            } else {
                nextRemain = minRewardSpeed(pondName);
            }
        } catch (Throwable ignored) {
            nextRemain = minRewardSpeed(pondName);
        }

        String out = tpl
                .replace("{player}", p.getName())
                .replace("{pond}", pondName)
                .replace("{money_today}", String.valueOf(moneyToday))
                .replace("{money_max}", String.valueOf(moneyMax))
                .replace("{points_today}", String.valueOf(pointsToday))
                .replace("{points_max}", String.valueOf(pointsMax))
                .replace("{command_today}", String.valueOf(commandToday))
                .replace("{command_max}", String.valueOf(commandMax))
                .replace("{online}", String.valueOf(onlineInPond))
                .replace("{next_reward_countdown}", String.valueOf(nextRemain))
                .replace("{money_countdown}", String.valueOf(moneyRemain))
                .replace("{points_countdown}", String.valueOf(pointsRemain))
                .replace("{command_countdown}", String.valueOf(commandRemain));
        return chat.color(out);
    }

    private static class BarConfig {
        String key;
        String title;
        String color;
        String style;
        String progressType; // MONEY, POINTS, COMMAND, NONE
        
        BarConfig(String key, String title, String color, String style, String progressType) {
            this.key = key;
            this.title = title;
            this.color = color;
            this.style = style;
            this.progressType = progressType;
        }
    }

    private List<BarConfig> getBarConfigs(String pondName) {
        List<BarConfig> configs = new ArrayList<>();
        String basePath = "ponds." + pondName + ".ui.bossbar";
        ConfigurationSection barsSec = plugin.getConfig().getConfigurationSection(basePath + ".bars");

        if (barsSec != null) {
            for (String key : barsSec.getKeys(false)) {
                ConfigurationSection sec = barsSec.getConfigurationSection(key);
                if (sec != null && sec.getBoolean("enable", true)) {
                    configs.add(new BarConfig(
                        key,
                        sec.getString("title", ""),
                        sec.getString("color", "BLUE"),
                        sec.getString("style", "SOLID"),
                        sec.getString("progress", "NONE")
                    ));
                }
            }
        } else {
            // Fallback to legacy config
            String title = "&b" + pondName + "&7: 金币 {money_today}/{money_max} | 点券 {points_today}/{points_max} | 在线 {online} | 下次奖励 {next_reward_countdown}s";
            configs.add(new BarConfig(
                "default",
                title,
                plugin.getConfig().getString(basePath + ".color", "BLUE"),
                plugin.getConfig().getString(basePath + ".style", "SEGMENTED_10"),
                "NONE"
            ));
        }
        return configs;
    }

    private void ensureAndUpdateBossBars(Player p, String pondName) {
        try {
            UUID id = p.getUniqueId();
            Map<String, Object> playerBars = activeBossBars.computeIfAbsent(id, k -> new HashMap<>());
            List<BarConfig> configs = getBarConfigs(pondName);
            Set<String> activeKeys = new HashSet<>();

            Class<?> bossBarClass = Class.forName("org.bukkit.boss.BossBar");
            Class<?> barColorClass = Class.forName("org.bukkit.boss.BarColor");
            Class<?> barStyleClass = Class.forName("org.bukkit.boss.BarStyle");

            for (BarConfig cfg : configs) {
                activeKeys.add(cfg.key);
                Object bar = playerBars.get(cfg.key);

                // Create if missing
                if (bar == null) {
                    Object color = Enum.valueOf((Class<Enum>) barColorClass, safeEnum(barColorClass, cfg.color, "BLUE"));
                    Object style = Enum.valueOf((Class<Enum>) barStyleClass, safeEnum(barStyleClass, cfg.style, "SOLID"));
                    bar = createBossBarCompat(formatString(cfg.title, p, pondName), color, style);
                    bossBarClass.getMethod("addPlayer", Player.class).invoke(bar, p);
                    try { bossBarClass.getMethod("setVisible", boolean.class).invoke(bar, true); } catch (Throwable ignored) {}
                    playerBars.put(cfg.key, bar);
                }

                // Update
                // Title
                String title = formatString(cfg.title, p, pondName);
                bossBarClass.getMethod("setTitle", String.class).invoke(bar, title);
                
                // Progress
                double progress = calculateProgress(p, pondName, cfg.progressType);
                bossBarClass.getMethod("setProgress", double.class).invoke(bar, progress);
            }

            // Remove bars that are no longer in config
            Iterator<Map.Entry<String, Object>> it = playerBars.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                if (!activeKeys.contains(entry.getKey())) {
                    Object bar = entry.getValue();
                    try {
                        bossBarClass.getMethod("removePlayer", Player.class).invoke(bar, p);
                    } catch (Throwable ignored) {}
                    it.remove();
                }
            }

        } catch (Throwable t) {
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().warning("[UI] BossBar error: " + t.getMessage());
            }
            removePlayerBossBars(p.getUniqueId());
        }
    }

    private double calculateProgress(Player p, String pondName, String type) {
        if (type == null) return 1.0;
        
        switch (type.toUpperCase()) {
            case "MONEY":
                int moneyToday = dataStore.getDailyMoney(p.getUniqueId(), pondName);
                int moneyMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.money.max", 1);
                return moneyMax > 0 ? Math.max(0.0, Math.min(1.0, (double) moneyToday / moneyMax)) : 0.0;
            case "POINTS":
                int pointsToday = dataStore.getDailyPoints(p.getUniqueId(), pondName);
                int pointsMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.points.max", 1);
                return pointsMax > 0 ? Math.max(0.0, Math.min(1.0, (double) pointsToday / pointsMax)) : 0.0;
            case "COMMAND_COUNT":
                int cmdToday = dataStore.getDailyCommand(p.getUniqueId(), pondName);
                int cmdMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.command.max", 1);
                return cmdMax > 0 ? Math.max(0.0, Math.min(1.0, (double) cmdToday / cmdMax)) : 0.0;
            case "COMMAND":
            case "COMMAND_TIMER":
                int cmdSpeed = plugin.getConfig().getInt("ponds." + pondName + ".reward.command.speed", 30);
                int cmdRemain = 0;
                if (plugin.getRewardManager() != null) {
                    cmdRemain = plugin.getRewardManager().getCommandRemainingSeconds(pondName);
                }
                return cmdSpeed > 0 ? Math.max(0.0, Math.min(1.0, (double) (cmdSpeed - cmdRemain) / cmdSpeed)) : 0.0;
            case "MONEY_TIMER":
                int mSpeed = plugin.getConfig().getInt("ponds." + pondName + ".reward.money.speed", 30);
                int mRemain = 0;
                if (plugin.getRewardManager() != null) {
                    mRemain = plugin.getRewardManager().getMoneyRemainingSeconds(pondName);
                }
                return mSpeed > 0 ? Math.max(0.0, Math.min(1.0, (double) (mSpeed - mRemain) / mSpeed)) : 0.0;
            case "POINTS_TIMER":
                int pSpeed = plugin.getConfig().getInt("ponds." + pondName + ".reward.points.speed", 30);
                int pRemain = 0;
                if (plugin.getRewardManager() != null) {
                    pRemain = plugin.getRewardManager().getPointsRemainingSeconds(pondName);
                }
                return pSpeed > 0 ? Math.max(0.0, Math.min(1.0, (double) (pSpeed - pRemain) / pSpeed)) : 0.0;
            default:
                return 1.0;
        }
    }

    // 适配不同 Bukkit 版本的 BossBar 创建方法
    private Object createBossBarCompat(String title, Object color, Object style) throws Throwable {
        Class<?> barColorClass = Class.forName("org.bukkit.boss.BarColor");
        Class<?> barStyleClass = Class.forName("org.bukkit.boss.BarStyle");
        // 1) 直接尝试常见的三参重载
        try {
            return Bukkit.class.getMethod("createBossBar", String.class, barColorClass, barStyleClass)
                    .invoke(null, title, color, style);
        } catch (NoSuchMethodException ignored) {}

        // 2) 兼容含 BarFlag... 的四参重载
        try {
            Class<?> barFlagClass = Class.forName("org.bukkit.boss.BarFlag");
            // 反射查找匹配的方法，避免不同实现的签名细节差异
            for (java.lang.reflect.Method m : Bukkit.class.getMethods()) {
                if (!"createBossBar".equals(m.getName())) continue;
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length == 4 && ps[0] == String.class && ps[1].isAssignableFrom(barColorClass)
                        && ps[2].isAssignableFrom(barStyleClass) && ps[3].isArray()
                        && ps[3].getComponentType().getName().equals(barFlagClass.getName())) {
                    Object emptyFlags = java.lang.reflect.Array.newInstance(ps[3].getComponentType(), 0);
                    return m.invoke(null, title, color, style, emptyFlags);
                }
            }
        } catch (Throwable ignored) {}

        // 3) 如果仍未找到，抛出异常以便上层回退 actionbar
        throw new NoSuchMethodException("No compatible Bukkit#createBossBar found");
    }

    private String safeEnum(Class<?> enumClass, String value, String def) {
        if (value == null) return def;
        try {
            Enum.valueOf((Class<Enum>) enumClass, value);
            return value;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private void checkBossBarApi() {
        if (bossApiChecked) return;
        bossApiChecked = true;
        try {
            Class.forName("org.bukkit.boss.BossBar");
            bossApiAvailable = true;
        } catch (Throwable ignored) {
            bossApiAvailable = false;
        }
    }

    private void removePlayerBossBars(UUID id) {
        Map<String, Object> bars = activeBossBars.remove(id);
        if (bars == null) return;
        try {
            Class<?> bossBarClass = Class.forName("org.bukkit.boss.BossBar");
            for (Object bar : bars.values()) {
                bossBarClass.getMethod("removeAll").invoke(bar);
            }
        } catch (Throwable ignored) {
        }
    }

    private void removeAllBossBars() {
        for (UUID id : new ArrayList<UUID>(activeBossBars.keySet())) {
            removePlayerBossBars(id);
        }
        activeBossBars.clear();
    }

    private int countPlayersInPond(String pondName) {
        int c = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String cur = states.getCurrentPond(p.getUniqueId());
            if (pondName.equals(cur)) c++;
        }
        return c;
    }

    private int minRewardSpeed(String pondName) {
        int moneySpeed = plugin.getConfig().getInt("ponds." + pondName + ".reward.money.speed", 0);
        int pointsSpeed = plugin.getConfig().getInt("ponds." + pondName + ".reward.points.speed", 0);
        int cmdSpeed = plugin.getConfig().getInt("ponds." + pondName + ".reward.command.speed", 0);
        int expSpeed = plugin.getConfig().getInt("ponds." + pondName + ".reward.exp.speed", 0);
        int min = Integer.MAX_VALUE;
        if (moneySpeed > 0) min = Math.min(min, moneySpeed);
        if (pointsSpeed > 0) min = Math.min(min, pointsSpeed);
        if (cmdSpeed > 0) min = Math.min(min, cmdSpeed);
        if (expSpeed > 0) min = Math.min(min, expSpeed);
        return min == Integer.MAX_VALUE ? 0 : min;
    }
}
