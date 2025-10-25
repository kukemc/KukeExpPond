package kuke.kukeExpPond.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatUtil {
    private final JavaPlugin plugin;

    public ChatUtil(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String prefix() {
        return color(plugin.getConfig().getString("general.prefix", ""));
    }

    public String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public void send(CommandSender sender, String msg) {
        if (sender == null) return;
        sender.sendMessage(prefix() + color(msg));
    }

    public void sendNoPrefix(CommandSender sender, String msg) {
        if (sender == null) return;
        sender.sendMessage(color(msg));
    }
}