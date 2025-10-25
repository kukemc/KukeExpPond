package kuke.kukeExpPond.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Configuration manager for original YAML with hot reload support and auto-completion.
 */
public class ConfigManager {
    private final JavaPlugin plugin;
    private File configFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    /**
     * 初始化配置文件管理器
     * 检查并补充缺失的配置项
     */
    public void initialize() {
        // 确保插件数据文件夹存在
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        // 如果配置文件不存在，创建默认配置
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            plugin.getLogger().info("已创建默认配置文件");
        }
        
        // 检查并补充缺失的配置项
        boolean updated = checkAndUpdateConfig();
        
        if (updated) {
            plugin.saveConfig();
            plugin.getLogger().info("配置文件已更新，补充了缺失的配置项");
        } else {
            plugin.getLogger().info("配置文件检查完成，无需更新");
        }
    }

    public void reload() {
        plugin.reloadConfig();
        // 重载时也检查并补充缺失项
        boolean updated = checkAndUpdateConfig();
        if (updated) {
            plugin.saveConfig();
            plugin.getLogger().info("配置文件重载完成，已补充缺失项");
        } else {
            plugin.getLogger().info("配置文件重载完成");
        }
    }

    public FileConfiguration get() {
        return plugin.getConfig();
    }

    /**
     * Return a normalized pond name (lowercased, trimmed) for consistent lookup.
     */
    public String normalizePondName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase().replace(' ', '_');
    }

    /**
     * Get list of configured pond names.
     */
    public Set<String> getPondNames() {
        FileConfiguration cfg = get();
        if (!cfg.isConfigurationSection("ponds")) return java.util.Collections.emptySet();
        return cfg.getConfigurationSection("ponds").getKeys(false);
    }

    /**
     * Get a flat snapshot map of pond overview data for listing commands.
     */
    public Map<String, String> getPondOverview() {
        FileConfiguration cfg = get();
        Map<String, String> map = new TreeMap<>();
        Set<String> names = getPondNames();
        for (String name : names) {
            String world = cfg.getString("ponds." + name + ".world", "-");
            String mode = cfg.getString("ponds." + name + ".mode.xp_mode", "direct");
            boolean enabledBottle = cfg.getBoolean("ponds." + name + ".bottle.enable", false);
            map.put(name, "world=" + world + ", mode=" + mode + ", bottle=" + enabledBottle);
        }
        return map;
    }

    /**
     * 检查并更新配置文件
     * @return 是否有更新
     */
    public boolean checkAndUpdateConfig() {
        boolean updated = false;
        
        try {
            // 获取默认配置
            InputStream defaultConfigStream = plugin.getResource("config.yml");
            if (defaultConfigStream == null) {
                plugin.getLogger().warning("无法找到默认配置文件");
                return false;
            }
            
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigStream, "UTF-8")
            );
            
            // 直接从磁盘读取当前配置文件，而不是使用plugin.getConfig()
            // 这样可以确保我们检查的是用户实际的配置文件，而不是内存中的完整配置
            FileConfiguration currentConfig;
            if (configFile.exists()) {
                currentConfig = YamlConfiguration.loadConfiguration(configFile);
            } else {
                // 如果配置文件不存在，创建一个空的配置
                currentConfig = new YamlConfiguration();
            }
            
            // 递归检查并补充缺失的配置项
            updated = mergeConfigurations("", defaultConfig, currentConfig);
            
            // 如果有更新，将合并后的配置写回到plugin的配置中
            if (updated) {
                // 将更新后的配置设置到plugin中
                for (String key : currentConfig.getKeys(true)) {
                    plugin.getConfig().set(key, currentConfig.get(key));
                }
            }
            
            defaultConfigStream.close();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "检查配置文件时发生错误", e);
        }
        
        return updated;
    }
    
    /**
     * 递归合并配置
     * @param path 当前路径
     * @param defaultConfig 默认配置
     * @param currentConfig 当前配置
     * @return 是否有更新
     */
    private boolean mergeConfigurations(String path, ConfigurationSection defaultConfig, ConfigurationSection currentConfig) {
        boolean updated = false;
        
        Set<String> defaultKeys = defaultConfig.getKeys(false);
        
        for (String key : defaultKeys) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            
            if (defaultConfig.isConfigurationSection(key)) {
                // 如果是配置节
                if (!currentConfig.isConfigurationSection(key)) {
                    // 当前配置中不存在此配置节，创建它
                    currentConfig.createSection(key);
                    updated = true;
                    plugin.getLogger().info("添加缺失的配置节: " + fullPath);
                }
                
                // 递归检查子配置
                ConfigurationSection defaultSection = defaultConfig.getConfigurationSection(key);
                ConfigurationSection currentSection = currentConfig.getConfigurationSection(key);
                
                if (mergeConfigurations(fullPath, defaultSection, currentSection)) {
                    updated = true;
                }
                
            } else {
                // 如果是配置值
                if (!currentConfig.contains(key)) {
                    // 当前配置中不存在此配置项，添加默认值
                    Object defaultValue = defaultConfig.get(key);
                    currentConfig.set(key, defaultValue);
                    updated = true;
                    plugin.getLogger().info("添加缺失的配置项: " + fullPath + " = " + defaultValue);
                }
            }
        }
        
        return updated;
    }
    
    /**
     * 手动更新配置文件（用于命令调用）
     * @return 是否有更新
     */
    public boolean updateConfig() {
        boolean updated = checkAndUpdateConfig();
        if (updated) {
            plugin.saveConfig();
        }
        return updated;
    }
    
    /**
     * 验证配置文件完整性
     * @return 验证结果信息
     */
    public String validateConfig() {
        StringBuilder result = new StringBuilder();
        result.append("配置文件验证结果:\n");
        
        FileConfiguration config = get();
        
        // 检查必要的配置项
        String[] requiredPaths = {
            "config_version",
            "general.prefix",
            "general.storage",
            "general.daily_reset_time",
            "general.timezone",
            "ponds.default.world",
            "ponds.default.region.pos1",
            "ponds.default.region.pos2"
        };
        
        int missingCount = 0;
        for (String path : requiredPaths) {
            if (!config.contains(path)) {
                result.append("- 缺失必要配置: ").append(path).append("\n");
                missingCount++;
            }
        }
        
        if (missingCount == 0) {
            result.append("- 所有必要配置项都存在\n");
        } else {
            result.append("- 发现 ").append(missingCount).append(" 个缺失的必要配置项\n");
        }
        
        // 检查池配置
        ConfigurationSection pondsSection = config.getConfigurationSection("ponds");
        if (pondsSection != null) {
            Set<String> ponds = pondsSection.getKeys(false);
            result.append("- 已配置的池: ").append(ponds.size()).append(" 个 ").append(ponds).append("\n");
        } else {
            result.append("- 警告: 未找到池配置节\n");
        }
        
        return result.toString();
    }
}