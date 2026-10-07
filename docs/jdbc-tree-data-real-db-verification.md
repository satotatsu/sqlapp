# JDBC tree data session 実DB検証

2026-10-08、Java 21とGradle Wrapperで以下を実行しました。
ローカルDockerの検証専用Testcontainersを使い、既存DB・本番データ・永続ボリュームは
使用していません。テストはコンテナ内のテスト用テーブルを作成・削除します。
終了後、稼働中の検証用コンテナがないことも確認しました。

| 対象イメージ | テストクラス | 成功数 |
| --- | --- | ---: |
| `postgres:18.4` | PostgresJdbcTreeDataSessionTest | 10 |
| `mysql:8.4` | MySqlJdbcTreeDataSessionTest | 9 |
| `mariadb:11.8` | MariadbJdbcTreeDataSessionTest | 9 |
| `gvenzl/oracle-free:23-slim-faststart` | OracleJdbcTreeDataSessionTest | 10 |
| `mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04` | SqlServerJdbcTreeDataSessionTest | 2 |

最終実行は40件成功、失敗・エラー・スキップはすべて0でした。
イメージタグは検証対象の記録であり、可変タグの将来の内容を固定するものではありません。

## 検証範囲

既存テストでは、各クラスが対応する生成キー取得・親子キー伝播、明示的なidentity値、
複数ルートバッチ、親ごとに異なる子件数、別接続からのコミット可視性、
SELECTを開いた状態での階層INSERTなどを検証しました。
全項目が全製品に共通するわけではなく、SQL Serverの既存テストは
SELECT中の階層INSERTを対象としています。

今回は5製品すべてに
`testSessionSelectCursorSurvivesPeriodicCommitsAndFinalPartialBatch`を追加しました。
親3行・子6行を `rootBatchSize = 2`、`commitEveryRootBatches = 1` で読み取り・更新し、
以下を検証します。

- セッション自身が開いたルートSELECTが、途中のコミット後も継続できる。
- 子SELECTが各バッチの親に対応し、親3行・子6行をすべて処理する。
- 最後の1親の端数バッチも処理し、計2バッチ・2コミットになる。
- 親子とも更新結果がDBに保存される。

初回実行ではSQL Serverの既存テストに `autoCommit=false` の設定漏れがあり、
現在のAPIの事前条件で失敗しました。テスト側の設定を補い、再実行で成功しています。
追加テストの初回コンパイルではOracleのfixture初期化引数を補正しました。
今回の実DB検証による製品コードの変更はありません。

## 再実行

Dockerが利用可能な環境で、リポジトリルートから実行します。
テスト用コンテナは再利用せず、終了時に停止・破棄する既定設定です。

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest `
  --tests '*PostgresJdbcTreeDataSessionTest' `
  --tests '*MySqlJdbcTreeDataSessionTest' `
  --tests '*MariadbJdbcTreeDataSessionTest' `
  --tests '*SqlServerJdbcTreeDataSessionTest' `
  --tests '*OracleJdbcTreeDataSessionTest' --console=plain
```

結果は `sqlapp-core-dialect-test/build/reports/tests/dockerTest/index.html` と
`sqlapp-core-dialect-test/build/test-results/dockerTest/` で確認できます。

DB2・Firebird・Informix・SAP HANAなどの他製品、他バージョン、障害注入、
同時実行時の競合、性能測定は今回の実行対象外です。また、今回追加したテストは
定期コミットとSELECT継続を対象とし、H2で追加した子カーソル・独自SELECTの
3件の回帰テストを全製品へ展開したものではありません。
