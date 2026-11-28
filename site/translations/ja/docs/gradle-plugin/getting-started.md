# Gradle Plugin入門 {#getting-started-with-the-gradle-plugin}

[タスクガイド](README.md) · [Schema・SQL・HTMLリファレンス](schema-sql-and-html.md)

これらのワークフローを含むビルド全体は、[`sqlapp-gradle-example`プロジェクト](https://github.com/satotatsu/sqlapp-gradle-example)と[プロジェクト対応表](example-project.md)を参照してください。

`com.sqlapp.db`プラグインはsqlappのコマンドをGradleタスクとして提供します。
一般的な流れは、一度読み取ったDBメタデータをSchema XMLに保存し、そのファイルからHTMLやSQLを生成する方法です。
以下はGroovy DSLとJava 21を使用した例です。

## 1. プラグインとDBランタイムを設定する {#1-apply-the-plugin-and-supply-the-database-runtime}

Gradle Wrapperのあるプロジェクトで`build.gradle`を作成します。

```groovy
plugins {
    id 'java'
    id 'com.sqlapp.db' version '<sqlapp-version>'
}

repositories {
    mavenCentral()
}

dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:<sqlapp-version>'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

バージョンのプレースホルダーを選択したリリースに置換し、sqlappのDialectとプラグインを同じバージョンに揃えてください。
ここではPostgreSQLを例にしています。他のDBでは対応する`sqlapp-core-{db}`モジュールとJDBCドライバーを指定します。
Dialectはメタデータ取得・SQL生成を、ドライバーはJDBC接続を提供します。役割は異なります。

関連サンプルでは`gradle.properties`で選択します。

```properties
db=hsql
jdbc_driver=org.hsqldb:hsqldb:2.7.4
schema_level=catalog
dataSourceProperties=./src/main/config/local/dataSource.properties
defaultEncoding=UTF-8
```

サンプルの`build.gradle`には`copyLib`タスクもあるため、これらを`implementation`依存関係に対応付けています。
通常の実行では`implementation`でも`runtimeOnly`でもJavaの`runtimeClasspath`に到達します。
ドライバーやDialectのクラスに対してコンパイルする場合にのみ`implementation`を使用してください。

`java`プラグインが`runtimeClasspath`を作成し、sqlappのタスクは構築時にこの構成を取得します。
カスタムタスク型でも同じです。Javaプラグインを使用しないビルドでは、構成を明示します。

```groovy
configurations { sqlappRuntime }
dependencies {
    sqlappRuntime 'com.sqlapp:sqlapp-core-postgres:<sqlapp-version>'
    sqlappRuntime 'org.postgresql:postgresql:<jdbc-driver-version>'
}
tasks.withType(com.sqlapp.gradle.plugins.AbstractTask).configureEach {
    runtimeClasspath.from(configurations.sqlappRuntime)
}
```

ランタイムの設定はどちらかを選びます。`com.sqlapp.db`の適用だけでは、汎用JDBC依存関係の構成は作成されません。

## 2. メタデータを出力する {#2-export-metadata}

`build.gradle`に次の設定を追加します。

```groovy
tasks.named('exportSchemaXml') {
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
    }
    includeSchemas.add('public')
    target = 'catalog'
    dumpRows = false
    outputDirectory = layout.buildDirectory.dir('schema')
    outputFileName = 'Catalog.xml'
}
```

実行前にDB用の環境変数を設定してください。
メタデータだけを出力するときは`dumpRows = false`を明示します。コマンドの既定値は`true`です。
`includeSchemas`はスキーマ名でメタデータを選択するもので、接続の既定スキーマを変更しません。

```shell
./gradlew exportSchemaXml
```

Windows PowerShellでは`.\gradlew.bat exportSchemaXml`を使用します。出力先は`build/schema/Catalog.xml`です。

## 3. XMLからHTMLを生成する {#3-generate-html-from-the-xml}

```groovy
tasks.named('generateHtmlDocs') {
    dependsOn(tasks.named('exportSchemaXml'))
    targetFile = layout.buildDirectory.file('schema/Catalog.xml')
    outputDirectory = layout.buildDirectory.dir('docs/database')
}
```

```shell
./gradlew generateHtmlDocs
```

先にエクスポートを実行し、`build/docs/database`にドキュメントを書き込みます。
HTMLタスク自体はXMLを読み込むためDB接続は不要です。
保存済み・確認済みのスナップショットなら`dependsOn`を省略し、`targetFile`を
`layout.projectDirectory.file('schemas/Catalog.xml')`に設定してください。この方法はDBからスナップショットを更新しません。

プラグインは各タスクを登録しますが、出力・比較・ドキュメント・移行を自動的には連結しません。
ファイル名が一致しているだけではタスク依存関係になりません。入力を生成する必要がある場合は`dependsOn`を設定します。

## 接続設定 {#connection-settings}

`dataSource { ... }`はタスクのHikariデータソースを設定します。
バージョン管理された移行タスクは、[移行ガイド](versioned-migrations.md#versioned-migrations)の`migration`拡張を共有します。

| プロパティ | 型 | 意味 |
|---|---|---|
| `jdbcUrl` | `Property<String>` | JDBC URL。`url`ではなくこの名前を使用 |
| `driverClassName` | `Property<String>` | 必要に応じてJDBCドライバークラスを明示 |
| `username`, `password` | `Property<String>` | 接続認証情報 |
| `passwordEnv` | `Property<String>` | パスワードを保持する環境変数名。両方ある場合は`password`が優先 |
| `catalog`, `schema` | `Property<String>` | 接続の既定カタログ・スキーマ |
| `maximumPoolSize` | `Property<Integer>` | 拡張で未設定の場合は5 |
| `minimumIdle` | `Property<Integer>` | 拡張で未設定の場合は0 |
| `autoCommit` | `Property<Boolean>` | 任意の接続設定。コマンドのトランザクション処理も適用 |
| `properties` | `ConfigurableFileCollection` | 接続設定ファイル |
| `dialectOptions` | `MapProperty<String, String>` | この接続のsqlapp Dialectオプション。例：`oracle.maxStringSize`。[Dialectオプション](#dialect-options)参照 |

既存のHikari propertiesファイルを読み、認証情報を上書きする例です。

```groovy
tasks.named('exportSchemaXml') {
    dataSource {
        properties file('config/jdbc.properties')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
        maximumPoolSize = 2
    }
}
```

```properties
jdbcUrl=jdbc:postgresql://localhost:5432/app
driverClassName=org.postgresql.Driver
```

拡張に明示した値がファイルの値を上書きします。プールサイズのフォールバック値5と0もファイル読み込み後に適用されます。
そのため、この2つの値を変える場合は拡張に設定してください。認証情報をコミットするビルドスクリプトやpropertiesファイルに保存しないでください。

設定ローダーはJava-properties XML、JSON、YAML、YMLも認識します。
指定したファイルがない場合は明確なメッセージで停止し、不明な設定名は警告します。
`properties`を繰り返し呼び出せば複数ファイルを指定できます。
ファイル内の`passwordEnv`にはパスワードの環境変数名を指定できます。
結合の動作、パスワードの選択肢、関連プロジェクトの形式は[タスクリファレンス](task-reference.md#configure-a-data-source-from-a-file)を参照してください。

### Dialectオプション {#dialect-options}

sqlappは接続先DBからDialectを選択します。
生成SQLを変える一部のDB設定は、データソースごとの`dialectOptions`、または設定ファイルの`dialect.*`キーで固定できます。
拡張の値がファイルの値を上書きします。接続先が対応していないオプションや無効な値を指定するとタスクは停止します。

| オプション | 値 | 意味 |
|---|---|---|
| `oracle.maxStringSize` | `STANDARD`, `EXTENDED` | Oracleの`MAX_STRING_SIZE`。VARCHAR2/NVARCHAR2の最大長は4000または32767バイト、RAWは2000または32767バイト。Oracle 12c以降では`EXTENDED`を指定可能 |
| `mysql.tinyInt1Mapping` | `BOOLEAN`（既定）, `NUMERIC` | MySQL/MariaDB/Aurora MySQLの符号付きTINYINT(1)の解釈。数値の元データを保つには`NUMERIC`。[TINYINT(1)の値](../data/mysql-tinyint.md)参照 |

オプションがなければ、Oracle 12c以降では追加権限が不要な`SELECT CAST(NULL AS VARCHAR2(32767)) FROM DUAL`で
`MAX_STRING_SIZE`を検出します。結果はJVM（Gradleデーモン）の存続期間中、JDBC URLごとにキャッシュされます。
Schema XMLからSQLを生成する場合など、接続がないときは`STANDARD`です。
接続せずにSQLを生成するタスク（`generateSql`、`generateDiffSql`、`generateLegacyRdbLoader`）では、同じオプションをタスクプロパティとして設定できます。

```groovy
tasks.named('generateDiffSql') {
    dialectOptions.put('oracle.maxStringSize', 'EXTENDED')
}
```

デスクトップアプリケーションは読み込んだデータソース設定ファイルの`dialect.*`キーを参照します。
例えば`MAX_STRING_SIZE=STANDARD`のDBにも収まるテーブルだけを作成する転送では、検出値を上書きします。

```groovy
tasks.named('transferDatabase') {
    dataSource {
        jdbcUrl = 'jdbc:oracle:thin:@//target.example:1521/app'
        dialectOptions.put('oracle.maxStringSize', 'STANDARD')
    }
}
```

```yaml
jdbcUrl: jdbc:oracle:thin:@//target.example:1521/app
dialect:
  oracle:
    maxStringSize: STANDARD
