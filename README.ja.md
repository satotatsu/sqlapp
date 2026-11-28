# sqlapp

**Database Migration, Schema Management and Data Engineering Framework**

sqlappは共通Schemaモデルで、スキーマの調査、変更確認、検証付きデータ移行、SQLベースのデータ加工をつなぐOSSフレームワークです。
主な入口は`com.sqlapp.db` Gradle Pluginです。Java APIの組み込みや、[Swingの移行支援GUI](sqlapp-swing/README.md)による計画確認もできます。

[English](README.md) · [公式ドキュメント（日本語）](https://satotatsu.github.io/sqlapp/ja/) ·
[Live Demo](https://satotatsu.github.io/sqlapp/ja/demos.html) · [対応DB・検証の根拠](docs/compatibility.md)

## できること

| 目的 | 得られるもの・ガイド |
|---|---|
| DB構造を理解・共有する | Schema XML、HTMLリファレンス、クリック可能なSVG ER図、DDL。[ガイド](docs/schema/html-documentation.md) |
| スキーマ変更を確認・管理する | 差分SQL、バージョン管理されたSQL移行。[ガイド](docs/gradle-plugin/versioned-migrations.md) |
| 関連するデータを移行する | 依存順の転送、明示設定による再開、行数・ハッシュ検証、修復計画。[ガイド](docs/migration/README.md) |
| データを加工・受け渡しする | Import/Export、形式変換、DuckDBのSQLモデルとテスト。[ガイド](docs/gradle-plugin/migration-models.md) |
| テストデータを生成する | Excelルール、ルックアップ、JDBCバッチ挿入。[ガイド](docs/data/test-data-generation.md) |
| GradleでDB操作を自動化する | コマンドを実行するタスク。[タスクリファレンス](docs/gradle-plugin/task-reference.md) |

Dialectの実装と、特定バージョンでの実機検証は異なります。[Compatibility Matrix](docs/compatibility.md)で制約と検証状況を確認してください。
Swing GUIは移行支援ツールとして紹介しています。対応範囲はモジュールのREADMEを参照してください。

## 外部DBなしで試す

このリポジトリでJava 25とGradle Wrapperを使用します。

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

Windows PowerShellでは次のコマンドです。

```powershell
.\gradlew.bat --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

`build/docs/demo/index.html`を開くと、顧客・注文のHTML、ER図、変更前後のスキーマ、差分SQL、移行検証結果を確認できます。
架空データ5行を独立したHSQLDB間で転送し、移行先の値だけを変更して不一致検出も確認します。
生成した差分SQLと修復は実行しません。[デモツアー](docs/getting-started/demo-tour.md)に詳細があります。

## 自分のプロジェクトでGradle Pluginを使う

リリースは[Plugin Portal](https://plugins.gradle.org/plugin/com.sqlapp.db)で確認してください。
確認できた公開版は0.30.0です。ソースの0.80.0を公開済み版として案内していません。
現在のタスクAPIが旧リリースにもあると仮定せず、使用するリリースの資料を確認してください。
以下は現在のソースに対応する設定例で、バージョンを実際に利用できる組み合わせへ置換する必要があります。

```groovy
plugins {
    id 'java'
    id 'com.sqlapp.db' version '<sqlapp-version>'
}
repositories { mavenCentral() }
dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:<sqlapp-version>'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

DialectとJDBCドライバーは役割が異なり、JDBCワークフローでは両方が必要です。
接続情報には環境変数を使用し、メタデータだけを出力する場合は`dumpRows = false`を指定します。
入力を生成するタスクとの`dependsOn`も明示してください。
[Gradle入門](docs/gradle-plugin/getting-started.md)にXML出力からHTML生成までの設定があります。

## ドキュメント・配布・開発

- [ドキュメント一覧](docs/README.md)：用途別のガイド。
- [Java API](docs/getting-started/java-api.md)：Schema、XML、SQL生成。
- [成果物と依存関係](docs/artifacts.md)：ライブラリの選択と配布形式。
- [Swing配布物の作成](sqlapp-swing/README.md)：Windows用ポータブルZIPとインストーラー。公開済みバイナリの存在はこの手順だけでは保証しません。
- [開発・テスト](docs/development/build-and-test.md)：Java 25とリポジトリのWrapperで`./gradlew build`を実行します。
- [サイトのメンテナンス](site/README.md)：Node.js、翻訳、デモ、Pages公開。
- [CONTRIBUTING](CONTRIBUTING.md)：貢献と検証のルール。

ライセンスは[GNU LGPL version 3以降](LICENSE.md)です。Copyright © 2007–2026 Tatsuo Satoh.
