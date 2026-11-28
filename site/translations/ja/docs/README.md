# sqlappドキュメント {#sqlapp-documentation}

用途に応じて最短のガイドを選ぶためのページです。
sqlappはGradle Plugin、Javaライブラリ、他のツールに組み込むコマンドクラスとして使用できます。

初めての場合は[できることと利点](getting-started/use-cases.md)を参照してください。
3つのワークフロー、出力例、運用上の制約を紹介しています。

## ドキュメントのセクション {#documentation-sections}

| セクション | 内容 |
|---|---|
| [はじめに](getting-started/README.md) | Java・Maven・コマンド・Gradleの入口 |
| [Schema](schema/README.md) | 共通モデル、Viewpoints、HTMLドキュメント、SQLワークフロー |
| [データ](data/README.md) | 変換、バルク挿入・UPSERT、JDBCの階層処理 |
| [移行](migration/README.md) | アセスメント、再開可能ジョブ、検証、復旧、スナップショット |
| [Gradle Plugin](gradle-plugin/README.md) | タスク設定とリファレンス |
| [開発](development/README.md) | アーキテクチャ、ビルド、ロードマップ、DB検証 |

互換性、依存関係、ログ、アップグレードの共通資料はドキュメントのルートにあります。
旧ページのパスにも現在のガイドへのリンクを残しています。

## ここからはじめる {#start-here}

