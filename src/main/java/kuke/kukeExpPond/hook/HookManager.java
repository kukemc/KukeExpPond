package kuke.kukeExpPond.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Centralized optional-dependency detector with graceful degradation.
 * Avoids hard compile dependencies by using reflection.
 */
public class HookManager {

    private final JavaPlugin plugin;
    private final Logger log;

    private boolean vaultPresent;
    private boolean playerPointsPresent;
    private boolean placeholderApiPresent;
    private boolean bStatsPresent;

    // Cached reflective handles
    private Object economyProvider; // Vault Economy instance
    private Object playerPointsApi; // PlayerPoints API instance (varies by versions)

    public HookManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    private boolean isDebug() {
        try {
            return plugin.getConfig().getBoolean("general.debug", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void init() {
        detectVault();
        detectPlayerPoints();
        detectPlaceholderAPI();
    }

    private void detectVault() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object rsp = Bukkit.getServer().getServicesManager().getRegistration(economyClass);
            if (rsp != null) {
                Method getProvider = rsp.getClass().getMethod("getProvider");
                this.economyProvider = getProvider.invoke(rsp);
                this.vaultPresent = this.economyProvider != null;
            } else {
                this.vaultPresent = false;
            }
        } catch (Throwable t) {
            this.vaultPresent = false;
        }
        if (isDebug()) {
            log.info("Vault economy: " + (vaultPresent ? "available" : "not found"));
        }
    }

    private void detectPlayerPoints() {
        Plugin pp = Bukkit.getPluginManager().getPlugin("PlayerPoints");
        if (pp == null) {
            this.playerPointsPresent = false;
            if (isDebug()) {
                log.info("PlayerPoints: not found");
            }
            return;
        }
        // Try common API access patterns across versions
        Object api = null;
        try {
            // org.black_ixx.playerpoints.PlayerPoints#getAPI()
            Method getApi = pp.getClass().getMethod("getAPI");
            api = getApi.invoke(pp);
        } catch (Throwable ignored) {
        }
        if (api == null) {
            try {
                // org.playerpoints.PlayerPoints#getApi() or getPlayerPointsApi()
                Method getApi = null;
                try { getApi = pp.getClass().getMethod("getApi"); } catch (Throwable ignored) {}
                if (getApi == null) {
                    try { getApi = pp.getClass().getMethod("getPlayerPointsApi"); } catch (Throwable ignored) {}
                }
                if (getApi != null) {
                    api = getApi.invoke(pp);
                }
            } catch (Throwable ignored) {
            }
        }
        this.playerPointsApi = api;
        this.playerPointsPresent = api != null;
        if (isDebug()) {
            log.info("PlayerPoints: " + (playerPointsPresent ? "available" : "not found"));
        }
    }

    private void detectPlaceholderAPI() {
        this.placeholderApiPresent = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (isDebug()) {
            log.info("PlaceholderAPI: " + (placeholderApiPresent ? "available" : "not found"));
        }
    }

    public boolean isVaultPresent() { return vaultPresent; }
    public boolean isPlayerPointsPresent() { return playerPointsPresent; }
    public boolean isPlaceholderApiPresent() { return placeholderApiPresent; }
    public boolean isBStatsPresent() { return bStatsPresent; }

    // Minimal wrappers for future rewards (safe no-ops when missing)
    public boolean depositMoney(java.util.UUID uuid, double amount) {
        if (!vaultPresent || economyProvider == null || amount <= 0) return false;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Method depositPlayer = economyClass.getMethod("depositPlayer", java.util.UUID.class, double.class);
            Object resp = depositPlayer.invoke(economyProvider, uuid, amount);
            // EconomyResponse#transactionSuccess()
            Method success = resp.getClass().getMethod("transactionSuccess");
            return (Boolean) success.invoke(resp);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean givePlayerPoints(java.util.UUID uuid, int points) {
        if (!playerPointsPresent || playerPointsApi == null || points <= 0) return false;
        try {
            // Try common method names
            Method give = null;
            try { give = playerPointsApi.getClass().getMethod("give", java.util.UUID.class, int.class); } catch (Throwable ignored) {}
            if (give == null) {
                try { give = playerPointsApi.getClass().getMethod("increase", java.util.UUID.class, int.class); } catch (Throwable ignored) {}
            }
            if (give == null) {
                try { give = playerPointsApi.getClass().getMethod("add", java.util.UUID.class, int.class); } catch (Throwable ignored) {}
            }
            if (give != null) {
                Object result = give.invoke(playerPointsApi, uuid, points);
                if (result instanceof Boolean) return (Boolean) result;
                return true; // assume success if void or non-boolean
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}