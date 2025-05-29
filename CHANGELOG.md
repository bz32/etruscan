# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),  
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [v1.3] - 2025-05-28
### Changed
- Renamed the app from **RefileApp** to **Etruscan**
- Renamed package from `com.example.refileapp` to `org.recaplib.etruscan`
- Updated file structure and Android package declarations to reflect new namespace
- Refactored DataWedge integration for improved barcode scanning reliability

### Added
- Support for uploading `t2shelf.dat` in addition to `refile.dat`
- Automatic renaming of uploaded files with timestamp suffixes (e.g., `refile-20250528T1530.dat`)
- MIT License applied to the project
- New README documentation with build, install, and usage instructions

## [v1.2] - 2025-05-27
### Added
- Tray-to-shelf (TTS) operation workflow:
    - Scan shelf barcode
    - Input numeric shelf position
    - Scan tray barcode
- Writes TTS data in `t2shelf.dat` file, formatted for LAS processing

## [v1.1] - 2025-04-30
### Added
- Validation for tray barcode format: two letters followed by 5–6 numbers
- Validation for item barcode formats: Codabar, Code 39, and 6-character alphanumeric
- Automatic clearing of tray and item text displays after a delay
- Improved user feedback on invalid scans

## [v1.0] - 2025-04-30
### Added
- Initial working version of Etruscan app
- Start and end refile sessions
- Scan tray and item barcodes
- Write formatted refile records to `refile.dat` file in `/Documents/RefileApp`
- Display item scan count