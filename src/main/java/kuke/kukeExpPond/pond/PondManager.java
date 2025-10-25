package kuke.kukeExpPond.pond;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PondManager {
    private final JavaPlugin plugin;
    private final Map<String, Pond> ponds = new HashMap<String, Pond>();

    public PondManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        ponds.clear();
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection pondsSec = cfg.getConfigurationSection("ponds");
        if (pondsSec == null) return;
        for (String name : pondsSec.getKeys(false)) {
            ConfigurationSection sec = pondsSec.getConfigurationSection(name);
            if (sec == null) continue;
            String world = sec.getString("world");
            ConfigurationSection region = sec.getConfigurationSection("region");
            if (world == null || region == null) {
                plugin.getLogger().warning("Pond '" + name + "' missing world/region, skipping.");
                continue;
            }
            int x1 = getInt3(region, "pos1.x");
            int y1 = getInt3(region, "pos1.y");
            int z1 = getInt3(region, "pos1.z");
            int x2 = getInt3(region, "pos2.x");
            int y2 = getInt3(region, "pos2.y");
            int z2 = getInt3(region, "pos2.z");
            String pondPerm = sec.getString("permission.pond_permission", "pond." + name);
            boolean pondJoin = sec.getBoolean("permission.pond_join", true);
            String bypassPerm = sec.getString("permission.bypass_permission", "kukeexppond.bypass");
            boolean tpBlock = sec.getBoolean("teleport.block_enter_by_teleport", false);
            String tpMsg = sec.getString("teleport.message", "&e请不要使用传送功能进入挂机池");
            Pond pond = new Pond(name, world, x1, x2, y1, y2, z1, z2,
                    pondPerm, pondJoin, bypassPerm, tpBlock, tpMsg);
            ponds.put(name.toLowerCase(), pond);
            
            // 扫描水方块位置
            pond.scanWaterBlocks();
        }
        plugin.getLogger().info("Loaded ponds: " + ponds.size());
    }

    private int getInt3(ConfigurationSection sec, String path) {
        return sec.getInt(path, 0);
    }

    public Pond getByName(String name) {
        if (name == null) return null;
        return ponds.get(name.toLowerCase());
    }

    public Pond findByLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String w = loc.getWorld().getName();
        for (Pond pond : ponds.values()) {
            if (!pond.getWorldName().equals(w)) continue;
            if (pond.contains(loc)) return pond;
        }
        return null;
    }

    public List<Pond> listAll() {
        return new ArrayList<Pond>(ponds.values());
    }
}