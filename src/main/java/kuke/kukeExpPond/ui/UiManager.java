package kuke.kukeExpPond.ui;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.pond.Pond;
import kuke.kukeExpPond.pond.PondManager;
import kuke.kukeExpPond.player.PlayerStateManager;
import kuke.kukeExpPond.storage.DataStore;
import kuke.kukeExpPond.util.ActionBarUtil;
import kuke.kukeExpPond.util.ChatUtil;
import org.bukkit.Bukkit;
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

    // BossBar support (1.9+). Keep lightweight per-player instances.
    private final Map<UUID, Object> bossBars = new HashMap<UUID, Object>(); // Object to avoid compile issues on legacy
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
                removeBossBar(id);
                continue;
            }
            Pond pond = pondManager.getByName(pondName);
            if (pond == null) {
                removeBossBar(id);
                continue;
            }

            // ActionBar
            if (plugin.getConfig().getBoolean("ponds." + pondName + ".ui.actionbar.enable", true)) {
                String tpl = plugin.getConfig().getString("ponds." + pondName + ".ui.actionbar.template", "&b{pond}&7: 金币 {money_today}/{money_max} | 点券 {points_today}/{points_max}");
                String msg = formatActionBar(tpl, p, pondName);
                actionBar.send(p, msg);
            }

            // BossBar (if API available)
            if (bossApiAvailable && plugin.getConfig().getBoolean("ponds." + pondName + ".ui.bossbar.enable", true)) {
                ensureAndUpdateBossBar(p, pondName);
            } else {
                // Fallback: ensure no stale bossbar
                removeBossBar(id);
            }
        }
    }

    private String formatActionBar(String tpl, Player p, String pondName) {
        int moneyToday = dataStore.getDailyMoney(p.getUniqueId(), pondName);
        int pointsToday = dataStore.getDailyPoints(p.getUniqueId(), pondName);
        int moneyMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.money.max", 0);
        int pointsMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.points.max", 0);
        String out = tpl
                .replace("{player}", p.getName())
                .replace("{pond}", pondName)
                .replace("{money_today}", String.valueOf(moneyToday))
                .replace("{money_max}", String.valueOf(moneyMax))
                .replace("{points_today}", String.valueOf(pointsToday))
                .replace("{points_max}", String.valueOf(pointsMax));
        return chat.color(out);
    }

    private void ensureAndUpdateBossBar(Player p, String pondName) {
        try {
            UUID id = p.getUniqueId();
            Class<?> bossBarClass = Class.forName("org.bukkit.boss.BossBar");
            Class<?> barColorClass = Class.forName("org.bukkit.boss.BarColor");
            Class<?> barStyleClass = Class.forName("org.bukkit.boss.BarStyle");
            Object bar = bossBars.get(id);
            if (bar == null) {
                String title = chat.color("&b" + pondName);
                Object color = Enum.valueOf((Class<Enum>) barColorClass, safeEnum(barColorClass, plugin.getConfig().getString("ponds." + pondName + ".ui.bossbar.color", "BLUE"), "BLUE"));
                Object style = Enum.valueOf((Class<Enum>) barStyleClass, safeEnum(barStyleClass, plugin.getConfig().getString("ponds." + pondName + ".ui.bossbar.style", "SEGMENTED_10"), "SEGMENTED_10"));
                bar = bossBarClass.getMethod("createBossBar", String.class, barColorClass, barStyleClass).invoke(Bukkit.class, title, color, style);
                bossBarClass.getMethod("addPlayer", Player.class).invoke(bar, p);
                bossBars.put(id, bar);
            }
            // Title: show combined info
            int moneyToday = dataStore.getDailyMoney(p.getUniqueId(), pondName);
            int pointsToday = dataStore.getDailyPoints(p.getUniqueId(), pondName);
            int moneyMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.money.max", 0);
            int pointsMax = plugin.getConfig().getInt("ponds." + pondName + ".reward.points.max", 0);
            int onlineInPond = countPlayersInPond(pondName);
            int nextSpeed = minRewardSpeed(pondName);
            String title = String.format("&b%s&7: 金币 %d/%d | 点券 %d/%d | 在线 %d | 下次奖励 %ds",
                    pondName, moneyToday, moneyMax, pointsToday, pointsMax, onlineInPond, nextSpeed);
            bossBarClass.getMethod("setTitle", String.class).invoke(bar, chat.color(title));
            double progress = 0.0d;
            if (moneyMax > 0) {
                progress = Math.max(0.0d, Math.min(1.0d, moneyToday / (double) moneyMax));
            } else if (pointsMax > 0) {
                progress = Math.max(0.0d, Math.min(1.0d, pointsToday / (double) pointsMax));
            }
            bossBarClass.getMethod("setProgress", double.class).invoke(bar, progress);
        } catch (Throwable ignored) {
            // On any error, drop bossbar and fallback to actionbar
            removeBossBar(p.getUniqueId());
        }
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

    private void removeBossBar(UUID id) {
        Object bar = bossBars.remove(id);
        if (bar == null) return;
        try {
            Class<?> bossBarClass = Class.forName("org.bukkit.boss.BossBar");
            bossBarClass.getMethod("removeAll").invoke(bar);
        } catch (Throwable ignored) {
        }
    }

    private void removeAllBossBars() {
        for (UUID id : new ArrayList<UUID>(bossBars.keySet())) {
            removeBossBar(id);
        }
        bossBars.clear();
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