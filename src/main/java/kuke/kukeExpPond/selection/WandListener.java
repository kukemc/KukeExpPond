package kuke.kukeExpPond.selection;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.util.ChatUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;

public class WandListener implements Listener {
    public static final String WAND_NAME = ChatColor.AQUA + "区域选择工具 (KukeExpPond)";

    private final KukeExpPond plugin;
    private final SelectionManager selections;
    private final ChatUtil chat;

    public WandListener(KukeExpPond plugin, SelectionManager selections) {
        this.plugin = plugin;
        this.selections = selections;
        this.chat = new ChatUtil(plugin);
    }

    public static ItemStack createWand() {
        Material m = Material.BLAZE_ROD; // 1.8+ 可用
        ItemStack wand = new ItemStack(m, 1);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(WAND_NAME);
            java.util.List<String> lore = new java.util.ArrayList<String>();
            lore.add(ChatColor.GRAY + "左键设置 pos1，右键设置 pos2");
            lore.add(ChatColor.GRAY + "跨世界选择将自动重置");
            meta.setLore(lore);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();
        if (item == null || !isWand(item)) return;
        Action action = e.getAction();
        if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
            Location loc = targetBlockOrPlayerLoc(p, e);
            if (loc != null) {
                selections.get(p).setPos1(loc);
                chat.send(p, "&a设置 &bpos1 &a为 &7(" + fmt(loc) + ")");
                e.setCancelled(true);
            }
        } else if (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR) {
            Location loc = targetBlockOrPlayerLoc(p, e);
            if (loc != null) {
                selections.get(p).setPos2(loc);
                chat.send(p, "&a设置 &bpos2 &a为 &7(" + fmt(loc) + ")");
                e.setCancelled(true);
            }
        }
    }

    private boolean isWand(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && WAND_NAME.equals(meta.getDisplayName());
    }

    private String fmt(Location loc) {
        return loc.getWorld().getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private Location targetBlockOrPlayerLoc(Player p, PlayerInteractEvent e) {
        if (e.getClickedBlock() != null) {
            return e.getClickedBlock().getLocation();
        }
        // 空气点击时取玩家脚下位置
        return p.getLocation();
    }
}