# OpenSiteSurvey

Java (JavaFX) 製の Wi-Fi サイトサーベイ/監視ツールです。
Windows の Native Wifi API (`Wlanapi.dll`) を JNA で直接叩き、実機の Wi-Fi アダプタから
SSID / BSSID / チャネル / RSSI / リンク品質 / PHY種別 / セキュリティ種別をリアルタイムに取得します。
インフラエンジニアの日常業務(サイト調査・障害後検証・チャネル設計・セキュリティ監査・継続監視)を想定したフル装備版です。
UIは日本語/英語(設定画面から切り替え、再起動後に反映)に対応しています。

> 仕様更新日: 2026-07-12(UI/UX全面刷新 — エンタープライズ ネットワーク管理コンソール風シェル版)

English README: [README.md](README.md)

## できること（要約）

- Windows Native Wifi API を JNA で直叩きし、SSID / BSSID / チャネル / RSSI / リンク品質 / PHY種別 / セキュリティ種別をリアルタイム取得
- ビーコンの生IEから **Wi-Fi 7 (802.11be) / MLO** を直接判定（ドライバのPHY種別表示が未対応でも検出できる場合がある）
- **Site Survey**: フロアプラン上での計測、IDW / Ordinary Kriging（球形バリオグラム） / Natural Neighbor による補間、カバレッジホール表示、Before/After比較、AP位置推定、貪欲法によるAP増設候補の提案
- **GPS / Wi-Fi 歩行ヒートマップ**: GPSは2〜3点キャリブレーション（相似変換／最小二乗アフィン変換、Haversineで実距離換算）、Wi-Fi方式はキャリブレーション不要
- **Security Audit**: RSN/WPA IE を解析して Open / WEP / WPA / WPA2 / WPA2-WPA3混在 / WPA3 を判定、OUIからベンダー表示
- **Channel Planning**: 混雑度スコア（RSSI + BSS Load）、5GHzは J52/J53/J56 で分割、HT40/VHT80/VHT160 の幅をビーコンIEから検出
- **Alerts / History / Traceroute**、**ヘッドレスCLI**、**REST API**（既定OFF・ループバックのみ）、**Plugin API**（ServiceLoader）
- エクスポート: CSV / JSON / GeoPackage(.gpkg) / HTML / PDF

各機能の詳細は [docs/FEATURES.ja.md](docs/FEATURES.ja.md) を参照してください。

## これは何ではないか

**真のRFスペクトラムアナライザではありません。** 通常のクライアントNICはWindows経由で生RF波形を提供しないため、専用ハードウェアなしに実測スペクトラムは取得できません。「疑似スペクトラム表示」と「混雑度スコア」はいずれもRSSIから合成・推定した表示です。その他の制約は [docs/LIMITATIONS.ja.md](docs/LIMITATIONS.ja.md) にまとめています。

## 必要環境

- Windows 10/11 + Wi-Fi アダプタ
- ビルド/実行に外部インストールは不要です。JDK 21 と Maven をプロジェクトローカルの `.tools/` 配下に同梱しています。

## ビルド・実行

```
.\mvnw.cmd javafx:run
```

### ヘッドレス/CLIモード

パッケージ済みjar/exeに `--headless` を渡すと、GUIなしでスキャンループのみを実行します(Ctrl+Cで停止)。

```
java -jar target\open-site-survey-<version>-shaded.jar --headless
java -jar target\open-site-survey-<version>-shaded.jar --headless --interval 5000
```

- `--interval <ms>`: ポーリング間隔(既定2000ms)。
- 通常のGUI版と同じ `~/.opensitesurvey/scan-log.db` に書き込むため、ヘッドレス機で収集したログをGUI版のHistoryタブから閲覧できます。
- 検出APの一覧をコンソールへ1スキャンごとに出力します。

## ドキュメント

| | |
|---|---|
| [docs/FEATURES.ja.md](docs/FEATURES.ja.md) | 機能の詳細 |
| [docs/CONFIGURATION.ja.md](docs/CONFIGURATION.ja.md) | 設定・保存仕様 |
| [docs/LIMITATIONS.ja.md](docs/LIMITATIONS.ja.md) | 技術的な制約と既知の制限 |
| [docs/PLUGINS.ja.md](docs/PLUGINS.ja.md) | プラグイン開発 |
| [docs/DEVELOPMENT.ja.md](docs/DEVELOPMENT.ja.md) | テスト・配布用exeビルド・プロジェクト構成 |

## ライセンス

MIT License。Copyright (c) 2026 Hibiki Suzuki。詳細は [LICENSE](LICENSE) を参照してください。

同梱している依存ライブラリ(OpenJFX / JNA / Jackson / SQLite JDBC / OpenPDF)のライセンス表記は、アプリ内の Help > About Third-Party Licenses から確認できます。
