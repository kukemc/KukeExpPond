package kuke.kukeExpPond.reward;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.hook.HookManager;
import kuke.kukeExpPond.pond.Pond;
import kuke.kukeExpPond.pond.PondManager;
import kuke.kukeExpPond.storage.DataStore;
import kuke.kukeExpPond.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

public class RewardManager {
    private final KukeExpPond plugin;
    private final PondManager pondManager;
    private final HookManager hookManager;
    private final DataStore dataStore;
    private final ChatUtil chat;

    private final Map<String, BukkitTask> moneyTasks = new HashMap<>();
    private final Map<String, BukkitTask> pointsTasks = new HashMap<>();
    private final Map<String, BukkitTask> commandTasks = new HashMap<>();
    private BukkitTask dailyResetTask;

    public RewardManager(KukeExpPond plugin, PondManager pondManager, HookManager hookManager, DataStore dataStore) {
        this.plugin = plugin;
        this.pondManager = pondManager;
        this.hookManager = hookManager;
        this.dataStore = dataStore;
        this.chat = new ChatUtil(plugin);
    }

    public void start() {
        stop();
        // create tasks per pond
        for (Pond pond : pondManager.listAll()) {
            startPondTasks(pond);
        }
        // daily reset checker
        dailyResetTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkDailyReset, 20L, 20L * 30); // every 30s
        // 补偿：若服务器错过预定重置时间，在启动/重载时立即检查一次
        ensureMissedReset();
    }

    public void stop() {
        for (BukkitTask t : moneyTasks.values()) t.cancel();
        for (BukkitTask t : pointsTasks.values()) t.cancel();
        for (BukkitTask t : commandTasks.values()) t.cancel();
        moneyTasks.clear();
        pointsTasks.clear();
        commandTasks.clear();
        if (dailyResetTask != null) {
            dailyResetTask.cancel();
            dailyResetTask = null;
        }
    }

    public void rebuild() {
        start();
    }

    private void startPondTasks(Pond pond) {
        String name = pond.getName();
        // money
        int moneySpeed = plugin.getConfig().getInt("ponds." + name + ".reward.money.speed", 0);
        int moneyCount = plugin.getConfig().getInt("ponds." + name + ".reward.money.count", 0);
        int moneyMax = plugin.getConfig().getInt("ponds." + name + ".reward.money.max", 0);
        boolean moneyEnable = plugin.getConfig().getBoolean("ponds." + name + ".reward.money.enable", false);
        if (moneyEnable && hookManager.isVaultPresent() && moneySpeed > 0 && moneyCount > 0) {
            BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                for (Player p : listPlayersInPond(pond)) {
                    if (!isEligibleForReward(p, pond)) continue;
                    java.util.UUID id = p.getUniqueId();
                    int daily = dataStore.getDailyMoney(id, name);
                    if (moneyMax > 0 && daily + moneyCount > moneyMax) continue;
                    // economy operations on main thread for safety
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        hookManager.depositMoney(id, moneyCount);
                        dataStore.addMoney(id, name, moneyCount);
                        chat.send(p, "&a获得金钱: &e" + moneyCount);
                    });
                }
            }, 20L * moneySpeed, 20L * moneySpeed);
            moneyTasks.put(name, t);
        }

        // points
        int pointsSpeed = plugin.getConfig().getInt("ponds." + name + ".reward.points.speed", 0);
        int pointsCount = plugin.getConfig().getInt("ponds." + name + ".reward.points.count", 0);
        int pointsMax = plugin.getConfig().getInt("ponds." + name + ".reward.points.max", 0);
        boolean pointsEnable = plugin.getConfig().getBoolean("ponds." + name + ".reward.points.enable", false);
        if (pointsEnable && hookManager.isPlayerPointsPresent() && pointsSpeed > 0 && pointsCount > 0) {
            BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                for (Player p : listPlayersInPond(pond)) {
                    if (!isEligibleForReward(p, pond)) continue;
                    java.util.UUID id = p.getUniqueId();
                    int daily = dataStore.getDailyPoints(id, name);
                    if (pointsMax > 0 && daily + pointsCount > pointsMax) continue;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        hookManager.givePlayerPoints(id, pointsCount);
                        dataStore.addPoints(id, name, pointsCount);
                        chat.send(p, "&a获得点券: &e" + pointsCount);
                    });
                }
            }, 20L * pointsSpeed, 20L * pointsSpeed);
            pointsTasks.put(name, t);
        }

        // command
        boolean cmdEnable = plugin.getConfig().getBoolean("ponds." + name + ".reward.command.enable", false);
        int cmdSpeed = plugin.getConfig().getInt("ponds." + name + ".reward.command.speed", 0);
        List<String> cmds = plugin.getConfig().getStringList("ponds." + name + ".reward.command.commands");
        if (cmdEnable && cmdSpeed > 0 && cmds != null && !cmds.isEmpty()) {
            BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                for (Player p : listPlayersInPond(pond)) {
                    if (!isEligibleForReward(p, pond)) continue;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (String cmd : cmds) {
                            String processed = cmd.replace("{player}", p.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
                        }
                    });
                }
            }, 20L * cmdSpeed, 20L * cmdSpeed);
            commandTasks.put(name, t);
        }
    }

    private boolean isEligibleForReward(Player p, Pond pond) {
        // If pond has a permission requirement and player doesn't have it, skip reward
        String perm = pond.getPondPermission();
        if (perm != null && perm.trim().length() > 0 && !p.hasPermission(perm)) {
            // allowJoinWithoutPerm means they can enter, but rewards should not be given
            if (!pond.isAllowJoinWithoutPerm()) {
                return false; // they shouldn't be inside normally
            }
            return false; // inside but no rewards
        }
        return true;
    }

    private List<Player> listPlayersInPond(Pond pond) {
        List<Player> list = new ArrayList<Player>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld() == null) continue;
            if (pond.contains(p.getLocation())) {
                list.add(p);
            }
        }
        return list;
    }

    private void checkDailyReset() {
        // For each pond, if current time equals configured HH:mm in its timezone, and last reset older than today, reset
        for (Pond pond : pondManager.listAll()) {
            String name = pond.getName();
            String timeStr = plugin.getConfig().getString("ponds." + name + ".reward.daily_reset_time",
                    plugin.getConfig().getString("general.daily_reset_time", "00:00"));
            String tzStr = plugin.getConfig().getString("ponds." + name + ".reward.timezone",
                    plugin.getConfig().getString("general.timezone", "Asia/Shanghai"));
            String[] parts = timeStr.split(":");
            if (parts.length != 2) continue;
            int hh, mm;
            try {
                hh = Integer.parseInt(parts[0]);
                mm = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            ZoneId zone = ZoneId.of(tzStr);
            ZonedDateTime now = ZonedDateTime.now(zone);
            if (now.getHour() == hh && now.getMinute() == mm) {
                // Only reset once per day
                long last = dataStore.getLastResetEpoch();
                ZonedDateTime lastZ = last > 0 ? ZonedDateTime.ofInstant(new Date(last).toInstant(), zone) : now.minusDays(1);
                if (lastZ.getDayOfYear() != now.getDayOfYear()) {
                    dataStore.resetDailyAll();
                    plugin.getLogger().info("已执行每日奖励计数重置");
                }
            }
        }
    }

    // 启动/重载补偿：若当前时间已过今天的预定重置点但尚未重置，则立即重置
    private void ensureMissedReset() {
        try {
            long last = dataStore.getLastResetEpoch();
            for (Pond pond : pondManager.listAll()) {
                String name = pond.getName();
                String timeStr = plugin.getConfig().getString("ponds." + name + ".reward.daily_reset_time",
                        plugin.getConfig().getString("general.daily_reset_time", "00:00"));
                String tzStr = plugin.getConfig().getString("ponds." + name + ".reward.timezone",
                        plugin.getConfig().getString("general.timezone", "Asia/Shanghai"));
                ZoneId zone = ZoneId.of(tzStr);
                int hh = 0, mm = 0;
                try {
                    String[] parts = timeStr.split(":");
                    hh = Integer.parseInt(parts[0]);
                    mm = Integer.parseInt(parts[1]);
                } catch (Throwable ignored) {}
                ZonedDateTime now = ZonedDateTime.now(zone);
                ZonedDateTime todayReset = now.withHour(hh).withMinute(mm).withSecond(0).withNano(0);
                ZonedDateTime lastZ = last > 0 ? ZonedDateTime.ofInstant(new Date(last).toInstant(), zone) : now.minusDays(1);
                if (now.isAfter(todayReset) && !lastZ.toLocalDate().isEqual(now.toLocalDate())) {
                    dataStore.resetDailyAll();
                    plugin.getLogger().info("已执行每日奖励计数重置（补偿：服务器错过预定时间）");
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }
}