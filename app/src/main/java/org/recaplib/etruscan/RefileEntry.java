package org.recaplib.etruscan;

public class RefileEntry {
    public final String containerType; // "Tray" or "Shelf"
    public final String containerId;
    public final String item;

    public RefileEntry(String containerType, String containerId, String item) {
        this.containerType = containerType;
        this.containerId = containerId;
        this.item = item;
    }

    @Override
    public String toString() {
        return containerType + " " + containerId + " | Item " + item;
    }
}
