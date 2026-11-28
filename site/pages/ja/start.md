---
title: はじめる
description: 外部DBなしでGradleからsqlappを試し、利用するプラグインのリリースを確認します。
---
# はじめる

## 現在の実装を試す

リポジトリをチェックアウトし、Java 25と付属のGradle Wrapperを使います。

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

Windows PowerShellの場合：

```powershell
.\gradlew.bat --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

`build/docs/demo/index.html`を開きます。HTML・ER図、変更前後のスキーマ、差分SQL、
実際のHSQLDB移行・検証レポートを確認できます。架空データとインメモリDBを使用するため、
外部の接続認証情報は不要です。初回ビルドでは依存関係をダウンロードします。

[生成した成果物をこのサイトで閲覧](demos.md)することもできます。

## Gradle Pluginを自分のワークフローに追加する

[Gradle設定ガイド](docs/gradle-plugin/getting-started.md)では、DialectとJDBCの依存関係、
環境変数による接続設定、メタデータだけのXML出力、HTML生成を説明しています。
[タスクリファレンス](docs/gradle-plugin/task-reference.md)で入出力とDBへの影響を確認できます。

::: warning ソースのドキュメントと公開版
このガイドは現在のソースを説明しています。ソースのバージョン`0.80.0`は公開済みプラグインとして確認できていません。
[Plugin Portal](https://plugins.gradle.org/plugin/com.sqlapp.db)の掲載版は`0.30.0`です。
現在のタスクAPIが旧版にあると仮定しないでください。バージョンのプレースホルダーは、
説明された機能を実際に提供するリリースへ置換します。上のデモはチェックアウトした実装を直接実行します。
:::

組み込み用途では[Java API](docs/getting-started/java-api.md)と[成果物の選択](docs/artifacts.md)を参照してください。
DBに接続する前に[対応状況と検証根拠](docs/compatibility.md)を確認してください。
