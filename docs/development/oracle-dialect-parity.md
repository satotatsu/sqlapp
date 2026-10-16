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

## 過去のOracle仕様参照

- [ALL_ARGUMENTS](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/ALL_ARGUMENTS.html): DEFAULTEDは有無のみ、DEFAULT_VALUEは将来用。
- [ALL_TRIGGERS](https://docs.oracle.com/en/database/oracle/oracle-database/18/refrn/ALL_TRIGGERS.html): DESCRIPTIONは作成文の再構築用、TRIGGER_BODYは本体。
- [ALTER TRIGGER構文](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/img_text/alter_trigger.html): RENAMEとENABLE/DISABLEは別の選択肢。