```

## トラブルシューティング {#troubleshooting}

| 症状 | 確認点 |
|---|---|
| タスク名が見つからない | `./gradlew tasks --all`で、プラグインのバージョンとカスタム登録の必要性を確認 |
| ドライバークラスをロードできない | 無関係なbuildscript構成だけでなくタスクの`runtimeClasspath`にドライバーを追加 |
| DBのDialectを解決できない | 対応するsqlapp Dialectを含め、Schema XMLのDB製品・バージョン情報を保持 |
| プロパティや入力ファイルがない | 必須入力と生成元への依存関係を設定。`--dry-run`は実行順だけを確認し、ファイル内容は確認しない |
| 出力時に想定外に行データを読む | メタデータだけのXMLには`dumpRows = false`を指定 |
| SQLがコンソールにしか出ない | SQL生成タスクの`outputDirectory`を設定 |
| オフラインのドキュメント生成でDBに接続する | 出力タスクへの依存関係を除き、保存済みXMLを指定 |

失敗時のトレースは`./gradlew <task> --stacktrace`で確認します。
タスク実装は既定でビルドキャッシュを無効にしています。DBの状態がGradleの入力で表現される、あるいはビルドスクリプトに変更がなければDBにも変更がない、と仮定しないでください。

クラスパス、Dialect、DataSourceファイル、タスク順序、出力先、バグ報告に必要な情報は[トラブルシューティングガイド](troubleshooting.md)を参照してください。

これらはソースとテストに照らして確認した設定例であり、利用者のDBで実行したという主張ではありません。
sqlapp自体の検証は[ビルドとテスト](../development/build-and-test.md)を参照してください。
