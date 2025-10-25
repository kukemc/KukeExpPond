package kuke.kukeExpPond.ice;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.pond.Pond;
import kuke.kukeExpPond.pond.PondManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

/**
 * 防结冰管理器 - 定时监测经验池区域内的冰块并转换为水
 */
public class IcePreventionManager {
    
    private final KukeExpPond plugin;
    private final PondManager pondManager;
    private final Logger logger;
    
    private BukkitTask preventionTask;
    private boolean enabled;
    private int checkInterval;
    private boolean convertIceToWater;
    private boolean debugLog;
    
    public IcePreventionManager(KukeExpPond plugin, PondManager pondManager) {
        this.plugin = plugin;
        this.pondManager = pondManager;
        this.logger = plugin.getLogger();
        loadConfig();
    }
    
    private boolean isDebug() {
        try {
            return plugin.getConfig().getBoolean("general.debug", false);
        } catch (Throwable ignored) {
            return false;
        }
    }
    
    /**
     * 从配置文件加载设置
     */
    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("general.ice_prevention.enable", true);
        this.checkInterval = plugin.getConfig().getInt("general.ice_prevention.check_interval", 30);
        this.convertIceToWater = plugin.getConfig().getBoolean("general.ice_prevention.convert_ice_to_water", true);
        this.debugLog = plugin.getConfig().getBoolean("general.ice_prevention.debug_log", false);
        
        // 确保检查间隔至少为5秒
        if (checkInterval < 5) {
            checkInterval = 5;
        }
    }
    
    /**
     * 启动防结冰系统
     */
    public void start() {
        if (!enabled) {
            logger.info("防结冰系统已禁用");
            return;
        }
        
        stop(); // 先停止现有任务
        
        // 启动定时任务
        preventionTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                checkAndConvertIce();
            }
        }, 20L * checkInterval, 20L * checkInterval); // 转换为tick
        
        logger.info("防结冰系统已启动，检查间隔: " + checkInterval + "秒");
    }
    
    /**
     * 停止防结冰系统
     */
    public void stop() {
        if (preventionTask != null) {
            preventionTask.cancel();
            preventionTask = null;
        }
    }
    
    /**
     * 重新加载配置并重启系统
     */
    public void reload() {
        loadConfig();
        start();
    }
    
    /**
     * 检查并转换所有经验池中的冰块
     */
    private void checkAndConvertIce() {
        if (!enabled || !convertIceToWater) {
            return;
        }
        
        int totalIceConverted = 0;
        
        // 遍历所有经验池
        for (Pond pond : pondManager.listAll()) {
            int iceConverted = checkPondIce(pond);
            totalIceConverted += iceConverted;
        }
        
        // 调试日志
        if ((debugLog || isDebug()) && totalIceConverted > 0) {
            logger.info("[防结冰] 本次检查转换了 " + totalIceConverted + " 个冰块为水");
        }
    }
    
    /**
     * 检查单个池的冰块并转换
     */
    private int checkPondIce(Pond pond) {
        World world = Bukkit.getWorld(pond.getWorldName());
        if (world == null) {
            return 0;
        }
        
        int iceConverted = 0;
        
        // 遍历池区域内的所有方块
        for (int x = pond.getMinX(); x <= pond.getMaxX(); x++) {
            for (int y = pond.getMinY(); y <= pond.getMaxY(); y++) {
                for (int z = pond.getMinZ(); z <= pond.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    
                    // 检查是否为冰块
                    if (isIceBlock(block)) {
                        // 转换为水
                        convertIceToWater(block);
                        iceConverted++;
                        
                        if (debugLog || isDebug()) {
                            logger.info("[防结冰] 池 '" + pond.getName() + "' 中的冰块已转换为水: " + 
                                x + "," + y + "," + z);
                        }
                    }
                }
            }
        }
        
        return iceConverted;
    }
    
    /**
     * 检查方块是否为冰块
     */
    private boolean isIceBlock(Block block) {
        Material type = block.getType();
        
        // 检查各种类型的冰块
        if (type == Material.ICE) {
            return true;
        }
        
        // 使用字符串比较以保持版本兼容性
        String typeName = type.name();
        return typeName.equals("FROSTED_ICE") || 
               typeName.equals("PACKED_ICE") || 
               typeName.equals("BLUE_ICE");
    }
    
    /**
     * 将冰块转换为水
     */
    private void convertIceToWater(Block block) {
        try {
            // 尝试使用新版本的WATER材质
            block.setType(Material.WATER);
        } catch (Exception e) {
            try {
                // 回退到旧版本的STATIONARY_WATER
                Material water = Material.valueOf("STATIONARY_WATER");
                block.setType(water);
            } catch (Exception e2) {
                // 如果都失败了，记录错误
                if (debugLog || isDebug()) {
                    logger.warning("[防结冰] 无法转换冰块为水: " + e2.getMessage());
                }
            }
        }
    }
    
    /**
     * 获取系统状态信息
     */
    public String getStatusInfo() {
        if (!enabled) {
            return "防结冰系统: 已禁用";
        }
        
        return String.format("防结冰系统: 已启用 | 检查间隔: %d秒 | 转换冰块: %s", 
            checkInterval, convertIceToWater ? "是" : "否");
    }
    
    // Getter方法
    public boolean isEnabled() { return enabled; }
    public int getCheckInterval() { return checkInterval; }
    public boolean isConvertIceToWater() { return convertIceToWater; }
    public boolean isDebugLog() { return debugLog; }
}