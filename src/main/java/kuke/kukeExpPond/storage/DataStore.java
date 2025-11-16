package kuke.kukeExpPond.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class DataStore {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private File file;
    private FileConfiguration data;

    public DataStore(org.bukkit.plugin.java.JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("无法创建 data.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        if (data == null) return;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("保存 data.yml 失败: " + e.getMessage());
        }
    }

    private String playerPath(UUID uuid) {
        return "players." + requireNonNull(uuid).toString();
    }

    private String pondPath(UUID uuid, String pond) {
        return playerPath(uuid) + ".ponds." + requireNonNull(pond);
    }

    public int getDailyMoney(UUID uuid, String pond) {
        return data.getInt(pondPath(uuid, pond) + ".money_day", 0);
    }

    public int getDailyPoints(UUID uuid, String pond) {
        return data.getInt(pondPath(uuid, pond) + ".points_day", 0);
    }

    public void resetDailyMoney(UUID uuid, String pond) {
        data.set(pondPath(uuid, pond) + ".money_day", 0);
    }

    public void resetDailyPoints(UUID uuid, String pond) {
        data.set(pondPath(uuid, pond) + ".points_day", 0);
    }

    public void addMoney(UUID uuid, String pond, int amount) {
        int day = getDailyMoney(uuid, pond) + amount;
        data.set(pondPath(uuid, pond) + ".money_day", day);
        int total = data.getInt(playerPath(uuid) + ".money_total", 0) + amount;
        data.set(playerPath(uuid) + ".money_total", total);
    }

    public void addPoints(UUID uuid, String pond, int amount) {
        int day = getDailyPoints(uuid, pond) + amount;
        data.set(pondPath(uuid, pond) + ".points_day", day);
        int total = data.getInt(playerPath(uuid) + ".points_total", 0) + amount;
        data.set(playerPath(uuid) + ".points_total", total);
    }

    public int getMoneyTotal(UUID uuid) {
        return data.getInt(playerPath(uuid) + ".money_total", 0);
    }

    public int getPointsTotal(UUID uuid) {
        return data.getInt(playerPath(uuid) + ".points_total", 0);
    }

    public void resetDailyAll() {
        if (data == null) return;
        if (!data.contains("players")) return;
        for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
            String base = "players." + uuidStr + ".ponds";
            if (!data.contains(base)) continue;
            for (String pond : data.getConfigurationSection(base).getKeys(false)) {
                data.set(base + "." + pond + ".money_day", 0);
                data.set(base + "." + pond + ".points_day", 0);
            }
        }
        data.set("meta.last_reset_epoch", System.currentTimeMillis());
        save();
    }

    public long getLastResetEpoch() {
        return data.getLong("meta.last_reset_epoch", 0L);
    }
}