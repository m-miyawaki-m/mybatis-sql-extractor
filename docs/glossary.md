# MyBatis 用語解説

MyBatis SQL Extractorで使用されるMyBatis内部構造の用語を解説する。

## 基本概念

### Configuration
MyBatisの全設定情報を保持する中心的なオブジェクト。
- 環境設定（DataSource、TransactionFactory）
- 登録されたMapper
- TypeAlias、TypeHandler
- MappedStatements（全SQL定義）

プログラム的に構築する場合、`new Configuration(environment)`で作成し、`addMapper()`でMapperを登録する。

### Environment
DataSourceとTransactionFactoryを保持する設定オブジェクト。
SQL抽出ツールでは実際のDB接続は不要だが、Configurationの構築にはEnvironmentが必須のため、ダミーのDataSourceを使用する。

### SqlSessionFactory
MyBatisのSqlSessionを生成するファクトリ。通常はConfigurationから構築される。
SQL抽出ツールではSqlSessionは不要なため、Configurationを直接使用する。

---

## Mapper XML関連

### Mapper XML
MyBatisのSQL定義ファイル。`<mapper namespace="...">`をルート要素とし、
`<select>`, `<insert>`, `<update>`, `<delete>`の各要素でSQL文を定義する。

### Namespace
Mapper XMLのルート要素に指定する一意の識別子。通常はJavaのMapper Interfaceの完全修飾名と一致させる。

例: `com.example.mapper.UserMapper`

### Statement ID
個々のSQL定義に付与されるID。namespace + "." + idでシステム全体で一意に識別される。

例: `com.example.mapper.UserMapper.selectById`

---

## SQL解析関連

### MappedStatement
Mapper XMLの各SQL定義（select/insert/update/delete）を解析した結果のオブジェクト。以下の情報を保持する：

| フィールド | 説明 |
|-----------|------|
| id | 完全修飾ID（namespace.id） |
| sqlSource | SQLの生成元 |
| sqlCommandType | SQL種別（SELECT/INSERT/UPDATE/DELETE） |
| parameterMap | パラメータマッピング |
| resultMaps | 結果マッピング |
| statementType | PREPARED/CALLABLE/STATEMENT |

### SqlSource
SQLの生成元を抽象化するインターフェース。`getBoundSql(Object parameterObject)`メソッドでBoundSqlを生成する。

#### 実装クラス

| クラス | 用途 |
|-------|------|
| **RawSqlSource** | 静的SQL（動的タグなし）。`#{}`のみを含むSQL。構築時に1度だけパースされ、`#{}`が`?`に置換される。 |
| **DynamicSqlSource** | 動的SQL（`<if>`, `<choose>`等を含む）。毎回パラメータに基づいてSQLを動的に生成する。 |
| **StaticSqlSource** | 最終的な実行可能SQL。RawSqlSourceとDynamicSqlSourceの両方が最終的にこれに変換される。 |
| **ProviderSqlSource** | `@SelectProvider`等のアノテーションで動的にSQLを生成する場合に使用。 |

### BoundSql
最終的な実行可能SQL文とパラメータ情報を保持するオブジェクト。

| フィールド | 型 | 説明 |
|-----------|-----|------|
| sql | String | `?`プレースホルダーを含む実行可能SQL |
| parameterMappings | List<ParameterMapping> | `?`の順序に対応するパラメータ情報 |
| parameterObject | Object | 元のパラメータオブジェクト |
| additionalParameters | Map | 動的SQLで生成された追加パラメータ |

### ParameterMapping
SQL中の各`?`プレースホルダーに対応するパラメータの情報。

| フィールド | 説明 |
|-----------|------|
| property | パラメータプロパティ名（例: "id", "name"） |
| javaType | Javaの型（例: Integer, String） |
| jdbcType | JDBCの型（例: INTEGER, VARCHAR） |
| mode | IN/OUT/INOUT |

---

## 動的SQLタグ

### SqlNode
動的SQLの各ノードを表すインターフェース。`apply(DynamicContext context)`メソッドでSQLを生成する。

#### 主要な実装クラス

| クラス | 対応タグ | 説明 |
|-------|---------|------|
| **IfSqlNode** | `<if>` | OGNL式で条件を評価し、trueの場合のみ内容を出力 |
| **ChooseSqlNode** | `<choose>` | 最初にtrueになった`<when>`の内容を出力、全てfalseなら`<otherwise>`を出力 |
| **ForEachSqlNode** | `<foreach>` | コレクションの各要素に対してSQL断片を繰り返し生成 |
| **WhereSqlNode** | `<where>` | 内容が空でなければWHEREを付与し、先頭のAND/ORを除去 |
| **SetSqlNode** | `<set>` | 内容が空でなければSETを付与し、末尾のカンマを除去 |
| **TrimSqlNode** | `<trim>` | prefix/suffix/prefixOverrides/suffixOverridesで前後の文字列を制御 |
| **MixedSqlNode** | （複合） | 複数のSqlNodeをリストとして保持し、順に適用 |
| **TextSqlNode** | テキスト | `${}`を含む可能性のあるテキストノード |
| **StaticTextSqlNode** | テキスト | 静的なテキストノード（`${}`を含まない） |

### DynamicContext
動的SQL評価時の実行コンテキスト。パラメータオブジェクトとSQL構築用のStringBuilderを保持する。
各SqlNodeがapply()でDynamicContextにSQL断片を追記し、最終的に完成したSQLを`getSql()`で取得する。

### OGNL (Object-Graph Navigation Language)
MyBatisが動的SQLの条件式評価に使用する式言語。

```xml
<!-- OGNLの例 -->
<if test="name != null">           <!-- null check -->
<if test="name != null and name != ''"> <!-- null + 空文字check -->
<if test="list != null and list.size() > 0"> <!-- コレクションサイズ -->
<if test="type == 'admin'">        <!-- 文字列比較 -->
```

---

## SQL断片の再利用

### `<sql>` / `<include>`
SQL断片を定義（`<sql id="...">`）し、他のSQL定義内で参照（`<include refid="...">`）する仕組み。

```xml
<!-- 定義 -->
<sql id="userColumns">id, name, email</sql>

<!-- 参照 -->
<select id="selectAll">
  SELECT <include refid="userColumns"/> FROM users
</select>
```

MyBatisはMapper XML解析時に`<include>`を展開するため、MappedStatement生成後は展開済みのSQLとして扱われる。

---

## XMLパース処理

### XMLMapperBuilder
Mapper XMLファイルをパースし、Configurationに各種オブジェクトを登録するビルダー。

処理順序:
1. `<cache-ref>`, `<cache>` の解析
2. `<parameterMap>` の解析
3. `<resultMap>` の解析
4. `<sql>` 断片の登録
5. `<select>`, `<insert>`, `<update>`, `<delete>` の解析 → MappedStatement生成

### XMLStatementBuilder
個々のSQL文（select/insert/update/delete要素）をパースし、MappedStatementを生成するビルダー。
動的SQLタグの解析、SqlSourceの生成、ParameterMapの構築を行う。

### SqlSourceBuilder
`#{property,javaType=...,jdbcType=...}`形式のパラメータプレースホルダーを解析し、`?`に置換してParameterMappingを生成する。最終的にStaticSqlSourceを返す。
