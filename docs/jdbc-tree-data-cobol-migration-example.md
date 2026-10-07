# COBOLの階層処理をJavaへ移植するサンプル

実在のプログラムからの移植ではなく、受注の金額確定バッチを想定した教材です。
Java版は [JdbcTreeDataSessionCobolMigrationExampleTest](../sqlapp-core-h2/src/test/java/com/sqlapp/data/db/dialect/h2/examples/JdbcTreeDataSessionCobolMigrationExampleTest.java)
にあり、組み込みH2で実行できます。業務処理は `priceOrders`、DDL・入力データは
`database`、結果検証はテストメソッドに分けています。

## 業務と階層

```text
SALES_ORDER（受注）
  └─ SALES_LINE（受注明細、ORDER_IDで親に関連）
       └─ SHIPMENT_ALLOCATION（出荷割当、LINE_IDで親に関連）
```

READYの受注だけをSQLで選び、出荷割当の数量×明細単価から割当金額を求めます。
割当金額を明細へ、明細金額を受注へ積み上げ、受注をPRICEDにします。
割当数量の合計が受注数量と違う場合や数量・単価が不正な場合は失敗させます。
通貨は小数2桁、数量は整数とし、Javaでは `BigDecimal` を使います。
税・値引き・端数丸めはこの例の対象外です。

| 受注 | 入力 | 処理後 |
| --- | --- | --- |
| 1 | 明細11: 単価100、割当1+2個。明細12: 単価25、割当1+1個 | 300+50 = 350.00 |
| 2 | 明細21: 単価10、割当1+2個 | 30.00 |
| 3 | 明細31: 単価15、割当1個 | 15.00 |
| 4 | HOLD、配下に明細と割当あり | 全階層とも変更しない |

## 想定する移植元

以下はCOBOL風の疑似コードです。コンパイル可能なCOBOLソースではありません。
ファイル宣言やSQLホスト変数、READ・UPDATE・エラー処理の実装は省略しています。
READ-NEXT-LINEとREAD-NEXT-ALLOCATIONは、現在の親キーに属するレコードだけを
順に返し、親を切り替えると終了フラグと読み取り位置を初期化する想定です。

```cobol
PERFORM READ-NEXT-READY-ORDER
PERFORM UNTIL END-ORDER
    MOVE ZERO TO ORDER-AMOUNT
    PERFORM READ-NEXT-LINE
    PERFORM UNTIL END-LINE
        PERFORM VALIDATE-PRICE-AND-ORDERED-QTY
        MOVE ZERO TO LINE-AMOUNT ALLOCATED-TOTAL
        PERFORM READ-NEXT-ALLOCATION
        PERFORM UNTIL END-ALLOCATION
            PERFORM VALIDATE-ALLOCATION-QTY
            COMPUTE ALLOCATION-AMOUNT = ALLOCATED-QTY * UNIT-PRICE
            ADD ALLOCATED-QTY TO ALLOCATED-TOTAL
            ADD ALLOCATION-AMOUNT TO LINE-AMOUNT
            PERFORM UPDATE-ALLOCATION
            PERFORM READ-NEXT-ALLOCATION
        END-PERFORM
        IF ALLOCATED-TOTAL NOT = ORDERED-QTY
            PERFORM ABORT-BATCH
        END-IF
        PERFORM UPDATE-LINE
        ADD LINE-AMOUNT TO ORDER-AMOUNT
        PERFORM READ-NEXT-LINE
    END-PERFORM
    MOVE 'PRICED' TO ORDER-STATUS
    PERFORM UPDATE-ORDER
    PERFORM READ-NEXT-READY-ORDER
END-PERFORM
PERFORM COMMIT-BATCH
```

## Javaへの対応

| 移植元の役割 | Java版 |
| --- | --- |
| READY受注の読み取り | パラメータ付き `session.select(orders, sql, context)` |
| 親キーを使った明細・割当の読み取り | SchemaのFKと `select(lines)` / `select(allocations)` |
| 3段のPERFORMとレコード領域 | 3段の `while (session.next(table))` と `getRow(table)` |
| 集計用WORKING-STORAGE | 受注ごと・明細ごとのローカル変数 |
| COMPUTE / ADD | `BigDecimal.multiply` / `add` |
| UPDATEレコード | `row.put(...)` と `row.update()` |
| 異常終了 | 受注・明細ID付きの例外を送出 |
| COMMIT / ROLLBACK | `execute`の成功・失敗境界 |

子カーソルは現在の親に属する行だけを返すため、業務コードで親キーの比較や
読取り位置の管理をしません。明細・割当の読取りと更新はルートバッチ単位で
まとめられ、各行のループ内で個別のSELECTやUPDATEを発行する必要もありません。
`TableOperationMode.NONE`により、明示的に `update()` した行だけを書き込みます。

この例は `rootBatchSize = 2` で3受注を2バッチに分け、最後の1件も処理します。
定期コミットは設定せず、成功時に全体を1回コミットします。専用の接続を使い、
auto-commitは無効にしてください。最終受注の割当数量を不正にする失敗テストでは、
先に実行済みの受注1・2の更新も親・子・孫すべてロールバックされます。
失敗時の実行レポートから、実行済み件数とコミット済み件数を区別できます。

```powershell
.\gradlew.bat :sqlapp-core-h2:test --tests '*JdbcTreeDataSessionCobolMigrationExampleTest' --console=plain
```

実際の移植では、元プログラムの丸め規則、NULL・空白の扱い、レコード順序、
排他制御、コミット単位を別途合わせる必要があります。この教材のループ内の集計は
順序に依存しません。また、空の受注は合計0で確定する仕様です。
