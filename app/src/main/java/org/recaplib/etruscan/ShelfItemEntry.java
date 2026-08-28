package org.recaplib.etruscan;

public class ShelfItemEntry {
    public final String shelf;
    public final String position;
    public final String item;

    public ShelfItemEntry(String shelf, String position, String item) {
        this.shelf = shelf;
        this.position = position;
        this.item = item;
    }

    @Override
    public String toString() {
        return "Shelf " + shelf + " | Pos " + position + " | Item " + item;
    }
}
