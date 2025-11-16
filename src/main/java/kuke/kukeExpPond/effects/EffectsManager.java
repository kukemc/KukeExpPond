package kuke.kukeExpPond.effects;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.pond.Pond;
import kuke.kukeExpPond.pond.PondManager;
import kuke.kukeExpPond.util.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Spawns configurable particle effects in ponds.
 * - Runs on main thread.
 * - Each effect can have its own rate and particle type.
 */
public class EffectsManager {
    private final KukeExpPond plugin;
    private final PondManager pondManager;
    private final ParticleUtil particles;

    // Track tasks per pond
    private final Map<String, List<BukkitTask>> pondTasks = new HashMap<String, List<BukkitTask>>();

    public EffectsManager(KukeExpPond plugin, PondManager pondManager) {
        this.plugin = plugin;
        this.pondManager = pondManager;
        this.particles = new ParticleUtil(plugin);
    }

    public void start() {
        stop();
        for (Pond pond : pondManager.listAll()) {
            startPond(pond);
        }
    }

    public void stop() {
        for (List<BukkitTask> list : pondTasks.values()) {
            for (BukkitTask t : list) {
                t.cancel();
            }
        }
        pondTasks.clear();
    }

    public void rebuild() { start(); }

    private void startPond(Pond pond) {
        String name = pond.getName();
        List<BukkitTask> list = new ArrayList<BukkitTask>();
        double density = plugin.getConfig().getDouble("ponds." + name + ".effects.density", 1.0);
        String fallbackParticle = plugin.getConfig().getString("general.version_mapping.particle_fallback", "CLOUD");

        // steam_hot_spring - 从水面生成并向上飘散
        if (plugin.getConfig().getBoolean("ponds." + name + ".effects.steam_hot_spring.enable", false)) {
            int rate = plugin.getConfig().getInt("ponds." + name + ".effects.steam_hot_spring.rate", 10);
            String particle = plugin.getConfig().getString("ponds." + name + ".effects.steam_hot_spring.particle", "CLOUD");
            list.add(Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    spawnSteamEffect(pond, particle, fallbackParticle, (int) Math.max(1, 6 * density));
                }
            }, 20L, Math.max(1L, rate)));
        }

        // water_bubble - 在水中生成并向上移动
        if (plugin.getConfig().getBoolean("ponds." + name + ".effects.water_bubble.enable", false)) {
            int rate = plugin.getConfig().getInt("ponds." + name + ".effects.water_bubble.rate", 10);
            String particle = plugin.getConfig().getString("ponds." + name + ".effects.water_bubble.particle", "BUBBLE_COLUMN_UP");
            list.add(Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    spawnBubbleEffect(pond, particle, fallbackParticle, (int) Math.max(1, 10 * density));
                }
            }, 20L, Math.max(1L, rate)));
        }

        // smoke_bottom - 从水面/方块顶部生成并向上飘散
        if (plugin.getConfig().getBoolean("ponds." + name + ".effects.smoke_bottom.enable", false)) {
            int rate = plugin.getConfig().getInt("ponds." + name + ".effects.smoke_bottom.rate", 20);
            String particle = plugin.getConfig().getString("ponds." + name + ".effects.smoke_bottom.particle", "CAMPFIRE_COSY_SMOKE");
            list.add(Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    // 调整基础数量为更低，避免过多；保持密度可控
                    spawnSmokeEffect(pond, particle, fallbackParticle, (int) Math.max(1, 4 * density));
                }
            }, 20L, Math.max(1L, rate)));
        }

        if (!list.isEmpty()) pondTasks.put(name, list);
    }

    // 蒸汽特效 - 从水面生成并向上飘散
    private void spawnSteamEffect(Pond pond, String particle, String fallbackParticle, int count) {
        World w = Bukkit.getWorld(pond.getWorldName());
        if (w == null) return;
        
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < count; i++) {
            // 在池子范围内随机选择位置
            double x = pond.getMinX() + r.nextDouble() * (pond.getMaxX() - pond.getMinX() + 1);
            double z = pond.getMinZ() + r.nextDouble() * (pond.getMaxZ() - pond.getMinZ() + 1);
            
            // 找到该位置的水面高度
            double y = findWaterSurface(w, (int)x, (int)z, pond.getMinY(), pond.getMaxY());
            if (y > 0) {
                Location loc = new Location(w, x, y + 0.1, z);
                // 蒸汽向上飘散，有轻微的水平偏移
                particles.spawn(w, loc, particle, 1, fallbackParticle, 0.1, 0.1, 0.1, 0.02);
            }
        }
    }
    
    // 气泡特效 - 在水中生成并向上移动
    private void spawnBubbleEffect(Pond pond, String particle, String fallbackParticle, int count) {
        World w = Bukkit.getWorld(pond.getWorldName());
        if (w == null) return;
        
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < count; i++) {
            // 在池子范围内随机选择位置
            int x = pond.getMinX() + r.nextInt(Math.max(1, pond.getMaxX() - pond.getMinX() + 1));
            int z = pond.getMinZ() + r.nextInt(Math.max(1, pond.getMaxZ() - pond.getMinZ() + 1));
            
            // 确保只在水方块中生成气泡
            Location waterLoc = findWaterLocation(w, x, z, pond.getMinY(), pond.getMaxY());
            if (waterLoc != null) {
                // 气泡向上移动，无水平偏移
                particles.spawn(w, waterLoc, particle, 1, fallbackParticle, 0.0, 0.0, 0.0, 0.1);
            }
        }
    }
    
    // 烟雾特效 - 从水面或覆盖方块顶部生成并向上飘散（避免生成在方块内部/水下）
    private void spawnSmokeEffect(Pond pond, String particle, String fallbackParticle, int count) {
        World w = Bukkit.getWorld(pond.getWorldName());
        if (w == null) return;
        
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < count; i++) {
            // 在池子范围内随机选择位置
            int x = pond.getMinX() + r.nextInt(Math.max(1, pond.getMaxX() - pond.getMinX() + 1));
            int z = pond.getMinZ() + r.nextInt(Math.max(1, pond.getMaxZ() - pond.getMinZ() + 1));
            
            // 仅在存在水柱的列生成烟雾，位置取水面之上或覆盖方块顶部
            double surfaceY = findWaterSurface(w, x, z, pond.getMinY(), pond.getMaxY());
            if (surfaceY <= 0) continue; // 无水，不生成
            int y = (int)Math.floor(surfaceY);
            // 如果水面上方被方块覆盖，则将生成位置抬到覆盖方块的顶部之上
            while (y <= pond.getMaxY()) {
                org.bukkit.block.Block b = w.getBlockAt(x, y, z);
                if (isAirMaterial(b.getType())) break; // 找到空气，停止上升（跨版本兼容）
                y++;
            }
            // 在最终位置微调以保证视觉效果在方块顶部/水面上
            Location loc = new Location(w, x + 0.5, Math.min(y, pond.getMaxY()) + 0.05, z + 0.5);
            // 垂直向上：取消水平偏移，设置极小速度，使用传入的数量
            particles.spawn(w, loc, particle, Math.max(1, count), fallbackParticle, 0.0, 0.0, 0.0, 0.02);
        }
    }
    
    // 辅助方法：找到指定位置的水面高度
    private double findWaterSurface(World world, int x, int z, int minY, int maxY) {
        try {
            for (int y = maxY; y >= minY; y--) {
                org.bukkit.block.Block block = world.getBlockAt(x, y, z);
                // 检查是否为水方块
                if (block.getType().toString().contains("WATER")) {
                    return y + 1.0; // 返回水面高度
                }
            }
        } catch (Exception e) {
            // 如果出现异常，返回池子的最大高度
            return maxY;
        }
        return -1; // 没有找到水
    }
    
    // 辅助方法：找到水中的随机位置
    private Location findWaterLocation(World world, int x, int z, int minY, int maxY) {
        java.util.List<Integer> waterLevels = new java.util.ArrayList<>();
        
        // 收集所有水方块的Y坐标
        for (int y = minY; y <= maxY; y++) {
            if (world.getBlockAt(x, y, z).getType().toString().contains("WATER")) {
                waterLevels.add(y);
            }
        }
        
        if (waterLevels.isEmpty()) {
            return null; // 没有水方块
        }
        
        // 随机选择一个水方块位置
        java.util.Random r = new java.util.Random();
        int selectedY = waterLevels.get(r.nextInt(waterLevels.size()));
        return new Location(world, x + 0.5, selectedY + 0.5, z + 0.5);
    }

    // 跨版本判断是否为空气方块：优先调用 Material#isAir()，否则回退到名称判断
    private boolean isAirMaterial(org.bukkit.Material mat) {
        if (mat == null) return false;
        try {
            java.lang.reflect.Method m = org.bukkit.Material.class.getMethod("isAir");
            Object ret = m.invoke(mat);
            if (ret instanceof Boolean) {
                return ((Boolean) ret).booleanValue();
            }
        } catch (Throwable ignored) {}
        // 回退：兼容没有 isAir 的版本，仅匹配已知空气类型
        String name = mat.name();
        return "AIR".equals(name) || "CAVE_AIR".equals(name) || "VOID_AIR".equals(name);
    }
}