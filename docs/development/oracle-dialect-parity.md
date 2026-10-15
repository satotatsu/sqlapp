# Oracle DialectとPostgreSQL Dialectの比較

確認日: 2026-10-15。比較対象はDB固有のSQL構文の数ではなく、共有Schemaモデル、メタデータ読取、DDL再生成、バージョン分離、テストの実装状況。
ソースコードと単体テストによる確認であり、全Oracleバージョンの実DB互換性を保証する監査ではない。

## 比較結果

| 領域 | Oracleの実装状況と今回の確認 |
| --- | --- |
| バージョン解決 | `OracleDialectResolver`に旧版～26aiの分岐がある。26aiの`23.26`境界は既存実装を維持。 |
| テーブル・列・制約・索引 | 専用ReaderとSQL Factoryがある。11g/12c/23aiの差分Reader、索引・パーティションの一括補完処理を確認。存在確認であり全オプションの同等性を認定するものではない。 |
| ビュー・マテリアライズドビュー | 専用Readerがある。23aiのduality viewは既存の拡張Readerで補完。高度なduality/domain機能の未対応範囲は既存`roadmap.md`を参照。 |
| シーケンス | 共通Schemaモデルを使用し、18c以降のscale/session用クエリを分離。既存単体テストあり。 |
| 関数・プロシージャ | PostgreSQLと同様に再生成情報を保持すべき経路で、Oracleの引数既定値・ヘッダーが失われる問題を修正。 |
| トリガー | PostgreSQLは所有スキーマと定義を保持。Oracleのスキーマ欠落、ヘッダー消失、不正な文レベルトリガーSQL、条件欠落、無効状態の欠落を修正。 |
| Oracle固有オブジェクト | package/type/synonym/db link/dimension等の既存Reader/Factoryあり。PostgreSQL固有のextension/publication等との件数比較は機能不足の判定に使わない。 |
| bulk処理 | 既存のinsert/upsert/set-based snapshot providerと単体テストあり。今回変更なし。 |

## 修正した再生成経路

`OracleFunctionReader`と`OracleProcedureReader`は、既存の一括`ALL_SOURCE`取得結果を
完全な`CREATE OR REPLACE` DDLとして`definition`へ保存する。
`ALL_ARGUMENTS.DEFAULTED`から既定式を復元することはできないため、引数の既定値を
`NULL`と推測しない。代わりに引数の`specifics.DEFAULTED=true`を保持し、正確な式は
ルーチン全体の定義から再生成する。`AUTHID`、SQL macro、名前付きEND等もソースに残る。
ソースが取得できなければ対象名を含む例外で失敗する。

Function/ProcedureのCREATE Factoryは完全なDDLを受け付けるとともに、従来の
`FUNCTION ...` / `PROCEDURE ...`で始まるユーザー定義も引き続き受け付ける。
公開メソッド、モジュール依存、設定形式の変更はない。読み出したSchema XMLでは
これらのルーチンの主な再生成情報が`statement`から`definition`へ移る。
定義内の識別子や引数を変更する場合は定義自体も更新する必要がある。

`OracleTriggerReader`は`OWNER`を設定し、`DESCRIPTION`、`WHEN_CLAUSE`と`TRIGGER_BODY`から完全な
CREATE DDLを保持する。これにより`UPDATE OF`、`REFERENCING`、`WHEN`、複合トリガーや
DBイベントのヘッダーを汎用モデルへ無理に分解せず保存する。定義の取得失敗を明示する。
CREATE Factoryはこの定義を使用し、無効トリガーには後続の`ALTER TRIGGER ... DISABLE`を生成する。
モデルから生成する文レベルトリガーでは`FOR EACH STATEMENT`を生成せず、行レベルの条件は`WHEN`で生成する。
名前変更と有効状態変更はOracle構文に合わせて別々のALTER操作として返す。

すべてOracleの基本Dialectから継承する範囲の変更であり、新しいカタログ列やバージョン固有SQLは追加していない。
オブジェクトごとの追加SELECTもない。

## 検証

- `OracleRoutineRecreationTest`: 基本Dialect、11gR2、19c、21c、23ai、26aiで既定式・AUTHID・名前付きENDの再生成、Schema XML往復、従来形式の定義、ソース欠落。
- `OracleTriggerRecreationTest`: 所有者・ヘッダー・本体・無効状態の保持、Schema XML往復、モデルからの行/文トリガー生成。
- `OracleTriggerFactoryTest`: 既存CREATEと、名前変更→無効化の2操作。
- Java 21、repository Gradle WrapperでOracleモジュール全体の単体テストを実行。
- PostgreSQLモジュールのtestタスクは成功（UP-TO-DATE）。実DBテストは今回実行していない。
- CommandのOracle移行評価関連テストとGradleプラグインのOracle関連テストも成功。

