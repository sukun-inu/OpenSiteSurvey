# 設定・保存仕様

[← README.ja.md に戻る](../README.ja.md)

---

## 設定・保存仕様

アプリ初回起動時に `~/.opensitesurvey/` 配下へ以下を作成します。

- `settings.json`: 永続設定
- `scan-log.db`: 長期スキャンログ(SQLite)

`settings.json` の主要項目(既定値):

| Key | Default | 説明 |
| --- | --- | --- |
| `rssiThresholdDbm` | `-75` | RSSIしきい値アラートの閾値(dBm) |
| `channelCongestionThreshold` | `50.0` | チャネル混雑度アラート閾値 |
| `rssiAlertEnabled` | `true` | RSSIしきい値アラートON/OFF |
| `rogueApAlertEnabled` | `true` | 未信頼AP出現アラートON/OFF |
| `newSsidAlertEnabled` | `true` | 新規SSIDアラートON/OFF |
| `channelCongestionAlertEnabled` | `true` | 混雑度アラートON/OFF |
| `windowsNotificationsEnabled` | `true` | Windows通知ON/OFF |
| `defaultPingHost` | `""` | Site Surveyで使う既定Ping先 |
| `language` | `"ja"` | UI言語(`ja`/`en`) |
| `scanLogIntervalMillis` | `2000` | 長期ログ書き込み間隔(ms) |
| `restApiEnabled` | `false` | REST API有効/無効(ループバックのみ) |
| `restApiPort` | `8787` | REST APIの待受ポート番号 |

`scan-log.db` の主要テーブル:

- `scan_samples` (Historyタブが参照する長期スキャンログ)
- `survey_points_log` (将来拡張向けに確保済み)
- `alert_log` (将来拡張向けに確保済み)
