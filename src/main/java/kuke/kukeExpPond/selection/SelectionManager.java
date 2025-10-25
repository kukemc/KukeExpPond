package kuke.kukeExpPond.selection;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    private final Map<UUID, Selection> selections = new HashMap<>();

    public Selection get(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), id -> new Selection());
    }
}