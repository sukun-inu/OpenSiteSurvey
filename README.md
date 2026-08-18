# OpenSiteSurvey

A Wi-Fi site survey and monitoring tool for Windows, written in Java (JavaFX).

It drives the Windows Native Wifi API (`Wlanapi.dll`) directly through JNA, reading SSID,
BSSID, channel, RSSI, link quality, PHY type and security type from the real adapter in
real time. Built around the day-to-day work of an infrastructure engineer: site surveys,
post-incident verification, channel planning, security audits and continuous monitoring.

The UI ships in both Japanese and English. 日本語の README は [README.ja.md](README.ja.md)。

## Highlights

- **Wi-Fi 7 (802.11be) / MLO detection from raw beacon IEs.** EHT Capabilities, EHT
  Operation and Multi-Link elements are parsed directly out of beacons and probe
  responses, so an AP can be identified as 802.11be even when the driver's own PHY-type
  reporting has not caught up.
- **Site survey with real interpolation.** IDW, Ordinary Kriging (spherical variogram)
  and Natural Neighbor (Sibson approximation by discrete sampling). Coverage-hole
  highlighting, before/after difference heatmaps against a second project,
  RSSI-weighted AP position estimation, and a greedy search that proposes up to three
  new AP placements to close the holes.
- **Walking heatmaps, indoors and out.** GPS mode calibrates two or three points against
  real latitude/longitude — similarity transform for two points, least-squares affine
  for three or more, Haversine great-circle distance for the pixel-to-metre scale.
  Wi-Fi mode needs no calibration at all and back-solves the current position from RSSI
  against estimated AP locations.
- **Security audit.** RSN/WPA information elements are parsed to classify
  Open / WEP / WPA / WPA2 / WPA2-WPA3 mixed / WPA3, with OUI vendor lookup so a known
  SSID suddenly served by an unexpected vendor stands out.
- **Channel planning.** Congestion scoring from RSSI, plus BSS Load utilisation where
  the AP advertises it. 5 GHz is split along the Japanese sub-bands (J52 / J53 / J56).
  HT40 / VHT80 / VHT160 widths are detected from beacon IEs and weighted into the score.
- **Headless / CLI mode.** `--headless` runs the scan loop with no JavaFX UI at all,
  writing to the same SQLite log as the GUI — for monitor-less servers and remote hosts.
- **Optional REST API.** Loopback-only and off by default. Four read-only JSON endpoints
  for external automation.
- **Plugin API.** Drop a `.jar` implementing `OpenSiteSurveyPlugin` into
  `~/.opensitesurvey/plugins/`; it is picked up via `ServiceLoader` at next start.
- **Exports.** CSV, JSON, GeoPackage (`.gpkg`, opens in QGIS and friends), HTML and PDF
  reports.

Rule-based alerts (RSSI drop, untrusted AP, new SSID, rising congestion) with Windows
notifications, SQLite history over preset or custom date ranges, and a traceroute view
with per-hop RTT statistics are also included. Full list in
[docs/FEATURES.ja.md](docs/FEATURES.ja.md).

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
