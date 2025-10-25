package kuke.kukeExpPond.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cross-version particle spawner with graceful fallbacks.
 */
public class ParticleUtil {
    private final JavaPlugin plugin;
    private final ChatUtil chat;

    public ParticleUtil(JavaPlugin plugin) {
        this.plugin = plugin;
        this.chat = new ChatUtil(plugin);
    }

    public void spawn(World world, Location loc, String particleName, int count, String fallbackName) {
        spawn(world, loc, particleName, count, fallbackName, 0.0, 0.0, 0.0, 0.0);
    }

    public void spawn(World world, Location loc, String particleName, int count, String fallbackName, 
                     double offsetX, double offsetY, double offsetZ, double speed) {
        if (world == null || loc == null) return;
        String name = particleName != null && particleName.length() > 0 ? particleName : fallbackName;
        if (name == null || name.length() == 0) return;
        try {
            // 1.9+ Particle API with offset and speed
            Class<?> particleClazz = Class.forName("org.bukkit.Particle");
            Object particle = Enum.valueOf((Class<Enum>) particleClazz, name);
            world.getClass().getMethod("spawnParticle", particleClazz, Location.class, int.class, 
                    double.class, double.class, double.class, double.class)
                    .invoke(world, particle, loc, count, offsetX, offsetY, offsetZ, speed);
            return;
        } catch (Throwable ignored) {
        }
        try {
            // Legacy Effect fallback - no offset/speed support
            Class<?> effectClazz = Class.forName("org.bukkit.Effect");
            Object effect = Enum.valueOf((Class<Enum>) effectClazz, name);
            for (int i = 0; i < Math.max(1, count); i++) {
                world.getClass().getMethod("playEffect", Location.class, effectClazz, int.class)
                        .invoke(world, loc, effect, 0);
            }
        } catch (Throwable ignored) {
            // No particle support available
        }
    }
}