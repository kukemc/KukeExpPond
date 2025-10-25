package kuke.kukeExpPond.command;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.config.ConfigManager;
import kuke.kukeExpPond.selection.Selection;
import kuke.kukeExpPond.selection.SelectionManager;
import kuke.kukeExpPond.selection.WandListener;
import kuke.kukeExpPond.util.ChatUtil;
import kuke.kukeExpPond.importer.NextConfigImporter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;

public class KepCommand implements CommandExecutor {

    private final KukeExpPond plugin;
    private final ChatUtil chat;
    private final SelectionManager selections;
    private final ConfigManager cfgMgr;

    public KepCommand(KukeExpPond plugin) {
        this.plugin = plugin;
        this.chat = new ChatUtil(plugin);
        this.selections = plugin.getSelectionManager();
        this.cfgMgr = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return help(sender, label);
        }
        String sub = args[0].toLowerCase();
        if ("help".equals(sub)) {
            return help(sender, label);
        }
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("kukeexppond.admin")) {
                chat.send(sender, "&c你没有权限执行此命令");
                return true;
            }
            cfgMgr.reload();
            plugin.rebuildPonds();
            chat.send(sender, "&a配置已重载");
            return true;
        }
        if ("updateconfig".equals(sub)) {
            if (!sender.hasPermission("kukeexppond.admin")) {
                chat.send(sender, "&c你没有权限执行此命令");
                return true;
            }
            try {
                boolean updated = plugin.getConfigUpdater().checkAndUpdateConfig();
                if (updated) {
                    chat.send(sender, "&a配置文件已更新，添加了缺失的配置项");
                    cfgMgr.reload();
                    plugin.rebuildPonds();
                    chat.send(sender, "&a配置已重载");
                } else {
                    chat.send(sender, "&e配置文件已是最新版本，无需更新");
                }
            } catch (Exception e) {
                chat.send(sender, "&c配置更新失败: " + e.getMessage());
                plugin.getLogger().log(java.util.logging.Level.WARNING, "配置更新失败", e);
            }
            return true;
        }
        if ("checkconfig".equals(sub)) {
            if (!sender.hasPermission("kukeexppond.admin")) {
                chat.send(sender, "&c你没有权限执行此命令");
                return true;
            }
            try {
                cfgMgr.checkAndUpdateConfig();
                chat.send(sender, "&a配置检查完成，已自动补充缺失的配置项");
                cfgMgr.reload();
                plugin.rebuildPonds();
                chat.send(sender, "&a配置已重载");
            } catch (Exception e) {
                chat.send(sender, "&c配置检查失败: " + e.getMessage());
                plugin.getLogger().log(java.util.logging.Level.WARNING, "配置检查失败", e);
            }
            return true;
        }
        if ("validateconfig".equals(sub)) {
            if (!sender.hasPermission("kukeexppond.admin")) {
                chat.send(sender, "&c你没有权限执行此命令");
                return true;
            }
            try {
                String result = cfgMgr.validateConfig();
                chat.send(sender, "&a配置验证完成:");
                String[] lines = result.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        chat.send(sender, "&7" + line);
                    }
                }
            } catch (Exception e) {
                chat.send(sender, "&c配置验证失败: " + e.getMessage());
                plugin.getLogger().log(java.util.logging.Level.WARNING, "配置验证失败", e);
            }
            return true;
        }
        if ("importnext".equals(sub)) {
            if (!sender.hasPermission("kukeexppond.admin")) {
                chat.send(sender, "&c你没有权限执行此命令");
                return true;
            }
            boolean override = args.length >= 2 && "override".equalsIgnoreCase(args[1]);
            NextConfigImporter importer = new NextConfigImporter(plugin);
            NextConfigImporter.Result res = importer.importDefault(override);
            if (res.imported == 0 && res.skipped == 0) {
                chat.send(sender, "&e未导入任何池（检查 Exppond-Next/config.yml 是否存在且格式正确）");
            } else {
                chat.send(sender, "&a导入完成: 已导入 " + res.imported + " 个，跳过 " + res.skipped + " 个");
                for (String d : res.details) {
                    chat.send(sender, " &7- " + d);
                }
            }
            return true;
        }
        if ("list".equals(sub)) {
            Set<String> names = cfgMgr.getPondNames();
            if (names.isEmpty()) {
                chat.send(sender, "&7暂无任何池");
                return true;
            }
            chat.send(sender, "&b池列表 (" + names.size() + "):");
            Map<String, String> map = cfgMgr.getPondOverview();
            for (Map.Entry<String, String> e : map.entrySet()) {
                chat.send(sender, " &7- &f" + e.getKey() + " &7(" + e.getValue() + ")");
            }
            return true;
        }
        if ("wand".equals(sub)) {
            if (!(sender instanceof Player)) {
                chat.send(sender, "&c该命令只能由玩家执行");
                return true;
            }
            if (!sender.hasPermission("kukeexppond.admin")) {
                chat.send(sender, "&c你没有权限执行此命令");
                return true;
            }
            Player p = (Player) sender;
            ItemStack wand = WandListener.createWand();
            p.getInventory().addItem(wand);
            chat.send(p, "&a已给予区域选择工具");
            return true;
        }
        if ("create".equals(sub)) {
            if (!sender.hasPermission("kukeexppond.admin")) {
                chat.send(sender, "&c你没有权限执行此命令");
                return true;
            }
            if (!(sender instanceof Player)) {
                chat.send(sender, "&c该命令只能由玩家执行");
                return true;
            }
            if (args.length < 2) {
                chat.send(sender, "&e用法: /" + label + " create <池名>");
                return true;
            }
            Player p = (Player) sender;
            Selection sel = selections.get(p);
            if (sel.getPos1() == null || sel.getPos2() == null || sel.getWorldName() == null) {
                chat.send(p, "&c请先使用 &b/" + label + " wand &c工具选择 pos1 与 pos2");
                return true;
            }
            String rawName = args[1];
            String pondName = cfgMgr.normalizePondName(rawName);
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            String base = "ponds." + pondName + ".";
            cfg.set(base + "world", sel.getWorldName());
            writeRegion(cfg, base + "region.", sel.getPos1(), sel.getPos2());
            // set defaults for permission and mode
            cfg.set(base + "permission.pond_permission", "pond." + pondName);
            cfg.set(base + "permission.pond_join", Boolean.TRUE);
            cfg.set(base + "permission.bypass_permission", "kukeexppond.bypass");
            cfg.set(base + "mode.xp_mode", "bottle");
            // basic bottle defaults
            cfg.set(base + "bottle.enable", Boolean.TRUE);
            cfg.set(base + "bottle.bottle_speed", 5);
            cfg.set(base + "bottle.bottle_count", 3);
            cfg.set(base + "bottle.only_above_water", Boolean.TRUE);
            cfg.set(base + "bottle.drop_height", 4.0);
            plugin.saveConfig();
            chat.send(p, "&a已创建池 &b" + pondName + " &7(世界=" + sel.getWorldName() + ")");
            return true;
        }
        if ("setregion".equals(sub)) {
            if (!sender.hasPermission("kukeexppond.admin")) {
                chat.send(sender, "&c你没有权限执行此命令");
                return true;
            }
            if (!(sender instanceof Player)) {
                chat.send(sender, "&c该命令只能由玩家执行");
                return true;
            }
            if (args.length < 2) {
                chat.send(sender, "&e用法: /" + label + " setregion <池名>");
                return true;
            }
            Player p = (Player) sender;
            Selection sel = selections.get(p);
            if (sel.getPos1() == null || sel.getPos2() == null || sel.getWorldName() == null) {
                chat.send(p, "&c请先使用 &b/" + label + " wand &c工具选择 pos1 与 pos2");
                return true;
            }
            String pondName = cfgMgr.normalizePondName(args[1]);
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            String base = "ponds." + pondName + ".";
            if (!cfg.isConfigurationSection("ponds." + pondName)) {
                chat.send(sender, "&c池不存在: " + pondName + "，请先 /" + label + " create");
                return true;
            }
            cfg.set(base + "world", sel.getWorldName());
            writeRegion(cfg, base + "region.", sel.getPos1(), sel.getPos2());
            plugin.saveConfig();
            chat.send(p, "&a已更新池区域 &b" + pondName);
            return true;
        }
        if ("info".equals(sub)) {
            if (args.length < 2) {
                chat.send(sender, "&e用法: /" + label + " info <池名>");
                return true;
            }
            String pondName = cfgMgr.normalizePondName(args[1]);
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            String base = "ponds." + pondName + ".";
            if (!cfg.isConfigurationSection("ponds." + pondName)) {
                chat.send(sender, "&c池不存在: " + pondName);
                return true;
            }
            chat.send(sender, "&b池信息: &f" + pondName);
            chat.send(sender, " &7世界: &f" + cfg.getString(base + "world", "-"));
            String pos1 = fmtPos(cfg, base + "region.pos1");
            String pos2 = fmtPos(cfg, base + "region.pos2");
            chat.send(sender, " &7区域: pos1(&f" + pos1 + "&7) pos2(&f" + pos2 + "&7)");
            chat.send(sender, " &7模式: &f" + cfg.getString(base + "mode.xp_mode", "direct"));
            chat.send(sender, " &7权限: &f" + cfg.getString(base + "permission.pond_permission", "-") +
                    ", 允许无权进入: " + cfg.getBoolean(base + "permission.pond_join", true));
            chat.send(sender, " &7瓶模式: enable=" + cfg.getBoolean(base + "bottle.enable", false) +
                    ", speed=" + cfg.getInt(base + "bottle.bottle_speed", 0) +
                    ", count=" + cfg.getInt(base + "bottle.bottle_count", 0));
            return true;
        }

        chat.send(sender, "&e未知子命令，使用 /" + label + " help 查看帮助");
        return true;
    }

    private boolean help(CommandSender sender, String label) {
        String cmd = "/" + label;
        chat.sendNoPrefix(sender, "&6================== &eKukeExpPond 帮助 &6==================");
        chat.sendNoPrefix(sender, "&7命令: &b" + cmd + "  &7(支持控制台，部分子命令需玩家)");
        chat.sendNoPrefix(sender, "");
        chat.sendNoPrefix(sender, "&b" + cmd + " help &7- 查看完整帮助与用法");
        chat.sendNoPrefix(sender, "&b" + cmd + " wand &7- 获取区域选择工具，左键 pos1，右键 pos2");
        chat.sendNoPrefix(sender, "&b" + cmd + " create <池名> &7- 以当前选区创建池，自动写入世界与坐标");
        chat.sendNoPrefix(sender, "&b" + cmd + " setregion <池名> &7- 将当前选区应用到指定池的 region");
        chat.sendNoPrefix(sender, "&b" + cmd + " list &7- 列出所有已配置池及模式/瓶状态");
        chat.sendNoPrefix(sender, "&b" + cmd + " info <池名> &7- 查看指定池的世界、区域、模式、权限等详情");
        chat.sendNoPrefix(sender, "&b" + cmd + " reload &7- 重载配置并重建奖励/经验/展示等任务");
        chat.sendNoPrefix(sender, "&b" + cmd + " updateconfig &7- 检查并自动添加缺失的配置项（旧版）");
        chat.sendNoPrefix(sender, "&b" + cmd + " checkconfig &7- 使用新配置管理器检查并补充缺失配置项");
        chat.sendNoPrefix(sender, "&b" + cmd + " validateconfig &7- 验证配置文件的完整性和有效性");
        chat.sendNoPrefix(sender, "&b" + cmd + " importnext [override] &7- 从 Exppond-Next 导入配置；override 覆盖同名池");
        chat.sendNoPrefix(sender, "&6============================================================");
        return true;
    }

    private void writeRegion(org.bukkit.configuration.file.FileConfiguration cfg, String base, Location p1, Location p2) {
        cfg.set(base + "pos1.x", p1.getBlockX());
        cfg.set(base + "pos1.y", p1.getBlockY());
        cfg.set(base + "pos1.z", p1.getBlockZ());
        cfg.set(base + "pos2.x", p2.getBlockX());
        cfg.set(base + "pos2.y", p2.getBlockY());
        cfg.set(base + "pos2.z", p2.getBlockZ());
    }

    private String fmtPos(org.bukkit.configuration.file.FileConfiguration cfg, String base) {
        int x = cfg.getInt(base + ".x", 0);
        int y = cfg.getInt(base + ".y", 0);
        int z = cfg.getInt(base + ".z", 0);
        return x + "," + y + "," + z;
    }
}