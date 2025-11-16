package kuke.kukeExpPond.xp;

import kuke.kukeExpPond.KukeExpPond;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 经验瞬间吸收监听器
 * 防止经验球堆积，让玩家瞬间吸收附近的经验
 */
public class ExpAbsorbListener implements Listener {
    private final KukeExpPond plugin;

    public ExpAbsorbListener(KukeExpPond plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExperienceOrbSpawn(EntitySpawnEvent event) {
        // 检查是否启用经验瞬间吸收功能
        if (!plugin.getConfig().getBoolean("general.instant_exp_absorb.enable", true)) {
            return;
        }

        // 只处理经验球
        if (!(event.getEntity() instanceof ExperienceOrb)) {
            return;
        }

        ExperienceOrb orb = (ExperienceOrb) event.getEntity();
        
        // 获取配置参数
        double searchRadius = plugin.getConfig().getDouble("general.instant_exp_absorb.search_radius", 5.0);
        int delayTicks = plugin.getConfig().getInt("general.instant_exp_absorb.delay_ticks", 1);
        
        // 延迟处理，确保经验球完全生成
        new BukkitRunnable() {
            @Override
            public void run() {
                if (orb.isDead() || !orb.isValid()) {
                    return;
                }
                
                // 查找附近的玩家
                Player nearestPlayer = null;
                double nearestDistance = Double.MAX_VALUE;
                
                for (Player player : orb.getWorld().getPlayers()) {
                    if (player.getLocation().distance(orb.getLocation()) <= searchRadius) {
                        double distance = player.getLocation().distance(orb.getLocation());
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestPlayer = player;
                        }
                    }
                }
                
                // 如果找到附近玩家，直接给予经验并移除经验球
                if (nearestPlayer != null) {
                    int expValue = orb.getExperience();
                    if (expValue > 0) {
                        nearestPlayer.giveExp(expValue);
                        
                        // 可选：播放经验吸收音效
                        if (plugin.getConfig().getBoolean("general.instant_exp_absorb.play_sound", true)) {
                            nearestPlayer.playSound(nearestPlayer.getLocation(), 
                                org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.1f, 1.0f);
                        }
                        
                        // 移除经验球
                        orb.remove();
                    }
                }
            }
        }.runTaskLater(plugin, delayTicks);
    }
}