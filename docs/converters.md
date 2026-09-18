# 型変換（sqlapp-core）

共通の入口は `new Converters()` または `Converters.getDefault()` です。
設定を変更する場合は、他の利用箇所に影響しないよう独立したインスタンスを使用します。

```java
Converters converters = new Converters();
Character letter = converters.convertObject("日", Character.class);
char[] text = converters.convertObject("日本語", char[].class);
boolean[] flags = converters.convertObject(List.of("yes", "off"), boolean[].class);
Number number = converters.convertObject("12.50", Number.class); // BigDecimal
```

## 追加された変換先

| 型 | 入力と既定値 |
| --- | --- |
| `Character` / `char` | `Character` またはUTF-16の1コード単位の `CharSequence`。null・空文字は `Character` ではnull、`char` では `\0`。空白はそのまま保持。複数コード単位や数値入力は `ConverterException`。 |
| `Character[]` | 配列、Collection、Iterable、JDBC Arrayの各要素をCharacterに変換。null要素を保持。単一入力は1要素として処理。 |
| `char[]` | 上記の各要素変換に加え、CharSequenceは全文を配列化。null要素は `\0`。文字列への変換は配列全体を文字列に戻す。 |
| `Boolean[]` / `boolean[]` | 既存のBooleanConverterを要素変換に使用。`true`/`false`、`yes`/`no`、`1`/`0` などを受け付ける。null要素はラッパー配列ではnull、プリミティブ配列ではfalse。既存の未知の真偽値入力に対する既定値動作を継承。 |
| `Number` | 既存のNumberConverterを登録。Number入力は保持し、それ以外は既定でBigDecimalへ変換。`setNumberConverter` で既定クラスを変更可能。 |

サロゲートペアを必要とする文字は `Character` には入りません。文字列または `char[]` を使ってください。
配列入力のコピーは新しい配列を返します。配列全体のnull入力は従来どおりnullです。
`setDefaultValue` に設定した配列Supplierは `getDefaultValue()` 時に評価され、返却配列はコピーされます。

## レビューで修正した問題

- **P1: 数値書式指定時の自己再帰。** Byte、Short、Integer、Long、Float、Double、BigInteger、BigDecimalの `format` が自身を呼び出していたため、共通のNumberFormat処理を呼び出すよう修正。
- **P1: ZoneOffsetの情報欠落。** TimeZoneConverterがZoneIdの文字列表現を使うことで固定オフセットをGMTへ変換していたため、ZoneIdを受け取るオーバーロードを使用。
- **P2: JDBC Arrayの変換失敗。** AbstractArrayConverterで `getArray()` の結果ではなくJDBC Array自身を参照していた箇所を修正。
- **P2: プリミティブ配列の処理失敗。** IterableのObject配列をプリミティブ配列へ直接コピーしていた処理と、書式化時のObject配列へのキャストを修正。
- **P2: 配列の既定値Supplierが評価されない。** Supplierの戻り値をコピーするよう修正。
- **P2: コンバーター設定・登録の欠落。** `setStringConverter` の引数をString/Clobにも反映し、`setNumberConverter` でNumber自体も登録。Number派生型の書式化が再帰しないよう対処。
- **P2: LocalDateTimeのISO書式化失敗。** オフセットを持たない値にISO_INSTANTを使っていたため、ISO_LOCAL_DATE_TIMEに変更。
- **P2: nullと等価比較の例外。** StringConverterのintern有効時のnull処理、DefaultConverter.equalsの誤ったDateConverterへのキャストを修正。

変更は共通Java型の変換に限定し、既存メソッドのシグネチャ、依存ライブラリ、Schema XMLや設定形式は変更していません。
追加型に対する `isConvertable` と変換結果、および従来例外となっていた上記の動作が変わります。
日時や数値の既定書式は維持し、明示的なISO/NumberFormat指定時の不具合を修正しています。

## 継続検討事項

