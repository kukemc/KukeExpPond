package kuke.kukeExpPond.xp;

import kuke.kukeExpPond.KukeExpPond;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.metadata.MetadataValue;

/**
 * 调整由插件投掷的经验瓶的经验值，使其与配置一致。
 */
public class XpBottleListener implements Listener {
    private final KukeExpPond plugin;
    private static final String META_KEY = "kep_xp_value";

    public XpBottleListener(KukeExpPond plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onExpBottle(ExpBottleEvent event) {
        if (event == null || event.getEntity() == null) return;
        if (!event.getEntity().hasMetadata(META_KEY)) return;
        for (MetadataValue mv : event.getEntity().getMetadata(META_KEY)) {
            if (mv == null) continue;
            if (mv.getOwningPlugin() == plugin) {
                int value = 0;
                try {
                    value = mv.asInt();
                } catch (Throwable ignore) {}
                if (value > 0) {
                    event.setExperience(value);
                }
                break;
            }
        }
    }
}