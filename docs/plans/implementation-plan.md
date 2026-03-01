# MyBatis SQL Extractor 実装計画書

## 概要

MyBatis Mapper XMLファイルから実行可能なSQL文を抽出するJavaツール。
MyBatisのConfiguration APIを使用し、動的SQL（`<if>`, `<choose>`, `<foreach>`等）を正確に解析する。

## アーキテクチャ: MyBatis Configuration API方式

### 処理フロー

```
1. Mapper XMLファイルを指定ディレクトリから読み込み
2. MyBatis Configurationをプログラム的に構築（DB接続不要）
3. XMLMapperBuilderでMapper XMLを解析 → MappedStatementを生成
4. 各MappedStatementからSqlSource → BoundSqlを取得
5. ダミーパラメータを注入して実行可能SQLを生成
6. SQLを整形して出力
```

### コア技術

- **Configuration**: MyBatisの設定を保持するオブジェクト。プログラム的に生成可能。
- **MappedStatement**: XMLの各SQL文（select/insert/update/delete）に対応するオブジェクト。
- **SqlSource**: SQLの生成元。DynamicSqlSource（動的SQL）とRawSqlSource（静的SQL）がある。
- **BoundSql**: 最終的な実行可能SQL文とパラメータマッピングを保持。
- **DynamicContext**: 動的SQLのノード（SqlNode）を評価する際のコンテキスト。

### ダミーDataSource

DB接続は不要だが、MyBatisのConfiguration生成にはEnvironment（DataSource含む）が必要。
UnpooledDataSourceでダミーJDBC URLを指定するか、最小限のDataSourceモックを使用する。

## プロジェクト構成

```
mybatis-sql-extractor/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── .gitignore
├── docs/
│   ├── plans/
│   │   └── implementation-plan.md    # この計画書
│   ├── glossary.md                   # 用語解説
│   ├── algorithm.md                  # アルゴリズム解説
│   └── reference.md                  # APIリファレンス
├── src/
│   ├── main/java/com/example/mybatis/
│   │   ├── Main.java                 # CLIエントリーポイント
│   │   ├── extractor/
│   │   │   ├── SqlExtractor.java     # SQL抽出メインロジック
│   │   │   └── SqlResult.java        # 抽出結果のデータクラス
│   │   ├── config/
│   │   │   └── MyBatisConfigBuilder.java  # Configuration構築
│   │   ├── parameter/
│   │   │   └── DummyParameterGenerator.java  # ダミーパラメータ生成
│   │   └── formatter/
│   │       └── SqlFormatter.java     # SQL整形
│   └── test/java/com/example/mybatis/
│       ├── extractor/
│       │   └── SqlExtractorTest.java
│       └── parameter/
│           └── DummyParameterGeneratorTest.java
└── src/test/resources/
    └── mappers/
        ├── simple-mapper.xml         # 基本CRUD
        ├── dynamic-mapper.xml        # 動的SQL（if, choose, where, set）
        ├── foreach-mapper.xml        # foreach
        ├── include-mapper.xml        # sql/include
        └── complex-mapper.xml        # 複合パターン
```

## クラス設計

### Main.java
- CLIオプション解析（--input, --output, --format）
- ファイルスキャンとSqlExtractor呼び出し
- 結果の出力（コンソール/ファイル）

### SqlExtractor.java
- Mapper XMLからSQLを抽出するメインクラス
- `extractAll(File mapperXml): List<SqlResult>` — 全SQL抽出
- `extractById(File mapperXml, String id): SqlResult` — ID指定抽出
- MyBatis Configuration構築を委譲

### SqlResult.java
- 抽出結果: namespace, id, sqlCommandType, sql, parameterMappings

### MyBatisConfigBuilder.java
- ダミーEnvironment/DataSource付きConfigurationを構築
- XMLMapperBuilderでMapper XMLを登録
- TypeAlias、TypeHandler等の最小設定

### DummyParameterGenerator.java
- パラメータ型に基づくダミー値生成
- String → "dummy", Integer → 1, List → [1,2,3] 等
- MapベースのパラメータObjectを構築

### SqlFormatter.java
- SQL文の整形（改行、インデント）
- `?`プレースホルダの表示
- パラメータ情報のコメント付加

## 依存ライブラリ

| ライブラリ | バージョン | 用途 |
|-----------|-----------|------|
| org.mybatis:mybatis | 3.5.16 | MyBatis本体 |
| org.junit.jupiter:junit-jupiter | 5.10.2 | テスト |

## テスト方針

1. **基本CRUD**: SELECT/INSERT/UPDATE/DELETEの静的SQL抽出
2. **動的SQL**: `<if>`, `<choose>/<when>/<otherwise>`, `<where>`, `<set>`, `<trim>`
3. **foreach**: IN句、バルクINSERT
4. **sql/include**: SQL断片の再利用
5. **複合パターン**: 上記の組み合わせ
6. **エッジケース**: パラメータなし、ネストした動的SQL

## 出力形式

### コンソール出力（デフォルト）
```
=== UserMapper.selectById ===
Type: SELECT
SQL:
  SELECT id, name, email
  FROM users
  WHERE id = ?
Parameters: [id:INTEGER]

=== UserMapper.selectByCondition ===
Type: SELECT
SQL:
  SELECT id, name, email
  FROM users
  WHERE 1=1
  AND name = ?
Parameters: [name:VARCHAR]
```

### JSON出力（--format=json）
```json
[
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectById",
    "type": "SELECT",
    "sql": "SELECT id, name, email FROM users WHERE id = ?",
    "parameters": [{"property": "id", "javaType": "Integer", "jdbcType": "INTEGER"}]
  }
]
```
