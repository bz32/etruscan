# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),  
and this project adheres to [Semantic Versioning](https://semver.org/).

---

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