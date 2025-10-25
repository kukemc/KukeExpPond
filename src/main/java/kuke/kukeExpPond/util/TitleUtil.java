package kuke.kukeExpPond.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class TitleUtil {
    private final JavaPlugin plugin;

    public TitleUtil(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendTitle(Player p, String title, String subTitle, int fadeIn, int stay, int fadeOut) {
        if (p == null) return;
        try {
            // Try 1.12+ signature
            Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class)
                    .invoke(p, color(title), color(subTitle), fadeIn, stay, fadeOut);
            return;
        } catch (Throwable ignored) {
        }
        try {
            // Try older signature (no timings)
            Player.class.getMethod("sendTitle", String.class, String.class)
                    .invoke(p, color(title), color(subTitle));
            return;
        } catch (Throwable ignored) {
        }
        // Fallback to chat
        p.sendMessage(color(title + (subTitle == null ? "" : " " + subTitle)));
    }

    private String color(String s) {
        return new ChatUtil(plugin).color(s);
    }
}