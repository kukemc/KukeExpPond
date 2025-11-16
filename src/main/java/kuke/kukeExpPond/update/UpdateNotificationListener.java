package kuke.kukeExpPond.update;

import kuke.kukeExpPond.KukeExpPond;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 更新通知监听器 - 处理管理员登录时的更新提示
 */
public class UpdateNotificationListener implements Listener {
    private final KukeExpPond plugin;
    private final UpdateChecker updateChecker;
    
    public UpdateNotificationListener(KukeExpPond plugin, UpdateChecker updateChecker) {
        this.plugin = plugin;
        this.updateChecker = updateChecker;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 检查是否启用更新检测和管理员登录提示
        if (!plugin.getConfig().getBoolean("general.update_checker.enable", true) ||
            !plugin.getConfig().getBoolean("general.update_checker.notify_admins_on_join", true)) {
            return;
        }
        
        // 检查是否为管理员
        if (!player.hasPermission("kukeexppond.admin") && !player.isOp()) {
            return;
        }
        
        // 延迟几秒后发送通知，确保玩家完全加载
        new BukkitRunnable() {
            @Override
            public void run() {
                // 如果还没有检查过更新，先检查一次
                if (updateChecker.getLatestVersion() == null && !updateChecker.isCheckInProgress()) {
                    updateChecker.checkForUpdates().thenRun(() -> {
                        // 检查完成后发送通知
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                updateChecker.notifyPlayer(player);
                            }
                        }.runTask(plugin);
                    });
                } else {
                    // 已经检查过了，直接发送通知
                    updateChecker.notifyPlayer(player);
                }
            }
        }.runTaskLater(plugin, 60L); // 3秒后执行
    }
}