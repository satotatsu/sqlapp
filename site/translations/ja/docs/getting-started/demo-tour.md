# sqlappの3つのワークフローを試す {#try-three-sqlapp-workflows}

[ドキュメント一覧](../README.md) · [はじめに](README.md)

ひとつのコマンドで、共通Schemaモデルによるドキュメント生成、変更確認、検証付きデータ移行を確認します。
これらのリポジトリ内デモは架空の顧客・注文データを使用し、外部DBは不要です。

## 一度実行して結果を開く {#run-once-and-open-the-results}

Java 25で、リポジトリのルートから実行します。

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

Windows PowerShellの場合：

```powershell
.\gradlew.bat --init-script docs/examples/offline-html/demo.init.gradle demoSqlapp
```

`build/docs/demo/index.html`を開きます。このページは実際の検証レポートから移行結果を読み取り、
生成した出力へリンクします。3つのデモがすべて成功した後に書き込まれます。

初回ビルドではGradleと依存関係をダウンロードする場合があります。
キャッシュ済みなら`--offline`を指定できます。ドキュメントとSQL生成はXMLを読み込み、
移行はプロセス内に独立した2つのHSQLメモリDBを作成します。外部DB接続設定は受け付けません。

## 各ステップで確認できること {#what-each-step-demonstrates}

| ステップ | 開くもの・比較するもの | 確認点 |
|---|---|---|
| 構造を理解して共有する | HTMLリファレンス、テーブルDDL、ER図 | 同じモデルからページ・SQL・リレーション図を生成 |
| 変更を確認する | 変更前後のHTMLと`change.sql` | `CUSTOMER.EMAIL`の追加がモデルのドキュメントとHSQLの生成SQLに反映 |
| 転送して検証する | 実行・一致・不一致のレポート | 5行の転送成功後、顧客名の変更を行数が同じでも不一致として検出 |

不一致は意図的で、デモ内で検証します。Gradleの成功は期待した不一致を検出したことを意味し、
変更した移行先を修復したことを意味しません。修復も生成した差分SQLも実行しません。
本番適用性、性能、クラッシュ復旧を示す例ではありません。複数テーブルのジョブは単一の原子的トランザクションではありません。

## 個別のステップを確認・応用する {#explore-or-adapt-one-step}

- [ドキュメントデモ](offline-demo.md)：サンプルXML、HTML構造、図からの移動。
- [変更確認デモ](offline-demo.md#review-a-schema-change)：変更後のモデルと生成SQL。
- [移行デモ](offline-migration.md)：架空の行、実際のレポート、検証内容。
- [ワークフローの選択](use-cases.md)：運用上の境界と組み込み方法。

各デモはそれぞれのガイドのタスクで個別に実行できます。再実行すると生成物を置き換え、新しいメモリDBを作成します。
XMLの元データを変更したり、前回の移行を再開したり、通常ビルドにタスクを追加したりしません。initスクリプトは明示的に指定した場合のみ有効です。

共有するときは`build/docs/demo`、`build/docs/offline-demo`、`build/docs/offline-change`、
`build/docs/offline-migration`を同じディレクトリ内に保持し、概要ページの相対リンクを維持してください。
