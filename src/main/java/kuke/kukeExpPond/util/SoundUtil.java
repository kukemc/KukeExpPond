package kuke.kukeExpPond.util;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-version sound player with graceful fallbacks.
 */
public class SoundUtil {
    private final JavaPlugin plugin;
    private final ChatUtil chat;

    public SoundUtil(JavaPlugin plugin) {
        this.plugin = plugin;
        this.chat = new ChatUtil(plugin);
    }

    public void play(Player p, String soundId, String fallbackId, float volume, float pitch) {
        if (p == null) return;
        Location loc = p.getLocation();
        String id = soundId;
        if (id == null || id.trim().isEmpty()) id = fallbackId;
        if (id == null || id.trim().isEmpty()) return;
        try {
            Sound s = Sound.valueOf(id);
            p.playSound(loc, s, volume, pitch);
            return;
        } catch (Throwable ignored) {
        }
        // As last resort, attempt older string-based API if present
        try {
            Player.class.getMethod("playSound", Location.class, String.class, float.class, float.class)
                    .invoke(p, loc, id, volume, pitch);
        } catch (Throwable ignored) {
            // Give up silently
        }
    }
}