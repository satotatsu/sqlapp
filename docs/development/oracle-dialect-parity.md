# Oracle DialectとPostgreSQL Dialectの比較

確認日: 2026-10-15。比較対象はDB固有のSQL構文の数ではなく、共有Schemaモデル、メタデータ読取、DDL再生成、バージョン分離、テストの実装状況。
ソースコードと単体テストによる確認であり、全Oracleバージョンの実DB互換性を保証する監査ではない。

## 比較結果

| 領域 | Oracleの実装状況と今回の確認 |
| --- | --- |
| バージョン解決 | `OracleDialectResolver`に旧版～26aiの分岐がある。26aiの`23.26`境界は既存実装を維持。 |
| テーブル・列・制約・索引 | 専用ReaderとSQL Factoryがある。11g/12c/23aiの差分Reader、索引・パーティションの一括補完処理を確認。存在確認であり全オプションの同等性を認定するものではない。 |
| ビュー・マテリアライズドビュー | 23ai/26aiのduality view補完、MviewのCREATE／REFRESHと基本設定、10g以降の完全DDL保持に対応。高度な設定の構造化編集と実DB検証は残る。 |
| シーケンス | 共通Schemaモデルを使用し、18c以降のscale/session用クエリを分離。既存単体テストあり。 |
| 関数・プロシージャ | PostgreSQLと同様に再生成情報を保持すべき経路で、Oracleの引数既定値・ヘッダーが失われる問題を修正。 |
| トリガー | PostgreSQLは所有スキーマと定義を保持。Oracleのスキーマ欠落、ヘッダー消失、不正な文レベルトリガーSQL、条件欠落、無効状態の欠落を修正。 |
| Oracle固有オブジェクト | package/type/synonym/db link/dimension等の既存Reader/Factoryあり。PACKAGE／TYPEと各BODYは完全ソースDDLを保持し、ヘッダー・名前付きENDの欠落とCREATEの二重付与を修正。PostgreSQL固有のextension/publication等との件数比較は機能不足の判定に使わない。 |
| bulk処理 | 既存のinsert/upsert/set-based snapshot providerと単体テストあり。今回変更なし。 |
| Domain | 23ai/26aiの単一列・複数列、26aiのFlexible関連付け、完全DDL保持、CREATE／ALTER／DROPの依存順に対応。構造化ALTERはDISPLAY／ORDER・object注釈に限定し、型・制約・完全definitionの変更は明示的に拒否する。 |

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

## 継続対応: 複数列Domain・参照・Duality詳細

共有モデルに任意の`Domain.columns`と`DomainColumn`を追加した。
従来のscalar Domainの設定やXMLは維持し、列を設定した場合だけ追加のXML要素を出力する。
Oracle 23ai/26aiのCREATE Factoryは次のモデルを`AS (CITY AS ..., ZIP AS ...)`形式で生成する。
未対応Factory、型が欠落した列、重複列名、scalar型／default／nullabilityとcolumnsの併用はエラーになる。

```java
Domain address = new Domain("ADDRESS");
address.getColumns().add(new DomainColumn("CITY").setDataType(DataType.VARCHAR).setLength(80));
address.getColumns().add(new DomainColumn("ZIP").setDataType(DataType.INT));
address.getChecks().add(new DomainCheckConstraint("CK_ZIP", "ZIP > 0"));
```

Oracle26aiの専用DomainReaderでは通常Domainの列定義を対象集合全体で一括取得する。
本体とCHECKの取得1回、複数列の場合の列取得1回、注釈取得1回に加え、完全DDLと依存関係を各1回で取得する。
JDBCでのSELECT回数はDomain数に比例しない。完全DDLのクエリ内では対象ごとにDBMS_METADATAを評価する。
期待する列数に足りない場合は失敗する。26aiで代替型がある場合は主型の構成列を補助情報として保持し、
代替型を含む完全DDLをdefinitionの正本として再出力する。
新しい`ALL_DOMAINS.TYPE`列を使うクエリは26aiに限定する。
23aiも既存の`COLS`と列カタログを使って複数列Domainを読み、列定義の一括取得処理を26aiと共有する。
23aiでは本体・CHECK、複数列の列定義、注釈を各1回で取得する。
既存のCOLUMN_NAMEによる絞り込みを外し、object-level／column-level注釈を別々に保持する。
古いrelease updateでのカタログ内容は実DB未検証であり、26ai固有列を23aiへ追加したものではない。
Flexible／Enumerated Domain、代替型、NOT NULL制約、scalar Domainのobject-level注釈など、
構造化モデルだけでは完全再現できないものは`DBMS_METADATA.GET_DDL('SQL_DOMAIN', ...)`で
実行可能な定義を保持する。これらの`definition`が正本であり、補助的なcolumns／checksは必須ではない。
SQL_DOMAINは26aiでも利用可能な互換名称である。メタデータの権限不足やDDL取得失敗は伝播する。

Domain間の参照は`ALL_DEPENDENCIES`から一括取得し、ownerとnameの組をJSONのspecificsへ保持する。
読取とCREATE集合の生成で参照先を先に並べ、循環参照や不正な依存情報はエラーとする。
取得対象外の参照先は事前に存在する必要がある。
Oracle 23ai/26aiのCatalog CREATEは登録済みSchema Factoryを使い、型・シーケンス等を各Schemaで
生成した後、全SchemaのDomainを依存順に生成してからテーブルを生成する。
Schema単独のCREATEは従来どおりであり、Catalog入力のSchemaやDomainコレクションの順序を変更しない。
個々のSchemaを別々に生成する場合や、Domain以外のオブジェクト間のSchema横断依存は呼出側で管理する。
definition内のSQL識別子は名前変更に自動追従しない。

`Column.setDomain`は共有SchemaモデルのDomainを参照する。XMLには`domainName`と
必要な`domainSchemaName`、複数列対応には`domainColumnName`を保持し、読取後にSchema内の
同一Domainへ解決する。型や長さが指定されたOracle列は物理型を保って`DOMAIN`句を生成する。
型を省略した単一列参照はDomain自体を型名として使用する。

複数列Domainを関連付ける場合:

```java
table.getColumns().add(new Column("LOCATION").setDomain(address).setDomainColumnName("CITY"));
table.getColumns().add(new Column("POSTCODE").setDomain(address).setDomainColumnName("ZIP"));
// DOMAIN ADDRESS(LOCATION, POSTCODE) をDomainの列順で生成する。
```

同じDomainを複数回関連付ける場合は各グループの列に同じ
`specifics.oracle.domain.associationId`を指定する。メタデータ読取ではOracleの
`DOMAIN_ASSOCIATION_ID`を保持する。関連付けの欠落や同一グループ内の重複はエラーとなる。
Domainと参照先componentの名前変更は参照へ追従し、cloneではコピー先Schemaへ解決する。
CHECK等のSQL式そのものを名前変更する機能はこの変更には含まない。
PostgreSQLはこの共有参照からscalar Domain型を生成する。未対応の汎用SQL Builderは参照を黙って落とさず拒否する。

26ai Duality Viewでは概要のほかテーブル／列詳細を各1回の一括クエリで読む。
親子table number、relationship、WHERE、flex、JSON key、hidden/generated、PK／ETAG／ORDER位置、更新可否を保持する。
`OracleJsonDualityViewUtils.getTableDetails(view)`と`getColumnDetails(view)`で取り出せる。
詳細は型を保持したJSONカタログ行として既存の`specifics`へ保存し、XMLでも維持する。
DDLの正本は従来のView statementであり、詳細行から別のGraphQL本文を合成しない。
カタログ参照権限／ビュー不在は警告で区別し、SQLの誤り等は伝播する。

検証は共有モデルのXML・clone・差分・名前変更、両Dialectの関連付けSQL、Oracleの
一括読取→XML→DDL、Dualityの詳細と権限エラーの区別を対象とする。実DBへの接続は行っていない。
共有変更時のモジュール単体テストはcore 1187件、Oracle 135件、PostgreSQL 300件、H2 49件がすべて成功した。
その後、23aiの複数列読取とSchema横断Domain順序の変更後にOracle全139件の成功を確認した。
追加の対象テストは`OracleMultiColumnDomainTest`と`OracleCatalogDomainOrderTest`。
Schema横断の作成順・循環検出、XML往復、入力順の保持、DomainがないCatalogの従来順序を確認する。
今回の変更モジュールはsqlapp-core-oracleと開発資料のみ。既存公開API・XML形式・依存関係の破壊的変更はない。
追加テストの初回はResultSetモックとSQL空白の期待値を修正して再実行した。
注釈対象の条件式は型推論でコンパイルが長引いたため中断し、明示的な共通型へ変更して再実行した。
完全DDLの追加テストではコンストラクターとdefinitionのリスト表現に関する期待値を修正して再実行した。

主な実行コマンド（各コマンドに`--gradle-user-home .gradle-user-home`を指定）:

```powershell
.\gradlew.bat :sqlapp-core:test --tests 'com.sqlapp.data.schemas.DomainColumnsTest' --tests 'com.sqlapp.data.schemas.ColumnDomainReferenceTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests 'com.sqlapp.data.db.dialect.oracle.metadata.OracleMultiColumnDomainTest' --tests 'com.sqlapp.data.db.dialect.oracle.metadata.Oracle26aiDualityDetailsTest'
.\gradlew.bat :sqlapp-core:test :sqlapp-core-oracle:test :sqlapp-core-postgres:test :sqlapp-core-h2:test --continue
.\gradlew.bat :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*'
```

Flexible／Enumerated Domain、名前付きNOT NULL、single-column Domainの注釈scopeの
構造化編集APIは引き続き残るが、26aiの完全DDLによる保存・再出力には対応する。
古い23ai release updateの実DB互換性、実DBでの全DDL実行検証は残る。

仕様参照:

