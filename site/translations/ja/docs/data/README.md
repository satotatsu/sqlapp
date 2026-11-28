# データ処理 {#data-processing}

[ドキュメント一覧](../README.md)

| 目的 | ガイド |
|---|---|
| Excelのルールから大量のテストデータを生成する | [テストデータ生成](test-data-generation.md) |
| JavaとJDBCの値を変換する | [値のコンバーター](converters.md) |
| CSV・TSV・固定バイト長レコードを読み書きする | [テキストファイル入出力](text-file-io.md) |
| Schemaのテーブル行を挿入・更新挿入する | [バルク挿入・UPSERT](bulk-insert.md) |
| JDBC SQLテンプレートとバッチ更新を実行する | [JDBC SQLテンプレート](jdbc-sql.md) |
| 関連する行を階層として処理する | [JDBCツリーデータセッション](jdbc-tree-data-session.md) |
| 階層型のCOBOL処理をJavaに移す | [COBOL移行例](jdbc-tree-data-cobol-migration-example.md) |
| Gradleで保存済みデータを出力・変換する | [カスタムタスク](../gradle-plugin/custom-tasks-and-migrations.md) |
| MySQLのMERGE（`ON DUPLICATE KEY UPDATE`）SQLを生成する | [MySQL MERGE](mysql-merge.md) |
| MySQLのTEXT型の容量を確認する | [MySQL TEXT型](mysql-text-types.md) |
| MySQLのDATETIMEとTIMESTAMPの精度をマッピングする | [MySQL TIMESTAMP](mysql-timestamp.md) |

再開可能な転送、移行ジョブ、検証、修復、スナップショットは[データ転送と移行](../migration/README.md)を参照してください。
JDBCツリー処理の実エンジンでの検証根拠は[検証ガイド](../development/jdbc-tree-data-real-db-verification.md)にあります。
