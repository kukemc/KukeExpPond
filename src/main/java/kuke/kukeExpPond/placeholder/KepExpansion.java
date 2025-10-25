package kuke.kukeExpPond.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import kuke.kukeExpPond.KukeExpPond;
import kuke.kukeExpPond.pond.PondManager;
import kuke.kukeExpPond.player.PlayerStateManager;
import kuke.kukeExpPond.storage.DataStore;

import java.util.UUID;

/**
 * PlaceholderAPI expansion for KukeExpPond.
 * Identifier: %kep_*
 * Provided placeholders (per current pond when available):
 * - kep_pond
 * - kep_money_today / kep_money_max / kep_money_total
 * - kep_points_today / kep_points_max / kep_points_total
 * Also supports explicit pond suffix: kep_money_today_<pond>, kep_points_today_<pond>.
 */
public class KepExpansion extends PlaceholderExpansion {

    private final KukeExpPond plugin;
    private final PondManager pondManager;
    private final PlayerStateManager states;
    private final DataStore dataStore;

    public KepExpansion(KukeExpPond plugin, PondManager pondManager, PlayerStateManager states, DataStore dataStore) {
        this.plugin = plugin;
        this.pondManager = pondManager;
        this.states = states;
        this.dataStore = dataStore;
    }

    @Override
    public String getIdentifier() { return "kep"; }

    @Override
    public String getAuthor() { return "Kuke"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean canRegister() { return true; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        UUID id = player.getUniqueId();
        String currentPond = states.getCurrentPond(id);

        String pond = currentPond;
        String metric = params;
        String[] allowSuffix = new String[] { "money_today", "points_today", "money_max", "points_max", "pond" };
        for (String m : allowSuffix) {
            String prefix = m + "_";
            if (params.startsWith(prefix) && params.length() > prefix.length()) {
                metric = m;
                pond = params.substring(prefix.length());
                break;
            }
        }

        // Generic non-pond metrics
        if ("pond".equalsIgnoreCase(metric)) {
            return pond == null ? "" : pond;
        }
        if ("money_total".equalsIgnoreCase(metric)) {
            return Integer.toString(dataStore.getMoneyTotal(id));
        }
        if ("points_total".equalsIgnoreCase(metric)) {
            return Integer.toString(dataStore.getPointsTotal(id));
        }

        // Per-pond metrics require pond name
        if (pond == null || pond.length() == 0) {
            return "0";
        }

        if ("money_today".equalsIgnoreCase(metric)) {
            return Integer.toString(dataStore.getDailyMoney(id, pond));
        }
        if ("points_today".equalsIgnoreCase(metric)) {
            return Integer.toString(dataStore.getDailyPoints(id, pond));
        }
        if ("money_max".equalsIgnoreCase(metric)) {
            int max = plugin.getConfig().getInt("ponds." + pond + ".reward.money.max", 0);
            return Integer.toString(max);
        }
        if ("points_max".equalsIgnoreCase(metric)) {
            int max = plugin.getConfig().getInt("ponds." + pond + ".reward.points.max", 0);
            return Integer.toString(max);
        }

        return "";
    }
}