package kuke.kukeExpPond.pond;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import kuke.kukeExpPond.KukeExpPond;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Pond {
    private final String name;
    private final String worldName;
    private final int minX, maxX, minY, maxY, minZ, maxZ;

    private final String pondPermission;
    private final boolean allowJoinWithoutPerm; // pond_join
    private final String bypassPermission;
    private final boolean blockEnterByTeleport;
    private final String teleportMessage;
    
    // 水方块位置缓存
    private final List<Location> waterLocations = new ArrayList<>();
    private final Random random = new Random();

    public Pond(String name, String worldName,
                int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                String pondPermission, boolean allowJoinWithoutPerm,
                String bypassPermission,
                boolean blockEnterByTeleport, String teleportMessage) {
        this.name = name;
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.maxX = Math.max(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.maxY = Math.max(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxZ = Math.max(minZ, maxZ);
        this.pondPermission = pondPermission;
        this.allowJoinWithoutPerm = allowJoinWithoutPerm;
        this.bypassPermission = bypassPermission;
        this.blockEnterByTeleport = blockEnterByTeleport;
        this.teleportMessage = teleportMessage;
    }

    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public String getPondPermission() { return pondPermission; }
    public boolean isAllowJoinWithoutPerm() { return allowJoinWithoutPerm; }
    public String getBypassPermission() { return bypassPermission; }
    public boolean isBlockEnterByTeleport() { return blockEnterByTeleport; }
    public String getTeleportMessage() { return teleportMessage; }

    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
    
    /**
     * 扫描池区域内的所有水方块位置并缓存
     */
    public void scanWaterBlocks() {
        waterLocations.clear();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Bukkit.getLogger().warning("[KukeExpPond] World '" + worldName + "' not found for pond '" + name + "'");
            return;
        }
        
        int waterCount = 0;
        long startTime = System.currentTimeMillis();
        
        // 遍历整个池区域
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isWater(block)) {
                        // 在水方块上方1格作为经验瓶生成位置
                        Location spawnLoc = new Location(world, x + 0.5, y + 1.5, z + 0.5);
                        waterLocations.add(spawnLoc);
                        waterCount++;
                    }
                }
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        KukeExpPond plugin = JavaPlugin.getPlugin(KukeExpPond.class);
        if (plugin != null && plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("[KukeExpPond] Pond '" + name + "' scanned " + waterCount + " water blocks in " + duration + "ms");
        }
    }
    
    /**
     * 检查方块是否为水
     */
    private boolean isWater(Block block) {
        Material material = block.getType();
        
        // 基本材质检查
        if (material == Material.WATER || material.name().equals("STATIONARY_WATER")) {
            return true;
        }
        
        // 使用反射检查 BlockData 和 Waterlogged（1.13+兼容性）
        try {
            Object blockData = block.getClass().getMethod("getBlockData").invoke(block);
            if (blockData != null) {
                Class<?> waterloggedClass = Class.forName("org.bukkit.block.data.Waterlogged");
                if (waterloggedClass.isInstance(blockData)) {
                    Boolean isWaterlogged = (Boolean) waterloggedClass.getMethod("isWaterlogged").invoke(blockData);
                    return isWaterlogged != null && isWaterlogged;
                }
            }
        } catch (Exception ignored) {
            // 1.12及以下版本不支持BlockData，忽略异常
        }
        
        return false;
    }
    
    /**
     * 随机获取一个水方块上方的生成位置
     */
    public Location getRandomWaterSpawnLocation() {
        if (waterLocations.isEmpty()) {
            return null;
        }
        return waterLocations.get(random.nextInt(waterLocations.size())).clone();
    }
    
    /**
     * 获取缓存的水方块数量
     */
    public int getWaterBlockCount() {
        return waterLocations.size();
    }
    
    /**
     * 清空水方块缓存
     */
    public void clearWaterCache() {
        waterLocations.clear();
    }
}