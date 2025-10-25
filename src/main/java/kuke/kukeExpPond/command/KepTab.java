package kuke.kukeExpPond.command;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class KepTab implements TabCompleter {
    private final ConfigManager cfg;

    public KepTab(KukeExpPond plugin) {
        this.cfg = plugin.getConfigManager();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<String>();
            subs.add("help");
            subs.add("wand");
            subs.add("create");
            subs.add("setregion");
            subs.add("list");
            subs.add("info");
            subs.add("reload");
            subs.add("updateconfig");
            subs.add("checkconfig");
            subs.add("validateconfig");
            subs.add("importnext");
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("setregion".equals(sub) || "info".equals(sub)) {
                Set<String> names = cfg.getPondNames();
                return filter(new ArrayList<String>(names), args[1]);
            }
            if ("importnext".equals(sub)) {
                List<String> opts = new ArrayList<String>();
                opts.add("override");
                return filter(opts, args[1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> out = new ArrayList<String>();
        String p = prefix.toLowerCase();
        for (String s : list) {
            if (s.toLowerCase().startsWith(p)) out.add(s);
        }
        return out;
    }
}