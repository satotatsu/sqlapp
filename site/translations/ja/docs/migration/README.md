# データ転送と移行 {#data-movement-and-migration}

[ドキュメント一覧](../README.md)

最初に[インメモリ移行デモ](../getting-started/offline-migration.md)を試してください。
架空データ5行の転送成功と、意図的な不一致の検出を確認できます。

まずワークフローを選び、その運用リファレンスを確認してください。
JavaとGradleの入口は、共通の移行検証・実行コンポーネントを使用します。

| 目的 | リファレンス | Gradle設定・関連ガイド |
|---|---|---|
| テーブル行を挿入・UPSERTする | [バルク挿入・UPSERT](../data/bulk-insert.md) | ベンダー別プロバイダーとバッチ動作 |
| 単一テーブルの転送を再開する | [チャンク移行](chunk-migration.md) | チェックポイント・リース・トランザクション |
| 転送したデータを検証・修復する | [検証とリカバリー](verification-and-recovery.md) | Java入門と高度な操作 |
| 複数テーブルを調整する | [移行ジョブ](jobs.md) | [Gradleタスク](../gradle-plugin/bulk-migration.md) |
| SCD2スナップショットを適用する | [スナップショット実行](snapshot.md) | [Gradleタスク](../gradle-plugin/snapshot.md) |
| Accessの移行を評価する | [アセスメント](../gradle-plugin/database-migration-assessment.md) | [マッピング](assessment-mapping.md)と[初期ロード](access-initial-load.md) |
| Oracleの移行を評価する | [Oracleアセスメント](../gradle-plugin/oracle-migration-assessment.md) | [Data Pumpと文字セット](oracle-data-pump-assessment.md)、[ソース検証](../development/oracle-assessment-verification.md) |
| バージョン管理されたSQL移行を適用する | [バージョン管理](../gradle-plugin/versioned-migrations.md#versioned-migrations) | Gradleのmigration拡張 |
| レガシーデータを正規化・ロードする | [レガシー移行](../gradle-plugin/normalization-and-legacy-migration.md) | 抽出契約と階層ロード |
| 進捗確認・再試行・一時停止・キャンセル | [ジョブの進捗と制御](job-progress.md) | 共通リスナーAPI |
| ジョブの同時実行を防ぐ | [リースとフェンシング](job-leases.md) | 安定した識別子とリース検証 |
| ジョブの証跡を構築・確認する | [ジョブレポート](job-reports.md) | 読み取り専用レポートとライブ更新 |
| データ修復を確認・承認する | [修復計画](repair.md) | 承認成果物とトランザクション制約 |
| 切り替えを評価・承認する | [切り替え](cutover.md) | 検証証跡とポータブルパッケージ |
| 運用レポートを生成する | [レポートタスク](../gradle-plugin/migration-reports.md) | 運用・修復計画レポート |

実行前に生成SQL、DBへの影響、トランザクション境界、再起動時の動作を確認してください。
DBを変更するサンプルは、使い捨てDBまたは明示的に許可されたDBで試してください。
