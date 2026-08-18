# OpenSiteSurvey

A tool for finding the weak Wi-Fi spots in a building, as a colour-coded map.

Load a floor plan, walk around taking measurements at a few points, and signal strength
gets painted across the map. It will tell you **where coverage is thin, and where to put
another access point to fill it in**. With GPS it works outdoors too; indoors, a handful
of manual readings is enough to let it track you as you walk.

It can also be left running as a watchdog — signal drops, unfamiliar access points
appearing, channels getting congested, all of it raises an alert.

Windows only. It reads from the Wi-Fi adapter directly, so there is no other-OS version.

日本語版: [README.ja.md](README.ja.md)

## What it does

- Real-time scan of every access point in range: SSID, channel, signal, security type
- Coverage heatmaps over a floor plan, with real interpolation (IDW, Ordinary Kriging,
  Natural Neighbor) rather than simple shading
- Suggests where to add access points to close the gaps, and checks coverage against
  per-use thresholds (voice, video, data)
- Detects Wi-Fi 7 (802.11be) and MLO from raw beacon data, even when the driver has not
  caught up
- Security audit of encryption in use, with hardware vendor lookup
- Channel planning, with the Japanese 5 GHz sub-bands handled separately
- Alerts, long-term history, traceroute, headless mode, a read-only REST API and plugins
- Exports to CSV, JSON, GeoPackage (`.gpkg`, opens in QGIS), HTML and PDF

The full list is in [docs/FEATURES.ja.md](docs/FEATURES.ja.md).

## What this is not

**It is not a true RF spectrum analyser.** An ordinary client NIC does not expose raw RF
waveforms through Windows, so no software can produce a measured spectrum without
dedicated hardware. The "pseudo-spectrum" view and the channel congestion score are both
synthesised from RSSI. The remaining limits are in
[docs/LIMITATIONS.ja.md](docs/LIMITATIONS.ja.md).

## Requirements

- Windows 10 or 11, with a Wi-Fi adapter
- Nothing to install separately: JDK 21 and Maven are bundled under `.tools/`

## Build and run

```
.\mvnw.cmd javafx:run
```

### Headless / CLI

```
java -jar target\open-site-survey-<version>-shaded.jar --headless
java -jar target\open-site-survey-<version>-shaded.jar --headless --interval 5000
```

`--interval <ms>` sets the polling interval (default 2000 ms). Headless writes to the
same `~/.opensitesurvey/scan-log.db` as the GUI, so logs collected on a headless box can
be browsed afterwards from the GUI's History tab.

## Documentation

| | |
|---|---|
| [docs/FEATURES.ja.md](docs/FEATURES.ja.md) | Full feature reference |
| [docs/CONFIGURATION.ja.md](docs/CONFIGURATION.ja.md) | Settings and storage layout |
| [docs/LIMITATIONS.ja.md](docs/LIMITATIONS.ja.md) | Technical limits and known issues |
| [docs/PLUGINS.ja.md](docs/PLUGINS.ja.md) | Writing a plugin |
| [docs/DEVELOPMENT.ja.md](docs/DEVELOPMENT.ja.md) | Tests, packaging, project layout |

Detailed documentation is currently Japanese-only.

## Licence

MIT. Copyright (c) 2026 Hibiki Suzuki. See [LICENSE](LICENSE).

Licences for the bundled dependencies (OpenJFX, JNA, Jackson, SQLite JDBC, OpenPDF) are
listed inside the app under Help > About Third-Party Licenses.