- [Oracle複数列Domain](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/create-domain.html)
- [ALL_DOMAINS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_DOMAINS.html)
- [DBMS_METADATA](https://docs.oracle.com/en/database/oracle/oracle-database/26/arpls/DBMS_METADATA.html)
- [Domain依存関係](https://docs.oracle.com/en/error-help/db/oci-11502/index.html)
- [Duality tables](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_JSON_DUALITY_VIEW_TABS.html)
- [Duality columns](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_JSON_DUALITY_VIEW_TAB_COLS.html)

## 2026-10-16: Domain型情報の追加確認

Oracle 23ai/26aiのscalar Domainクエリに既存カタログのCOLLATIONを追加し、
Domainモデル・XML・CREATE DOMAINのCOLLATE句まで保持する。
複数列Domainのcomponentに未対応のENUMや配列型を設定した場合は、型情報を落としたDDLを
生成せず、対象名を含むエラーで完全なdefinitionの指定を案内する。
完全DDLがある場合に補助componentを必須としない既存動作は維持する。

変更はOracleモジュール内に限定し、公開API・XML形式・モジュール依存に変更はない。
対象テストはOracleDomainCheckTestとOracleMultiColumnDomainTest。
対象テスト成功後、Gradle WrapperでOracle全141件、commandのgenerator／Oracle評価関連149件、
Gradle pluginのOracle関連1件が成功した。core・PostgreSQL・H2は今回未変更のため再実行していない。
DISPLAY／ORDERの複数行式は既存specificsのXML属性で改行が正規化されるため制限を維持する。
この確認の初回テストでは改行保持の期待値が失敗し、その変更を取り下げて対象テストを再実行した。
実DBへの接続と生成DDLの実行は行っていない。

## 2026-10-16: 完全DDLのDomainとテーブル関連付け

代替型Domainでも主型の構成列を一括取得し、テーブル列との対応順をXML経由で保持する。
構成列クエリはDATA_TYPE_ID=1に限定する。代替型自体は完全DDLで保持し、補助列から作り直さない。
DDL取得が成功した後の一時フラグ判定は、Mapの存在判定ではなく実際の値を確認する。
追加した読取→XML→Domain DDL／テーブルDDLのテストで、この成功経路の誤判定も修正した。

Flexible Domainのテーブル関連付けはUSINGのdiscriminant対応が必須である。
構成列が解決できていない参照では、不正なscalar形式やUSINGなしのDDLを生成せずエラーを返す。
以下の継続対応で構成列がある場合のUSING生成を追加した。Domain自体の完全DDL出力は維持する。
変更はOracleモジュール内で、公開API・XML形式・依存関係に変更はない。実DBテストは未実施。
対象テスト成功後、Gradle WrapperでOracle全143件、commandのgenerator／Oracle評価関連149件、
Gradle pluginのOracle関連1件が成功した。未変更のcore・PostgreSQL・H2は再実行していない。

## 2026-10-16: Flexible DomainのUSING対応

Oracle26aiのReaderはFLEXIBLE Domainの構成列を追加の一括SELECTで取得する。
ALL_DOMAIN_COLS.DISCRIMINANTをDomainColumnのspecifics.oracle.domain.discriminantへ保持し、
同じDomain.columnsの中で通常構成列と判別列を区別する。通常構成列・判別列の順序はそれぞれカタログ順を保つ。
Domainの選択式は完全definitionが正本で、関連付けのために同じDDLを解析しない。
この情報はXML・cloneに維持され、既存のdomainColumnNameとassociationIdでテーブル列へ対応付ける。

例えば次の関連付けを生成できる:

```sql
CREATE TABLE T (
    CATEGORY NUMBER,
    PAYLOAD VARCHAR2(80),
    DOMAIN APP.CHOICE(PAYLOAD) USING (CATEGORY)
);
```

手動モデルではOracleDomainAssociationUtils.KINDにFLEXIBLE、判別用DomainColumnの
OracleDomainAssociationUtils.DISCRIMINANTにtrueを設定する。各テーブル列は物理型を明示する。
Oracle23aiへの生成、未解決構成列、欠落した対応、判別列のないFlexible Domainはエラーとする。
Flexible Domain自体のCREATEには完全definitionが必要である。選択式の構造化編集は未対応。
Oracle26ai固有カタログ列の参照は26aiのReader内に限定し、共通モデル・公開API・XML形式の破壊的変更はない。
対象テストはOracleAdvancedDomainDefinitionTestとOracleDomainAssociationTest。実DBテストは未実施。
対象テスト成功後、Gradle WrapperでOracle全145件、commandのgenerator／Oracle評価関連149件、
Gradle pluginのOracle関連1件が成功した。未変更のcore・PostgreSQL・H2は再実行していない。

仕様参照: [ALL_DOMAIN_COLS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_DOMAIN_COLS.html)、
[Flexible Domain関連付け](https://docs.oracle.com/en/database/oracle/oracle-database/26/adfns/registering-application-data-usage-database.html)。

## 2026-10-16: 単一列Domain参照のALTER TABLE

Oracle23ai/26ai用のALTER TABLE Factoryを追加した。単一列のDomain解除・別Domainへの切替では、
既存の列変更処理より前に専用の`MODIFY (column) DROP DOMAIN`を生成する。
Domainの解除は他の列変更と同一文に混ぜず、型・default・nullability等の変更は既存Factoryへ委譲する。
解除後の物理型がない場合は推測せずエラーとする。

```sql
ALTER TABLE APP.T MODIFY (V) DROP DOMAIN;
ALTER TABLE APP.T MODIFY (V VARCHAR2(40) DOMAIN APP.NEW_DOMAIN);
```

Domain由来制約の扱いはOracle標準のDROP DOMAINに従う。PRESERVE CONSTRAINTSの指定は今回追加しない。
Domain付き列のrenameと他変更を同時に行う差分は、既存処理のDROP/ADDによるデータ消失を避けるため拒否する。
複数列／Flexible Domainの関連付け変更については、以下の継続対応でグループ単位のALTER生成を追加した。
Oracle21c以前のFactory登録は維持し、共通モデル・XML・モジュール依存は変更していない。
対象テストはOracleDomainAlterTableTest。初回は引用識別子の期待値を修正して再実行した。
対象テスト成功後、Gradle WrapperでOracle全148件、commandのgenerator／Oracle評価関連149件、
Gradle pluginのOracle関連1件が成功した。未変更のcore・PostgreSQL・H2は再実行していない。
実DBテストは未実施。

仕様参照: [Domain関連付け解除](https://docs.oracle.com/en/database/oracle/oracle-database/26/adfns/registering-application-data-usage-database.html)。

## 2026-10-16: 複数列DomainのグループALTER

CREATEとALTERで関連付けグループの解決・構成列検証・通常列／判別列の順序を共有する。
ALTERは元と先のグループを比較し、変更された旧グループを解除してから列変更処理へ委譲し、
変更された新グループを最後に関連付ける。関連列の追加・削除にも同じ順序を適用する。
不完全なグループ、対応しない構成列、重複構成列は生成前に拒否する。

```sql
ALTER TABLE APP.T MODIFY (CX, CY) DROP DOMAIN;
-- 必要な物理列変更
ALTER TABLE APP.T MODIFY (CX, CY) ADD DOMAIN APP.PAIR;
```

Flexible DomainのALTERでは通常構成列を先に、判別列を後に並べる。
Oracle公式のALTER構文に従い、CREATEのUSING句とは異なり一つのMODIFY列リストへ列を並べる。
Oracle23aiへのFlexible関連付けは拒否し、renameと他変更の同時指定に対する既存の安全検証は維持する。
Domain由来制約はOracle標準のDROP DOMAINの扱いに従う。
変更はOracleモジュールと資料のみで、共通モデル・XML形式・依存関係は変更していない。
対象テストはOracleDomainAlterTableTest、OracleDomainAssociationTest。実DBテストは未実施。
関連列の追加・削除とFlexibleの通常列／判別列順も検証した。
Gradle WrapperでOracle全149件、commandのgenerator／Oracle評価関連149件、
Gradle pluginのOracle関連1件が成功した。未変更のcore・PostgreSQL・H2は再実行していない。

仕様参照: [複数列／Flexible Domainの関連付け変更](https://docs.oracle.com/en/database/oracle/oracle-database/26/adfns/registering-application-data-usage-database.html)。

## 2026-10-16: ライフサイクル・依存順・読取再現性の一括対応

次の経路を一まとまりとして実装・検証した。

- 構造化Domain本体のDISPLAY／ORDERのADD・MODIFY・DROP、object注釈のADD・REPLACE・DROP。
- Domain集合のCREATE差分では参照先が先、DROP集合・削除差分では参照元が先になる順序。
- Schema／Catalog差分では、参照元テーブルの変更・関連付け解除後にDomainをDROPする順序。
- 製品情報のない手動モデルやcloneが汎用Dialectへ戻る場合、明示選択したOracle23ai/26ai Registryを使用する処理。
- 26aiで複数行DISPLAY／ORDER式を検出した場合の完全DDL保持。specificsのXML属性で改行が変わっても、再生成はdefinitionの正本を使う。
- 手動Domain・DomainColumnのBYTE／CHAR指定をCREATEへ反映する処理。26aiで読み取った文字型Domainは元の長さ指定を推測せず完全DDLを保持する。

```sql
ALTER DOMAIN APP.D ADD DISPLAY TO_CHAR(VALUE);
ALTER DOMAIN APP.D MODIFY ORDER -VALUE;
ALTER DOMAIN APP.D ANNOTATIONS (REPLACE Label 'New label');
ALTER DOMAIN APP.D DROP DISPLAY;
```

Domain／Schema／CatalogのState.Modifiedを対応Factoryへ登録した。
型、CHECK、名前、Flexible選択式、完全definitionなどの未対応なDomain本体変更はDROP/CREATEへ
置き換えず、対象名を含むエラーとする。構造化ALTERを使うには完全definitionを正本としないDomainモデルが必要である。
Domainを参照する別Schemaを含むCatalog差分の削除ではdecorateSchemaName=trueが必要。
Catalog追加差分のDomain依存順は次の継続対応で解決する。
個別に生成したSchemaの組合せ、Domain以外のSchema横断依存については引き続き呼出側で順序を管理する。

26aiのALL_DOMAIN_COLSにはCHAR_USED相当の明確な識別列がないため、文字型Domainの
完全DDL取得も同じ一括クエリへ含める。DBMS_METADATAの権限不足は取得失敗として伝播する。
旧23aiのメタデータはこの26ai用クエリを使わない。手動の複数行DISPLAY／ORDER式に対する制限は維持する。

変更モジュールはsqlapp-core-oracleと開発資料のみ。共通モデル・XML形式・モジュール依存の破壊的変更はない。
OracleSqlBuilder.domainTypeDefinitionを追加し、既存の一般的な型出力APIは変更しない。
対象テストはOracleAlterDomainTest、OracleDomainDependenciesTest、OracleDomainMigrationOrderTest、
OracleAdvancedDomainDefinitionTest、OracleDomainCheckTest。
初回の継承・差分API指定によるコンパイル失敗を修正し、Schema／Catalog統合テストで見つかった汎用Dialectへの戻りも修正して再実行した。
最終検証はOracle全157件、commandのgenerator／Oracle評価関連149件、Gradle pluginのOracle関連1件が成功。
core 1187件、PostgreSQL 300件、H2 49件のtestタスクも成功（この3モジュールはUP-TO-DATEで再実行なし）。
今回実行した対象の失敗・skipは0件。git diff --checkも成功した。
実DBへの接続、実DDL実行、生成物の意図的な変更は行っていない。

主な検証コマンド（`--gradle-user-home .gradle-user-home`を指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleAlterDomainTest' --tests '*OracleDomainDependenciesTest' --tests '*OracleDomainMigrationOrderTest' --tests '*OracleAdvancedDomainDefinitionTest' --tests '*OracleDomainCheckTest'
.\gradlew.bat :sqlapp-core:test :sqlapp-core-oracle:test :sqlapp-core-postgres:test :sqlapp-core-h2:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照: [ALTER DOMAIN](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/alter-domain.html)、
[Annotations](https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/annotations_clause.html)、
[ALL_DOMAIN_COLS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_DOMAIN_COLS.html)。

## 2026-10-16: Catalog追加差分のSchema横断依存順

Oracle23ai/26aiのCatalog ALTERは追加Domainと、その前提・利用側のSQLを依存関係で並べる。
追加するシーケンス・型・package等やCatalog-level作成オブジェクトを前提にし、Domain間の依存情報を
使って参照先を先に作成する。既存テーブルへDomainを設定するALTERと、新規テーブルのCREATEの両方を扱う。
テーブル変更・索引・ビュー等の既存の処理順序は維持し、前提とDomainのSQLを必要に応じて先に実行する。
通常列と判別列を含むFlexible Domainにも同じ順序を適用する。

検証した代表例は次の順序になる:

```text
BASE.SEQ / BASE.X → BASE.D → APP.CHOICE → APP.T → APP.Tの索引 → APP.TV
```

既存Schemaの変更だけでなく、新規Schemaを含むCatalog差分も対象とする。
元のSchemaモデル・コレクション順は変更しない。Domainの追加・削除を含むCatalog差分には
decorateSchemaName=trueが必要で、未指定のSchemaを実行時のsearch pathから推測しない。
Domainのない処理は既存の順序を維持する。循環依存は対象名を含むエラーで拒否する。
追加Domain間の参照は既存のspecifics.oracle.domain.dependenciesを使い、完全DDL本文からSQL解析で推測しない。
読取モデルでは一括取得した情報を使用し、手動で完全DDLを作る場合は依存先も指定する。
Catalog外の参照先や必要なEXECUTE等の権限、Domain以外のSchema横断参照は呼出側の前提として残る。

さらにSchema／Catalogの変更後モデルを検証し、削除DomainへのTable列参照やDomain間参照が
残っている場合は生成時に拒否する。依存先を先にDROPして実DBで失敗するSQLを返さない。
Catalogに含まれない実DB上の依存オブジェクトについては未検証である。

変更モジュールはsqlapp-core-oracleと資料のみ。共通モデル・XML形式・公開設定形式は変更しない。
対象テストはOracleCatalogDomainAdditionOrderTest、OracleDomainMigrationOrderTest。
XML往復、23ai/26ai、既存テーブル、新規Schema、前提オブジェクト、索引・ビューの順序、
循環、不完全な削除、非修飾名の拒否、入力コレクション順保持を確認する。
実DBへの接続・DDL実行は行っていない。
対象テスト成功後、Oracle全164件、commandのgenerator／Oracle評価関連149件、
Gradle pluginのOracle関連1件が成功した（失敗・skip 0件）。
未変更のcore・PostgreSQL・H2は今回再実行していない。git diff --checkも成功した。

主な実行コマンド（`--gradle-user-home .gradle-user-home`を指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleCatalogDomainAdditionOrderTest' --tests '*OracleDomainMigrationOrderTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

## 2026-10-16: Domainメタデータの整合性と文字型推定

対象はOracle 23ai/26ai、変更モジュールはsqlapp-core-oracleのみ。
公開API・共通モデル・XML形式は変更していない。

- 26aiのFlexible Domain一括クエリで通常構成列の取得件数と
  ALL_DOMAINS.DISCRIMINANTCOLSを返し、読取後の構成列数を照合する。
  欠落・不整合はDomain名を含むエラーとし、SELECT数は増やしていない。
  通常列の件数は取得行集合に基づくため、カタログ自体の欠落の検出を保証しない。
- 23ai/26aiの構成列注釈は識別子を完全一致で解決し、不明な列への注釈を拒否する。
- 26aiの改行を含む列注釈は完全DDLを保持し、XML往復で改行が正規化されても
  CREATE文で元の値を再現する。
- 完全DDLを持つDomainから物理文字列型を推定する場合、BYTE/CHARを推測しない。
  Domain構成列のcharacterSemantics、またはテーブル列の物理型を明示する。

OracleDomainMetadataIntegrityTestとOracleDomainAssociationTestで、構成列不足、
識別子の大小文字、注釈のXML往復、文字型推定の拒否と明示指定を検証する。
実DBへの接続・DDL実行は行っていない。23aiの改行注釈の完全DDL保存は未対応。
対象4クラスのテスト成功後、Oracle全168件、commandのgenerator／Oracle評価関連149件、
Gradle pluginのOracle関連1件が成功した（失敗・error・skip 0件）。
core・PostgreSQL・H2は今回再実行していない。git diff --checkも成功した。
初回のテストコンパイルではnull引数のオーバーロードが曖昧だったため、
テストのenum型を明示して修正した。

実行コマンド（`--gradle-user-home .gradle-user-home`を指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleDomainMetadataIntegrityTest' --tests '*OracleAdvancedDomainDefinitionTest' --tests '*OracleDomainAssociationTest' --tests '*OracleDomainAnnotationIdentityTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

## 2026-10-16: PACKAGE／TYPEと各BODYの再生成

OraclePackageReader／OraclePackageBodyReaderは、従来の正規表現による本文抽出をやめ、
一括ALL_SOURCEのヘッダーと本文を完全なCREATE OR REPLACE DDLとしてdefinitionへ保存する。
AUTHID、引数既定値、初期化ブロック、名前付きENDを保持する。
OracleTypeReader／OracleTypeBodyReaderも同じ経路へ揃え、4種類ともソースが取得できない場合は
所有者・対象名を含むエラーとする。オブジェクトごとの追加SELECTはない。

対応する4種類のCREATE Factoryは完全DDLにCREATEを重複付与せず、既存の
PACKAGE／TYPEで始まるdefinitionと、statementからの生成も引き続き受け付ける。
この変更は基本Oracle Dialectから26aiまでの継承範囲が対象。
変更モジュールはsqlapp-core-oracleのみで、公開API・XML形式・依存関係は変更しない。
読取結果の主な再生成情報はdefinitionとなり、定義内の識別子を変更する場合は
DDL自体の更新も必要になる。構造化TYPE列は補助メタデータとして引き続き保持する。

OraclePackageTypeRecreationTestでは基本版、11gR2、19c、21c、23ai、26aiについて、
4種類の完全DDL・従来形式、XML往復、AUTHID／既定引数／名前付きEND、ソース欠落を検証する。
関数・プロシージャの既存OracleRoutineRecreationTestも併せて成功した。
実DBでの読取・DDL実行は未検証。
Oracle全170件、commandのgenerator／Oracle評価関連149件、Gradle pluginのOracle関連1件が
成功した（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功した。意図的な生成ファイルの変更はない。

実行コマンド（`--gradle-user-home .gradle-user-home`を指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OraclePackageTypeRecreationTest' --tests '*OracleRoutineRecreationTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

## 2026-10-16: Materialized ViewのCREATE／REFRESH

OracleCreateMviewFactoryの構造化生成から不正なOR REPLACEを除去し、
既存Readerが保持するspecifics.BUILD_MODE（IMMEDIATE／DEFERRED）、
REFRESH_METHOD（FAST／COMPLETE／FORCE／NEVER）、REFRESH_MODE（COMMIT／DEMAND／NEVER）
をSQLへ反映する。未指定時はOracleの既定動作を維持する。
NEVERはNEVER REFRESHとして生成し、矛盾する設定、不正値、クエリ欠落を拒否する。
PREBUILTとON STATEMENTは追加の前提やオプションを構造化情報だけで再現しきれないため、
完全definitionを要求する。完全DDLは書き換えず、構造化オプションは補助情報とする。

OracleRefreshMviewFactoryは従来のALTER MATERIALIZED VIEW ... REFRESHの代わりに
BEGIN DBMS_MVIEW.REFRESH(list => 'schema.name'); END;を生成する。
識別子の引用と文字列リテラルのエスケープ、decorateSchemaNameを適用する。
refresh methodは省略し、DB上の既定メソッドを使用する。NEVER REFRESHのモデルは
無操作で成功したように扱わず、生成時に拒否する。

変更はsqlapp-core-oracleと資料のみ。公開API、設定キー、モデル、XML形式に変更はなく、
追加クエリもない。基本Oracle Dialectから26aiまでの共通経路を修正した。
OracleMviewRecreationTestは読取オプションのXML往復、CREATE／REFRESH、
基本版・11gR2・19c・21c・23ai・26ai、引用識別子、不正値、NEVER、完全DDLを検証する。
初回はテストの継承setter戻り型とResultSetモック不足で失敗し、修正後に対象5件が成功した。

実DBでの読取・DDL・refreshは実行していない。物理ストレージ、refreshスケジュール、
query rewrite、ROWID／PRIMARY KEY等の詳細再現はこの対応の範囲外である。
DBリンクReaderに残るオブジェクト単位のDDL問合せも別途対応が必要。
Oracle全175件、commandのgenerator／Oracle評価関連149件、Gradle pluginのOracle関連1件が
成功した（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
DialectResolverの既存バージョン境界を確認し、変更していない。
git diff --checkも成功した。意図的な生成ファイル変更はない。

実行コマンド（`--gradle-user-home .gradle-user-home`を指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleMviewRecreationTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [CREATE MATERIALIZED VIEW](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/CREATE-MATERIALIZED-VIEW.html)
- [ALL_MVIEWS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_MVIEWS.html)
- [DBMS_MVIEW](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_MVIEW.html)

## 2026-10-16: 残る一括読取・Materialized View再現性・注釈識別子

Oracle10gMviewReaderの既存一覧クエリにDBMS_METADATA.GET_DDL('MATERIALIZED_VIEW',...)
を加え、完全なCREATE DDLをdefinitionに保持する。statementのqueryと構造化列は補助情報として
残す。PREBUILTの精度設定、refreshスケジュール、query rewrite等はDDLから再生成できる。
JDBC SELECTは件数に関係なく1回で、サーバー側のGET_DDL評価は各行で行われる。
DDLが空なら対象名付きエラーとし、GET_DDLの権限・取得失敗も伝播する。
ALL_MVIEWSが見えるだけでは他SchemaのDDLを取得できるとは限らず、DBMS_METADATAの
必要権限が追加の前提となる。既定Readerが扱う旧8/9系のSQLは変更しない。
DDL内の識別子・物理設定はモデル変更に自動追従せず、実行環境に合わせた更新が必要。

23ai/26aiは専用Oracle23aiMviewReaderを登録し、23ai用ColumnReader／IndexReaderを使う。
旧Readerに新カタログ列を追加せず、VECTOR等の補助メタデータの読落としを防ぐ。
手動の構造化Mviewモデルでは、列コレクション順の引用付き列別名もCREATEへ反映する。

OracleDbLinkReader／OraclePublicDbLinkReaderの個別GET_DDLを除去した。
ALL_DB_LINKS.USERNAMEがNULLのリンクは固定ユーザーではない場合があり、そこから
パスワードや認証方式を推定できない。既存一覧の名前・所有者・HOST・USERNAME・作成日時を保持し、
passwordは未取得のままとする。固定ユーザーの認証情報回復、connected/current userの区別、
DBリンクCREATE／DROPの新実装は今回含めていない。既存公開Pattern定数は互換性のため残す。

23ai/26aiのTable／Column／Index注釈は所有者と識別子を完全一致で照合する。
APPとApp、Tとt、Cとc、Iとiを混同しない。不一致の取得行は対象外として扱い、
追加SELECTは発行しない。

変更モジュールはsqlapp-core-oracleのみ。公開API・モデル・XML形式・モジュール依存は変更しない。
OracleObjectMetadataBulkTestは25件のMview完全DDL／XML往復、DDL欠落、Readerバージョン境界、
private/public DBリンクの件数に依存しないクエリ回数を確認する。
OracleMviewRecreationTestは列別名、OracleTableAnnotationIdentityTestは引用識別子を確認する。
対象テストで列別名の許容空白と、索引Schema名の親優先規則に合わせたテスト修正後に成功した。
実DBへの接続、DBリンク接続、DDLやrefreshの実行は行っていない。
Oracle全182件、commandのgenerator／Oracle評価関連149件、Gradle pluginのOracle関連1件が
成功した（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功した。意図的な生成ファイルの変更はない。

実行コマンド（`--gradle-user-home .gradle-user-home`を指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleObjectMetadataBulkTest' --tests '*OracleMviewRecreationTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleTableAnnotationIdentityTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [DBMS_METADATA](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_METADATA.html)
- [ALL_DB_LINKS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_DB_LINKS.html)
- [Distributed Database Concepts](https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/distributed-database-concepts.html)

## 2026-10-16: DBMS_METADATA権限不足時の継続

上記のMview完全DDL取得を、通常一覧とDDL補完の2つの一括SELECTに分離した。
10g～26aiでDBMS_METADATAが許可されればdefinitionを保持し、
ORA-01031（権限不足）またはORA-31603（対象が見えない／取得できない）の場合は
警告を出し、通常一覧のstatement・specifics・列情報の読取を継続する。
ORA-31603は対象消失でも発生し得るため、警告は権限不足または対象不可視として扱う。
取得途中で権限エラーになったMviewのDDL補完結果は適用せず、通常メタデータを保持する。
DDL補完は空一覧なら実行せず、件数に依存しない最大2回のSELECTとなる。

26ai DomainのDBMS_METADATA補完にも同じ警告・継続処理を適用した。
未取得の高度なDomainを推測して追加することはせず、取得済みの通常メタデータを保持する。
完全DDLが未取得のDomainにはoracle.domain.definitionRequiredを残し、
CREATE時は完全definitionを指定するまで明示的に拒否する。
権限がある場合の通常読取・再生成は維持する。
構文・接続等のその他エラーや、成功クエリが返す空／欠落DDLは警告扱いにせず伝播する。

対象はsqlapp-core-oracleのみ。公開API・XML形式・設定形式・旧版Readerに変更なし。
OracleMetadataPermissionTestで原因例外／SQLExceptionチェーン、権限不足時の継続、
通常エラーの伝播、Domainの不完全再生成拒否を検証する。
OracleObjectMetadataBulkTestでは権限ありの2回一括読取と完全DDLのXML往復を確認する。
初回はテストのstatementをStringと比較して失敗し、List形式に修正して対象テストが成功した。
実DBの権限変更・接続・DDL実行は行っていない。
Oracle全185件、command関連149件、Gradle plugin関連1件が成功した
（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功した。意図的な生成ファイル変更はない。
対象テストはOracleMetadataPermissionTest、OracleObjectMetadataBulkTest、
OracleAdvancedDomainDefinitionTestをGradleの--testsで指定して実行した後、
前節と同じOracle全体・command generator／Oracle assessment・plugin Oracleの
テストコマンドを実行した。いずれも--gradle-user-home .gradle-user-homeを指定した。

## 2026-10-16: DDL補完途中の権限失敗

26ai Domainの完全DDL補完を一時コレクションへ保存し、補完SELECTが成功してから
通常読取モデルへ反映するように修正した。途中で権限エラーが発生した場合、
途中まで取得したFlexible Domain等を追加せず、通常カタログから取得済みのDomainを保持する。
これにより、権限エラーを警告で継続した後、不完全なFlexible構成列読取で失敗する経路を防ぐ。
既存のMview補完も同じ方針である。SELECT回数、公開API、XML形式に変更はない。

OracleMetadataPermissionTestに以下を追加した:

- Domain補完で1行取得後に権限エラーとなっても、未完了のFlexible Domainを追加しない。
- Mview補完で1行取得後に権限エラーとなっても、全対象の通常メタデータを保持する。
- 権限エラーがないのに対象DDLが欠落している場合は、対象名を含むエラーで失敗する。
- 空Mview一覧ではDDL補完SELECTを実行しない。

対象モジュールはsqlapp-core-oracleのみ。対象テストと既存の高度Domain・メタデータ整合性テストは成功。
実DBの権限変更・接続・DDL実行は行っていない。
Oracle全188件、command関連149件、Gradle plugin関連1件が成功した（失敗・error・skip 0件）。
core・PostgreSQL・H2は今回再実行していない。git diff --checkも成功した。
実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleMetadataPermissionTest' --tests '*OracleAdvancedDomainDefinitionTest' --tests '*OracleDomainMetadataIntegrityTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

## 2026-10-16: GET_DDLなしのMview補助設定

OracleMviewReaderは既存ALL_MVIEWS一覧からREWRITE_ENABLED、USE_NO_INDEX、
COMPILE_STATEをspecificsに保持し、COMPILE_STATEが取得できた場合はvalidにも反映する。
OracleCreateMviewFactoryはREWRITE_ENABLED=Y/NをENABLE/DISABLE QUERY REWRITEへ、
USE_NO_INDEX=YをUSING NO INDEXへ変換する。Nは既定索引動作を維持し、未設定時は句を追加しない。
Y/N以外の値は生成時に拒否する。完全definitionがあれば従来どおりそのDDLを優先する。

これらは通常カタログから読めるため、DBMS_METADATA権限不足時の継続結果にも残る。
OracleMetadataPermissionTestで権限エラー後のCREATE再生成、OracleMviewRecreationTestで
両フラグのY/N、XML往復、コンパイル状態、不正値の拒否を確認した。
Oracle9iの公式カタログにも存在する列であり、旧Readerには新しい固定カタログ列を追加していない。
SQLは既存A.*を使い、列がない場合は既存のsetSpecificsの判定で未設定とする。
変更はsqlapp-core-oracleと資料のみ。公開API・XML形式・SELECT回数は変更しない。
実DBでの読取・DDL・権限変更は行っていない。
Oracle全189件、command関連149件、Gradle plugin関連1件が成功した（失敗・error・skip 0件）。
core・PostgreSQL・H2は今回再実行していない。git diff --checkも成功した。
実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleMviewRecreationTest' --tests '*OracleMetadataPermissionTest' --tests '*OracleObjectMetadataBulkTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [Oracle9i ALL_MVIEWS](https://docs.oracle.com/cd/B10500_01/server.920/a96536/ch286.htm)
- [Oracle19c ALL_MVIEWS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_MVIEWS.html)

## 2026-10-16: テーブル・列・制約・索引の一括再監査

今回の変更はsqlapp-core-oracle内に限定し、共有Schemaモデルと既存のSQL Factory経路を使用した。
公開APIの削除、XML／設定形式の変更、モジュール依存追加、追加SELECTはない。
DialectResolverの既存境界を維持し、生成列の新版構文は生成時に対応版を確認する。

| 領域 | 確認した欠落と修正 |
| --- | --- |
| 外部キー | 対象Schema／Tableのフィルタを参照先にも適用し、対象外の親へのFKを落としていた。参照先制約はALL_CONSTRAINTSから所有者・制約名で照合する。CTEは子のR制約のみと必要列に限定し、不要なLONG SEARCH_CONDITIONも除いた。 |
| 制約状態 | CHECK／UNIQUE／PK／FKの遅延、有効／無効、VALIDATE／NOVALIDATE、RELY／NORELYをCREATE TABLEとALTER TABLE ADDへ反映する。後二者の読取も補った。 |
| NOT NULLチェック | 全件除外をやめ、名前付き・無効・未検証・遅延のチェックを保持する。通常の有効・検証済み・遅延不可なシステム生成NOT NULLだけは列nullabilityへ任せる。 |
| NUMBER | JDBC NULL精度を0へ変換せず保持。NUMBERとNUMBER(*,scale)を列CREATEでも再現する。 |
| 仮想列 | 11g以降のVIRTUAL_COLUMNのDATA_DEFAULTをformulaへ保存し、DEFAULTではなくGENERATED ALWAYS AS (...) VIRTUALを生成する。 |
| IDENTITY | 12c以降でALWAYS／BY DEFAULT／BY DEFAULT ON NULLと共有Sequenceの開始・増分・範囲・cycle・cache・order設定を再生成する。 |
| DEFAULT ON NULL | 12c以降の通常列にも読取・生成を追加する。 |
| テーブル | 表領域、一時表、SYS$SESSION／SYS$TRANSACTIONの行保持期間を保持・生成する。圧縮状態NULLを有効扱いせず、COMPRESS_FORを有効な圧縮句へ変換する。 |
| 索引 | CREATE INDEXの索引名もdecorateSchemaNameに従ってSchema修飾する。表名だけ修飾して索引を接続先のSchemaに作る問題を防ぐ。 |

手動モデルの一時表はTable.TableType.Temporaryを指定する。行保持期間は任意の
specifics.oracle.table.duration=SYS$SESSIONまたはSYS$TRANSACTIONで、未指定時は
Oracle既定のON COMMIT DELETE ROWSを使用する。一時表への永続表領域／圧縮、
不正な保持期間、不正な圧縮・制約状態、未対応のON UPDATE／ON DELETE動作は明示的に拒否する。
11g未満の仮想列、12c未満のIDENTITY／DEFAULT ON NULLも拒否する。

生成列のCREATEとALTER TABLE ADDは対応するが、既存IDENTITY／仮想列の構造化変更は
単純rename以外を拒否する。未対応のMODIFYにCREATE用句を流用したり、黙って属性を捨てない。
名前付きNOT NULLは補助のCheckConstraintとして保持するため、再生成時は名前付きCHECKになる。

OracleSchemaParityRegressionTestでメタデータ読取、XML往復、CREATE／ALTER ADD、
基本版・11gR2・19c・23ai・26ai、生成列の版境界、親が取得対象外のFKクエリ構造、
各制約Readerの1回クエリ、引用Schema／索引名、型NULL、圧縮・一時表を検証する。
FKのSQLはソース構造と読取モデルを検証したもので、Oracle上でのクエリ実行検証ではない。
初回のテストはResultSetモックの大小文字／統計値取得、IF NOT EXISTS、
IDENTITYの暗黙NOT NULLの期待値が原因で失敗し、修正後に対象テストが成功した。

残る範囲は、実DBでの読取→XML→DDL実行、旧RU差分、不可視列や高度な列属性、
既存生成列のALTER、IOT／外部表／LOBストレージ、索引の高度な圧縮・式・運用状態等。
今回の検証だけでテーブル・索引の全オプションの同等性を認定しない。
実DBへの接続・DDL実行・本番データ変更は行っていない。
手動のDECIMAL／NUMERIC列も精度が未指定の場合はOracleのNUMBERとして生成する。
圧縮方式の版・エディション・ストレージ要件は実行対象Oracleとの整合を呼出側で確認する必要がある。
OracleのUNIQUE／PKには降順列を指定できないため拒否する。IDENTITYと独立DEFAULT式の併用も拒否する。

最終実装でOracle全199件、commandのgenerator／Oracle評価関連149件、Gradle plugin関連1件が成功した。
その後、同じ実装に対する最終テストassertion追加を含むOracleSchemaParityRegressionTest全10件も成功した。
失敗・skipは最終実行で0件。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功した。意図的な生成ファイル変更はない。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleSchemaParityRegressionTest' --tests '*OracleIndexPlacementRegressionTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleSchemaParityRegressionTest' --tests '*OracleColumnDomainReferenceTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleSchemaParityRegressionTest' --tests '*OracleDomainAlterTableTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleSchemaParityRegressionTest'
```

仕様参照:

- [Oracle constraint](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/constraint.html)
- [Oracle Data Types](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Data-Types.html)
- [ALL_TABLES](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_TABLES.html)
- [CREATE TABLE](https://docs.oracle.com/en/database/oracle/oracle-database/21/sqlrf/CREATE-TABLE.html)

## 2026-10-16: 不可視列・IDENTITY生成方式ALTER・共通LOB表領域

12c以降の通常列クエリは、HIDDEN_COLUMN=YESかつUSER_GENERATED=YESの不可視列を
通常のユーザー列と一緒に取得する。システム隠し列の専用取得とは分け、不可視列には
specifics.oracle.column.invisible=trueを保存する。共有hidden属性はシステム隠し列用に維持する。
列取得はCOLUMN_IDに加えてINTERNAL_COLUMN_IDで順序を安定させる。11g以前のSQLは変更しない。

CREATE／ADDは通常・IDENTITY・仮想列のいずれもINVISIBLEを一度だけ生成する。
明示falseはVISIBLEとなり、不正値と12c未満への指定は拒否する。
ALTERはVISIBLE／INVISIBLEだけの変更を単独のMODIFY句で生成する。
同時に型・名前・default等を変更する場合は別操作を求め、属性を落とさない。

既存IDENTITY列はALWAYS／BY DEFAULT／BY DEFAULT ON NULLの切替をALTERで生成できる。
型・Sequence・開始値等は変更せず、START WITHやDROP／CREATEを生成しない。
Sequenceや物理列属性も変わる場合、不正な生成方式の場合は明示的に拒否する。
仮想列の式変更やIDENTITYの開始値／増分変更は引き続き未対応である。

LOB表領域は既存のTable.lobTableSpaceNameを使用する。通常テーブル一覧へALL_LOBSの
所有者・テーブル単位集約をJOINし、全LOBの表領域が非NULLで一意の場合だけ保存する。
SQLは1回の一覧SELECTのままで、LOBごとの追加SELECTはない。
列別やpartitioned LOBの配置を単一値に推測してまとめない。
CREATEではBLOB／CLOB／NCLOB列の順でLOB (...) STORE AS (TABLESPACE ...)を生成する。
LOB列がない指定、一時表への永続LOB表領域指定は拒否する。

変更モジュールはsqlapp-core-oracleのみ。公開API・共通モデル・XML形式・依存関係は変更しない。
OracleColumnVisibilityTestで通常／不可視／システム隠し列の区別、XML往復、版境界、
可視性の両方向ALTER、IDENTITY生成方式の両方向切替、開始値を再設定しないこと、
共通LOB表領域の読取・生成と不正指定を検証する。
最初の可視性ALTERテストは括弧内の空白の期待値を修正後に成功した。

IOTは親・overflow・mapping表やPK索引との対応、LOBの詳細は列／partitionごとの
SecureFile／BasicFile・圧縮・cache等を保持するモデルが必要で、今回の単一LOB表領域対応では
解決していない。共有Tableは完全definitionを保持する型でもないため、specificsへ完全DDLを
押し込む回避は行っていない。これらの構造化対応と実DB再実行検証は残る。
実DBの接続・DDL実行・権限変更は行っていない。
Oracle全205件、command関連149件、Gradle plugin関連1件が成功した（失敗・error・skip 0件）。
core・PostgreSQL・H2は今回再実行していない。git diff --checkも成功した。
意図的な生成ファイル変更はない。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleColumnVisibilityTest' --tests '*OracleSchemaParityRegressionTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
.\gradlew.bat :sqlapp-core-oracle:test
```

仕様参照:

- [Managing Tables](https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/managing-tables.html)
- [ALTER TABLE](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-TABLE.html)
- [ALL_LOBS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_LOBS.html)
- [LOB Storage](https://docs.oracle.com/en/database/oracle/oracle-database/19/adlob/managing-LOBs.html)

## 2026-10-16: IDENTITYオプションALTERとXML属性順

Oracle 12c以降の既存IDENTITY列で、生成方式だけでなくINCREMENT BY、MINVALUE／MAXVALUE、
CYCLE／NOCYCLE、CACHE／NOCACHE、ORDER／NOORDERの変更をALTER TABLE MODIFYへ反映する。
変更項目だけを出力し、内部SequenceをALTER SEQUENCEで直接変更しない。
START WITH・現在値・Sequence識別子・未対応属性の変更、列の型等との同時変更は拒否する。
OracleではSTART WITH変更時に他オプションの既定値が使われるため、開始位置の再設定は
自動化せず引き続き未対応とする。MIN／MAXの明示的削除はNOMINVALUE／NOMAXVALUE、
cache=0またはcache=falseはNOCACHEとなる。増分0、cache=1／負数、不正な最小最大、
変更後の増分／cacheサイズが曖昧な設定は拒否する。

検証中、共有AbstractColumnのsetIdentityOrder／Cycle／Cacheがidentity=falseの時点では
値を捨てるため、XMLの属性適用順によりORDERが失われることを確認した。
数値のIDENTITY setterと同様、Sequenceを必要に応じて作成し、フラグ設定前でも値を保持する。
identityフラグそのものは変更しない。Oracle固有の分岐をcoreへ追加していない。
XML形式・公開メソッド・既定値・モジュール依存は維持する。
変更モジュールはsqlapp-coreとsqlapp-core-oracleで、共有setterの利用者は全Dialectとなる。

IdentityColumnOptionsTestでsetter順、XML往復、cloneの独立性を確認する。
OracleIdentityOptionsTestでXML往復後の差分SQL、変更項目だけの出力、開始値を出さないこと、
境界・曖昧な指定・再開始の拒否を検証する。初回のORDERの差分テストが失敗し、
XML属性の消失を切り分けて共有setterを修正した後に成功した。
実DBへの接続、IDENTITY値の発行、DDL実行は行っていない。
対象テスト成功後、core 1189件、PostgreSQL 300件、H2 49件、Oracle 208件、
command関連149件、Gradle plugin関連1件が成功した（失敗・error・skip 0件）。
git diff --checkも成功した。意図的な生成ファイル変更はない。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core:test --tests '*IdentityColumnOptionsTest' :sqlapp-core-oracle:test --tests '*OracleIdentityOptionsTest' --tests '*OracleColumnVisibilityTest' --tests '*OracleSchemaParityRegressionTest'
.\gradlew.bat :sqlapp-core:test :sqlapp-core-postgres:test :sqlapp-core-h2:test :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [ALTER TABLE identity_options](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-TABLE.html)

## 2026-10-16: 仮想列の式ALTERとコメント差分

11g以降の既存仮想列は、formulaだけの変更をALTER TABLE MODIFY
(column GENERATED ALWAYS AS (...) VIRTUAL)で生成する。DROP／ADD、型・DEFAULTの再指定は行わない。
同時に型・名前等を変える場合、空白式、persisted／identityとの併用は拒否する。
モデルに索引または制約があるテーブルでは、依存関係の変更を先に別操作で行うようエラーにする。
対象外の実DB上の依存や式のOracle上での妥当性は生成時には検証できない。

コメントだけの列変更はMODIFYへ流さずCOMMENT ONを生成する。
OracleAlterTableFactoryで、物理storageの差分がないとコメント処理へ到達しない問題、
remarks差分のString値をColumnへcastする問題、削除時にIS IS NULLとなる問題を修正した。
通常・仮想・IDENTITY列とTableのコメント追加／削除、引用符を含む値を対象とする。
式変更とコメント変更を同時に行う場合は別操作を求める。

変更モジュールはsqlapp-core-oracleのみ。公開API・XML形式・依存関係・SELECT数に変更はない。
OracleGeneratedColumnAlterTestで式のXML往復、MODIFY生成、DROPしないこと、
依存／同時変更の拒否、3種類の列とTableのコメント追加・削除を検証する。
初回のコメントテストで既存cast不具合を検出し、修正後に対象テストが成功した。
実DBの接続・DDL実行は行っていない。
Oracle全211件、command関連149件、Gradle plugin関連1件が成功した（失敗・error・skip 0件）。
core・PostgreSQL・H2は今回再実行していない。git diff --checkも成功した。
意図的な生成ファイル変更はない。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleGeneratedColumnAlterTest' --tests '*OracleIdentityOptionsTest' --tests '*OracleColumnVisibilityTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [Managing Tables](https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/managing-tables.html)
- [ALTER TABLE](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-TABLE.html)
- [modify_virtcol_properties](https://docs.oracle.com/en/database/oracle/oracle-database/12.2/sqlqr/Subclauses.html)

## 2026-10-16: 単純IOTと索引圧縮の再生成

sqlapp-core-oracleで、単純な索引構成表をORGANIZATION INDEXとして再生成する。
ALL_TABLESとALL_INDEXESを一括参照してIOTの表領域を保持し、overflow/mapping用の内部表と
IOT - TOP索引を独立オブジェクトとして生成しない。主キーがない構成、無効・遅延可能な主キー、
overflow/mapping・partitioning・compression・temporaryの複合構成は明示的に拒否する。
Oracle 10g以降の既存readerに適用し、追加のSELECT往復は行わない。

索引のENABLED圧縮とPREFIX_LENGTH、ADVANCED LOW/HIGHを共通compressionTypeと
specificsで保持する。CREATE INDEXはCOMPRESS nまたはCOMPRESS ADVANCED LOW/HIGHを生成する。
LOWは12c以降、HIGHはこのプロジェクトのバージョン区分では18c以降に限定する
（12.2専用Dialectがないため12cではHIGHを拒否する）。不正なprefix長やbitmapとの組合せも拒否する。
partition/subpartition単位の詳細な圧縮と索引状態のライフサイクルは引き続き未対応。

公開API・XML形式・依存関係の変更なし。OracleIotIndexRecreationTestでmetadata→Table XML→DDL、
バージョン境界と未対応構成の拒否を検証する。実DB接続・DDL実行は行っていない。
対象6件、Oracle全215件、command関連149件、Gradle plugin関連1件が成功した
（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功し、意図的な生成ファイル変更はない。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleIotIndexRecreationTest' --tests '*OracleIndexPlacementRegressionTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [ALL_TABLES](https://docs.oracle.com/en/database/oracle/oracle-database/12.2/refrn/ALL_TABLES.html)
- [Managing Indexes](https://docs.oracle.com/en/database/oracle/oracle-database/23/admin/managing-indexes.html)

## 2026-10-16: 索引の可視性と使用可否の再生成

sqlapp-core-oracleでALL_INDEXES.STATUSをoracle.index.statusに保持し、通常の非partition索引の
UNUSABLEをCREATE INDEXへ反映する。制約のenableとは別の情報として扱う。
11g以降は専用Oracle11gIndexReaderでVISIBILITYをoracle.index.visibilityに保持し、
CREATE時にVISIBLE/INVISIBLEを生成する。Table・Mviewのreader選択と23ai reader継承を更新した。
10g以前のreaderはVISIBILITYを参照しない。既存の一括SELECTを使用し、SELECT数は増えない。

不正なvisibility/status、旧Dialectへのvisibility指定、partitioned索引のUNUSABLE指定は拒否する。
Vector索引の非標準状態も無視せず拒否する。既存索引のALTER/REBUILD、partition別状態、
関数索引のFUNCIDX_STATUSなど追加の状態管理は未対応。公開設定形式・依存関係は変更せず、
既存specificsのXML表現を利用する。追加readerとprotected hookは既存APIへの追加のみ。
実DB接続・DDL実行は行っていない。

OracleIndexStateTestでXML→DDL、旧バージョンのカラム非参照、reader選択、
未対応値・構成の拒否を検証する。最初のテストで不完全なpartitionモデルに由来する失敗を検出し、
テストモデルを修正後、対象12件が成功した。
Oracle全220件、command関連149件、Gradle plugin関連1件が成功した
（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
意図的な生成ファイル変更はなく、git diff --checkも成功した。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleIndexStateTest' --tests '*OracleIotIndexRecreationTest' --tests '*Oracle23aiIndexReaderTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [11g Managing Indexes](https://docs.oracle.com/cd/E11882_01/server.112/e25494/indexes.htm)
- [ALL_INDEXES](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_INDEXES.html)
- [CREATE INDEX](https://docs.oracle.com/en/database/oracle/oracle-database/12.2/sqlrf/CREATE-INDEX.html)

## 2026-10-16: 索引partitionの圧縮と継承

sqlapp-core-oracleで、既存のALL_IND_PARTITIONS/ALL_IND_SUBPARTITIONS一括取得から
ENABLED・ADVANCED LOW/HIGH・DISABLEDを保持する。nullを圧縮有効と誤認しない。
有効な圧縮方式は共通compressionTypeを利用し、明示的なDISABLEDはXML往復で失われないよう
oracle.index.compression specificも保持する。共通XML形式・依存関係は変更しない。

Localおよびglobal range索引のpartitionにCOMPRESS・COMPRESS ADVANCED LOW/HIGH・NOCOMPRESSを
生成する。prefix長は索引本体の設定を利用し、partitionでprefix圧縮を使うには索引本体の
prefix圧縮を必須とする。subpartitionには圧縮句を直接生成せず、partitionからの継承を検証する。
Hash partitionの個別変更は拒否し、同じ設定は継承させる。ADVANCEDのバージョン制限は
通常索引と同じくLOWが12c以降、HIGHが18c以降。不正値・bitmapの圧縮・継承矛盾は拒否する。

Global partition生成でTABLESPACEに不要な等号が入る問題と、VALUES LESS THAN (MAXVALUE)の
括弧が欠落する問題も修正した。共通のOracle partition factoryを使用する表の同じ構文にも適用する。
Global composite index指定は明示的に拒否する。変更モジュールはsqlapp-core-oracleのみで、
公開APIにはprotected metadata hookの追加のみ。追加SELECTや実DBアクセス・DDL実行はない。

OracleIndexPartitionCompressionTestでmetadata→XML→DDL、圧縮無効の上書き、
subpartition継承、バージョン境界、hash制限、global境界構文を検証する。
最初のテストでXML往復時のDISABLED消失を検出し、修正後に対象13件が成功した。
Oracle全体の初回実行では旧構文を期待する3件が失敗したため、対応する手書きSQL fixtureを
正しい構文に更新し、ALTERと圧縮の対象20件が成功した。
最終的にOracle全227件、command関連149件、Gradle plugin関連1件が成功した
（失敗・error・skip 0件）。最後の再実行ではcommand/pluginは変更がなくUP-TO-DATEで、
直前の実行結果を利用している。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功した。意図的な生成ファイル変更はない。
索引ALTER/REBUILD、partition別の状態管理や追加の高度な物理属性は引き続き未対応。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:compileJava
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleIndexPartitionCompressionTest' --tests '*OracleIndexPlacementRegressionTest' --tests '*OracleIotIndexRecreationTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleAlterTableFactoryTest' --tests '*OracleIndexPartitionCompressionTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [Using Key Compression with Partitioned Indexes](https://docs.oracle.com/database/121/VLDBG/GUID-0C13E8A8-568B-4A60-8A46-F1120D193EFE.htm)
- [ALL_IND_PARTITIONS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_IND_PARTITIONS.html)
- [ALL_IND_SUBPARTITIONS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_IND_SUBPARTITIONS.html)
- [CREATE INDEX](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/CREATE-INDEX.html)

## 2026-10-16: 既存索引の状態ALTERとREBUILD

sqlapp-core-oracleでIndexのALTER/REBUILD factoryを登録した。
Table差分でも、既存索引の可視性・使用可否だけの変更はDROP/CREATEではなくALTER INDEXを生成する。
11g以降のVISIBLE/INVISIBLE変更、VALID（または未指定）からUNUSABLEへの変更、
UNUSABLEから明示的なVALIDへのREBUILDに対応する。可視性と状態を同時に変更する場合は
別々のSQLに分ける。REBUILD後の可視性も明示的に変更できる。
Schema modelに存在しない状態を推測しないため、targetの状態削除・不正な遷移は拒否する。

通常のBTree・bitmap・関数索引が対象で、構造や他のspecificsとの同時変更は拒否する。
Partition索引の全体可視性は変更できるが、全体REBUILD/状態変更は拒否する。
Temporary table上の索引REBUILD、特殊索引（Domain/Vector/Clustered等）の状態操作も拒否する。
明示的なREBUILDは既存のregistry.createSql(index, SqlType.REBUILD)を利用し、
ALTERはcreateDiffSql(original.diff(target))を利用する。直接ALTERだけを指定する操作は拒否する。
10gの従来registryにもREBUILDを登録し、11gのvisibility制限を保つ。resolver変更なし。

新規の公開factoryを追加するが、既存API・設定形式・依存関係の変更はない。
状態変更を含まないTable内の索引構造変更は既存の差分経路を維持する。
SELECT数に変更はなく、実DB接続・DDL実行は行っていない。
Partition/subpartition単位のALTER/REBUILD、ONLINE・tablespace移動や圧縮変更、renameは引き続き未対応。

OracleIndexLifecycleTestでTable差分経由、XML往復、引用符付きschema/index名、
状態回復・別SQL生成、旧/新Dialectの登録、未対応操作の拒否とno-opを検証する。
初回はspecificsがflattenされた差分の扱いとテストモデルの組み立てで失敗し、修正後に対象テストが成功した。
対象21件、Oracle全235件、command関連149件、Gradle plugin関連1件が成功した
（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功した。意図的な生成ファイル変更はない。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:compileJava
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleIndexLifecycleTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleIndexLifecycleTest' --tests '*OracleAlterTableFactoryTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [ALTER INDEX](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-INDEX.html)
- [Managing Indexes](https://docs.oracle.com/en/database/oracle/oracle-database/19/admin/managing-indexes.html)
- [Oracle 10g ALTER INDEX](https://docs.oracle.com/cd/B13789_01/server.101/b10759/statements_1008.htm)

## 2026-10-16: 索引partition/subpartitionの状態と回復

sqlapp-core-oracleで既存のALL_IND_PARTITIONS/ALL_IND_SUBPARTITIONS一括取得から
STATUSを各partition/subpartitionのoracle.index.statusへ保持する。
10g以降の既存readerで利用でき、追加SELECTはない。XML形式・依存関係は変更しない。
通常索引本体のVALIDとは区別し、物理セグメントの状態にはUSABLE/UNUSABLEを使う。

Table差分またはIndexのALTER factoryで、USABLE（または未指定）→UNUSABLEは
ALTER INDEX MODIFY PARTITION/SUBPARTITION name UNUSABLEを生成し、
UNUSABLE→USABLEはALTER INDEX REBUILD PARTITION/SUBPARTITION nameを生成する。
名前は既存builderで引用し、schema装飾オプションも適用する。
Local/globalの単純partitionとLocal複合索引のsubpartitionを扱う。
構造・表領域・圧縮・その他の属性を同時に変更した場合や状態値の削除・不正な遷移は拒否する。
複合索引の親partitionは物理セグメントではないため、親の状態変更を拒否してsubpartition操作を求める。
Temporary table上のREBUILDと特殊索引の状態操作も従来同様に拒否する。

CREATE INDEXにも各物理セグメントのUNUSABLEを反映する。
USABLEは既定状態とし、複合親のN/AはDDLへ出力しない。再生成と差分生成を同じ状態表現に揃えた。
公開APIはprotected metadata hookの追加のみで、利用者は既存のTable/Index差分生成を使用する。
明示的な索引全体のREBUILDはpartition索引では引き続き拒否する。
ONLINE・表領域移動・圧縮ALTER・partition構造変更・複雑なIOTは未対応。
実DB接続・DDL実行は行っていない。

OracleIndexPartitionLifecycleTestとOracleIndexStateTestで、metadata/XML/CREATE、
Table差分の個別状態変更と回復、global索引、引用符付き識別子、未対応構成の拒否を検証する。
最初のコンパイルでSchemaPropertiesとSchemaObjectPropertiesの参照を修正し、対象20件が成功した。
Oracle全242件、command関連149件、Gradle plugin関連1件が成功した
（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功した。意図的な生成ファイル変更はない。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:compileJava
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleIndexPartitionLifecycleTest' --tests '*OracleIndexLifecycleTest' --tests '*OracleIndexStateTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [ALTER INDEX](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-INDEX.html)
- [Managing Indexes](https://docs.oracle.com/en/database/oracle/oracle-database/26/admin/managing-indexes.html)
- [ALL_IND_SUBPARTITIONS](https://docs.oracle.com/en/database/oracle/oracle-database/18/refrn/ALL_IND_SUBPARTITIONS.html)
- [Oracle 10g Partition Maintenance](https://docs.oracle.com/cd/B12037_01/server.101/b10739/partiti.htm)

## 2026-10-16: IOTを除く索引REBUILDオプションをまとめて対応

sqlapp-core-oracleで、通常索引の表領域移動・prefix/advanced圧縮変更をREBUILDにまとめる。
CREATE/REBUILDの圧縮構文とバージョン検証はOracleIndexCompressionへ共通化し、
圧縮無効化はNOCOMPRESSを生成する。表領域削除は移動先を推測せず拒否する。
状態回復と物理変更を同時指定してもREBUILDは重複しない。
分割索引本体の表領域はMODIFY DEFAULT ATTRIBUTES TABLESPACEで既定値だけを変更し、
実際のセグメント移動はpartition/subpartitionの表領域差分から個別REBUILDを生成する。

非複合partitionでは移動・圧縮変更・回復を1回のREBUILDにまとめる。
複合圧縮は親のMODIFY PARTITIONに続いて全subpartitionをREBUILDする。
対象モデルの子の圧縮方式は親と一致し、物理再構築後は明示的なUSABLEを求める。
個別subpartitionの圧縮変更や複合親の物理移動は拒否する。
直接Index差分のRENAMEにも対応し、同時に再構築する場合は最後にRENAME TOを生成する。
Tableの索引名対応が解決できないrenameを推測する機能は追加しない。

ONLINEは既存TableOptions.setOnlineIndex(boolean/predicate)で指定し、既定値falseを維持する。
明示的/差分由来REBUILDに適用し、10gの通常索引と11g以降のsubpartitionを扱う。
遅延可能な一意制約を支える索引、join index、bitmap index、Local hashセグメントでは拒否する。
bitmapは本generatorの保守的制限。JOIN_INDEXは既存ALL_INDEXES一括SELECTから保持する。
Local hash再構築はTABLESPACE以外の追加オプションを拒否する。
advanced圧縮の既存version制限（LOWは12c以降、HIGHは18c以降）は維持する。
追加SELECT・実DB接続・DDL実行はなく、IOT実装は変更していない。

表領域参照へのnull代入が無視される共通不具合も修正した。
当初SimpleBeanWrapper.setFieldを一律変更したところ、core全体で従来のnull無視に依存する
clone初期化が12件失敗したため、その変更を戻した。
最終実装ではSimpleBeanWrapper/SimpleBeanUtilsに明示的なsetNullableFieldを追加し、
TableSpacePropertyだけで使用する。非primitive参照の明示的なクリアを可能にし、
既存setFieldの動作、構造参照のclone契約、primitive/null拒否を保つ。
この不具合はTableSpacePropertyを利用する複数の共通モデルに関係するため、
Dialect内の回避策ではなくsqlapp-coreで修正した。既存API signature・XML形式・依存関係は変更しない。
新しい補助APIの追加と、表領域setterへのnull指定が実際に反映される動作修正がある。

OracleIndexRebuildOptionsTestは通常/partition/subpartitionの移動・圧縮・ONLINE、
XML往復、複合操作順、既定表領域、rename、version境界と未対応操作の拒否を検証する。
SimpleBeanReferenceNullTestは明示的null・不正型拒否・clone独立性とlegacy動作を検証する。
初回のOracle対象4件の失敗で参照null問題を検出し、最終修正後の対象テストは成功した。
最終回帰はcore全1191件、PostgreSQL全300件、H2全49件、Oracle全254件、
command関連149件、Gradle plugin関連1件が成功した（合計1944件、失敗・error・skip 0件）。
最後の再実行ではcore/PostgreSQL/H2は変更がなくUP-TO-DATEで、直前の成功結果を利用している。
一時表上のUNUSABLE変更も明示的に拒否する。git diff --checkは成功し、意図的な生成ファイル変更はない。
IOT、特殊索引専用のライフサイクル、partition境界や個数の変更、実DB再生成検証は対象外。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:compileJava
.\gradlew.bat :sqlapp-core:test --tests '*SimpleBeanReferenceNullTest' --tests '*SimpleBeanUtilsTest' --tests '*SimpleBeanWrapperTest' :sqlapp-core-oracle:test --tests '*OracleIndexRebuildOptionsTest' --tests '*OracleIndexPartitionLifecycleTest' --tests '*OracleIndexLifecycleTest'
.\gradlew.bat :sqlapp-core:test --tests '*SimpleBeanReferenceNullTest' --tests '*UniqueConstraintTest' --tests '*ExcludeConstraintTest' --tests '*CatalogTest' --tests '*TableTest4' :sqlapp-core-oracle:test --tests '*OracleIndexRebuildOptionsTest' --tests '*OracleIndexPartitionLifecycleTest' --tests '*OracleIndexLifecycleTest'
.\gradlew.bat :sqlapp-core:test :sqlapp-core-postgres:test :sqlapp-core-h2:test :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [ALTER INDEX](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-INDEX.html)
- [11g ALTER INDEX](https://docs.oracle.com/cd/B28359_01/server.111/b28286/statements_1009.htm)
- [10g ALTER INDEX](https://docs.oracle.com/cd/B13789_01/server.101/b10759/statements_1008.htm)

## 2026-10-16: Domain索引の再生成とパラメータALTER

sqlapp-core-oracleの既存ALL_INDEXES一括取得で、Domain/FunctionDomain索引の
ITYP_OWNER・ITYP_NAME・PARAMETERS・DOMIDX_STATUS・DOMIDX_OPSTATUSを保持する。
通常のSchema Domainとは別の、Oracle Text/Spatial等のindextypeを使う索引が対象。
10g以降の非partition Domain索引を、1カラムまたは式とINDEXTYPE IS owner.name、
PARAMETERS文字列から再生成する。Schemaモデルのspecificsを利用し、公開API・XML形式・依存関係は変更しない。

パラメータ文字列はODCIへ渡す不透明な文字列として扱う。末尾空白・引用符を保持し、
SQL literalの引用符だけをescapeする。改行・CR・TABを含むcatalog値はXML属性の正規化を避けるため
oracle.index.domain.parametersBase64へUTF-8/Base64で保持する。通常の値はPARAMETERSに保持し、
両方ある場合は明示的なPARAMETERS値を優先する。不正なBase64/UTF-8は拒否する。
ODCI固有のパラメータ構文とデータベース文字集合による長さ制約は実DBでの検証が必要。

Table/Index差分のPARAMETERS変更はDROP/CREATEではなくALTER INDEX ... PARAMETERSを生成し、
同時renameは別のRENAME TOとする。パラメータ削除は推測せず、明示的なtarget文字列を求める。
明示的REBUILDも登録済みの共通入口から利用できるが、CREATE用PARAMETERSを再送しない。
CREATE用設定がREBUILD用のODCI命令としても有効とは限らないためである。
Domain索引のONLINE・partition構成・一意/圧縮/表領域等の通常索引用属性・異常状態は明示的に拒否する。
type未指定や不足する定義を通常索引として生成しない。

NORMAL/REVのメタデータをBTreeとoracle.index.reverseへ保持し、非partition BTreeのCREATEで
REVERSEを再現する。Join索引はFROM/join述語が共通モデルにないためCREATEを拒否し、
未対応のcatalog索引種別もoracle.index.unmodeledTypeを保持してCREATEを拒否する。
既存23ai Vector/Hybrid Vectorの専用生成経路は維持する。IOT実装は変更していない。

OracleDomainIndexRecreationTestでmetadata→XML→CREATE、パラメータALTER、REBUILD、
reverse-key、引用符・改行・末尾空白、不正設定と不足定義の拒否、一括取得1クエリを検証する。
最初の対象テストでXML属性による改行消失を検出し、保存形式を修正した。
テストmockの大文字カラム名処理とschema装飾の期待値も修正し、対象12件が成功した。
Oracle全体の初回実行では、FullTextを通常BTreeとして生成する旧期待により1件が失敗した。
旧テストをindextype不足の拒否検証に更新し、対象22件が成功した。
最終的にOracle全263件、command関連149件、Gradle plugin関連1件が成功した
（失敗・error・skip 0件）。最後の再実行のcommand/pluginはUP-TO-DATEで直前の成功結果を利用した。
core・PostgreSQL・H2は今回再実行していない。git diff --checkは成功し、意図的な生成ファイル変更はない。
追加SELECT・実DB接続・DDL実行はない。Domain partition/特殊状態の回復、Join索引の完全DDL、
custom indextype自体の作成・依存解決、partition構造変更は未対応。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:compileJava
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleDomainIndexRecreationTest' --tests '*Oracle23aiIndexReaderTest' --tests '*Oracle23aiCreateIndexFactoryTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleDomainIndexRecreationTest' --tests '*Oracle23aiIndexReaderTest'
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleDomainIndexRecreationTest' --tests '*OracleAlterTableFactoryTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [ALL_INDEXES](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_INDEXES.html)
- [Building Domain Indexes](https://docs.oracle.com/en/database/oracle/oracle-database/12.2/addci/building-domain-indexes.html)
- [Oracle 10g ALTER INDEX](https://docs.oracle.com/cd/B13789_01/server.101/b10759/statements_1008.htm)

## 2026-10-16: Local Domain索引のpartition対応

sqlapp-core-oracleで、既存のALL_IND_PARTITIONS一括取得からDomain/FunctionDomain索引の
partitionごとのPARAMETERSとDOMIDX_OPSTATUSを保持する。改行等を含む値は索引本体と同じ
任意のUTF-8/Base64 specificへ保持し、通常値はPARAMETERSを使う。SELECT数は増やさない。
Oracle 10g以降のsimple Local Domain索引を対象に、
INDEXTYPE IS ... LOCAL (PARTITION name PARAMETERS (...)) PARAMETERS (...)を生成する。
Local partitionは表から境界を継承するため、索引CREATEでVALUES LESS THANなどは生成しない。

Table/Index差分でpartition固有のPARAMETERS変更はMODIFY PARTITION ... PARAMETERSを生成する。
UNUSABLE→USABLEはREBUILD PARTITION、USABLE/未指定→UNUSABLEはMODIFY PARTITION ... UNUSABLEを生成する。
回復と明示的パラメータ変更を組み合わせる場合は1回のREBUILD PARTITION ... PARAMETERSへまとめる。
回復だけならCREATE用PARAMETERSを再送しない。索引本体のパラメータ変更とrenameは別SQLで生成する。

構造・境界・物理属性を同時に変更する操作、Global/composite Domain索引、異常な操作状態、
ONLINEとLocal索引全体のREBUILDは拒否する。Domain CREATEのpartition句ではUNUSABLE状態を
直接表現できないため、その状態でのCREATEは明示的に拒否し、既存索引への差分操作だけを扱う。
通常索引用TABLESPACE/圧縮属性もPARAMETERSへ勝手に変換せず拒否する。
indextypeがpartition操作を実装していることとODCI固有パラメータの妥当性は実DBでの確認が必要。
IOT・共通Schema形式・依存関係は変更せず、公開APIにはprotected metadata hookの追加のみ。
実DB接続・DDL実行は行っていない。

OracleDomainIndexPartitionTestでmetadata→XML→CREATE、引用符/改行/末尾空白の保持、
XML往復のno-op、partitionのパラメータ変更・状態変更・回復、同時変更のSQL統合と拒否境界を検証する。
対象15件が成功した。
Oracle全269件、command関連149件、Gradle plugin関連1件が成功した
（失敗・error・skip 0件）。core・PostgreSQL・H2は今回再実行していない。
git diff --checkも成功した。意図的な生成ファイル変更はない。

実行コマンド（すべて--gradle-user-home .gradle-user-homeを指定）:

```powershell
.\gradlew.bat :sqlapp-core-oracle:compileJava
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleDomainIndexPartitionTest' --tests '*OracleDomainIndexRecreationTest'
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-command:test --tests 'com.sqlapp.data.db.command.generator.*' --tests 'com.sqlapp.data.db.command.migration.assessment.*Oracle*' :sqlapp-gradle-plugin:test --tests 'com.sqlapp.gradle.plugins.*Oracle*' --continue
```

仕様参照:

- [ALL_IND_PARTITIONS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_IND_PARTITIONS.html)
- [11g ALL_IND_PARTITIONS](https://docs.oracle.com/cd/B28359_01/server.111/b28320/statviews_1092.htm)
- [10g ALL_IND_PARTITIONS](https://docs.oracle.com/cd/B13789_01/server.101/b10755/statviews_1058.htm)
- [CREATE INDEX](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/CREATE-INDEX.html)
- [ALTER INDEX](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/ALTER-INDEX.html)

## 過去のOracle仕様参照

- [ALL_ARGUMENTS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_ARGUMENTS.html): DEFAULTEDは有無のみ、DEFAULT_VALUEは将来用。
- [ALL_TRIGGERS](https://docs.oracle.com/en/database/oracle/oracle-database/18/refrn/ALL_TRIGGERS.html): DESCRIPTIONは作成文の再構築用、TRIGGER_BODYは本体。
- [ALTER TRIGGER構文](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/img_text/alter_trigger.html): RENAMEとENABLE/DISABLEは別の選択肢。
