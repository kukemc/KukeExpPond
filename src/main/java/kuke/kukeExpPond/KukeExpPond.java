//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package kuke.kukeExpPond;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import kuke.kukeExpPond.command.KepCommand;
import kuke.kukeExpPond.command.KepTab;
import kuke.kukeExpPond.config.ConfigManager;
import kuke.kukeExpPond.config.ConfigUpdater;
import kuke.kukeExpPond.effects.EffectsManager;
import kuke.kukeExpPond.hook.HookManager;
import kuke.kukeExpPond.ice.IcePreventionManager;
import kuke.kukeExpPond.listener.IcePreventListener;
import kuke.kukeExpPond.listener.MoveTeleportListener;
import kuke.kukeExpPond.player.PlayerStateManager;
import kuke.kukeExpPond.pond.PondManager;
import kuke.kukeExpPond.reward.RewardManager;
import kuke.kukeExpPond.selection.SelectionManager;
import kuke.kukeExpPond.selection.WandListener;
import kuke.kukeExpPond.storage.DataStore;
import kuke.kukeExpPond.ui.UiManager;
import kuke.kukeExpPond.xp.XpBottleListener;
import kuke.kukeExpPond.xp.XpManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class KukeExpPond extends JavaPlugin {
    private Logger log;
    private HookManager hooks;
    private ConfigManager configManager;
    private ConfigUpdater configUpdater;
    private SelectionManager selectionManager;
    private PondManager pondManager;
    private PlayerStateManager playerStateManager;
    private DataStore dataStore;
    private RewardManager rewardManager;
    private XpManager xpManager;
    private UiManager uiManager;
    private EffectsManager effectsManager;
    private IcePreventionManager icePreventionManager;

    public KukeExpPond() {
    }

    public void onEnable() {
        this.log = this.getLogger();
        this.configManager = new ConfigManager(this);
        this.configManager.initialize();
        this.configUpdater = new ConfigUpdater(this);
        this.selectionManager = new SelectionManager();
        this.pondManager = new PondManager(this);
        this.pondManager.reload();
        this.playerStateManager = new PlayerStateManager();
        this.dataStore = new DataStore(this);
        this.dataStore.load();
        this.log.info("KukeExpPond enabled. Server: " + Bukkit.getVersion());
        this.initBStats();
        this.hooks = new HookManager(this);
        this.hooks.init();
        this.getServer().getPluginManager().registerEvents(new WandListener(this, this.selectionManager), this);
        this.getServer().getPluginManager().registerEvents(new MoveTeleportListener(this, this.pondManager, this.playerStateManager), this);
        this.getServer().getPluginManager().registerEvents(new XpBottleListener(this), this);
        this.getServer().getPluginManager().registerEvents(new IcePreventListener(this, this.pondManager), this);
        this.rewardManager = new RewardManager(this, this.pondManager, this.hooks, this.dataStore);
        this.rewardManager.start();
        this.xpManager = new XpManager(this, this.pondManager);
        this.xpManager.start();
        this.uiManager = new UiManager(this, this.pondManager, this.playerStateManager, this.dataStore);
        this.uiManager.start();
        this.effectsManager = new EffectsManager(this, this.pondManager);
        this.effectsManager.start();
        this.icePreventionManager = new IcePreventionManager(this, this.pondManager);
        this.icePreventionManager.start();
        if (this.hooks.isPlaceholderApiPresent()) {
            try {
                Class<?> expClass = Class.forName("kuke.kukeExpPond.placeholder.KepExpansion");
                Constructor<?> cons = expClass.getConstructor(KukeExpPond.class, PondManager.class, PlayerStateManager.class, DataStore.class);
                Object exp = cons.newInstance(this, this.pondManager, this.playerStateManager, this.dataStore);
                Method register = expClass.getMethod("register");
                Boolean ok = (Boolean)register.invoke(exp);
                this.log.info("PlaceholderAPI expansion registered: " + (ok ? "ok" : "failed"));
            } catch (Throwable var8) {
                Throwable t = var8;
                this.log.warning("PlaceholderAPI expansion registration failed: " + t.getMessage());
            }
        }

        if (this.getCommand("kukeexppond") != null) {
            KepCommand executor = new KepCommand(this);
            this.getCommand("kukeexppond").setExecutor(executor);
            this.getCommand("kukeexppond").setTabCompleter(new KepTab(this));
        } else {
            this.log.warning("Command 'kukeexppond' not found in plugin.yml");
        }

    }

    public void onDisable() {
        if (this.rewardManager != null) {
            this.rewardManager.stop();
        }

        if (this.xpManager != null) {
            this.xpManager.stop();
        }

        if (this.uiManager != null) {
            this.uiManager.stop();
        }

        if (this.effectsManager != null) {
            this.effectsManager.stop();
        }

        if (this.icePreventionManager != null) {
            this.icePreventionManager.stop();
        }

        if (this.dataStore != null) {
            this.dataStore.save();
        }

        if (this.log != null) {
            this.log.info("KukeExpPond disabled.");
        }

    }

    public HookManager getHooks() {
        return this.hooks;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public ConfigUpdater getConfigUpdater() {
        return this.configUpdater;
    }

    public SelectionManager getSelectionManager() {
        return this.selectionManager;
    }

    public PondManager getPondManager() {
        return this.pondManager;
    }

    public PlayerStateManager getPlayerStateManager() {
        return this.playerStateManager;
    }

    public void rebuildPonds() {
        if (this.pondManager != null) {
            this.pondManager.reload();
        }

        if (this.rewardManager != null) {
            this.rewardManager.rebuild();
        }

        if (this.xpManager != null) {
            this.xpManager.rebuild();
        }

        if (this.uiManager != null) {
            this.uiManager.rebuild();
        }

        if (this.effectsManager != null) {
            this.effectsManager.rebuild();
        }

    }

    private void initBStats() {
        try {
            // Try to use Bukkit's built-in Metrics class (available in most modern servers)
            Class<?> metricsClass = Class.forName("org.bukkit.plugin.java.metrics.Metrics");
            Object metrics = metricsClass.getConstructor(JavaPlugin.class).newInstance(this);
            this.log.info("bStats metrics initialized using Bukkit built-in Metrics");
        } catch (Throwable t1) {
            try {
                // Fallback to external bStats library if available
                Class<?> metricsClass = Class.forName("org.bstats.bukkit.Metrics");
                metricsClass.getConstructor(JavaPlugin.class, int.class).newInstance(this, 27598);
                this.log.info("bStats metrics initialized using external library (id=27598)");
            } catch (Throwable t2) {
                this.log.info("bStats: no metrics library found, metrics disabled");
            }
        }
    }
}
