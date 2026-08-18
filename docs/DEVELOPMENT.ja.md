# 開発・テスト・配布

[← README.ja.md に戻る](../README.ja.md)

---

## テスト

```
.\mvnw.cmd test
```

---

## 配布用exeビルド

外部インストール不要(同梱の `.tools/jdk21` の `jpackage` を使用)で、単体で実行できるWindowsアプリイメージ(exe)を `dist/<version>/` 配下に生成します。

```
.\build-release.ps1
```

- Maven本ビルド(テスト込み)→ 依存込みfat jar化(`maven-shade-plugin`)→ `jpackage --type app-image` の順で実行します。
- 生成物: `dist/<version>/OpenSiteSurvey/OpenSiteSurvey.exe`(Java実行環境同梱、そのまま配布可能)、および同ディレクトリに配布用zipも作成されます。
- `dist/` はバージョンごとにサブディレクトリを分けるビルド専用の配布物置き場です(gitでは追跡しません)。再ビルドすると対象バージョンのフォルダのみ作り直されます。
- テストをスキップしたい場合: `.\build-release.ps1 -SkipTests`
- `.msi` インストーラも生成したい場合: `.\build-release.ps1 -Msi`。jpackageのMSI生成には WiX Toolset (`candle.exe`/`light.exe`) が必要ですが、システムへのインストールは不要です — 初回実行時にWiX v3.11のポータブル版バイナリを自動ダウンロードし、`.tools/wix/` 配下に配置します(JDK/Mavenと同じ「システムにインストールせずプロジェクトローカルに同梱する」方針)。ダウンロードのみネットワーク接続が必要です。
- `PdfReportGeneratorTest`(1件)は環境によって、生成直後の一時PDFファイルをOS側(Windows Defenderのリアルタイム保護やSearch Indexer等)が瞬間的にロックし、JUnitの`@TempDir`削除に失敗して`BUILD FAILURE`になることがあります(コード側のリソースリークではなく、開発機のファイルロックに起因する既知の環境要因です)。発生した場合は`-SkipTests`を使うか、`.\mvnw.cmd test`を単体で再実行すれば通ることが多いです。

主なテスト対象:

- WLAN/セキュリティ: `Dot11SsidTest`, `SecurityClassifierTest`
- チャネル設計: `ChannelPlannerTest`, `BssLoadParserTest`, `ChannelUtilTest`, `ChannelWidthParserTest`
- Wi-Fi7/MLO検出: `Wifi7ParserTest`
- アラート: `AlertEngineTest`
- Ping/Traceroute: `PingProbeTest`, `TracerouteProbeTest`, `HopRttHistoryTest`, `ThroughputProbeTest`
- Site Survey/レポート: `IdwInterpolatorTest`, `KrigingInterpolatorTest`, `NaturalNeighborInterpolatorTest`, `ApPositionEstimatorTest`, `ApPlacementAdvisorTest`, `CoverageRequirementEvaluatorTest`, `RoamingAnalyzerTest`, `GeoPackageExporterTest`, `SurveyProjectStoreTest`, `PdfReportGeneratorTest`
- GPS/Wi-Fi歩行ヒートマップ: `GeoReferenceTest`, `PathSamplerTest`, `GpsProbeTest`, `WifiPositionEstimatorTest`
- REST API: `ApiServerTest`
- Plugin API: `PluginManagerTest`
- ダッシュボード/履歴補助: `RssiHistoryStoreTest`, `CategoricalColorPaletteTest`, `CsvUtilTest`, `VendorLookupTest`
- 設定/国際化: `AppConfigStoreTest`, `MessagesTest`

---

## プロジェクト構成

```
com.opensitesurvey.tool
├── App.java                 JavaFXエントリポイント + MenuBar
├── Launcher.java             fat jar/exe用のプレーンなmainエントリポイント(--headless判定含む)
├── HeadlessRunner.java       GUIなしのコンソールスキャンループ(--headless)
├── i18n/                    メッセージバンドル切替
├── wlan/                    Wlanapi.dll のJNAバインディングとポーリング
├── model/                   AP/スキャン/サーベイ点のデータモデル
├── util/                    チャネル変換・色スケール・ノイズ推定・MACベンダー(OUI)検索
├── security/                IE解析によるセキュリティ種別判定 + Security Audit UI
├── channel/                 チャネル混雑度スコア・チャネル幅(HT/VHT IE)検出 + Channel Planning UI
├── wifi7/                   EHT Capabilities/Operation・Multi-Link要素(Wi-Fi7/MLO)の検出
├── alert/                   アラートルール・エンジン・信頼済みAP管理・設定ダイアログ・Windows通知
├── ping/                    ping/tracert 実行・パース
├── gps/                     GPS位置取得(PowerShell/GeoCoordinateWatcher)・緯度経度⇔画像座標キャリブレーション・移動距離間引き
├── report/                  HTML/PDFサイトサーベイレポート生成
├── ui/dashboard/            ライブダッシュボードUI
├── ui/survey/               サイトサーベイ・ヒートマップ・カバレッジホール・比較モードUI
├── ui/history/              長期ログ閲覧UI(プリセット/カスタム期間検索)
├── ui/ping/                 Traceroute監視UI
├── api/                     REST API(JDK標準com.sun.net.httpserver、ループバック限定)
├── plugin/                  Plugin API(ServiceLoaderベースのプラグイン読み込み・通知)
└── persistence/             設定・長期ログ(SQLite)・サーベイプロジェクト・CSV/JSON出力
```
