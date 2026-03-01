# 分岐網羅SQL抽出 設計ドキュメント

## 概要

MyBatis Mapper XMLの動的SQL（`<if>`等）に対して、全分岐ON / 全分岐OFFの2パターンでSQLを抽出する機能を追加する。
また、`--classpath`オプションで対象プロジェクトのクラスを読み込み、`parameterType`/`resultType`のフィールド情報からより正確なダミーパラメータを生成する。

## 要件

- `<if>` の分岐を2パターン（all-set / all-null）で網羅
- `<choose>` は現状維持（最初の`<when>`のみ）
- `parameterType`/`resultType`のクラスをクラスパスから読み込みリフレクションでフィールド情報を取得
- 出力にパターン名とパラメータ値マップを含む
- `--branches`フラグなしなら現状と同一出力（後方互換性）

## アプローチ

### SqlSource二重呼び出し方式

1つのMappedStatementに対して2種類のダミーパラメータでBoundSqlを2回取得する。

```
MappedStatement (SqlSource)
├── getBoundSql(allSetParams)  → 全分岐ON SQL  (最大SQL)
└── getBoundSql(allNullParams) → 全分岐OFF SQL (最小SQL)
```

- **all-set**: 全プロパティにnon-null値 → `<if test="x != null">` が全てtrue
- **all-null**: 全プロパティにnull → `<if test="x != null">` が全てfalse

## クラス設計

### 新規クラス

| クラス | パッケージ | 役割 |
|-------|-----------|------|
| `BranchPattern` | extractor | 分岐パターンを表すenum (ALL_SET, ALL_NULL) |
| `NullParameterMap` | parameter | 全キーに対してnullを返すMap |
| `ClasspathTypeResolver` | parameter | クラスパスからの型解決・フィールド情報取得 |

### 変更クラス

| クラス | 変更内容 |
|-------|---------|
| `SqlResult` | `branchPattern`, `parameterValues` フィールド追加 |
| `SqlExtractor` | 2パターン抽出ロジック追加 |
| `DummyParameterGenerator` | ClasspathTypeResolverからの型情報活用 |
| `Main` | `--classpath`, `--branches` オプション追加 |

### BranchPattern

```java
public enum BranchPattern {
    ALL_SET,   // 全分岐ON（最大SQL）
    ALL_NULL   // 全分岐OFF（最小SQL）
}
```

### NullParameterMap

```java
// HashMap<String, Object> を拡張
// containsKey() → true（OGNLのプロパティ存在チェック通過用）
// get() → null（if条件をfalseにする）
```

### ClasspathTypeResolver

```java
// --classpath指定時にparameterType/resultTypeのクラスをロード
// URLClassLoaderで外部jar/classesを読み込み
// Reflectorを使ってフィールド名・型を取得
// 型に基づくダミー値Mapを生成
```

### SqlResult の変更

```java
// 既存フィールドに追加:
private BranchPattern branchPattern;       // どの分岐パターンか
private Map<String, Object> parameterValues; // そのときのパラメータ値
```

### Main.java の変更

```
新オプション:
  --classpath <path>  対象プロジェクトのjar/classesパス（複数指定可、:区切り）
  --branches          分岐パターン出力を有効化（デフォルト: 無効=現状動作）
```

## 出力形式

### テキスト出力（--branches有効時）

```
=== com.example.mapper.UserMapper.selectByCondition [ALL_SET] ===
Type: SELECT
SQL:
  SELECT id, name, email FROM users WHERE name = ? AND age = ?
Parameters: [name:VARCHAR, age:INTEGER]
Parameter Values: {name=dummy_name, age=1}

=== com.example.mapper.UserMapper.selectByCondition [ALL_NULL] ===
Type: SELECT
SQL:
  SELECT id, name, email FROM users
Parameters: []
Parameter Values: {}
```

### JSON出力（--branches有効時）

```json
[
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectByCondition",
    "type": "SELECT",
    "branchPattern": "ALL_SET",
    "sql": "SELECT id, name, email FROM users WHERE name = ? AND age = ?",
    "parameters": [
      {"property": "name", "javaType": "String", "jdbcType": "VARCHAR"}
    ],
    "parameterValues": {"name": "dummy_name", "age": 1}
  },
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectByCondition",
    "type": "SELECT",
    "branchPattern": "ALL_NULL",
    "sql": "SELECT id, name, email FROM users",
    "parameters": [],
    "parameterValues": {}
  }
]
```

### デフォルト（--branches無効時）

現状と完全に同一の出力。branchPattern/parameterValuesフィールドは出力しない。

### 静的SQL（動的タグなし）の扱い

RawSqlSourceの場合、2パターンで結果が同じなので1パターンのみ出力。

## エラーハンドリング

### all-null パラメータでのOGNL評価エラー

`<foreach collection="ids">` で ids が null の場合、例外がスローされる。

**対策**: all-null パターンでBoundSql取得時に例外発生した場合、そのstatementのall-nullパターンはスキップし、all-setのみ出力。ログに警告。

### クラスパス解決エラー

parameterTypeのクラスがクラスパスに見つからない場合。

**対策**: 警告ログを出し、現状のパターンマッチ方式にフォールバック。ツール全体は停止しない。

### 両パターンで同一SQLの場合

**対策**: SQL文字列を比較し、同一なら1つだけ出力（ALL_SETラベルで）。
