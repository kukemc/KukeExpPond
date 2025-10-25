package kuke.kukeExpPond.listener;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.pond.Pond;
import kuke.kukeExpPond.pond.PondManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;

/**
 * 防止经验池中的水结冰
 */
public class IcePreventListener implements Listener {
    
    private final KukeExpPond plugin;
    private final PondManager ponds;
    
    public IcePreventListener(KukeExpPond plugin, PondManager ponds) {
        this.plugin = plugin;
        this.ponds = ponds;
    }
    
    /**
     * 阻止池区域内的水结冰
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockForm(BlockFormEvent event) {
        // 检查是否为冰块形成事件
        if (event.getNewState().getType() != Material.ICE && 
            !event.getNewState().getType().name().equals("FROSTED_ICE")) {
            return;
        }
        
        Location location = event.getBlock().getLocation();
        Pond pond = ponds.findByLocation(location);
        
        // 如果在池区域内，取消结冰事件
        if (pond != null) {
            event.setCancelled(true);
            
            // 调试信息
            if (plugin.getConfig().getBoolean("general.debug", false)) {
                plugin.getLogger().info("[DEBUG] 阻止池 '" + pond.getName() + "' 中的水结冰，位置: " + 
                    location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            }
        }
    }
}