- 一部の `equals` は `super.equals(this)` を呼んでおり、相手側の既定値や数値書式を比較していません（例: BooleanConverter、IntegerConverter）。等価性全体の統一は今回の変更には含めていません。
- Convertersの継承型検索キャッシュは登録変更時に全面無効化されません。型を一度検索した後で基底型のコンバーターを追加・置換する動的な使い方には別途検証が必要です。
- ファイルシステム型や追加の暦型は、具体的な入出力用途が確定してから検討します。java.timeのMonthDay・Month・DayOfWeek対応は以下に記載します。

## java.timeの月日・月・曜日

```java
Converters converters = new Converters();
MonthDay anniversary = converters.convertObject("--02-29", MonthDay.class);
Month month = converters.convertObject(12, Month.class); // DECEMBER
DayOfWeek day = converters.convertObject(1, DayOfWeek.class); // MONDAY
MonthDay[] dates = converters.convertObject(List.of("--01-01", "--12-31"), MonthDay[].class);
```

- `MonthDay` はISO形式 `--MM-dd` を読み書きします。`--02-29` は有効、`--02-30` や `--04-31` は例外です。null・空白のみの文字列は既定値（初期値null）を返します。
- `MonthDay`、`Month`、`DayOfWeek` は、必要な日付フィールドを持つ `TemporalAccessor` と `java.sql.Date` から抽出できます。OffsetDateTime/ZonedDateTimeの現地の日付を維持します。Instantや不完全な日付からタイムゾーン・不足フィールドを推測せず、例外にします。
- `Month` は1〜12、`DayOfWeek` はISOの1〜7（月曜〜日曜）の数値と整数文字列を受け付けます。小数部分のある数値・範囲外・整数オーバーフローは切り捨てずに例外にします。
- 既存の `JANUARY`、`MONDAY` などの列挙名による変換と出力を維持します。Enumの空文字設定も従来どおりで、必要なら初回利用前に `setEnumEmptyToNull(true)` を指定します。
- いずれもOptional/Supplier入力に対応します。`MonthDay[]` を追加し、既存の `Month[]` / `DayOfWeek[]` にも数値・日付の要素変換が適用されます。
- 共通の入口は `Converters` です。独立利用には `MonthDayConverter`、`MonthConverter`、`DayOfWeekConverter` を利用できます。既存の公開メソッド、Schema XML、設定形式、依存ライブラリの変更はありません。

回帰テストは `ConverterRegressionTest` にあり、JDBC Arrayはスタブを使って解放と例外経路を検証します。外部データベースを必要としません。

## 検証結果（2026-09-18）

Java 21とリポジトリのGradle Wrapperを使用しました。
実行時のみ `GRADLE_USER_HOME=C:/Users/satot/.gradle` として既存キャッシュを利用しています。

```powershell
.\gradlew.bat :sqlapp-core:test --tests 'com.sqlapp.data.converter.ConverterRegressionTest' --console=plain --offline
.\gradlew.bat :sqlapp-core:test --console=plain --offline
.\gradlew.bat :sqlapp-command:test --tests '*ConvertDataCommandTest' --tests '*TableFileReaderTest' :sqlapp-core-postgres:test --tests '*PostgresArrayColumnTypeMatcherTest' --console=plain --offline
```

- 追加回帰テスト16件: 成功。途中で数値書式のオーバーロード選択の誤りを検出し、修正後に再実行して成功。
- sqlapp-core全体1,032件（追加16件を含む）: 失敗・スキップともに0件。
- 上記command / PostgreSQLの関連テスト: 成功。
- 外部DB接続なし。coreテストでは既存の組み込みHSQLDBテストを含む。
- その他のモジュール全体テスト、実DBとの結合テストは未実行。
- ソース管理対象の生成ファイル変更なし。テストによるbuild出力のみ生成。

java.time追加後は、以下の専用テスト9件と `:sqlapp-core:test` 全1,041件を実行し、失敗・スキップともに0件でした。

```powershell
.\gradlew.bat :sqlapp-core:test --tests '*JavaTimeValueConverterTest' --console=plain --offline
```
