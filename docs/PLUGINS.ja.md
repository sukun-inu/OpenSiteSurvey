# プラグイン開発

[← README.ja.md に戻る](../README.ja.md)

---

## プラグイン開発

`com.opensitesurvey.tool.plugin.OpenSiteSurveyPlugin` インターフェースを実装したクラスを持つ`.jar`を作成し、`META-INF/services/com.opensitesurvey.tool.plugin.OpenSiteSurveyPlugin` に実装クラスの完全修飾名を1行書いて同梱、`~/.opensitesurvey/plugins/` に配置してアプリを再起動すると読み込まれます(標準の`ServiceLoader`規約)。

```java
package com.example;

import com.opensitesurvey.tool.model.ScanSnapshot;
import com.opensitesurvey.tool.plugin.OpenSiteSurveyPlugin;

public class MyPlugin implements OpenSiteSurveyPlugin {
    @Override
    public String name() {
        return "My Plugin";
    }

    @Override
    public void onScanSnapshot(ScanSnapshot snapshot) {
        // スキャン毎にWLANポーラーのバックグラウンドスレッドから呼び出されます。
        // JavaFX Application Threadではないため、UI操作は行わないでください。
        // 処理は短時間で完了させてください(ブロックすると他プラグイン/次スキャンが遅延します)。
    }
}
```

1台のプラグインの読み込み失敗・例外は他のプラグインやアプリ本体には影響しません(stderrへログ出力されるのみ)。読み込み済みプラグインの一覧は ヘルプ > 読み込み済みプラグイン から確認できます。
