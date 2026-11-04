# Preset Assets Provenance

This directory contains preset offline assets bundled with the BlockAds application.

### `geoip_ipv4.bin` (Dynamic Download)
- **Distribution**: No longer bundled in the APK to maintain lightweight package size (F-Droid & Play Store friendly). Downloaded on-demand at first app launch and refreshed weekly via WorkManager.
- **Workflow**: Auto-built and released weekly via GitHub Actions (`.github/workflows/update_geoip.yml`).
- **Source Data**: [sapics/ip-location-db](https://github.com/sapics/ip-location-db) (`user-country/user-country-ipv4.csv`).
- **License**: [PDDL 1.0 (Public Domain Dedication and License)](https://opendatacommons.org/licenses/pddl/1-0/) / CC0.
- **Generator Script**: [`scripts/generate_geoip.py`](../../../scripts/generate_geoip.py).
- **Format**: Binary array of 10-byte records (`>II2s`):
  - 4 bytes uint32: start IP
  - 4 bytes uint32: end IP
  - 2 bytes ASCII: 2-letter ISO country code

### `world_map_polygons.json`
- **Purpose**: Low-poly country boundary polygons for the offline traffic destination world map visualization.
- **Source Data**: Natural Earth 1:110m Cultural Vectors (Admin 0 – Countries).
- **License**: [Public Domain](https://www.naturalearthdata.com/about/terms-of-use/).

### `browsers.txt`
- **Purpose**: Known Android browser package names used for cosmetic rule injection and browser-specific network routing.
- **License**: MIT (BlockAds project).

### `default_filters.json`
- **Purpose**: Initial filter list catalog definitions and URLs seeded on first application run.
- **License**: MIT (BlockAds project).