| 目的 | 最初に読むもの | 続けて読むもの |
|---|---|---|
| sqlappの用途を知る | [用途と出力例](getting-started/use-cases.md) | [はじめに](getting-started/README.md) |
| 3つのワークフローと実際の出力を一度に見る | [デモツアー](getting-started/demo-tour.md) | [用途](getting-started/use-cases.md) |
| DBなしで試す | [オフラインデモ](getting-started/offline-demo.md) | [HTMLドキュメント](schema/html-documentation.md) |
| デスクトップGUIでSchema XMLを比較し、移行を確認・適用し、データ計画を調べる | [Swing移行レビュー](../sqlapp-swing/README.md) | [バージョン管理された移行](gradle-plugin/versioned-migrations.md) |
| Gradleにタスクを追加する | [Gradle Plugin入門](gradle-plugin/getting-started.md) | [タスクリファレンス](gradle-plugin/task-reference.md) |
| 関連サンプルを実行する | [実行可能なGradleサンプル対応表](gradle-plugin/example-project.md) | [`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example) |
| JavaからSchemaモデル・SQL APIを使う | [Java API入門](getting-started/java-api.md) | [配布成果物](artifacts.md) |
| Mavenにライブラリを追加する | [Maven入門](getting-started/maven.md) | [Java API入門](getting-started/java-api.md) |
| DialectとJDBCドライバーを選ぶ | [配布成果物](artifacts.md) | [Compatibility Matrix](compatibility.md) |
| Gradleタスクを診断する | [Gradleトラブルシューティング](gradle-plugin/troubleshooting.md) | [ビルドとテスト](development/build-and-test.md) |
| ログを設定し、失敗の証跡を集める | [ログと診断](logging-and-diagnostics.md) | [Gradleトラブルシューティング](gradle-plugin/troubleshooting.md) |
| 依存関係を更新する | [アップグレード](upgrading.md) | [Compatibility Matrix](compatibility.md) |
| sqlappに貢献する | [アーキテクチャ](development/architecture.md) | [ビルドとテスト](development/build-and-test.md)、[CONTRIBUTING](../CONTRIBUTING.md) |

現在の例はsqlapp `0.80.0`、または明示的な`<sqlapp-version>`プレースホルダーを使っています。
プラグイン、core、command、レンダラー、Dialectを同じバージョンに揃えてください。

## Gradle Plugin {#gradle-plugin}

[Gradle Pluginタスクガイド](gradle-plugin/README.md)は`com.sqlapp.db`の全体索引です。
目的別の資料は次のとおりです。

- [入門](gradle-plugin/getting-started.md)：適用、ランタイム依存関係、DataSource、Schema XML出力、HTML生成。
- [Kotlin DSL](gradle-plugin/kotlin-dsl.md)：`build.gradle.kts`での型付き設定、遅延プロパティ、カスタムタスク。
- [タスクリファレンス](gradle-plugin/task-reference.md)：登録名、型、主な入出力、DBへの影響、共通設定、DataSourceファイル。
- [Schema XML・SQL・HTML](gradle-plugin/schema-sql-and-html.md)：出力、比較、DDL生成、辞書、ドキュメント。
- [HTMLドキュメント](schema/html-documentation.md)：生成ページ、テーブルDDL、SVG ER図、Mermaidのダウンロード、Viewpoints、出力構成。
- [カスタムタスク](gradle-plugin/custom-tasks-and-migrations.md)：データ出力、ファイル変換、SQL実行、登録。
- [バージョン管理されたSQL移行](gradle-plugin/versioned-migrations.md)：移行拡張、計画、チェックサム、復旧、環境比較。
- [正規化とレガシー移行](gradle-plugin/normalization-and-legacy-migration.md)：確認可能な正規化・抽出・ロード。
- [DB移行アセスメント](gradle-plugin/database-migration-assessment.md)：Access診断、任意のデータ検査、マッピング、[Oracle](gradle-plugin/access-oracle-migration-assessment.md)・[SQL Server](gradle-plugin/access-sqlserver-migration-assessment.md)向けDDL検証。
- [Oracle移行アセスメント](gradle-plugin/oracle-migration-assessment.md)：オフライン事前確認、ソース検証、移行先のレビュー。
- [トラブルシューティング](gradle-plugin/troubleshooting.md)：タスク検索、クラスパス、Dialect、DataSource、パス、出力。

関連する[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)には実行可能なGroovy DSL設定があります。
[サンプル対応表](gradle-plugin/example-project.md)でタスク・ファイルとガイドの関係を確認できます。

## Javaライブラリ {#java-libraries}

- [Maven入門](getting-started/maven.md)：Java 21の完全なPOM、依存スコープ、依存ツリー検査、パッケージング。
- [Java API入門](getting-started/java-api.md)：Schema構築、XML往復、Dialect解決、SQL生成、リソース所有権。
- [JDBCツリーデータセッション](data/jdbc-tree-data-session.md)：階層データ処理。[COBOL例](data/jdbc-tree-data-cobol-migration-example.md)と[実DB検証](development/jdbc-tree-data-real-db-verification.md)も参照。
- [Schemaモデル](schema/model.md)：所有権、テーブル、列、制約、インデックス、関係、検索、XML、変更のルール。
- [Command API入門](getting-started/command-api.md)：Javaからのコマンド実行、DataSource所有権、トランザクション、失敗。
- [ログと診断](logging-and-diagnostics.md)：コマンド、Log4j、Gradle出力の区別と安全な診断材料。
- [アップグレード](upgrading.md)：依存関係、Schema XML、生成SQL、DB操作、ロールバックの確認。
- [配布成果物と依存関係](artifacts.md)：ユースケース別の成果物と実行時依存関係。
- [データコンバーター](data/converters.md)：`sqlapp-core`の値変換機能。
- [Schema Viewpoints](schema/viewpoints.md)：ドキュメントとローダーで共有する名前付きテーブル選択。

API資料は安定した入口と運用動作を中心に記載しています。
記載のないAPIでは、そのモジュールのソースとテストを現在の動作の根拠とし、Issueで不足する用途を報告してください。

## DB対応 {#database-support}

- [互換性・DB検証Matrix](compatibility.md)：Java・Gradle要件、Dialectのバージョン境界、ビルドに使うJDBC版、実機検証の根拠。
- [配布成果物](artifacts.md#database-dialect-artifacts)：DB製品からDialect成果物と現在のJDBC依存関係への対応表。
- [Dialect開発の継続記録](development/dialect-enhancement-continuation.md)：実装範囲、制限、検証コマンド。

成果物の存在は、そのDB製品用の実装があることを意味します。
特定のサーバー版で本番利用する前に、検証根拠と機能の制限を確認してください。

## データ転送と移行 {#data-movement-and-migration}

[移行ワークフロー索引](migration/README.md)で、バルク挿入・UPSERT、再開可能な移行、複数テーブルのジョブ、検証・復旧、
スナップショット、アセスメント、レガシーロードを選択してください。

- [バルク挿入・UPSERT](data/bulk-insert.md)：Java APIとベンダープロバイダー。
- [検証と復旧](migration/verification-and-recovery.md)：Java入門、検証、修復、高度な操作。
- [修復計画](migration/repair.md)と[切り替え承認](migration/cutover.md)：詳細レビュー、証跡、実行境界。
- [Gradle移行タスク](gradle-plugin/bulk-migration.md)：ジョブ設定、承認、リース、実行証跡、修復。
- [バージョン管理されたSQL移行](gradle-plugin/versioned-migrations.md#versioned-migrations)。
- [正規化とレガシー移行](gradle-plugin/normalization-and-legacy-migration.md)。

DBを変更する例は、使い捨てDBか明示的に許可されたDBで先に試してください。
本番利用前に生成SQL、タスクのDBへの影響、トランザクション境界、再起動動作を確認します。

## 内部構造と貢献 {#project-internals-and-contribution}

- [アーキテクチャ](development/architecture.md)：モジュール境界、Schema、Dialect検出、コマンド・Gradle層、依存方向。
- [ビルドとテスト](development/build-and-test.md)：Java 21設定、対象を絞ったGradleコマンド、統合テスト境界、依存関係の診断。
- [ロードマップ](development/roadmap.md)：現在の方向性と既知の作業領域。
- [CONTRIBUTING](../CONTRIBUTING.md)：貢献と検証のルール。

sqlappのライセンスは[GNU Lesser General Public License version 3以降](../LICENSE.md)です。

## ドキュメントの表記 {#documentation-conventions}

- シェルの例は`./gradlew`を使います。Windows PowerShellでは`.\gradlew.bat`です。
- `<sqlapp-version>`や`<jdbc-driver-version>`などの山括弧付きプレースホルダーを実行前に置換してください。
- Gradleの相対パスはそのプロジェクトから解決されます。マルチプロジェクトでは必要に応じてプロジェクト名とタスク名を修飾します。
- 認証情報は環境変数、追跡しないローカルファイル、シークレットプロバイダーに保持し、サンプル設定にコミットしません。
- 実DB対応に関する主張は、Matrixに記録された根拠の範囲に限ります。

## ドキュメントの保守 {#maintaining-documentation}

- ルートREADMEは製品と主な入口に集中させます。
- 全体の対応表はこの索引、各トピックはワークフロー別索引を使います。
- Java・Maven・コマンド入門は`getting-started/`、Schema・HTMLは`schema/`、データ処理は`data/`、貢献者向け資料は`development/`に置きます。共通の運用資料はルートに保持します。
- 共通の移行セマンティクスとJava APIは`migration/`、Gradleタスク設定は`gradle-plugin/`に置きます。
- タスク名、型、入出力、DBへの影響はタスクリファレンスを正本とし、ワークフローからリンクします。
- 詳細で通常の使い方が見えにくくなる場合は、ワークフローの境界で分割します。例、安全上の要件、失敗時の動作は保持します。
- セクションの移動時は相対リンクを確認し、既存のブックマーク用に旧見出しのアンカーと移動先へのリンクを残します。
- リンク、見出し、索引、ページ位置を更新したら[オフライン検証](development/build-and-test.md#documentation-checks)を実行します。
