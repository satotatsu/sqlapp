# スキーマとデータベースドキュメント {#schema-and-database-documentation}

[ドキュメント一覧](../README.md)

sqlappのSchemaモデルは、メタデータ取得、SQL生成、ドキュメント生成、移行ワークフローで使用する共通の表現です。

| 目的 | ガイド |
|---|---|
| Schemaオブジェクトを構築・変更する | [Schemaモデル](model.md) |
| JDBCメタデータへのフォールバックと制限を理解する | [JDBCメタデータ](jdbc-metadata.md) |
| 物理プロジェクションをモデル化し文書化する | [物理プロジェクション](projections.md) |
| 再利用できる名前付きテーブル選択を定義する | [Schema Viewpoints](viewpoints.md) |
| 閲覧可能なデータベースドキュメントとER図を生成する | [HTMLドキュメント](html-documentation.md) |
| GradleでXML出力、スナップショット比較、SQL生成を行う | [Schema・SQL・HTMLタスク](../gradle-plugin/schema-sql-and-html.md) |

最初のJavaサンプルは[Java APIガイド](../getting-started/java-api.md)を参照してください。
