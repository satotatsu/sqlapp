---
layout: home
title: 複数のデータベースを、ひとつのフレームワークで。
description: GradleとJavaでスキーマ管理、検証付きデータ移行、SQLベースのデータ加工を自動化します。
hero:
  name: sqlapp
  text: 複数のデータベースを、ひとつのフレームワークで。
  tagline: データベース移行・スキーマ管理・データエンジニアリングの統合フレームワーク。
  actions:
    - theme: brand
      text: Gradleではじめる
      link: /ja/start
    - theme: alt
      text: 実際の生成物を見る
      link: /ja/demos
    - theme: alt
      text: GitHub
      link: https://github.com/satotatsu/sqlapp
features:
  - title: データベース移行
    details: 異種DB間の転送計画と型マッピングを確認し、関連する行を移行して検証結果を確認できます。保証範囲はワークフローごとに異なります。
    link: /ja/docs/migration/README
  - title: スキーマ管理
    details: 共通SchemaモデルとXMLでメタデータを保存。差分を確認し、DialectごとのSQLを生成して変更をバージョン管理します。
    link: /ja/docs/schema/README
  - title: データベースドキュメント
    details: 保存したモデルから、テーブル・列・DDLのHTMLとクリック可能なSVG ER図を生成して共有します。
    link: /ja/docs/schema/html-documentation
  - title: データのImport/Export
    details: 形式変換とトランザクション制御を明示して、ファイルとデータベースの間で行を受け渡します。
    link: /ja/docs/gradle-plugin/file-transfer
  - title: テストデータ生成
    details: ルールテンプレート、ルックアップ、JDBCバッチ挿入を使って関連するテストデータを作成します。
    link: /ja/docs/data/test-data-generation
  - title: データエンジニアリング
    details: DuckDB上のテーブルやビューをSQLモデルで定義し、依存関係の順に構築してユニットテスト・データテストを実行します。
    link: /ja/docs/gradle-plugin/migration-models
  - title: Gradleによる自動化
    details: メタデータ出力、SQL生成、ドキュメント生成、移行をタスクで再現できます。独自ツールにはJava APIを組み込めます。
    link: /ja/docs/gradle-plugin/README
  - title: デスクトップGUI
    details: Swingの移行支援ツールでスキーマ比較、マイグレーション、データ転送計画を確認。実行前にDBへの影響を確認できます。
    link: /ja/sqlapp-swing/README
---

## 実際の出力からはじめる

[生成したHTML、ER図、差分SQL、移行検証レポートを見る](/ja/demos)。
顧客・注文のサンプルは架空データを使用し、外部DBは不要です。

## 利用するDBを選ぶ

[対応DB、バージョン境界、検証の根拠](/ja/docs/compatibility#database-dialect-matrix)は
Compatibility Matrixに集約しています。Dialectの実装は、全バージョンや全移行経路での実機検証を意味しません。

## ひとつのモデルをワークフロー全体で活用

[Gradleガイド](/ja/docs/gradle-plugin/getting-started)からはじめ、
[ドキュメント](/ja/docs/README)や[Java API](/ja/docs/getting-started/java-api)を参照してください。
依存関係の選び方は[配布成果物](/ja/docs/artifacts)に記載しています。
リポジトリの開発環境はJava 25です。公開済みリリースの要件は異なる場合があるため、使用するリリースの資料を確認してください。

sqlappは[ライセンス](/ja/LICENSE)に従って利用できるOSSです。機能の制約や実験段階のDB派生製品は各ガイドとMatrixに記載しています。
