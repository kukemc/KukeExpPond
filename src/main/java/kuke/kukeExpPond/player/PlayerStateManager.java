package kuke.kukeExpPond.player;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerStateManager {
    private final Map<UUID, String> currentPond = new HashMap<UUID, String>();
    private final Map<UUID, Location> lastSafeLoc = new HashMap<UUID, Location>();

    public String getCurrentPond(UUID id) { return currentPond.get(id); }

    public void setCurrentPond(UUID id, String pondName) {
        if (pondName == null) {
            currentPond.remove(id);
        } else {
            currentPond.put(id, pondName);
        }
    }

    public Location getLastSafeLoc(UUID id) { return lastSafeLoc.get(id); }

    public void setLastSafeLoc(UUID id, Location loc) {
        if (loc == null) {
            lastSafeLoc.remove(id);
        } else {
            lastSafeLoc.put(id, loc.clone());
        }
    }
}