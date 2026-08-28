package org.recaplib.etruscan;

public class BarcodeUtils {

    public static boolean validateTray(String tray) {
        return tray.matches("[A-Z]{2}\\d{5,6}");
    }

    public static boolean validateShelf(String shelf) {
        return shelf.matches("\\d{5,6}");
    }

    public static boolean validatePosition(String pos) {
        return pos.matches("\\d{2}");
    }

    public static String cleanItemBarcode(String raw) {
        if (raw == null) return null;

        // Codabar: possibly surrounded by non-digit start/stop characters
        if (raw.matches("^[A-D][0-9]{6,20}[A-D]$")) {
            // Remove first and last characters
            return raw.substring(1, raw.length() - 1);
        }

        // Codabar without start/stop: just digits, 6–20 digits
        if (raw.matches("^[0-9]{6,20}$")) {
            return raw;
        }

        // Code 39: allow letters, digits, and -.$/+% (but we'll treat it loosely)
        if (raw.matches("^[A-Z0-9-\\. $/+%]{6,20}$")) {
            return raw;
        }

        // 6-character alphanumeric
        if (raw.matches("^[A-Z0-9]{6}$")) {
            return raw;
        }

        // If it doesn't match any known pattern, return null to indicate invalid
        return null;
    }
}
