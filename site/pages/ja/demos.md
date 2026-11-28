---
title: 実際の生成物
description: sqlappが生成したHTML、ER図、差分SQL、Schema XMLと移行検証レポートを閲覧できます。
---
# 実際の生成物

Pagesのビルド時にsqlappで再生成した成果物です。リポジトリ内の架空の顧客・注文Schema XMLと、
独立した2つのインメモリHSQLDBを使用します。外部DBや接続認証情報は不要です。
生成ページの相対参照と付属ファイルをまとめて公開します。

| 内容 | 実際の出力 |
|---|---|
| 3つのワークフロー | [生成した概要ページ](/sqlapp/demos/demo/index.html) |
| データベースリファレンス | [テーブル・列・制約・インデックス・DDL](/sqlapp/demos/offline-demo/index.html) |
| リレーション | [クリック可能なER図](/sqlapp/demos/offline-demo/relationships.html) |
| スキーマ変更 | [変更前](/sqlapp/demos/offline-change/before/index.html)、[変更後](/sqlapp/demos/offline-change/after/index.html)、[生成した差分SQL](/sqlapp/demos/offline-change/change.sql) |
| 移行結果 | [実行レポート](/sqlapp/demos/offline-migration/execution.json) |
| 検証 | [一致したデータ](/sqlapp/demos/offline-migration/verification-match.json)、[意図的な不一致](/sqlapp/demos/offline-migration/verification-mismatch.json) |
| Schema XML | [元のモデル](/sqlapp/demos/catalog.xml) |
| DuckDBのSQLモデル | [実際の集計CSV](/sqlapp/demos/data-engineering/customer-sales.csv)、[モデル・テストの結果](/sqlapp/demos/data-engineering/result.json)、[入力YAML](/sqlapp/demos/data-engineering/models.yaml) |
| 保存済みの大きなサンプル | [20テーブルの業務DBリファレンス](/sqlapp/docs/examples/gradle-html/index.html) |

差分SQLは確認用に生成するだけで、**実行しません**。移行では2テーブルの架空データ5行を転送します。
続いて移行先の顧客名を変更し、行数が一致していてもハッシュの不一致を検出します。修復は実行しません。
これはHSQLDB同士の転送例であり、異なるDB製品間の移行を検証するものではありません。

20テーブルのリファレンスは既存の保存済み成果物です。顧客・注文デモと異なりサイトのワークフローでは再生成しません。

Java 25とリポジトリのGradle Wrapperで再現できます。

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

Windowsでは`.\gradlew.bat`を使用します。[デモの読み方](docs/getting-started/demo-tour.md)、
[テストデータ生成](docs/data/test-data-generation.md)、[DuckDBのSQLモデル](docs/gradle-plugin/migration-models.md)も参照してください。
DuckDBの例では、独立した組み込み接続上で依存順に2つのSQLモデルを構築し、モデルのユニットテストを1件実行します。
顧客ごとの集計値を検証してからCSVに出力します。JSONはコマンドの実行結果から作成するデモ用の要約で、
バルク移行の検証レポート形式とは異なります。このギャラリーにはData Generatorの実行成果物はまだ含まれていません。

```shell
./gradlew --init-script site/demo.init.gradle demoSiteDataEngineering
```
