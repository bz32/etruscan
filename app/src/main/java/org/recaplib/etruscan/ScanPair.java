package org.recaplib.etruscan;

public class ScanPair {
    public final String tray;
    public final String item;

    public ScanPair(String tray, String item) {
        this.tray = tray;
        this.item = item;
    }

    @Override
    public String toString() {
        return tray + " | " + item;
    }
}