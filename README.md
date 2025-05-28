# Etruscan

Etruscan is an Android app designed to support high-density off-site library storage workflows in conjunction with the LAS inventory management system. This app allows users to scan tray and item barcodes and save the results to a file which can be uploaded to be processed by LAS.

It was developed in partnership with AI.

---

## Features

- **Support key operational processes**, including Refile and Tray-to-shelf activities
- **Scan and capture item tray and shelf barcodes**
- **Barcode format validation**: Ensure that tray and item barcodes meet required formats
- **Scan count**: Track on-screen how many items have been scanned in the session.
- **Write captured data to files formatted for LAS processing**, including `refile.dat` and `t2shelf.dat`
- **Upload to LAS capabilities**, securely depositing files on the LAS server for processing
- **Offline Logging**: Operates offline and stores locally until upload is possible
- **Scan Logging**: Keeps a log of all scans for diagnostics or audit

---

## Installation

### Prerequisites

- Zebra ET45 device (or compatible Android 11+ barcode scanner device)
- Android Studio (for development)
- Java 8 or higher
- DataWedge profile enabled for barcode input

### From Android Studio

1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_ORG/etruscan.git
   cd etruscan

2. Open the project in Android Studio:

    File > Open > [select etruscan directory]

3. Build the project:

   Select your device/emulator.

   Click Run or use Build > Build Bundle/APK > Build APK.

4. Install the APK on your device if not using Android Studio:

    adb install app/build/outputs/apk/debug/app-debug.apk

---

## Configuration

DataWedge (for Zebra devices)

Ensure DataWedge is set up to send barcode data to the app:

    Open DataWedge.

    Create a new profile or modify an existing one.

    Associate it with org.recaplib.etruscan.

    Enable Intent Output:
        Action: com.zebra.scanner.ACTION
        Category: DEFAULT
        Delivery: Broadcast Intent

    Enable Keystroke Output only if needed (usually not).


---

## Usage

1. Refile Mode

   From the dashboard, tap Refile Items.

   Scan a tray barcode.

   Scan item barcodes. Each pairing is logged to refile.dat.

   Use the dashboard to upload to LAS when Wi-Fi is available.

2. Tray-to-Shelf Mode

   Tap Tray to Shelf.

   Scan a shelf barcode.

   Enter a numeric shelf position.

   Scan a tray barcode to complete the association.

   Repeat or upload later from the dashboard.

3. Upload to LAS

   Tap Upload to LAS.

   Enter your credentials.

   The app will:

        Upload refile.dat and t2shelf.dat via SFTP.

        Archive each file locally with a timestamp suffix.

---

## Data Storage

Files are saved on the Android device in: /Documents/Etruscan/
refile.dat
t2shelf.dat
scanlog.txt
refile-<timestamp>.dat (archived)
t2shelf-<timestamp>.dat (archived)

---

## License

This project is licensed under the [MIT License].
