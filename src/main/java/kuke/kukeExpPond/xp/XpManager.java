package kuke.kukeExpPond.xp;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.pond.Pond;
import kuke.kukeExpPond.pond.PondManager;
import kuke.kukeExpPond.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class XpManager {
    private final KukeExpPond plugin;
    private final PondManager pondManager;
    private final Map<String, BukkitTask> xpTasks = new HashMap<String, BukkitTask>();
    private final Random random = new Random();

    public XpManager(KukeExpPond plugin, PondManager pondManager) {
        this.plugin = plugin;
        this.pondManager = pondManager;
    }

    private boolean isDebug() {
        try {
            return plugin.getConfig().getBoolean("general.debug", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void start() {
        stop();
        for (Pond pond : pondManager.listAll()) {
            startPondTask(pond);
        }
    }

    public void stop() {
        for (BukkitTask t : xpTasks.values()) t.cancel();
        xpTasks.clear();
    }

    public void rebuild() {
        start();
    }

    private void startPondTask(Pond pond) {
        String name = pond.getName();
        String mode = plugin.getConfig().getString("ponds." + name + ".mode.xp_mode", "bottle");
        if ("direct".equalsIgnoreCase(mode)) {
            boolean enable = plugin.getConfig().getBoolean("ponds." + name + ".reward.exp.enable", true);
            if (!enable) return;

            // 配置简化：优先使用 reward.exp.mode 与 per_tick/interval_ticks；兼容旧的 continuous / per_second / speed
            String expModeStr = plugin.getConfig().getString("ponds." + name + ".reward.exp.mode", null);
            boolean continuousLegacy = plugin.getConfig().getBoolean("ponds." + name + ".reward.exp.continuous", expModeStr == null);
            boolean isContinuous = expModeStr != null ? "continuous".equalsIgnoreCase(expModeStr) : continuousLegacy;

            if (isContinuous) {
                // 每tick发放，默认使用 per_tick；无则回退到 per_second 或 count
                int perTick = plugin.getConfig().getInt(
                        "ponds." + name + ".reward.exp.per_tick",
                        plugin.getConfig().getInt(
                                "ponds." + name + ".reward.exp.per_second",
                                plugin.getConfig().getInt("ponds." + name + ".reward.exp.count", 1)
                        )
                );
                int tickPeriod = Math.max(1, plugin.getConfig().getInt(
                        "ponds." + name + ".reward.exp.tick_period",
                        1
                ));
                if (perTick <= 0) return;
                BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
                    @Override
                    public void run() {
                        final List<Player> players = listPlayersInPond(pond);
                        if (players.isEmpty()) return;
                        // 在主线程发放经验
                        Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override
                            public void run() {
                                for (Player p : players) {
                                    if (!isEligibleForXp(p, pond)) continue;
                                    p.giveExp(perTick);
                                }
                            }
                        });
                    }
                }, tickPeriod, tickPeriod); // 可调周期（默认每tick）
                xpTasks.put(name, t);
                return;
            } else {
                // 间隔模式：使用 interval_ticks；回退到 speed（秒）与 count
                int intervalTicks = plugin.getConfig().getInt(
                        "ponds." + name + ".reward.exp.interval_ticks",
                        20 * Math.max(1, plugin.getConfig().getInt("ponds." + name + ".reward.exp.speed", 10))
                );
                int count = plugin.getConfig().getInt("ponds." + name + ".reward.exp.count", 5);
                if (intervalTicks <= 0 || count <= 0) return;
                BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
                    @Override
                    public void run() {
                        final List<Player> players = listPlayersInPond(pond);
                        if (players.isEmpty()) return;
                        Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            @Override
                            public void run() {
                                for (Player p : players) {
                                    if (!isEligibleForXp(p, pond)) continue;
                                    p.giveExp(count);
                                }
                            }
                        });
                    }
                }, intervalTicks, intervalTicks);
                xpTasks.put(name, t);
                return;
            }
        }

        // bottle mode
        boolean bottleEnable = plugin.getConfig().getBoolean("ponds." + name + ".bottle.enable", false);
        int bottleSpeed = plugin.getConfig().getInt("ponds." + name + ".bottle.bottle_speed", 10);
        int bottleCount = plugin.getConfig().getInt("ponds." + name + ".bottle.bottle_count", 3);
        boolean onlyAboveWater = plugin.getConfig().getBoolean("ponds." + name + ".bottle.only_above_water", true);
        double dropHeight = plugin.getConfig().getDouble("ponds." + name + ".bottle.drop_height", 4.0D);
        int expPerCycle = plugin.getConfig().getInt("ponds." + name + ".reward.exp.count", 5);
        if (!bottleEnable || bottleSpeed <= 0 || bottleCount <= 0) return;

        BukkitTask t = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                final List<Player> players = listPlayersInPond(pond);
                if (players.isEmpty()) {
                    if (isDebug()) plugin.getLogger().info("[DEBUG] No players in pond '" + name + "' for bottle mode");
                    return;
                }
                if (isDebug()) plugin.getLogger().info("[DEBUG] Bottle mode: " + players.size() + " players in pond '" + name + "'");
                // spawn on main thread
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        for (Player p : players) {
                            if (!isEligibleForXp(p, pond)) continue;
                            
                            // 检查是否有缓存的水方块位置
                            if (pond.getWaterBlockCount() == 0) {
                                if (isDebug()) plugin.getLogger().warning("[DEBUG] No water locations cached for pond '" + name + "', falling back to orb for " + p.getName());
                                ExperienceOrb orb = p.getWorld().spawn(p.getLocation().add(0, 1, 0), ExperienceOrb.class);
                                orb.setExperience(expPerCycle);
                                continue;
                            }
                            
                            if (isDebug()) plugin.getLogger().info("[DEBUG] Spawning bottles for " + p.getName() + " with " + expPerCycle + " XP at different locations");
                            
                            int xpRemaining = Math.max(1, expPerCycle);
                            int perOrb = Math.max(1, xpRemaining / bottleCount);
                            int leftover = Math.max(0, xpRemaining - perOrb * bottleCount);
                            
                            for (int i = 0; i < bottleCount; i++) {
                                // 为每个经验瓶获取不同的随机位置
                                Location spawnLoc = pond.getRandomWaterSpawnLocation();
                                if (spawnLoc == null) {
                                    if (isDebug()) plugin.getLogger().warning("[DEBUG] Failed to get random water location for bottle #" + (i+1) + ", skipping");
                                    continue;
                                }
                                
                                int orbXp = perOrb + (i == 0 ? leftover : 0);
                                World w = spawnLoc.getWorld();
                                if (w == null) continue;
                                
                                // 提升高度，模拟从上方砸下
                                Location drop = spawnLoc.clone();
                                drop.setY(spawnLoc.getY() + dropHeight);
                                if (isDebug()) plugin.getLogger().info("[DEBUG] Spawning bottle #" + (i+1) + " at " + drop + " for player " + p.getName());
                                
                                try {
                                    ThrownExpBottle bottle = w.spawn(drop, ThrownExpBottle.class);
                                    double vx = (random.nextDouble() - 0.5D) * 0.2D;
                                    double vz = (random.nextDouble() - 0.5D) * 0.2D;
                                    bottle.setVelocity(new org.bukkit.util.Vector(vx, -0.7D, vz));
                                    bottle.setMetadata("kep_xp_value", new FixedMetadataValue(plugin, orbXp));
                                    if (isDebug()) plugin.getLogger().info("[DEBUG] Successfully spawned bottle with " + orbXp + " XP at " + spawnLoc);
                                } catch (Throwable t) {
                                    // 不支持则回退生成经验球
                                    if (isDebug()) plugin.getLogger().warning("[DEBUG] Failed to spawn bottle, falling back to orb: " + t.getMessage());
                                    ExperienceOrb orb = w.spawn(spawnLoc, ExperienceOrb.class);
                                    orb.setExperience(orbXp);
                                }
                            }
                        }
                    }
                });
            }
        }, 20L * bottleSpeed, 20L * bottleSpeed);
        xpTasks.put(name, t);
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

    private boolean isEligibleForXp(Player p, Pond pond) {
        String perm = pond.getPondPermission();
        if (perm != null && perm.trim().length() > 0 && !p.hasPermission(perm)) {
            if (!pond.isAllowJoinWithoutPerm()) {
                return false;
            }
            return false; // inside but no xp rewards
        }
        return true;
    }

    // 旧的复杂位置查找方法已被移除，现在使用Pond类的水方块缓存系统
}