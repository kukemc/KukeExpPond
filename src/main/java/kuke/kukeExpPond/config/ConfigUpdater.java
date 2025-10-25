package kuke.kukeExpPond.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 配置文件自动更新管理器
 * 在插件启动时检测配置文件中缺失的配置项，并自动添加默认值
 */
public class ConfigUpdater {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final List<String> addedKeys = new ArrayList<>();
    
    public ConfigUpdater(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * 检查并更新配置文件
     * @return 是否有配置项被添加
     */
    public boolean checkAndUpdateConfig() {
        addedKeys.clear();
        
        try {
            // 获取当前配置文件
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            FileConfiguration currentConfig = plugin.getConfig();
            
            logger.info("[配置更新] 开始检查配置文件...");
            
            // 获取默认配置文件
            InputStream defaultConfigStream = plugin.getResource("config.yml");
            if (defaultConfigStream == null) {
                logger.warning("[配置更新] 无法找到默认配置文件");
                return false;
            }
            
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultConfigStream, "UTF-8"));
            
            // 直接检查并添加缺失的配置项，不依赖版本号比较
            boolean hasChanges = addMissingKeys(currentConfig, defaultConfig, "");
            
            // 确保配置文件有版本号（如果默认配置有的话）
            String defaultVersion = defaultConfig.getString("config_version");
            if (defaultVersion != null && !currentConfig.contains("config_version")) {
                currentConfig.set("config_version", defaultVersion);
                addedKeys.add("config_version");
                hasChanges = true;
            }
            
            // 如果有变化，保存配置文件
            if (hasChanges) {
                currentConfig.save(configFile);
                logger.info("[配置更新] 配置文件已更新，添加了 " + addedKeys.size() + " 个缺失的配置项");
                
                // 重新加载配置
                plugin.reloadConfig();
                return true;
            } else {
                logger.info("[配置更新] 配置文件检查完成，无需更新");
                return false;
            }
            
        } catch (Exception e) {
            logger.severe("[配置更新] 更新配置文件时发生错误: " + e.getMessage());
            java.util.logging.Level level = java.util.logging.Level.SEVERE;
            logger.log(level, "[配置更新] 异常详情", e);
            return false;
        }
    }
    
    /**
     * 递归添加缺失的配置键
     */
    private boolean addMissingKeys(ConfigurationSection current, ConfigurationSection defaults, String path) {
        boolean hasChanges = false;
        
        Set<String> defaultKeys = defaults.getKeys(false);
        for (String key : defaultKeys) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            
            if (!current.contains(key)) {
                // 缺失的配置项
                Object defaultValue = defaults.get(key);
                
                if (defaults.isConfigurationSection(key)) {
                    // 如果是配置段，创建空段然后递归添加
                    ConfigurationSection newSection = current.createSection(key);
                    ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
                    addMissingKeys(newSection, defaultSection, fullPath);
                } else {
                    // 如果是普通值，直接设置
                    current.set(key, defaultValue);
                }
                
                addedKeys.add(fullPath);
                hasChanges = true;
                
                // 移除逐项debug日志，避免刷屏，仅保留最终统计输出
                
            } else if (defaults.isConfigurationSection(key) && current.isConfigurationSection(key)) {
                // 如果都是配置段，递归检查子项
                ConfigurationSection currentSection = current.getConfigurationSection(key);
                ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
                boolean subChanges = addMissingKeys(currentSection, defaultSection, fullPath);
                hasChanges = hasChanges || subChanges;
            }
        }
        
        return hasChanges;
    }
    
    /**
     * 获取本次更新添加的配置项列表
     */
    public List<String> getAddedKeys() {
        return new ArrayList<>(addedKeys);
    }
    
    /**
     * 检查特定配置路径是否存在
     */
    public boolean hasConfigPath(String path) {
        return plugin.getConfig().contains(path);
    }
    
    /**
     * 添加单个配置项（如果不存在）
     */
    public boolean addConfigIfMissing(String path, Object defaultValue) {
        if (!hasConfigPath(path)) {
            plugin.getConfig().set(path, defaultValue);
            
            try {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                plugin.getConfig().save(configFile);
                plugin.reloadConfig();
                
                logger.info("[配置更新] 手动添加配置项: " + path + " = " + defaultValue);
                return true;
            } catch (IOException e) {
                logger.severe("[配置更新] 保存配置文件失败: " + e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    /**
     * 获取当前配置版本
     * @return 配置版本，如果没有版本号则返回 "未知版本"
     */
    public String getConfigVersion() {
        String version = plugin.getConfig().getString("config_version");
        if (version == null) {
            return "未知版本（旧配置文件）";
        }
        return version;
    }
    
    /**
     * 设置配置版本
     */
    public void setConfigVersion(String version) {
        plugin.getConfig().set("config_version", version);
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            plugin.getConfig().save(configFile);
        } catch (IOException e) {
            logger.severe("[配置更新] 保存配置版本失败: " + e.getMessage());
        }
    }
}