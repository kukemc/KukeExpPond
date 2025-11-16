package kuke.kukeExpPond.listener;

import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.player.PlayerStateManager;
import kuke.kukeExpPond.pond.Pond;
import kuke.kukeExpPond.pond.PondManager;
import kuke.kukeExpPond.util.ChatUtil;
import kuke.kukeExpPond.util.TitleUtil;
import kuke.kukeExpPond.util.SoundUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class MoveTeleportListener implements Listener {
    private final KukeExpPond plugin;
    private final PondManager ponds;
    private final PlayerStateManager states;
    private final ChatUtil chat;
    private final TitleUtil titles;
    private final SoundUtil sounds;

    public MoveTeleportListener(KukeExpPond plugin, PondManager ponds, PlayerStateManager states) {
        this.plugin = plugin;
        this.ponds = ponds;
        this.states = states;
        this.chat = new ChatUtil(plugin);
        this.titles = new TitleUtil(plugin);
        this.sounds = new SoundUtil(plugin);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom() == null || e.getTo() == null) return;
        // Debounce: ignore tiny block-unmoved changes
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockY() == e.getTo().getBlockY() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        Player p = e.getPlayer();
        Location to = e.getTo();
        Pond pondTo = ponds.findByLocation(to);

        String current = states.getCurrentPond(p.getUniqueId());
        String next = pondTo == null ? null : pondTo.getName();

        // Leaving a pond
        if (current != null && (next == null || !current.equals(next))) {
            handleLeave(p, current);
            states.setCurrentPond(p.getUniqueId(), null);
        }

        // Entering a pond
        if (next != null && (current == null || !current.equals(next))) {
            handleEnter(e, p, pondTo);
        }

        // Update last safe location (outside ponds)
        if (pondTo == null) {
            states.setLastSafeLoc(p.getUniqueId(), to);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null) return;
        Player p = e.getPlayer();
        Pond target = ponds.findByLocation(e.getTo());
        if (target == null) return;
        if (!target.isBlockEnterByTeleport()) return;
        String bypass = target.getBypassPermission();
        if (bypass != null && bypass.length() > 0 && p.hasPermission(bypass)) return;
        e.setCancelled(true);
        String msg = target.getTeleportMessage();
        chat.send(p, msg);
    }

    private void handleEnter(PlayerMoveEvent e, Player p, Pond pond) {
        // Permissions
        String perm = pond.getPondPermission();
        boolean has = perm == null || perm.length() == 0 || p.hasPermission(perm);
        if (!has) {
            // Unauthorized
            showErrorTitle(p);
            if (!pond.isAllowJoinWithoutPerm()) {
                // bounce back to last safe location
                Location back = states.getLastSafeLoc(p.getUniqueId());
                if (back != null) {
                    e.setTo(back);
                } else {
                    e.setTo(e.getFrom());
                }
                chat.send(p, "&c你没有进入该池的权限");
                return;
            } else {
                // Allow enter but mark inside without rewards (to be enforced in reward system)
                chat.send(p, "&e你进入了池，但没有权限将不获得奖励");
            }
        }
        // Authorized entry
        states.setCurrentPond(p.getUniqueId(), pond.getName());
        showJoinTitle(p);
        playJoinSound(p, pond);
    }

    private void handleLeave(Player p, String pondName) {
        showLeaveTitle(p);
        Pond pond = ponds.getByName(pondName);
        if (pond != null) {
            playLeaveSound(p, pond);
        }
    }

    private void showJoinTitle(Player p) {
        // read config titles for join
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        String currentPond = states.getCurrentPond(p.getUniqueId());
        String rawBase = "ponds." + currentPond + ".ui.title.";
        String normName = plugin.getConfigManager().normalizePondName(currentPond);
        String normBase = "ponds." + normName + ".ui.title.";
        // 优先使用原始池名键；若不存在则回退到规范化键，避免大小写不一致导致找不到配置
        String base = cfg.contains(rawBase + "join_title") || cfg.isConfigurationSection("ponds." + currentPond + ".ui.title")
                ? rawBase
                : normBase;
        String title = cfg.getString(base + "join_title", "&b欢迎来到经验温泉");
        String sub = cfg.getString(base + "join_subTitle", "");
        int fi = cfg.getInt(base + "fadeIn", 10);
        int st = cfg.getInt(base + "stay", 40);
        int fo = cfg.getInt(base + "fadeOut", 10);
        titles.sendTitle(p, title, sub, fi, st, fo);
    }

    private void showLeaveTitle(Player p) {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        String currentPond = states.getCurrentPond(p.getUniqueId());
        String rawBase = "ponds." + currentPond + ".ui.title.";
        String normName = plugin.getConfigManager().normalizePondName(currentPond);
        String normBase = "ponds." + normName + ".ui.title.";
        String base = cfg.contains(rawBase + "leave_title") || cfg.isConfigurationSection("ponds." + currentPond + ".ui.title")
                ? rawBase
                : normBase;
        String title = cfg.getString(base + "leave_title", "&7你离开了经验温泉");
        String sub = cfg.getString(base + "leave_subTitle", "");
        int fi = cfg.getInt(base + "fadeIn", 10);
        int st = cfg.getInt(base + "stay", 40);
        int fo = cfg.getInt(base + "fadeOut", 10);
        titles.sendTitle(p, title, sub, fi, st, fo);
    }

    private void showErrorTitle(Player p) {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        // We use default pond UI timings, fallback to general join timings if not found
        String anyBase = "ponds.default.ui.title.";
        String title = cfg.getString("ponds.default.ui.title.error_title", "&c无法进入");
        String sub = cfg.getString("ponds.default.ui.title.error_subTitle", "&7你没有权限");
        int fi = cfg.getInt(anyBase + "fadeIn", 10);
        int st = cfg.getInt(anyBase + "stay", 40);
        int fo = cfg.getInt(anyBase + "fadeOut", 10);
        titles.sendTitle(p, title, sub, fi, st, fo);
    }

    private void playJoinSound(Player p, Pond pond) {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        String base = "ponds." + pond.getName() + ".sound.";
        String id = cfg.getString(base + "join_sound_id", "ENTITY_PLAYER_LEVELUP");
        float vol = (float) cfg.getDouble(base + "join_sound_volume", 1.0);
        float pitch = (float) cfg.getDouble(base + "join_sound_pitch", 1.2);
        String fallback = cfg.getString("general.version_mapping.join_sound_fallback", "ENTITY_EXPERIENCE_ORB_PICKUP");
        sounds.play(p, id, fallback, vol, pitch);
    }

    private void playLeaveSound(Player p, Pond pond) {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
        String base = "ponds." + pond.getName() + ".sound.";
        String id = cfg.getString(base + "leave_sound_id", "BLOCK_WATER_AMBIENT");
        float vol = (float) cfg.getDouble(base + "leave_sound_volume", 0.8);
        float pitch = (float) cfg.getDouble(base + "leave_sound_pitch", 1.0);
        String fallback = cfg.getString("general.version_mapping.leave_sound_fallback", "BLOCK_WATER_AMBIENT");
        sounds.play(p, id, fallback, vol, pitch);
    }
}