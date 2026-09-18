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
- 新たなjava.time型やファイルシステム型の追加は、具体的な入出力用途が確定してから行うのが適切です。今回は基本型の明確な欠落に限定しました。

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
