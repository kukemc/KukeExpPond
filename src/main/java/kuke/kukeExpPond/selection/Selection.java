package kuke.kukeExpPond.selection;

import org.bukkit.Location;

public class Selection {
    private String worldName;
    private Location pos1;
    private Location pos2;

    public String getWorldName() {
        return worldName;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos1(Location loc) {
        if (loc == null) return;
        setWorld(loc.getWorld() == null ? null : loc.getWorld().getName());
        this.pos1 = loc.clone();
    }

    public void setPos2(Location loc) {
        if (loc == null) return;
        setWorld(loc.getWorld() == null ? null : loc.getWorld().getName());
        this.pos2 = loc.clone();
    }

    private void setWorld(String name) {
        if (this.worldName == null) {
            this.worldName = name;
            return;
        }
        if (name != null && !name.equals(this.worldName)) {
            // Cross-world selection not allowed; reset previous
            this.pos1 = null;
            this.pos2 = null;
            this.worldName = name;
        }
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && worldName != null;
    }
}