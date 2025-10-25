package kuke.kukeExpPond.importer;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Importer that reads Exppond-Next's config.yml and converts ponds
 * into KukeExpPond's configuration structure.
 */
public class NextConfigImporter {

    private final KukeExpPond plugin;
    private final ConfigManager cfgMgr;

    public NextConfigImporter(KukeExpPond plugin) {
        this.plugin = plugin;
        this.cfgMgr = plugin.getConfigManager();
    }

    public static class Result {
        public int imported;
        public int skipped;
        public final List<String> details = new ArrayList<String>();
    }

    /**
     * Import from default plugins directory: plugins/Exppond-Next/config.yml
     */
    public Result importDefault(boolean override) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File srcDir = new File(pluginsDir, "Exppond-Next");
        File file = new File(srcDir, "config.yml");
        return importFromFile(file, override);
    }

    /**
     * Import from specific file path.
     */
    public Result importFromFile(File file, boolean override) {
        Result res = new Result();
        if (file == null || !file.exists()) {
            res.details.add("未找到配置文件: " + (file == null ? "<null>" : file.getAbsolutePath()));
            return res;
        }
        YamlConfiguration src = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = src.getConfigurationSection("exppond");
        if (root == null) {
            res.details.add("源配置缺少 'exppond' 节点，无法导入");
            return res;
        }

        FileConfiguration dst = plugin.getConfig();
        for (String rawName : root.getKeys(false)) {
            String pondName = cfgMgr.normalizePondName(rawName);
            ConfigurationSection pSec = root.getConfigurationSection(rawName);
            if (pSec == null) { res.skipped++; res.details.add("跳过: " + rawName + "，配置节点为空"); continue; }

            // Parse locations
            ConfigurationSection locSec = pSec.getConfigurationSection("location");
            Loc l1 = parseLoc(locSec == null ? null : locSec.getString("location1"));
            Loc l2 = parseLoc(locSec == null ? null : locSec.getString("location2"));
            if (l1 == null || l2 == null) {
                res.skipped++; res.details.add("跳过: " + rawName + "，位置坐标不完整");
                continue;
            }

            String base = "ponds." + pondName + ".";
            // Handle override or skip on conflict
            if (dst.isConfigurationSection("ponds." + pondName) && !override) {
                res.skipped++; res.details.add("已存在: " + pondName + "，未启用 override，跳过");
                continue;
            }
            if (override) {
                dst.set("ponds." + pondName, null); // wipe old section
            }

            // World and region
            dst.set(base + "world", l1.world);
            writeRegion(dst, base + "region.", l1, l2);

            // Permission
            String pondPerm = pSec.getString("pond_permission", "pond." + pondName);
            dst.set(base + "permission.pond_permission", pondPerm);
            dst.set(base + "permission.pond_join", Boolean.TRUE);
            dst.set(base + "permission.bypass_permission", "kukeexppond.bypass");

            // Bottle / Mode
            ConfigurationSection bottleSec = pSec.getConfigurationSection("bottle");
            boolean bottleEnable = bottleSec != null && bottleSec.getBoolean("enable", true);
            dst.set(base + "mode.xp_mode", bottleEnable ? "bottle" : "direct");
            dst.set(base + "bottle.enable", bottleEnable);
            if (bottleSec != null) {
                dst.set(base + "bottle.bottle_speed", bottleSec.getInt("bottle_speed", 15));
                dst.set(base + "bottle.bottle_count", bottleSec.getInt("bottle_count", 20));
            }
            // Defaults for our plugin
            dst.set(base + "bottle.only_above_water", Boolean.TRUE);
            dst.set(base + "bottle.drop_height", 4.0D);

            // Reward: money
            ConfigurationSection moneySec = pSec.getConfigurationSection("money");
            if (moneySec != null) {
                dst.set(base + "reward.money.enable", moneySec.getBoolean("enable", false));
                dst.set(base + "reward.money.speed", moneySec.getInt("speed", 60));
                dst.set(base + "reward.money.count", moneySec.getInt("count", 50));
                dst.set(base + "reward.money.max", moneySec.getInt("max", 2000));
            }
            // Reward: points
            ConfigurationSection pointsSec = pSec.getConfigurationSection("points");
            if (pointsSec != null) {
                dst.set(base + "reward.points.enable", pointsSec.getBoolean("enable", false));
                dst.set(base + "reward.points.speed", pointsSec.getInt("speed", 120));
                dst.set(base + "reward.points.count", pointsSec.getInt("count", 3));
                dst.set(base + "reward.points.max", pointsSec.getInt("max", 20));
            }
            // Reward: command
            ConfigurationSection cmdSec = pSec.getConfigurationSection("command");
            if (cmdSec != null) {
                dst.set(base + "reward.command.enable", cmdSec.getBoolean("enable", false));
                dst.set(base + "reward.command.speed", cmdSec.getInt("speed", 150));
                List<String> cmds = cmdSec.getStringList("commands");
                if (cmds != null && !cmds.isEmpty()) {
                    dst.set(base + "reward.command.commands", cmds);
                }
            }

            // UI: title (convert seconds to ticks)
            ConfigurationSection titleSec = pSec.getConfigurationSection("title");
            if (titleSec != null) {
                dst.set(base + "ui.title.join_title", titleSec.getString("join_title", "&b欢迎来到经验温泉"));
                dst.set(base + "ui.title.join_subTitle", titleSec.getString("join_subTitle", ""));
                dst.set(base + "ui.title.leave_title", titleSec.getString("leave_title", "&7你离开了经验温泉"));
                dst.set(base + "ui.title.leave_subTitle", titleSec.getString("leave_subTitle", ""));
                dst.set(base + "ui.title.error_title", titleSec.getString("error_title", "&c无法进入"));
                dst.set(base + "ui.title.error_subTitle", titleSec.getString("error_subTitle", "&7你没有权限"));
                dst.set(base + "ui.title.fadeIn", secondsToTicks(titleSec.getInt("fadeIn", 1)));
                dst.set(base + "ui.title.stay", secondsToTicks(titleSec.getInt("stay", 3)));
                dst.set(base + "ui.title.fadeOut", secondsToTicks(titleSec.getInt("fadeOut", 1)));
            }

            // UI: actionbar - 添加默认的actionbar配置
            dst.set(base + "ui.actionbar.enable", true);
            dst.set(base + "ui.actionbar.template", "&b{pond}&7: 金币 {money_today}/{money_max} | 点券 {points_today}/{points_max}");

            // UI: bossbar - 添加默认的bossbar配置
            dst.set(base + "ui.bossbar.enable", true);
            dst.set(base + "ui.bossbar.color", "BLUE");
            dst.set(base + "ui.bossbar.style", "SEGMENTED_10");
            java.util.List<String> bossbarItems = new java.util.ArrayList<String>();
            bossbarItems.add("MONEY_PROGRESS");
            bossbarItems.add("POINTS_PROGRESS");
            bossbarItems.add("ONLINE_IN_POND");
            bossbarItems.add("NEXT_REWARD_COUNTDOWN");
            dst.set(base + "ui.bossbar.items", bossbarItems);

            // Sound mapping
            ConfigurationSection soundSec = pSec.getConfigurationSection("sound");
            if (soundSec != null) {
                dst.set(base + "sound.join_sound_id", soundSec.getString("join_sound_id", "ENTITY_PLAYER_LEVELUP"));
                dst.set(base + "sound.join_sound_volume", safeFloat(soundSec, "join_sound_volume", 1.0F));
                dst.set(base + "sound.join_sound_pitch", safeFloat(soundSec, "join_sound_pitch", 1.2F));
                dst.set(base + "sound.leave_sound_id", soundSec.getString("leave_sound_id", "BLOCK_WATER_AMBIENT"));
                dst.set(base + "sound.leave_sound_volume", safeFloat(soundSec, "leave_sound_volume", 0.8F));
                dst.set(base + "sound.leave_sound_pitch", safeFloat(soundSec, "leave_sound_pitch", 1.0F));
            }

            // UI: message mapping
            ConfigurationSection msgSec = pSec.getConfigurationSection("message");
            if (msgSec != null) {
                dst.set(base + "ui.message.money", msgSec.getString("money", "&a获得金币 {amount} (今日 {money_today}/{money_max})"));
                dst.set(base + "ui.message.points", msgSec.getString("points", "&a获得点券 {amount} (今日 {points_today}/{points_max})"));
            } else {
                // 如果没有message配置，添加默认值
                dst.set(base + "ui.message.money", "&a获得金币 {amount} (今日 {money_today}/{money_max})");
                dst.set(base + "ui.message.points", "&a获得点券 {amount} (今日 {points_today}/{points_max})");
            }
            // 添加teleport消息配置
            dst.set(base + "ui.message.teleport", "&e请不要使用传送功能进入挂机池");

            // Effects: 添加默认的粒子特效配置
            dst.set(base + "effects.steam_hot_spring.enable", true);
            dst.set(base + "effects.steam_hot_spring.rate", 5);
            dst.set(base + "effects.steam_hot_spring.particle", "CLOUD");
            
            dst.set(base + "effects.water_bubble.enable", true);
            dst.set(base + "effects.water_bubble.rate", 10);
            dst.set(base + "effects.water_bubble.particle", "BUBBLE_COLUMN_UP");
            
            dst.set(base + "effects.smoke_bottom.enable", false);
            dst.set(base + "effects.smoke_bottom.rate", 3);
            dst.set(base + "effects.smoke_bottom.particle", "CAMPFIRE_COSY_SMOKE");
            
            dst.set(base + "effects.density", 1.0);

            res.imported++;
            res.details.add("已导入池: " + pondName);
        }

        plugin.saveConfig();
        // Rebuild to reflect new ponds and tasks
        plugin.rebuildPonds();
        return res;
    }

    private int secondsToTicks(int s) { return s <= 0 ? 0 : s * 20; }

    private float safeFloat(ConfigurationSection sec, String key, float def) {
        if (sec == null) return def;
        try {
            Object v = sec.get(key);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return Float.parseFloat((String) v);
        } catch (Throwable ignored) { }
        return def;
    }

    private void writeRegion(FileConfiguration cfg, String base, Loc p1, Loc p2) {
        cfg.set(base + "pos1.x", p1.x);
        cfg.set(base + "pos1.y", p1.y);
        cfg.set(base + "pos1.z", p1.z);
        cfg.set(base + "pos2.x", p2.x);
        cfg.set(base + "pos2.y", p2.y);
        cfg.set(base + "pos2.z", p2.z);
    }

    private static class Loc {
        final String world; final int x; final int y; final int z;
        Loc(String w, int x, int y, int z) { this.world = w; this.x = x; this.y = y; this.z = z; }
    }

    private Loc parseLoc(String s) {
        if (s == null) return null;
        String[] parts = s.split(";");
        if (parts.length != 4) return null;
        String w = parts[0];
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Loc(w, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}