実行コマンド（`--gradle-user-home .gradle-user-home`を各コマンドに指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-core-postgres:test
.\gradlew.bat :sqlapp-command:test --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*'
```

初回のWrapper取得はネットワーク制約で失敗し、許可された再実行で取得した。
追加テストの初回コンパイルでFactory取得メソッド名を修正し、再実行で成功。
利用側テストの短い`*Oracle*`フィルタはWindowsでディレクトリ名へ展開されタスク選択に失敗したため、
完全修飾クラス名のフィルタで再実行して成功した。

高度なdata use case domainのモデル化、duality viewの詳細、権限差による挙動、実DBでのDDL実行を含む
全体の同等性は、この単体テスト結果だけでは確認できない。既存の
`sqlapp-core-dialect-test/src/dockerTest`を使う実DB検証は別途必要。

## 継続対応: 26ai Duality ViewとDomain

Oracle26aiに専用Catalog/Schema/View ReaderとSQL Factory Registryを追加した。
既存の一括Duality Viewクエリに`LOGICAL_REPLICATION`を追加し、23ai側のクエリには
追加していない。Oracleの資料はこの列をRU 23.6以降と記載しているが、既存の
DialectResolverは23.26以降を26aiとして解決するため、今回の対応は26aiの境界に限定する。
23.xの途中RUを一律に対応済みと推測しない。

```java
View view = new View("CUSTOMER_DV");
view.setSchemaName("APP");
OracleJsonDualityViewUtils.setJsonRelationalDualityView(view, true);
view.setStatement("SELECT JSON {'_id': c.ID} FROM CUSTOMERS c");
view.getSpecifics().put(OracleJsonDualityViewUtils.LOGICAL_REPLICATION, true);
// Oracle26aiのCREATE Factoryが AS ENABLE LOGICAL REPLICATION を生成する。
```

`false`はDISABLE、未設定なら句を追加しない。不正値、通常ビューへの設定、
旧Dialectへの設定、完全な`definition`とこのオプションの併用は明示的なエラーになる。
完全な`definition`のみを指定したDuality Viewは、その定義をそのまま生成する。
生成SQLの実行にはOracle側のsupplemental logging等の前提設定が必要であり、
sqlappはDB全体の設定を自動変更しない。

追加修正:

- 無名Domain CHECKでは不正な`CONSTRAINT CHECK`を生成せず、`CHECK`のみ生成する。
- Domain注釈・Duality View補完の対象を所有者と名前の完全一致で照合する。引用識別子の大小文字を混同しない。
- 23ai/26aiのViewReaderで23ai用ColumnReaderを使い、VECTOR等の列メタデータを旧Readerで読み落とさない。

公開メソッドの削除、共有SchemaモデルやXML形式の変更、新しいモジュール依存はない。
既存`LOGICAL_REPLICATION`キーを使用する。無視されていた不正・未対応設定は今回からエラーになる。
クエリ数はDuality補完1回、Domain本体1回＋注釈1回を維持し、N+1 SELECTを追加していない。

追加テストは`Oracle26aiDualityViewTest`、`OracleDualityMetadataTest`、
`OracleDomainCheckTest`、`OracleDomainAnnotationIdentityTest`。
SQL生成、XML往復、旧版との境界、設定エラー、引用識別子の照合、クエリ数を検証する。
Oracle全119単体テストと前掲Command／GradleプラグインのOracle関連テストが成功した。
追加テストの初回はViewの継承APIの戻り型・型定数とResultSetモック不足で失敗し、修正後に再実行して成功した。
実DBでのレプリケーション実行は未検証。複数列／flexible／enumerated Domain、
複数制約の完全モデル、26aiのduality table/column詳細は引き続き未対応。

仕様参照:

- [ALL_JSON_DUALITY_VIEWS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_JSON_DUALITY_VIEWS.html)
- [CREATE構文](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlqr/SQL-Statements.html)
- [レプリケーション前提条件](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/alter-json-relational-duality-view.html)

## 継続対応: 複数の名前付きDomain CHECK

Oracleの`useCaseDomains.sql`がCHECKを先頭1件へ絞り込む処理を廃止した。
PostgreSQLでも式をANDで結合して制約名を失っていたため、共有Schemaモデルに
`DomainCheckConstraint`と`Domain.getChecks()`を追加し、両Dialectで使用する。
Oracle 23ai/26aiの変更であり、旧Oracleのcollection type用Domain生成は変更しない。

単純な設定は従来どおり:

```java
Domain domain = new Domain("POSITIVE").setDataType(DataType.INT).setCheck("VALUE > 0");
```

名前付き／複数CHECKが必要な場合:

```java
Domain domain = new Domain("BOUNDED").setDataType(DataType.INT);
domain.getChecks().add(new DomainCheckConstraint("CK_LOWER", "VALUE > 0"));
domain.getChecks().add(new DomainCheckConstraint("CK_UPPER", "VALUE < 100"));
```

Oracleでは制約ごとに`setDeferrability`、`setEnable`を指定できる。
Oracle固有の検証状態・RELYは各CHECKの`specifics.VALIDATED`（`VALIDATED`/`NOT VALIDATED`）と
`specifics.RELY`（`RELY`/`NORELY`）で保持する。これらはコンパイル有効性を示す`valid`とは別。
遅延制約は`DEFERRABLE INITIALLY DEFERRED/IMMEDIATE`として生成し、従来の単一checkも同じ生成規則を使う。
PostgreSQLのDomain CHECKは有効・遅延不可として生成し、無効化や遅延設定はエラーにする。

再生成テストで見つかった無制限`NUMBER`の`NUMBER(0,0)`への変化も修正した。
Oracle Domainのカタログ精度・スケールがNULLの場合はNULLのまま共有モデルへ保存する。
汎用decimal生成の既定値で意味が変わるこのケースのみOracle Factoryで`NUMBER`、
スケールだけ指定されている場合は`NUMBER(*,scale)`を生成する。

`check`と`checks`の併用、名前や式の欠落、重複名、未対応Factoryへの新コレクションの入力を
明示的なエラーにする。引用名の大小文字は別名として保持する。ENUMへのDomain CHECKも拒否する。

既存メソッドの削除や既存XMLの読取形式変更はない。新コレクションを使う場合だけ
`<checks><domainCheckConstraint name="..." expression="..."/></checks>`が追加される。
この新要素を含むXMLの読取には対応したsqlappバージョンが必要。
Oracle/PostgreSQLの読取CHECKは`getCheck()`ではなく`getChecks()`へ格納するため、
メタデータ利用側で式を直接参照するコードは新コレクションも扱う必要がある。
従来の単一checkを手で作成するコードは継続して使用できる。

共有モデルのXML、clone、所有関係、差分、型登録をテストし、Oracle/PostgreSQLでは
一括読取→XML→DDLの経路とクエリ数を検証する。OracleのDomain本体＋CHECKは1クエリ、
注釈は1クエリ、PostgreSQLは1クエリであり、オブジェクトごとの追加SELECTはない。
実DBテストは今回も未実行。複数列、Flexible、Enumerated Domain、名前付きNOT NULL、
代替型やDomain参照の完全なモデル化は引き続き残る。

実行コマンド（各コマンドに`--gradle-user-home .gradle-user-home`を指定）:

```powershell
.\gradlew.bat :sqlapp-core:test --tests 'com.sqlapp.data.schemas.DomainChecksTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests 'com.sqlapp.data.db.dialect.oracle.metadata.OracleDomainNamedChecksTest' :sqlapp-core-postgres:test --tests 'com.sqlapp.data.db.dialect.postgres.metadata.PostgresDomainNamedChecksTest'
.\gradlew.bat :sqlapp-core:test
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-core-postgres:test :sqlapp-core-h2:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*'
```

初回の共有モデルコンパイルで必須メソッド、広域テストで新Schema型のFactory登録・型数期待値を修正した。
`sqlapp-core:test`は最終状態で1184テスト成功。Oracleの遅延設定テストの失敗はDEFERRABLE生成の修正で解消した。
最終結果はOracle 122件、PostgreSQL 299件、H2 48件も失敗0件。
Commandの生成系・Oracle関連とGradleプラグインのOracle関連テストも成功した。
生成物はテスト実行に伴うもののみで、意図的な生成済みファイルのコミット対象変更はない。

仕様参照: [Oracle CREATE DOMAIN](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/create-domain.html)、
[Oracle ALL_DOMAIN_CONSTRAINTS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_DOMAIN_CONSTRAINTS.html)、
[PostgreSQL CREATE DOMAIN](https://www.postgresql.org/docs/18/sql-createdomain.html)。

## 以前のOracle仕様参照

- [ALL_ARGUMENTS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_ARGUMENTS.html): DEFAULTEDは有無のみ、DEFAULT_VALUEは将来用。
- [ALL_TRIGGERS](https://docs.oracle.com/en/database/oracle/oracle-database/18/refrn/ALL_TRIGGERS.html): DESCRIPTIONは作成文の再構築用、TRIGGER_BODYは本体。
- [ALTER TRIGGER構文](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/img_text/alter_trigger.html): RENAMEとENABLE/DISABLEは別の選択肢。
