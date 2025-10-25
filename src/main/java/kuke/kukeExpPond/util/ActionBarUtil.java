package kuke.kukeExpPond.util;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-version ActionBar sender.
 * Attempts Spigot API on modern versions, falls back to chat on legacy.
 */
public class ActionBarUtil {
    private final JavaPlugin plugin;

    public ActionBarUtil(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(Player player, String message) {
        if (player == null) return;
        String colored = new ChatUtil(plugin).color(message);
        try {
            // Try Spigot API: player.spigot().sendMessage(ChatMessageType.ACTION_BAR, BaseComponent...)
            Class<?> chatMsgType = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = chatMsgType.getField("ACTION_BAR").get(null);
            Class<?> textCompClazz = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object text = textCompClazz.getConstructor(String.class).newInstance(colored);
            Object spigot = Player.class.getMethod("spigot").invoke(player);
            spigot.getClass().getMethod("sendMessage", chatMsgType, Class.forName("net.md_5.bungee.api.chat.BaseComponent")).invoke(spigot, actionBar, text);
            return;
        } catch (Throwable ignored) {
        }
        try {
            // Paper 1.16+: Player#sendActionBar(String)
            Player.class.getMethod("sendActionBar", String.class).invoke(player, colored);
            return;
        } catch (Throwable ignored) {
        }
        // Fallback to chat without prefix
        player.sendMessage(colored);
    }
}