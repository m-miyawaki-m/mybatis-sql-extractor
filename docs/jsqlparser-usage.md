# JSqlParser 利用ガイド

本プロジェクトで使用しているJSqlParser 4.9の利用方法をまとめる。

## 概要

JSqlParserはJavaのSQLパーサーライブラリ。SQL文字列を構文木（AST）に変換し、テーブル名・カラム名・条件式などを構造的に取得できる。

本プロジェクトでは主に**SQLからの利用テーブル抽出**と**CRUD操作の分類**に使用している。

## 主要クラス

### CCJSqlParserUtil（パーサー）

SQL文字列をStatementオブジェクトに変換するエントリーポイント。

```java
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;

Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM users WHERE id = ?");
```

### TablesNamesFinder（テーブル名抽出）

SQLからテーブル名を自動抽出する静的ユーティリティ。

```java
import net.sf.jsqlparser.util.TablesNamesFinder;

// SQL文字列から直接抽出
Set<String> tables = TablesNamesFinder.findTables("SELECT * FROM users u JOIN orders o ON u.id = o.user_id");
// → [users, orders]

// WHERE句などのExpression文字列から抽出
Set<String> tables = TablesNamesFinder.findTablesInExpression("id IN (SELECT user_id FROM orders)");
// → [orders]
```

**自動処理される内容：**
- エイリアス解決（`users u` → `users`）
- サブクエリ内のテーブル（再帰的に探索）
- JOIN先のテーブル
- 重複排除（Setで返却）

### Statement型（SQL種別判定）

parseの戻り値を `instanceof` で判定してCRUD種別を特定する。

```java
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.Select;

Statement stmt = CCJSqlParserUtil.parse(sql);

if (stmt instanceof Insert) {
    Insert insert = (Insert) stmt;
    String table = insert.getTable().getName();  // INSERT先テーブル
    Select select = insert.getSelect();          // INSERT SELECTのSELECT部（null可）
}

if (stmt instanceof Update) {
    Update update = (Update) stmt;
    String table = update.getTable().getName();  // UPDATE対象テーブル
    Expression where = update.getWhere();        // WHERE句（null可）
}

if (stmt instanceof Delete) {
    Delete delete = (Delete) stmt;
    String table = delete.getTable().getName();  // DELETE対象テーブル
    Expression where = delete.getWhere();        // WHERE句（null可）
}

if (stmt instanceof Select) {
    // TablesNamesFinder.findTablesで全テーブルを取得可能
}
```

## 本プロジェクトでの利用パターン

### TableExtractor.extractTableUsages()

SQL文から操作種別付きのテーブル利用情報を抽出する。

```
SQL → CCJSqlParserUtil.parse() → Statement
  ├─ Insert → INSERT先テーブル + SELECT元テーブル（INSERT SELECTの場合）
  ├─ Update → UPDATE対象テーブル + WHERE内サブクエリのテーブル
  ├─ Delete → DELETE対象テーブル + WHERE内サブクエリのテーブル
  └─ Select → 全テーブル（FROM, JOIN, サブクエリ含む）
```

**INSERT SELECTの例：**
```java
Statement stmt = CCJSqlParserUtil.parse(
    "INSERT INTO archive SELECT * FROM users u JOIN orders o ON u.id = o.user_id");
Insert insert = (Insert) stmt;

insert.getTable().getName();  // → "archive"（INSERT先）
insert.getSelect();           // → SELECT文のAST（users, ordersを含む）
```

**WHERE内サブクエリの例：**
```java
Statement stmt = CCJSqlParserUtil.parse(
    "DELETE FROM users WHERE id IN (SELECT user_id FROM blacklist)");
Delete delete = (Delete) stmt;

delete.getTable().getName();           // → "users"（DELETE対象）
delete.getWhere().toString();          // → "id IN (SELECT user_id FROM blacklist)"
TablesNamesFinder.findTablesInExpression(delete.getWhere().toString());
// → [blacklist]
```

## 対応するSQLパターン

| パターン | 対応 | 例 |
|---|---|---|
| 基本CRUD | ○ | `SELECT / INSERT / UPDATE / DELETE` |
| エイリアス | ○ | `users u`, `users AS u` |
| JOIN | ○ | `INNER / LEFT / RIGHT / FULL / CROSS JOIN` |
| サブクエリ（WHERE） | ○ | `WHERE id IN (SELECT ...)` |
| サブクエリ（FROM） | ○ | `FROM (SELECT ...) sub` |
| サブクエリ（SELECT） | ○ | `SELECT (SELECT COUNT(*)) AS cnt` |
| INSERT SELECT | ○ | `INSERT INTO a SELECT * FROM b` |
| CTE | ○ | `WITH cte AS (...) SELECT * FROM cte` |
| UNION | ○ | `SELECT ... UNION SELECT ...` |
| Window関数 | ○ | `OVER (PARTITION BY ...)` |
| CASE式 | ○ | `CASE WHEN ... THEN ... END` |

## 注意点・制限事項

### NULLチェック必須

```java
// getWhere() はWHERE句がないとnullを返す
if (update.getWhere() != null) {
    // ...
}

// getSelect() はINSERT...VALUESの場合nullを返す
if (insert.getSelect() != null) {
    // INSERT SELECT
}
```

### パース失敗時の対応

不正なSQLやDB固有構文でParseExceptionが発生する場合がある。

```java
try {
    Statement stmt = CCJSqlParserUtil.parse(sql);
} catch (JSQLParserException e) {
    // パース失敗 → 空リストを返すなどフォールバック
}
```

本プロジェクトでは `catch (Exception e)` でフォールバックし、空リストを返す。

### DB固有構文

- MySQL, PostgreSQL, Oracle等の主要DBの構文をサポート
- ただし一部のベンダー固有構文（PL/SQL等）はパースできない場合がある
- MyBatis Mapper XMLのDML（SELECT/INSERT/UPDATE/DELETE）は基本的に問題なし

### MyBatisプレースホルダ

MyBatisが生成する `?` プレースホルダはJSqlParserで正常にパースされる。
`#{param}` はMyBatisが `?` に変換した後のSQLを渡すため、問題にならない。

## 参考

- JSqlParser GitHub: https://github.com/JSQLParser/JSqlParser
- JSqlParser API: `net.sf.jsqlparser` パッケージ
- 本プロジェクトでの実装: `src/main/java/com/example/mybatis/extractor/TableExtractor.java`
