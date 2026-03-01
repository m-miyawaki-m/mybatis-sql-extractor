# MyBatis 3.5.x Internal Architecture Reference

MyBatis 3.5.16 の内部アーキテクチャの包括的リファレンス。
SQL Extractor ツール（JDBC接続なしでMapper XMLからSQLを抽出）の設計に特に関連するクラスに重点を置く。

ソース: https://github.com/mybatis/mybatis-3/tree/mybatis-3.5.16

---

## 目次

1. [アーキテクチャ概観](#1-アーキテクチャ概観)
2. [Session Layer](#2-session-layer)
3. [Mapping Layer](#3-mapping-layer)
4. [Scripting Layer](#4-scripting-layer)
5. [Builder Layer (XML)](#5-builder-layer-xml)
6. [Builder Layer (Core)](#6-builder-layer-core)
7. [Reflection Layer](#7-reflection-layer)
8. [Type Layer](#8-type-layer)
9. [Parsing Layer](#9-parsing-layer)
10. [Executor Layer](#10-executor-layer)
11. [DataSource Layer](#11-datasource-layer)
12. [SQL抽出に関連するフロー](#12-sql抽出に関連するフロー)

---

## 1. アーキテクチャ概観

MyBatisは以下のレイヤードアーキテクチャで構成される:

```
┌──────────────────────────────────────────────────────┐
│                   Session Layer                       │
│  (Configuration, SqlSession, SqlSessionFactory)       │
├──────────────────────────────────────────────────────┤
│                   Mapping Layer                       │
│  (MappedStatement, SqlSource, BoundSql, ResultMap)    │
├──────────────────────────────────────────────────────┤
│                  Scripting Layer                      │
│  (SqlNode tree, DynamicContext, XMLScriptBuilder)     │
├────────────────────────┬─────────────────────────────┤
│    Builder Layer       │     Executor Layer           │
│  (XMLMapperBuilder,    │  (Executor, StatementHandler,│
│   XMLConfigBuilder)    │   ParameterHandler)          │
├────────────────────────┴─────────────────────────────┤
│              Support Layers                           │
│  (Reflection, Type, Parsing, DataSource)              │
└──────────────────────────────────────────────────────┘
```

**SQL抽出ツールの観点での重要度:**
- **最重要**: Scripting Layer, Builder Layer, Mapping Layer の一部 (SqlSource, BoundSql)
- **重要**: Session Layer (Configuration), Parsing Layer, Reflection Layer
- **参考**: Executor Layer, DataSource Layer, Type Layer (直接的なJDBC実行は不要だが、Configuration構築に必要)

---

## 2. Session Layer

**パッケージ**: `org.apache.ibatis.session`

### 2.1 Configuration

**目的**: MyBatisフレームワーク全体の中央設定ハブ。全ての設定、レジストリ、コンポーネントファクトリを管理する。SQL抽出ツールにとって最も重要なクラスの一つ。

**主要フィールド**:
| フィールド | 型 | 説明 |
|-----------|-----|------|
| `environment` | Environment | データベース環境設定 |
| `defaultExecutorType` | ExecutorType | デフォルトのExecutorタイプ |
| `mapperRegistry` | MapperRegistry | Mapperインターフェースの登録 |
| `typeHandlerRegistry` | TypeHandlerRegistry | 型変換ハンドラ |
| `typeAliasRegistry` | TypeAliasRegistry | 型エイリアス |
| `languageRegistry` | LanguageDriverRegistry | SQL言語ドライバ |
| `mappedStatements` | Map<String, MappedStatement> | 登録されたSQL文 |
| `resultMaps` | Map<String, ResultMap> | 結果マッピング |
| `parameterMaps` | Map<String, ParameterMap> | パラメータマッピング |
| `sqlFragments` | Map<String, XNode> | 再利用可能なSQL断片 |
| `caches` | Map<String, Cache> | キャッシュ設定 |
| `interceptorChain` | InterceptorChain | プラグインチェーン |
| `objectFactory` | ObjectFactory | オブジェクト生成ファクトリ |
| `reflectorFactory` | ReflectorFactory | リフレクションファクトリ |

**主要メソッド**:
```java
// コンストラクタ - 全てのデフォルト型エイリアスを登録
public Configuration()
public Configuration(Environment environment)

// MappedStatement管理 ★SQL抽出の中核
public void addMappedStatement(MappedStatement ms)
public MappedStatement getMappedStatement(String id)
public Collection<String> getMappedStatementNames()
public boolean hasStatement(String statementName)
public void buildAllStatements()  // 未解決のstatementを全てビルド

// Mapper管理
public <T> void addMapper(Class<T> type)
public <T> T getMapper(Class<T> type, SqlSession sqlSession)
public void addMappers(String packageName)

// リソース管理
public void addResultMap(ResultMap rm)
public ResultMap getResultMap(String id)
public void addParameterMap(ParameterMap pm)
public void addCache(Cache cache)

// ファクトリメソッド
public MetaObject newMetaObject(Object object)  // ★リフレクションラッパー生成
public Executor newExecutor(Transaction transaction, ExecutorType executorType)
public StatementHandler newStatementHandler(...)
public ParameterHandler newParameterHandler(...)
public ResultSetHandler newResultSetHandler(...)

// 言語ドライバ
public LanguageDriver getDefaultLanguageDriver()
public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass)

// 未解決要素の遅延ビルド
public void parsePendingStatements()
public void parsePendingResultMaps()
public void parsePendingCacheRefs()
```

**依存関係**:
- 全てのMyBatisコンポーネントを集約する中央ハブ
- `MapperRegistry`, `TypeHandlerRegistry`, `TypeAliasRegistry` を所有
- `MappedStatement`, `ResultMap`, `ParameterMap` のリポジトリ
- `Executor`, `StatementHandler`, `ParameterHandler`, `ResultSetHandler` のファクトリ

**SQL抽出での使い方**:
```java
Configuration config = new Configuration();
// Environment設定（DataSource不要にする場合はダミーを使用）
// XMLMapperBuilderを使ってMapper XMLを解析し、configに登録
// config.getMappedStatements() で全SQLにアクセス
```

**内部クラス: StrictMap<V>**:
`ConcurrentHashMap`を拡張し、キーの重複を防止し、短縮名の曖昧さを検出するスレッドセーフなMap実装。

---

### 2.2 SqlSession

**目的**: MyBatis操作のプライマリインターフェース。`Closeable`を拡張する。SQL実行、トランザクション管理、Mapper取得を提供。

**主要メソッド**:
```java
// SELECT操作
<T> T selectOne(String statement, Object parameter)
<E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds)
<K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey)
<T> Cursor<T> selectCursor(String statement, Object parameter)
void select(String statement, Object parameter, ResultHandler handler)

// DML操作
int insert(String statement, Object parameter)
int update(String statement, Object parameter)
int delete(String statement, Object parameter)

// トランザクション管理
void commit(boolean force)
void rollback(boolean force)
List<BatchResult> flushStatements()

// セッション管理
void close()
void clearCache()
Configuration getConfiguration()
<T> T getMapper(Class<T> type)
Connection getConnection()
```

**SQL抽出との関連**: SQL抽出ツールでは直接使用しない。SQLの実行はせず、`Configuration`と`MappedStatement`からSQLを取得するのみ。

---

### 2.3 SqlSessionFactory

**目的**: `SqlSession`インスタンスの生成ファクトリ。

**主要メソッド**:
```java
SqlSession openSession()
SqlSession openSession(boolean autoCommit)
SqlSession openSession(ExecutorType execType)
SqlSession openSession(TransactionIsolationLevel level)
Configuration getConfiguration()
```

**実装クラス**: `DefaultSqlSessionFactory`

---

### 2.4 SqlSessionFactoryBuilder

**目的**: `SqlSessionFactory`の構築。XML設定ファイルまたは`Configuration`オブジェクトから構築する。

**主要メソッド**:
```java
// XML設定ファイルから構築
SqlSessionFactory build(Reader reader, String environment, Properties properties)
SqlSessionFactory build(InputStream inputStream, String environment, Properties properties)

// Configurationオブジェクトから直接構築 ★SQL抽出で有用
SqlSessionFactory build(Configuration config)
```

**内部処理**: `XMLConfigBuilder`を使用してXML設定を解析し、`DefaultSqlSessionFactory`を生成する。

---

### 2.5 ExecutorType

**目的**: SQL実行戦略を示す列挙型。

```java
public enum ExecutorType {
    SIMPLE,  // 各SQL文を個別に実行
    REUSE,   // PreparedStatementを再利用
    BATCH    // 複数のSQL文をバッチ実行
}
```

---

## 3. Mapping Layer

**パッケージ**: `org.apache.ibatis.mapping`

### 3.1 MappedStatement

**目的**: Mapper XML内の個々のSQL文（`<select>`, `<insert>`, `<update>`, `<delete>`）を表現する。SQL抽出ツールの主要な操作対象。

**主要フィールド**:
| フィールド | 型 | 説明 |
|-----------|-----|------|
| `id` | String | 完全修飾ID（namespace.statementId） |
| `resource` | String | ソースファイル |
| `configuration` | Configuration | 設定への参照 |
| `sqlSource` | SqlSource | **SQL生成の中核** |
| `sqlCommandType` | SqlCommandType | SQL種別 (SELECT/INSERT/UPDATE/DELETE) |
| `statementType` | StatementType | STATEMENT/PREPARED/CALLABLE |
| `parameterMap` | ParameterMap | パラメータマッピング |
| `resultMaps` | List<ResultMap> | 結果マッピング |
| `cache` | Cache | キャッシュ設定 |
| `keyGenerator` | KeyGenerator | キー自動生成 |
| `keyProperties` | String[] | キープロパティ |
| `lang` | LanguageDriver | 言語ドライバ |
| `fetchSize` | Integer | フェッチサイズ |
| `timeout` | Integer | タイムアウト |

**主要メソッド**:
```java
// ★SQL抽出の中核メソッド
public BoundSql getBoundSql(Object parameterObject)

// ゲッター
public String getId()
public SqlSource getSqlSource()
public SqlCommandType getSqlCommandType()
public StatementType getStatementType()
public List<ResultMap> getResultMaps()
public ParameterMap getParameterMap()
public Configuration getConfiguration()
public LanguageDriver getLang()
```

**構築方法**: `MappedStatement.Builder`（ビルダーパターン）で構築される。
```java
MappedStatement.Builder builder = new MappedStatement.Builder(
    configuration, id, sqlSource, sqlCommandType);
builder.resource(resource)
       .parameterMap(parameterMap)
       .resultMaps(resultMaps)
       .build();
```

**getBoundSql() の内部動作** (★重要):
1. `sqlSource.getBoundSql(parameterObject)` を呼び出す
2. `DynamicSqlSource`の場合: SqlNodeツリーを評価し、動的SQLを生成
3. `RawSqlSource`の場合: 事前解析済みの静的SQLを返す
4. `#{...}` プレースホルダは `?` に変換され、`ParameterMapping`のリストが生成される

---

### 3.2 SqlSource (インターフェース)

**目的**: SQL文の表現を抽象化する。Mapper XMLまたはアノテーションから読み取られたSQL内容を表す。

```java
public interface SqlSource {
    BoundSql getBoundSql(Object parameterObject);
}
```

**実装クラスの階層**:
```
SqlSource (interface)
├── DynamicSqlSource      ← 動的SQL（<if>, <foreach>等を含む）
├── RawSqlSource          ← 静的SQL（動的要素を含まない）
├── StaticSqlSource       ← 最終的に解析済みのSQL（内部使用）
└── ProviderSqlSource     ← @SelectProvider等のアノテーション用
```

---

### 3.3 DynamicSqlSource

**パッケージ**: `org.apache.ibatis.scripting.xmltags`
**実装**: `SqlSource`

**目的**: 動的SQLタグ（`<if>`, `<choose>`, `<foreach>`等）を含むSQL文を処理する。実行時にパラメータに基づいてSQLを動的に生成する。

**フィールド**:
- `configuration` (Configuration): MyBatis設定
- `rootSqlNode` (SqlNode): SQLノードツリーのルート

**コンストラクタ**:
```java
public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode)
```

**getBoundSql() の処理フロー** (★最重要):
```java
public BoundSql getBoundSql(Object parameterObject) {
    // 1. DynamicContextを作成（パラメータをバインディングに設定）
    DynamicContext context = new DynamicContext(configuration, parameterObject);

    // 2. SqlNodeツリーを評価してSQL文字列を構築
    rootSqlNode.apply(context);

    // 3. #{...} プレースホルダを ? に変換し、ParameterMappingを生成
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());

    // 4. BoundSqlを生成
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);

    // 5. DynamicContext のバインディングを追加パラメータとしてコピー
    context.getBindings().forEach(boundSql::setAdditionalParameter);

    return boundSql;
}
```

---

### 3.4 RawSqlSource

**パッケージ**: `org.apache.ibatis.scripting.defaults`
**実装**: `SqlSource`

**目的**: 動的要素を含まない静的SQLを処理する。初期化時にSQLを事前解析してパフォーマンスを最適化する。

**フィールド**:
- `sqlSource` (SqlSource): 内部的に保持する`StaticSqlSource`

**コンストラクタ**:
```java
public RawSqlSource(Configuration configuration, SqlNode rootSqlNode, Class<?> parameterType)
public RawSqlSource(Configuration configuration, String sql, Class<?> parameterType)
```

**内部処理**:
```java
// 初期化時に一度だけSqlNodeツリーを評価
private static String getSql(Configuration configuration, SqlNode rootSqlNode) {
    DynamicContext context = new DynamicContext(configuration, null);
    rootSqlNode.apply(context);
    return context.getSql();
}
// SqlSourceBuilderで #{...} を ? に変換
// 結果をStaticSqlSourceとして保持
```

**DynamicSqlSourceとの違い**:
- `RawSqlSource`: 初期化時に一度だけ解析。毎回同じSQLを返す。
- `DynamicSqlSource`: `getBoundSql()`呼び出しの度にSqlNodeツリーを評価。パラメータに応じて異なるSQLを返す。

---

### 3.5 StaticSqlSource

**パッケージ**: `org.apache.ibatis.builder`
**実装**: `SqlSource`

**目的**: `#{...}` が `?` に変換された後の最終的なSQL文を保持する。`SqlSourceBuilder`の出力。

**フィールド**:
- `sql` (String): プレースホルダ`?`を含むSQL文
- `parameterMappings` (List<ParameterMapping>): パラメータマッピングのリスト
- `configuration` (Configuration): MyBatis設定

**コンストラクタ**:
```java
public StaticSqlSource(Configuration configuration, String sql)
public StaticSqlSource(Configuration configuration, String sql, List<ParameterMapping> parameterMappings)
```

**getBoundSql()**:
```java
public BoundSql getBoundSql(Object parameterObject) {
    return new BoundSql(configuration, sql, parameterMappings, parameterObject);
}
```

---

### 3.6 ProviderSqlSource

**パッケージ**: `org.apache.ibatis.builder.annotation`
**実装**: `SqlSource`

**目的**: `@SelectProvider`, `@InsertProvider`等のアノテーションでJavaメソッドからSQLを生成する。

**フィールド**:
- `providerType` (Class<?>): SQLプロバイダクラス
- `providerMethod` (Method): SQL生成メソッド
- `languageDriver` (LanguageDriver): 言語ドライバ
- `mapperMethod` (Method): Mapperインターフェースメソッド

**SQL抽出との関連**: XMLベースのMapperでは使用されない。アノテーションベースのMapper用。

---

### 3.7 BoundSql

**目的**: 実行可能なSQL文とそのパラメータ情報を保持する最終的なデータ構造。SQL抽出ツールの主要な出力。

**フィールド**:
| フィールド | 型 | 説明 |
|-----------|-----|------|
| `sql` | String | `?` プレースホルダを含むSQL文 |
| `parameterMappings` | List<ParameterMapping> | パラメータマッピングのリスト |
| `parameterObject` | Object | 入力パラメータオブジェクト |
| `additionalParameters` | Map<String, Object> | 動的SQLで生成された追加パラメータ |
| `metaParameters` | MetaObject | 追加パラメータのリフレクションラッパー |

**コンストラクタ**:
```java
public BoundSql(Configuration configuration, String sql,
                List<ParameterMapping> parameterMappings, Object parameterObject)
```

**主要メソッド**:
```java
public String getSql()                          // ★SQL文を取得
public List<ParameterMapping> getParameterMappings()  // ★パラメータ情報を取得
public Object getParameterObject()
public boolean hasAdditionalParameter(String name)
public void setAdditionalParameter(String name, Object value)
public Object getAdditionalParameter(String name)
```

---

### 3.8 ParameterMapping

**目的**: SQL内の個々のパラメータプレースホルダ（`#{...}` から変換された `?`）のメタデータを保持する。

**フィールド**:
| フィールド | 型 | 説明 |
|-----------|-----|------|
| `property` | String | プロパティ名 |
| `mode` | ParameterMode | IN / OUT / INOUT |
| `javaType` | Class<?> | Java型（デフォルト: Object.class） |
| `jdbcType` | JdbcType | JDBC型 |
| `numericScale` | Integer | 数値スケール |
| `typeHandler` | TypeHandler<?> | 型変換ハンドラ |
| `resultMapId` | String | 結果マップID（OUT用） |

**構築**: `ParameterMapping.Builder`で構築。
```java
new ParameterMapping.Builder(configuration, "id", Integer.class)
    .jdbcType(JdbcType.INTEGER)
    .build();
```

---

### 3.9 ParameterMap

**目的**: パラメータマッピングの名前付きコレクション。`<parameterMap>` 要素に対応（非推奨だが互換性のため残存）。

**フィールド**:
- `id` (String): 識別子
- `type` (Class<?>): パラメータ型
- `parameterMappings` (List<ParameterMapping>): マッピングのリスト

---

### 3.10 ResultMap

**目的**: SQL結果からJavaオブジェクトへのマッピング設定。`<resultMap>` 要素に対応。

**フィールド**:
| フィールド | 型 | 説明 |
|-----------|-----|------|
| `id` | String | 識別子 |
| `type` | Class<?> | マッピング先のJavaクラス |
| `resultMappings` | List<ResultMapping> | 全結果マッピング |
| `idResultMappings` | List<ResultMapping> | IDフラグ付きマッピング |
| `constructorResultMappings` | List<ResultMapping> | コンストラクタパラメータ |
| `propertyResultMappings` | List<ResultMapping> | プロパティベースマッピング |
| `mappedColumns` | Set<String> | マッピングされたカラム名 |
| `discriminator` | Discriminator | ポリモーフィック結果の判別 |
| `hasNestedResultMaps` | boolean | ネストされた結果マップの有無 |
| `autoMapping` | Boolean | 自動マッピングフラグ |

**構築**: `ResultMap.Builder`で構築。

---

### 3.11 ResultMapping

**目的**: ResultMap内の個々のカラム-プロパティマッピング。

**主要フィールド**:
- `property` (String): Javaプロパティ名
- `column` (String): SQLカラム名
- `javaType` (Class<?>): Java型
- `jdbcType` (JdbcType): JDBC型
- `typeHandler` (TypeHandler<?>): 型変換ハンドラ
- `nestedResultMapId` (String): ネストされた結果マップのID
- `nestedQueryId` (String): ネストされたクエリのID
- `lazy` (boolean): 遅延ロードフラグ

---

### 3.12 Environment

**目的**: データベース環境設定。トランザクションファクトリとデータソースを保持する。

**フィールド**:
- `id` (String): 環境識別子
- `transactionFactory` (TransactionFactory): トランザクションファクトリ
- `dataSource` (DataSource): データソース

**構築**: `Environment.Builder`で構築。
```java
new Environment.Builder("development")
    .transactionFactory(transactionFactory)
    .dataSource(dataSource)
    .build();
```

**SQL抽出での使い方**: ダミーの`TransactionFactory`と`DataSource`でConfigurationを初期化するために必要。

---

### 3.13 SqlCommandType

```java
public enum SqlCommandType {
    UNKNOWN,   // 不明
    INSERT,    // INSERT文
    UPDATE,    // UPDATE文
    DELETE,    // DELETE文
    SELECT,    // SELECT文
    FLUSH      // キャッシュフラッシュ
}
```

### 3.14 StatementType

```java
public enum StatementType {
    STATEMENT,   // java.sql.Statement
    PREPARED,    // java.sql.PreparedStatement（デフォルト）
    CALLABLE     // java.sql.CallableStatement
}
```

### 3.15 Discriminator

**目的**: 特定のカラム値に基づいてResultMapを切り替えるポリモーフィックマッピング。

**フィールド**:
- `resultMapping` (ResultMapping): 判別カラムの設定
- `discriminatorMap` (Map<String, String>): 値からResultMap IDへのマッピング

**主要メソッド**:
```java
public ResultMapping getResultMapping()
public Map<String, String> getDiscriminatorMap()
public String getMapIdFor(String s)  // 値に対応するResultMap IDを取得
```

### 3.16 DatabaseIdProvider (インターフェース)

**目的**: DataSourceからデータベース種別を識別する。マルチベンダーSQL対応に使用。

```java
public interface DatabaseIdProvider {
    default void setProperties(Properties p) {}
    String getDatabaseId(DataSource dataSource) throws SQLException;
}
```

---

## 4. Scripting Layer

**パッケージ**: `org.apache.ibatis.scripting.xmltags`

このレイヤーはSQL抽出ツールにとって最も重要。Mapper XMLの動的SQLタグをパースし、SqlNodeツリーとして表現し、それを評価してSQL文字列を生成する。

### 4.1 SqlNode (インターフェース)

**目的**: 動的SQLの構成要素を表す。Compositeパターンの基底インターフェース。

```java
public interface SqlNode {
    boolean apply(DynamicContext context);
}
```

全てのSqlNode実装は`apply()`メソッドでDynamicContextにSQL断片を追加する。

**SqlNode実装の階層**:
```
SqlNode (interface)
├── StaticTextSqlNode    ← 静的テキスト
├── TextSqlNode          ← ${...} を含むテキスト
├── IfSqlNode            ← <if test="...">
├── ChooseSqlNode        ← <choose>/<when>/<otherwise>
├── ForEachSqlNode       ← <foreach>
├── TrimSqlNode          ← <trim>
│   ├── WhereSqlNode     ← <where> (TrimSqlNodeの特殊化)
│   └── SetSqlNode       ← <set> (TrimSqlNodeの特殊化)
├── MixedSqlNode         ← 複数SqlNodeのコンテナ
└── VarDeclSqlNode       ← <bind>
```

---

### 4.2 DynamicContext

**目的**: 動的SQLの評価コンテキスト。パラメータバインディングの保持とSQL断片の蓄積を行う。

**定数**:
- `PARAMETER_OBJECT_KEY = "_parameter"`: パラメータオブジェクトのバインディングキー
- `DATABASE_ID_KEY = "_databaseId"`: データベースIDのバインディングキー

**フィールド**:
- `bindings` (ContextMap): パラメータバインディング（OGNL式の評価に使用）
- `sqlBuilder` (StringJoiner): SQL断片をスペース区切りで蓄積
- `uniqueNumber` (int): ユニークな番号生成カウンタ（`<foreach>`用）

**コンストラクタ**:
```java
public DynamicContext(Configuration configuration, Object parameterObject)
```
パラメータオブジェクトをMetaObjectでラップし、`_parameter`と`_databaseId`をバインディングに設定する。

**主要メソッド**:
```java
public Map<String, Object> getBindings()      // バインディングMap取得
public void bind(String name, Object value)   // バインディングに値を追加
public void appendSql(String sql)             // SQL断片を追加
public String getSql()                        // 蓄積されたSQL全体を取得
public int getUniqueNumber()                  // ユニーク番号をインクリメントして返す
```

**内部クラス**:
- **ContextMap**: HashMapを拡張。直接のキー検索に失敗した場合、パラメータオブジェクトのMetaObjectを通じてプロパティアクセスにフォールバックする。
- **ContextAccessor**: OGNLの`PropertyAccessor`を実装。ContextMap上のプロパティアクセスを仲介する。

**SQL抽出での重要性**: DynamicContextにダミーパラメータを設定し、SqlNodeツリーを評価することでSQLを生成する。

---

### 4.3 StaticTextSqlNode

**目的**: 動的要素を含まない純粋なテキストをDynamicContextに追加する。

```java
public class StaticTextSqlNode implements SqlNode {
    private final String text;

    public StaticTextSqlNode(String text) { this.text = text; }

    @Override
    public boolean apply(DynamicContext context) {
        context.appendSql(text);
        return true;
    }
}
```

---

### 4.4 TextSqlNode

**目的**: `${...}` 文字列置換を含むテキストを処理する。実行時にOGNL式を評価して置換する。

**フィールド**:
- `text` (String): `${...}` を含むテキスト
- `injectionFilter` (Pattern): SQLインジェクション防止用フィルタ

**主要メソッド**:
```java
public boolean isDynamic()  // ${...} を含むかチェック
public boolean apply(DynamicContext context)  // ${...} をOGNL評価結果で置換
```

**内部クラス**:
- **BindingTokenParser**: `${...}` トークンをOGNLで評価して置換する`TokenHandler`実装
- **DynamicCheckerTokenParser**: `${...}` の存在をチェックする`TokenHandler`実装

**`#{...}` との違い**:
- `#{...}`: PreparedStatementの `?` パラメータに変換される（安全）
- `${...}`: 文字列がSQLに直接埋め込まれる（SQLインジェクションリスクあり）

---

### 4.5 IfSqlNode

**目的**: `<if test="...">` タグを処理する。OGNL式が`true`の場合のみ内容を追加する。

**フィールド**:
- `evaluator` (ExpressionEvaluator): OGNL式の評価器
- `test` (String): テスト条件式
- `contents` (SqlNode): 条件が真の場合に評価するSqlNode

**apply() の処理**:
```java
public boolean apply(DynamicContext context) {
    if (evaluator.evaluateBoolean(test, context.getBindings())) {
        contents.apply(context);
        return true;
    }
    return false;
}
```

**SQL抽出での重要性**: ダミーパラメータの設計次第で、if条件がtrue/falseとなり出力SQLが変わる。

---

### 4.6 ChooseSqlNode

**目的**: `<choose>/<when>/<otherwise>` タグを処理する。Java の switch文に相当。

**フィールド**:
- `ifSqlNodes` (List<SqlNode>): `<when>` 条件のリスト（各要素は `IfSqlNode`）
- `defaultSqlNode` (SqlNode): `<otherwise>` のSqlNode（省略可能）

**apply() の処理**:
```java
public boolean apply(DynamicContext context) {
    for (SqlNode sqlNode : ifSqlNodes) {
        if (sqlNode.apply(context)) {
            return true;  // 最初にマッチした<when>で終了
        }
    }
    if (defaultSqlNode != null) {
        defaultSqlNode.apply(context);
        return true;
    }
    return false;
}
```

---

### 4.7 ForEachSqlNode

**目的**: `<foreach>` タグを処理する。コレクションを反復してSQL断片を生成する。

**フィールド**:
| フィールド | 型 | 説明 |
|-----------|-----|------|
| `ITEM_PREFIX` | String | `"__frch_"` プレフィックス |
| `evaluator` | ExpressionEvaluator | 式評価器 |
| `collectionExpression` | String | コレクション式 |
| `nullable` | boolean | null許容フラグ（3.5.9+） |
| `contents` | SqlNode | ループ本体のSqlNode |
| `open` | String | 開始デリミタ |
| `close` | String | 終了デリミタ |
| `separator` | String | 区切り文字 |
| `item` | String | 要素変数名 |
| `index` | String | インデックス変数名 |

**apply() の処理概要**:
1. `collectionExpression`をOGNLで評価してIterableを取得
2. 空の場合は何もしない（nullableの場合はnullも許容）
3. 各要素に対して:
   - `index`と`item`変数をバインド
   - `open`, `separator`, `close` デリミタを適用
   - `contents.apply(context)` を呼び出す
   - 要素/インデックス変数は `__frch_{item}_{uniqueNumber}` 形式で一意化される

**内部クラス**:
- **FilteredDynamicContext**: `item`/`index`変数参照をユニーク名に変換するフィルタ
- **PrefixedContext**: separator と open デリミタの注入を管理

**SQL抽出での重要性**: ダミーのコレクション（例: `[1, 2, 3]`）を提供する必要がある。

---

### 4.8 TrimSqlNode

**目的**: `<trim>` タグを処理する。内容の前後にprefix/suffixを追加し、指定された文字列を除去する。

**フィールド**:
- `contents` (SqlNode): 内容のSqlNode
- `prefix` (String): 追加するプレフィックス
- `suffix` (String): 追加するサフィックス
- `prefixesToOverride` (List<String>): 内容先頭から除去する文字列リスト
- `suffixesToOverride` (List<String>): 内容末尾から除去する文字列リスト

**apply() の処理**:
1. `FilteredDynamicContext`を作成して内容を評価
2. 内容が空でなければ:
   - 先頭の`prefixesToOverride`に一致する文字列を除去
   - 末尾の`suffixesToOverride`に一致する文字列を除去
   - `prefix`を先頭に追加
   - `suffix`を末尾に追加

**内部クラス**: FilteredDynamicContext（DynamicContextのラッパー）

---

### 4.9 WhereSqlNode

**目的**: `<where>` タグを処理する。`TrimSqlNode`の特殊化。

```java
public class WhereSqlNode extends TrimSqlNode {
    private static List<String> prefixList = Arrays.asList(
        "AND ", "OR ", "AND\n", "OR\n", "AND\r", "OR\r", "AND\t", "OR\t"
    );

    public WhereSqlNode(Configuration configuration, SqlNode contents) {
        super(configuration, contents, "WHERE", prefixList, null, null);
    }
}
```

**動作**: 内容が空でなければ `WHERE` を先頭に追加し、先頭の `AND` / `OR` を除去する。

---

### 4.10 SetSqlNode

**目的**: `<set>` タグを処理する。`TrimSqlNode`の特殊化。

```java
public class SetSqlNode extends TrimSqlNode {
    private static final List<String> COMMA = Collections.singletonList(",");

    public SetSqlNode(Configuration configuration, SqlNode contents) {
        super(configuration, contents, "SET", COMMA, null, COMMA);
    }
}
```

**動作**: 内容が空でなければ `SET` を先頭に追加し、先頭・末尾のカンマを除去する。

---

### 4.11 MixedSqlNode

**目的**: 複数のSqlNodeを順番に評価するコンテナ。Compositeパターンの実装。

```java
public class MixedSqlNode implements SqlNode {
    private final List<SqlNode> contents;

    public MixedSqlNode(List<SqlNode> contents) { this.contents = contents; }

    @Override
    public boolean apply(DynamicContext context) {
        contents.forEach(node -> node.apply(context));
        return true;
    }
}
```

**位置付け**: 通常、SqlNodeツリーのルートは`MixedSqlNode`である。XMLの各子要素が個別のSqlNodeとなり、MixedSqlNodeがそれらをまとめる。

---

### 4.12 VarDeclSqlNode (bind タグ)

**目的**: `<bind>` タグを処理する。OGNL式を評価してDynamicContextに変数をバインドする。

**フィールド**:
- `name` (String): 変数名
- `expression` (String): OGNL式

```java
public boolean apply(DynamicContext context) {
    final Object value = OgnlCache.getValue(expression, context.getBindings());
    context.bind(name, value);
    return true;
}
```

**注意**: ソースコード上に `BindSqlNode` クラスは存在しない。`<bind>` タグの処理は `VarDeclSqlNode` が担当する。XMLScriptBuilder内の `BindHandler` が `VarDeclSqlNode` を生成する。

---

### 4.13 OgnlCache

**目的**: OGNL式の解析結果をキャッシュするユーティリティクラス。

**フィールド**:
- `expressionCache` (ConcurrentHashMap<String, Object>): パース済み式のキャッシュ

**主要メソッド**:
```java
public static Object getValue(String expression, Object root)
```
OGNL式を`root`オブジェクトに対して評価する。パース済み式はキャッシュされる。

**SQL抽出での重要性**: `<if test="...">`, `<bind>`, `<foreach>`, `${...}` の全てでOGNL式評価に使用される。パラメータオブジェクトに対するプロパティアクセスはOGNLを通じて行われる。

---

### 4.14 ExpressionEvaluator

**目的**: OGNL式を特定の型（boolean / Iterable）に変換するユーティリティ。

**主要メソッド**:
```java
// boolean評価: IfSqlNodeで使用
public boolean evaluateBoolean(String expression, Object parameterObject)
// - Boolean型はそのまま返す
// - Number型は0でなければtrue
// - その他のnon-nullオブジェクトはtrue

// Iterable評価: ForEachSqlNodeで使用
public Iterable<?> evaluateIterable(String expression, Object parameterObject, boolean nullable)
// - Iterable型はそのまま返す
// - 配列型はListに変換（プリミティブ配列も対応）
// - Map型はentrySet()を返す
// - nullable=trueならnullを許容
// - それ以外はBuilderExceptionをスロー
```

---

### 4.15 XMLScriptBuilder

**目的**: Mapper XMLのSQL要素（`<select>`, `<insert>`等）の内容をパースし、`SqlNode`ツリーと`SqlSource`を構築する。

**継承**: `BaseBuilder`を拡張

**フィールド**:
- `context` (XNode): 解析対象のXMLノード
- `isDynamic` (boolean): 動的SQLフラグ
- `parameterType` (Class<?>): パラメータ型
- `nodeHandlerMap` (Map<String, NodeHandler>): タグ名からハンドラへのマッピング

**コンストラクタ**:
```java
public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType)
```

**主要メソッド**:
```java
// ★SqlSourceを生成する主要メソッド
public SqlSource parseScriptNode()
// 動的SQLの場合: DynamicSqlSource を返す
// 静的SQLの場合: RawSqlSource を返す
```

**内部のNodeHandler実装**:
| ハンドラ | 処理するタグ | 生成するSqlNode |
|---------|------------|----------------|
| `IfHandler` | `<if>`, `<when>` | `IfSqlNode` |
| `ChooseHandler` | `<choose>` | `ChooseSqlNode` |
| `ForEachHandler` | `<foreach>` | `ForEachSqlNode` |
| `TrimHandler` | `<trim>` | `TrimSqlNode` |
| `WhereHandler` | `<where>` | `WhereSqlNode` |
| `SetHandler` | `<set>` | `SetSqlNode` |
| `BindHandler` | `<bind>` | `VarDeclSqlNode` |
| `OtherwiseHandler` | `<otherwise>` | `MixedSqlNode` |

**parseDynamicTags() の処理**:
1. XNodeの子ノードを順に処理
2. テキストノードの場合:
   - `${...}` を含むなら `TextSqlNode`（動的フラグ=true）
   - 含まないなら `StaticTextSqlNode`
3. 要素ノードの場合:
   - `nodeHandlerMap` から対応するハンドラを取得
   - ハンドラが `SqlNode` を生成してリストに追加
   - 動的フラグ=true に設定
4. 結果を `MixedSqlNode` でラップ

---

### 4.16 XMLLanguageDriver

**目的**: デフォルトの言語ドライバ。XMLベースのSQL定義をサポートする。

**実装**: `LanguageDriver`インターフェース

**主要メソッド**:
```java
// ParameterHandler生成
public ParameterHandler createParameterHandler(MappedStatement ms, Object parameterObject, BoundSql boundSql)
// → DefaultParameterHandler を生成

// XNodeからSqlSource生成 ★
public SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType)
// → XMLScriptBuilder を使用してパース

// 文字列からSqlSource生成（アノテーション用）
public SqlSource createSqlSource(Configuration configuration, String script, Class<?> parameterType)
// → <script>タグで始まる場合はXML解析
// → それ以外はTextSqlNode/StaticTextSqlNodeとしてDynamic/RawSqlSourceを生成
```

---

## 5. Builder Layer (XML)

**パッケージ**: `org.apache.ibatis.builder.xml`

### 5.1 XMLConfigBuilder

**目的**: MyBatis設定XMLファイル（`mybatis-config.xml`）をパースして`Configuration`オブジェクトを構築する。

**継承**: `BaseBuilder`

**フィールド**:
- `parsed` (boolean): 二重パース防止フラグ
- `parser` (XPathParser): XMLパーサー
- `environment` (String): 環境名
- `localReflectorFactory` (ReflectorFactory): リフレクションファクトリ

**コンストラクタ**（多数のオーバーロード）:
```java
XMLConfigBuilder(Reader reader)
XMLConfigBuilder(InputStream inputStream)
XMLConfigBuilder(InputStream inputStream, String environment, Properties props)
// 等、合計9つ
```

**主要メソッド**:
```java
public Configuration parse()  // ★設定パースのエントリーポイント
```

**parseConfiguration() の処理順序**:
```java
private void parseConfiguration(XNode root) {
    propertiesElement(root.evalNode("properties"));
    Properties settings = settingsAsProperties(root.evalNode("settings"));
    loadCustomVfsImpl(settings);
    loadCustomLogImpl(settings);
    typeAliasesElement(root.evalNode("typeAliases"));
    pluginsElement(root.evalNode("plugins"));
    objectFactoryElement(root.evalNode("objectFactory"));
    reflectorFactoryElement(root.evalNode("reflectorFactory"));
    settingsElement(settings);
    environmentsElement(root.evalNode("environments"));
    typeHandlersElement(root.evalNode("typeHandlers"));
    mappersElement(root.evalNode("mappers"));  // ★Mapper XMLの読み込み
}
```

**SQL抽出との関連**: SQL抽出ツールではMyBatis設定XMLを使用せず、プログラマティックに`Configuration`を構築する場合が多い。しかし、`mappersElement()`の内部処理を理解することは重要。

---

### 5.2 XMLMapperBuilder

**目的**: Mapper XMLファイル（`*Mapper.xml`）をパースして`Configuration`に登録する。SQL抽出ツールにとって最も重要なBuilderの一つ。

**継承**: `BaseBuilder`

**フィールド**:
- `parser` (XPathParser): XMLパーサー
- `builderAssistant` (MapperBuilderAssistant): Mapper構築アシスタント
- `sqlFragments` (Map<String, XNode>): `<sql>` 断片のキャッシュ
- `resource` (String): リソース識別子

**コンストラクタ**:
```java
public XMLMapperBuilder(InputStream inputStream, Configuration configuration,
                        String resource, Map<String, XNode> sqlFragments)
```

**主要メソッド**:
```java
// ★Mapper XMLパースのエントリーポイント
public void parse()

// SQL断片の取得
public XNode getSqlFragment(String refid)
```

**parse() の内部処理**:
```java
public void parse() {
    if (!configuration.isResourceLoaded(resource)) {
        configurationElement(parser.evalNode("/mapper"));
        configuration.addLoadedResource(resource);
        bindMapperForNamespace();
    }
    parsePendingResultMaps();
    parsePendingCacheRefs();
    parsePendingStatements();
}
```

**configurationElement() の処理** (★):
```java
private void configurationElement(XNode context) {
    String namespace = context.getStringAttribute("namespace");
    builderAssistant.setCurrentNamespace(namespace);
    cacheRefElement(context.evalNode("cache-ref"));
    cacheElement(context.evalNode("cache"));
    parameterMapElement(context.evalNodes("/mapper/parameterMap"));
    resultMapElements(context.evalNodes("/mapper/resultMap"));
    sqlElement(context.evalNodes("/mapper/sql"));                  // <sql>断片の登録
    buildStatementFromContext(context.evalNodes("select|insert|update|delete"));  // ★SQL文の構築
}
```

**buildStatementFromContext()**: 各SQL文要素に対して`XMLStatementBuilder`を生成し、パースする。

---

### 5.3 XMLStatementBuilder

**目的**: 個々のSQL文要素（`<select>`, `<insert>`, `<update>`, `<delete>`）をパースして`MappedStatement`を構築する。

**継承**: `BaseBuilder`

**フィールド**:
- `builderAssistant` (MapperBuilderAssistant): 構築アシスタント
- `context` (XNode): SQL文のXMLノード
- `requiredDatabaseId` (String): データベースIDフィルタ

**主要メソッド**:
```java
// ★SQL文パースのエントリーポイント
public void parseStatementNode()
```

**parseStatementNode() の処理** (★):
1. `id`, `databaseId`, `fetchSize`, `timeout`, `parameterMap`, `parameterType`, `resultMap`, `resultType` 等の属性を読み取る
2. `<include>` タグを展開（`XMLIncludeTransformer`）
3. `<selectKey>` ノードを処理
4. `LanguageDriver.createSqlSource()` でSqlSourceを生成 ★
5. `builderAssistant.addMappedStatement()` でConfigurationに登録

---

### 5.4 XMLIncludeTransformer

**目的**: `<include>` タグを処理し、`<sql>` 断片で置換する。

**主要メソッド**:
```java
public void applyIncludes(Node source)
```

**処理**:
1. `<include refid="...">` ノードを検出
2. 参照先の `<sql>` 断片を `sqlFragments` から取得
3. 変数置換（`<property>` 子要素の値を適用）
4. `<include>` ノードを展開されたSQL断片で置換

---

### 5.5 MapperBuilderAssistant

**パッケージ**: `org.apache.ibatis.builder`

**目的**: XMLMapperBuilderとXMLStatementBuilderを支援し、namespace管理、Cache設定、ResultMap/ParameterMap/MappedStatementの構築と登録を行う。

**フィールド**:
- `currentNamespace` (String): 現在のnamespace
- `resource` (String): リソース識別子
- `currentCache` (Cache): 現在のキャッシュ
- `unresolvedCacheRef` (boolean): 未解決のキャッシュ参照フラグ

**主要メソッド**:
```java
// Namespace管理
public String getCurrentNamespace()
public void setCurrentNamespace(String currentNamespace)
public String applyCurrentNamespace(String base, boolean isReference)

// Cache操作
public Cache useCacheRef(String namespace)
public Cache useNewCache(...)

// ★MappedStatement登録
public MappedStatement addMappedStatement(String id, SqlSource sqlSource,
    StatementType statementType, SqlCommandType sqlCommandType, ...)

// ResultMap構築
public ResultMap addResultMap(String id, Class<?> type, ...)
public ResultMapping buildResultMapping(Class<?> resultType, String property, String column, ...)

// ParameterMap構築
public ParameterMap addParameterMap(String id, Class<?> type, List<ParameterMapping> parameterMappings)
```

---

### 5.6 XMLMapperEntityResolver

**目的**: MyBatis DTDのオフライン解決。ネットワークアクセスなしでDTD検証を可能にする。

**処理**: `mybatis-3-mapper.dtd` と `mybatis-3-config.dtd` のsystem IDをクラスパスのローカルリソースにマッピングする。

---

## 6. Builder Layer (Core)

**パッケージ**: `org.apache.ibatis.builder`

### 6.1 BaseBuilder

**目的**: 全てのBuilderクラスの基底抽象クラス。型解決やエイリアス解決のユーティリティを提供する。

**フィールド**:
- `configuration` (Configuration): 設定
- `typeAliasRegistry` (TypeAliasRegistry): 型エイリアスレジストリ
- `typeHandlerRegistry` (TypeHandlerRegistry): 型ハンドラレジストリ

**主要メソッド**:
```java
public Configuration getConfiguration()

// 型解決ユーティリティ
protected Class<?> resolveClass(String alias)         // エイリアスからClassを解決
protected Class<?> resolveAlias(String alias)         // TypeAliasRegistryに委譲
protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType)
protected JdbcType resolveJdbcType(String alias)       // JdbcType列挙値を解決
protected ResultSetType resolveResultSetType(String alias)
protected ParameterMode resolveParameterMode(String alias)

// 値変換ユーティリティ
protected Boolean booleanValueOf(String value, Boolean defaultValue)
protected Integer integerValueOf(String value, Integer defaultValue)
protected Set<String> stringSetValueOf(String value, String defaultValue)

// インスタンス生成
protected Object createInstance(String alias)
```

---

### 6.2 SqlSourceBuilder

**目的**: `#{...}` プレースホルダを `?` に変換し、`ParameterMapping`のリストを生成する。DynamicSqlSourceとRawSqlSourceの両方から使用される。

**継承**: `BaseBuilder`

**定数**:
- `PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName"`: パラメータ式で使用可能なプロパティ

**主要メソッド**:
```java
// ★#{...} を ? に変換する
public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters)
// 戻り値: StaticSqlSource

// 空白正規化
public static String removeExtraWhitespaces(String original)
```

**内部クラス: ParameterMappingTokenHandler**:
`TokenHandler`を実装し、`#{...}` トークンを処理する。

```java
// TokenHandlerインターフェースの実装
public String handleToken(String content) {
    parameterMappings.add(buildParameterMapping(content));
    return "?";  // #{...} を ? に置換
}
```

**buildParameterMapping() の処理**:
1. `#{id,javaType=int,jdbcType=INTEGER}` のような式をパース
2. `ParameterExpression` で式を解析
3. `ParameterMapping.Builder` で `ParameterMapping` を構築
4. javaType, jdbcType, typeHandler等を設定

---

### 6.3 StaticSqlSource

（3.5節で既述）

---

### 6.4 ParameterExpression

**目的**: `#{...}` 内のインラインパラメータ式をパースする。

**継承**: `HashMap<String, String>`

**サポートする文法**:
```
#{property}
#{property, javaType=int}
#{property, javaType=int, jdbcType=INTEGER}
#{property, typeHandler=MyTypeHandler}
#{(expression), javaType=int}
```

**パース結果の例**:
- 入力: `"id, javaType=int, jdbcType=INTEGER"`
- 結果: `{"property": "id", "javaType": "int", "jdbcType": "INTEGER"}`

---

## 7. Reflection Layer

**パッケージ**: `org.apache.ibatis.reflection`

### 7.1 MetaObject

**目的**: 任意のJavaオブジェクトに対するリフレクションベースのプロパティアクセスを提供する。ネストされたプロパティ（`user.address.city`）もサポートする。

**フィールド**:
- `originalObject` (Object): ラップされた元のオブジェクト
- `objectWrapper` (ObjectWrapper): プロパティアクセスのラッパー
- `objectFactory` (ObjectFactory): オブジェクト生成ファクトリ
- `objectWrapperFactory` (ObjectWrapperFactory): ラッパーファクトリ
- `reflectorFactory` (ReflectorFactory): リフレクションファクトリ

**ファクトリメソッド**:
```java
public static MetaObject forObject(Object object, ObjectFactory objectFactory,
    ObjectWrapperFactory objectWrapperFactory, ReflectorFactory reflectorFactory)
```

**主要メソッド**:
```java
// プロパティアクセス
public Object getValue(String name)              // ネストプロパティ対応
public void setValue(String name, Object value)   // ネストプロパティ対応

// プロパティ検査
public String findProperty(String propName, boolean useCamelCaseMapping)
public boolean hasGetter(String name)
public boolean hasSetter(String name)
public Class<?> getGetterType(String name)
public Class<?> getSetterType(String name)
public String[] getGetterNames()
public String[] getSetterNames()

// ネストされたMetaObject生成
public MetaObject metaObjectForProperty(String name)

// コレクション操作
public boolean isCollection()
public void add(Object element)
public <E> void addAll(List<E> list)
```

**SQL抽出での重要性**: DynamicContext内でパラメータオブジェクトのプロパティにアクセスするために使用される。OGNL式の評価やバインディング変数の解決の基盤。

---

### 7.2 MetaClass

**目的**: クラスレベルのメタデータ（プロパティ名、型情報）へのアクセスを提供する。インスタンスなしでクラス構造を検査できる。

**主要メソッド**:
```java
public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory)
public MetaClass metaClassForProperty(String name)
public String findProperty(String name)
public String findProperty(String name, boolean useCamelCaseMapping)
public String[] getGetterNames()
public String[] getSetterNames()
public Class<?> getSetterType(String name)
public Class<?> getGetterType(String name)
public boolean hasSetter(String name)
public boolean hasGetter(String name)
public boolean hasDefaultConstructor()
```

---

### 7.3 ObjectWrapper (インターフェース)

**目的**: オブジェクトへのプロパティアクセスを標準化するラッパーインターフェース。

```java
public interface ObjectWrapper {
    Object get(PropertyTokenizer prop);
    void set(PropertyTokenizer prop, Object value);
    String findProperty(String name, boolean useCamelCaseMapping);
    String[] getGetterNames();
    String[] getSetterNames();
    Class<?> getSetterType(String name);
    Class<?> getGetterType(String name);
    boolean hasSetter(String name);
    boolean hasGetter(String name);
    MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);
    boolean isCollection();
    void add(Object element);
    <E> void addAll(List<E> element);
}
```

---

### 7.4 BeanWrapper

**目的**: POJO/JavaBeanに対する`ObjectWrapper`実装。getter/setterメソッドを通じてプロパティにアクセスする。

**フィールド**:
- `object` (Object): ラップされたBeanインスタンス
- `metaClass` (MetaClass): Beanクラスのメタデータ

**特徴**:
- `isCollection()` は常に`false`を返す
- プロパティアクセスはReflectorを通じてgetter/setterメソッドを呼び出す
- ネストされたプロパティの自動生成（`instantiatePropertyValue`）をサポート

---

### 7.5 MapWrapper

**目的**: `Map<String, Object>` に対する`ObjectWrapper`実装。Mapのキーをプロパティ名として扱う。

**特徴**:
- `hasSetter()` は常に`true`を返す（Mapは任意のキーを受け入れる）
- `hasGetter()` はキーの存在をチェック
- `findProperty()` はキーをそのまま返す（変換なし）
- `instantiatePropertyValue()` はネストされたプロパティに新しい`HashMap`を生成

**SQL抽出での重要性**: ダミーパラメータを`Map`として提供する場合、MapWrapperが使用される。

---

### 7.6 SystemMetaObject

**目的**: デフォルトのファクトリ設定でMetaObjectを生成するユーティリティ。

**静的フィールド**:
- `DEFAULT_OBJECT_FACTORY`: `DefaultObjectFactory`インスタンス
- `DEFAULT_OBJECT_WRAPPER_FACTORY`: `DefaultObjectWrapperFactory`インスタンス
- `NULL_META_OBJECT`: null用のMetaObject（NullObjectパターン）

**主要メソッド**:
```java
public static MetaObject forObject(Object object)
// デフォルトのファクトリとDefaultReflectorFactoryを使用してMetaObjectを生成
```

---

### 7.7 Reflector

**目的**: 単一クラスのリフレクション情報（プロパティ名、getter/setter、型情報）をキャッシュする。

**フィールド**:
- `type` (Class<?>): 対象クラス
- `readablePropertyNames` (String[]): getterのあるプロパティ名
- `writablePropertyNames` (String[]): setterのあるプロパティ名
- `setMethods` / `getMethods` (Map<String, Invoker>): プロパティ名からInvokerへのマッピング
- `setTypes` / `getTypes` (Map<String, Class<?>>): プロパティ名から型へのマッピング
- `defaultConstructor` (Constructor<?>): デフォルトコンストラクタ
- `caseInsensitivePropertyMap` (Map<String, String>): 大文字小文字を無視したプロパティ名解決

**コンストラクタ**:
```java
public Reflector(Class<?> clazz)
// クラスを解析してプロパティメタデータを抽出・キャッシュする
```

**内部処理**:
1. メソッド探索: 全てのdeclared/inheritedメソッドを収集し、getter/setterをフィルタ
2. 競合解決: 同一プロパティに複数メソッドがマッチする場合、最も具体的な型を選択
3. フィールドフォールバック: getter/setterがないプロパティに対して直接フィールドアクセスを追加
4. Java 15+ Recordサポート: アクセサメソッドのみを検出

---

### 7.8 ObjectFactory (インターフェース)

**目的**: MyBatis全体でのオブジェクト生成を抽象化する。

```java
public interface ObjectFactory {
    default void setProperties(Properties properties) {}
    <T> T create(Class<T> type);
    <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);
    <T> boolean isCollection(Class<T> type);
}
```

---

### 7.9 DefaultObjectFactory

**目的**: `ObjectFactory`のデフォルト実装。リフレクションを使用してオブジェクトを生成する。

**主要メソッド**:
```java
// インターフェース型の具象クラスへの解決
protected Class<?> resolveInterface(Class<?> type)
// List/Collection/Iterable → ArrayList
// Map                      → HashMap
// SortedSet                → TreeSet
// Set                      → HashSet

// コンストラクタ呼び出しによるインスタンス生成
private <T> T instantiateClass(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs)
```

---

### 7.10 ParamNameResolver

**目的**: メソッドパラメータの名前解決。`@Param`アノテーション、または実際のパラメータ名を使用する。

**フィールド**:
- `GENERIC_NAME_PREFIX = "param"`: ジェネリックパラメータ名のプレフィックス
- `names` (SortedMap<Integer, String>): パラメータインデックスから名前へのマッピング
- `hasParamAnnotation` (boolean): `@Param`アノテーションの有無

**主要メソッド**:
```java
// パラメータ名の配列を返す
public String[] getNames()

// 引数をMapまたは単一オブジェクトとして返す
public Object getNamedParams(Object[] args)
// - 単一パラメータ（@Paramなし）: そのまま返す
// - 複数パラメータまたは@Param: ParamMapとして返す
//   ("paramName" → value, "param1" → value, "param2" → value, ...)

// コレクション/配列のラッピング
public static Object wrapToMapIfCollection(Object object, String actualParamName)
// Collection → ParamMap {"collection": obj, "list": obj}
// Array     → ParamMap {"array": obj}
```

---

## 8. Type Layer

**パッケージ**: `org.apache.ibatis.type`

### 8.1 TypeHandler<T> (インターフェース)

**目的**: JavaオブジェクトとJDBC型の間の変換を定義する。

```java
public interface TypeHandler<T> {
    void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;
    T getResult(ResultSet rs, String columnName) throws SQLException;
    T getResult(ResultSet rs, int columnIndex) throws SQLException;
    T getResult(CallableStatement cs, int columnIndex) throws SQLException;
}
```

**SQL抽出との関連**: SQL抽出ではJDBCを使用しないが、`TypeHandlerRegistry`はConfiguration構築時に必要であり、`ParameterMapping`の構築で`TypeHandler`の解決が行われる。

---

### 8.2 TypeAliasRegistry

**目的**: 型エイリアス（短縮名からJavaクラスへのマッピング）を管理する。

**デフォルトで登録されるエイリアス例**:
- `"string"` → `String.class`
- `"int"` / `"integer"` → `Integer.class`
- `"map"` → `Map.class`
- `"hashmap"` → `HashMap.class`
- `"list"` → `List.class`
- `"arraylist"` → `ArrayList.class`
- `"date"` → `Date.class`
- プリミティブ型とそのラッパー型

**主要メソッド**:
```java
public <T> Class<T> resolveAlias(String string)       // エイリアスをClassに解決
public void registerAlias(String alias, Class<?> value) // エイリアスを登録
public void registerAlias(Class<?> type)                // @Aliasまたは単純名で登録
public void registerAliases(String packageName)        // パッケージスキャンで一括登録
```

---

### 8.3 TypeHandlerRegistry

**目的**: Java型/JDBC型からTypeHandlerへのマッピングを管理する。

**主要データ構造**:
- `jdbcTypeHandlerMap` (EnumMap<JdbcType, TypeHandler<?>>): JDBC型から直接のハンドラ
- `typeHandlerMap` (Map<Type, Map<JdbcType, TypeHandler<?>>>): Java型 → JDBC型 → ハンドラ
- `allTypeHandlersMap` (Map<Class<?>, TypeHandler<?>>): 全ハンドラのクラスマッピング
- `unknownTypeHandler` (TypeHandler<Object>): フォールバックハンドラ

**主要メソッド**:
```java
// ハンドラの検索
public <T> TypeHandler<T> getTypeHandler(Class<T> type)
public <T> TypeHandler<T> getTypeHandler(Class<T> type, JdbcType jdbcType)
public boolean hasTypeHandler(Class<?> javaType)

// ハンドラの登録
public <T> void register(TypeHandler<T> typeHandler)
public void register(Class<?> javaType, JdbcType jdbcType, TypeHandler<?> handler)
public void register(String packageName)  // パッケージスキャン
```

---

### 8.4 JdbcType (列挙型)

**目的**: JDBC SQL型のコード定数。

**主要な値**: `BIT`, `TINYINT`, `SMALLINT`, `INTEGER`, `BIGINT`, `FLOAT`, `REAL`, `DOUBLE`, `NUMERIC`, `DECIMAL`, `CHAR`, `VARCHAR`, `LONGVARCHAR`, `DATE`, `TIME`, `TIMESTAMP`, `BINARY`, `VARBINARY`, `BLOB`, `CLOB`, `BOOLEAN`, `NCHAR`, `NVARCHAR`, `NCLOB`, `NULL`, `OTHER`, `ARRAY`, `STRUCT`, `CURSOR`, `UNDEFINED` 等（合計47値）

**主要メソッド**:
```java
public static JdbcType forCode(int code)  // 型コードからEnum値を取得
```

---

## 9. Parsing Layer

**パッケージ**: `org.apache.ibatis.parsing`

### 9.1 XNode

**目的**: DOM Nodeのラッパー。MyBatis固有のXML操作（属性の型変換、変数置換、XPath評価）を提供する。

**フィールド**:
- `node` (Node): 元のDOM Node
- `name` (String): ノード名
- `body` (String): ノードの本文
- `attributes` (Properties): 解析済み属性
- `variables` (Properties): 変数プロパティ（`${...}` 置換用）
- `xpathParser` (XPathParser): XPathパーサー

**主要メソッド**:
```java
// XPath評価
public String evalString(String expression)
public Boolean evalBoolean(String expression)
public List<XNode> evalNodes(String expression)
public XNode evalNode(String expression)

// ナビゲーション
public XNode getParent()
public List<XNode> getChildren()
public String getPath()

// 属性取得（型付き）
public String getStringAttribute(String name)
public String getStringAttribute(String name, String def)
public Boolean getBooleanAttribute(String name)
public Integer getIntAttribute(String name)
public <T extends Enum<T>> T getEnumAttribute(Class<T> enumType, String name)

// 本文取得（型付き）
public String getStringBody()
public Boolean getBooleanBody()
public Integer getIntBody()

// 子要素→Properties変換
public Properties getChildrenAsProperties()
```

---

### 9.2 XPathParser

**目的**: XPathベースのXML文書パーサー。DTD検証、エンティティ解決、変数置換をサポートする。

**フィールド**:
- `document` (Document): パース済みXML文書
- `validation` (boolean): DTD検証フラグ
- `entityResolver` (EntityResolver): エンティティリゾルバ
- `variables` (Properties): 変数プロパティ
- `xpath` (XPath): XPath評価器

**コンストラクタ**（16のオーバーロード）:
```java
XPathParser(String xml)
XPathParser(Reader reader)
XPathParser(InputStream inputStream)
XPathParser(Document document)
// + validation, variables, entityResolver の組み合わせ
```

**主要メソッド**:
```java
// XPath評価（document全体に対して）
public String evalString(String expression)
public Boolean evalBoolean(String expression)
public Integer evalInteger(String expression)
public List<XNode> evalNodes(String expression)
public XNode evalNode(String expression)

// XPath評価（特定ノードに対して）
public String evalString(Object root, String expression)
public List<XNode> evalNodes(Object root, String expression)
public XNode evalNode(Object root, String expression)

// 変数設定
public void setVariables(Properties variables)
```

---

### 9.3 GenericTokenParser

**目的**: 開始トークンと終了トークンで囲まれた式を検出し、TokenHandlerに処理を委譲する汎用パーサー。

**フィールド**:
- `openToken` (String): 開始デリミタ（例: `"${"`）
- `closeToken` (String): 終了デリミタ（例: `"}"`）
- `handler` (TokenHandler): トークン処理ハンドラ

**主要メソッド**:
```java
public String parse(String text)
```

**使用箇所**:
| openToken | closeToken | 用途 | 使用クラス |
|-----------|------------|------|-----------|
| `#{` | `}` | パラメータプレースホルダの変換 | SqlSourceBuilder |
| `${` | `}` | 文字列置換 | TextSqlNode, PropertyParser |

**エスケープ**: バックスラッシュ（`\`）で開始トークンをエスケープ可能。

---

### 9.4 PropertyParser

**目的**: `${...}` プレースホルダを`Properties`の値で置換するユーティリティ。

**定数**:
- `ENABLE_DEFAULT_VALUE`: デフォルト値機能の有効化（デフォルト: `false`）
- `DEFAULT_VALUE_SEPARATOR`: デフォルト値セパレータ（デフォルト: `:`）

**主要メソッド**:
```java
public static String parse(String string, Properties variables)
// "Hello ${name}" + {name: "World"} → "Hello World"
// デフォルト値有効時: "${name:Unknown}" → "Unknown"（nameが未定義の場合）
```

**内部クラス: VariableTokenHandler**:
`TokenHandler`実装。`Properties`から値を検索し、オプションでデフォルト値をサポートする。

---

### 9.5 TokenHandler (インターフェース)

**目的**: GenericTokenParserが検出したトークンを処理する。

```java
public interface TokenHandler {
    String handleToken(String content);
}
```

**主要な実装**:
- `SqlSourceBuilder.ParameterMappingTokenHandler`: `#{...}` → `?` 変換
- `TextSqlNode.BindingTokenParser`: `${...}` → OGNL評価結果
- `TextSqlNode.DynamicCheckerTokenParser`: `${...}` の存在チェック
- `PropertyParser.VariableTokenHandler`: `${...}` → Properties値
- `ForEachSqlNode.FilteredDynamicContext`: 変数参照の一意化

---

## 10. Executor Layer

**パッケージ**: `org.apache.ibatis.executor`

SQL抽出ツールではJDBC実行は不要だが、Configurationの内部構造を理解するために把握しておく。

### 10.1 Executor (インターフェース)

**目的**: SQL実行の抽象化。トランザクション管理とキャッシュを統合する。

```java
public interface Executor {
    int update(MappedStatement ms, Object parameter) throws SQLException;
    <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                      ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;
    <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;
    List<BatchResult> flushStatements() throws SQLException;
    void commit(boolean required) throws SQLException;
    void rollback(boolean required) throws SQLException;
    CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);
    boolean isCached(MappedStatement ms, CacheKey key);
    void clearLocalCache();
    Transaction getTransaction();
    void close(boolean forceRollback);
    boolean isClosed();
}
```

---

### 10.2 BaseExecutor

**目的**: Executorのテンプレートメソッド実装。キャッシュ管理とトランザクション管理の共通ロジックを提供する。

**テンプレートメソッドパターン**:
- **テンプレートメソッド** (BaseExecutor): `query()`, `update()` はキャッシュロジックを管理
- **抽象メソッド** (サブクラスが実装):
  - `doUpdate()`: 実際のDML実行
  - `doQuery()`: 実際のSELECT実行
  - `doQueryCursor()`: カーソルクエリ実行
  - `doFlushStatements()`: バッチフラッシュ

---

### 10.3 SimpleExecutor

**目的**: 最もシンプルなExecutor実装。毎回新しいStatementを作成して実行する。

---

### 10.4 ReuseExecutor

**目的**: SQL文字列をキーとしてPreparedStatementをキャッシュし、再利用するExecutor実装。

---

### 10.5 BatchExecutor

**目的**: 複数のDML文をバッチ実行するExecutor実装。`doUpdate()`で文を蓄積し、`doFlushStatements()`で一括実行する。

---

### 10.6 ParameterHandler (インターフェース)

**目的**: PreparedStatementにパラメータを設定する。

```java
public interface ParameterHandler {
    Object getParameterObject();
    void setParameters(PreparedStatement ps) throws SQLException;
}
```

---

### 10.7 DefaultParameterHandler

**パッケージ**: `org.apache.ibatis.scripting.defaults`

**目的**: `ParameterHandler`のデフォルト実装。BoundSqlのParameterMappingに従ってPreparedStatementにパラメータを設定する。

**処理フロー**:
1. `BoundSql.getParameterMappings()` からパラメータリストを取得
2. 各パラメータについて:
   - OUTモードはスキップ
   - 追加パラメータ → パラメータオブジェクト → MetaObject の順に値を検索
   - `TypeHandler.setParameter()` でPreparedStatementに設定

---

### 10.8 ResultSetHandler (インターフェース)

**目的**: ResultSetからJavaオブジェクトへのマッピングを管理する。

```java
public interface ResultSetHandler {
    <E> List<E> handleResultSets(Statement stmt) throws SQLException;
    <E> Cursor<E> handleCursorResultSets(Statement stmt) throws SQLException;
    void handleOutputParameters(CallableStatement cs) throws SQLException;
}
```

---

### 10.9 DefaultResultSetHandler

**目的**: `ResultSetHandler`のデフォルト実装。ResultMapに基づいてResultSetをJavaオブジェクトにマッピングする。非常に大きなクラス。

**主要機能**:
- 単純な結果マッピング
- ネストされた結果マッピング
- Discriminatorによるポリモーフィックマッピング
- 自動マッピング
- コンストラクタベースのマッピング
- 遅延ロード
- カーソルベースの結果処理

---

### 10.10 StatementHandler (インターフェース)

**目的**: JDBCのStatement操作を抽象化する。

```java
public interface StatementHandler {
    Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException;
    void parameterize(Statement statement) throws SQLException;
    void batch(Statement statement) throws SQLException;
    int update(Statement statement) throws SQLException;
    <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException;
    <E> Cursor<E> queryCursor(Statement statement) throws SQLException;
    BoundSql getBoundSql();
    ParameterHandler getParameterHandler();
}
```

---

## 11. DataSource Layer

**パッケージ**: `org.apache.ibatis.datasource`

### 11.1 UnpooledDataSource

**目的**: コネクションプーリングなしのDataSource実装。

**主要フィールド**:
- `driver` (String): JDBCドライバクラス名
- `url` (String): 接続URL
- `username` / `password` (String): 認証情報
- `autoCommit` (Boolean): 自動コミットフラグ

**主要メソッド**:
```java
public Connection getConnection() throws SQLException
public Connection getConnection(String username, String password) throws SQLException
```

---

### 11.2 PooledDataSource

**目的**: コネクションプーリング付きのDataSource実装。内部的に`UnpooledDataSource`を使用して実際のコネクションを生成する。

**主要設定**:
- `poolMaximumActiveConnections` (10): 最大アクティブコネクション数
- `poolMaximumIdleConnections` (5): 最大アイドルコネクション数
- `poolMaximumCheckoutTime` (20000ms): チェックアウトタイムアウト
- `poolPingEnabled`: ヘルスチェッククエリの有効化
- `poolPingQuery`: ヘルスチェッククエリ

---

### 11.3 UnpooledDataSourceFactory

**目的**: `UnpooledDataSource`のファクトリ。`DataSourceFactory`インターフェースを実装。

**主要メソッド**:
```java
public void setProperties(Properties properties)  // "driver."プレフィックスのプロパティを分離
public DataSource getDataSource()
```

---

### 11.4 PooledDataSourceFactory

**目的**: `PooledDataSource`のファクトリ。`UnpooledDataSourceFactory`を拡張。

```java
public class PooledDataSourceFactory extends UnpooledDataSourceFactory {
    public PooledDataSourceFactory() {
        this.dataSource = new PooledDataSource();
    }
}
```

---

## 12. SQL抽出に関連するフロー

### 12.1 Mapper XMLからSQL文を抽出する全体フロー

```
1. Configuration構築
   Configuration config = new Configuration();
   config に Environment（ダミーDataSource付き）を設定

2. Mapper XMLのパース
   XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
       inputStream, config, resource, config.getSqlFragments());
   mapperBuilder.parse();
   └── configurationElement()
       ├── namespace設定
       ├── <sql>断片の登録
       └── buildStatementFromContext()
           └── XMLStatementBuilder.parseStatementNode()
               ├── XMLIncludeTransformer.applyIncludes()  // <include>展開
               ├── LanguageDriver.createSqlSource()
               │   └── XMLScriptBuilder.parseScriptNode()
               │       ├── parseDynamicTags()  // SqlNodeツリー構築
               │       │   ├── TextSqlNode / StaticTextSqlNode
               │       │   ├── IfSqlNode
               │       │   ├── ChooseSqlNode
               │       │   ├── ForEachSqlNode
               │       │   ├── WhereSqlNode / SetSqlNode / TrimSqlNode
               │       │   ├── VarDeclSqlNode (bind)
               │       │   └── MixedSqlNode (コンテナ)
               │       └── return DynamicSqlSource or RawSqlSource
               └── builderAssistant.addMappedStatement()

3. SQL抽出
   for (MappedStatement ms : config.getMappedStatements()) {
       Object dummyParam = createDummyParameter(ms);
       BoundSql boundSql = ms.getBoundSql(dummyParam);
       └── SqlSource.getBoundSql(dummyParam)
           ├── [DynamicSqlSource の場合]
           │   ├── DynamicContext context = new DynamicContext(config, dummyParam)
           │   ├── rootSqlNode.apply(context)  // SqlNodeツリーを評価
           │   │   ├── StaticTextSqlNode: context.appendSql(text)
           │   │   ├── TextSqlNode: ${...} をOGNL評価して置換
           │   │   ├── IfSqlNode: test条件をOGNL評価
           │   │   ├── ForEachSqlNode: コレクションを反復
           │   │   └── ...
           │   ├── SqlSourceBuilder.parse()  // #{...} → ? 変換
           │   └── return BoundSql
           └── [RawSqlSource の場合]
               └── return StaticSqlSource.getBoundSql()  // 事前解析済み

       String sql = boundSql.getSql();                         // SQL文
       List<ParameterMapping> params = boundSql.getParameterMappings(); // パラメータ情報
   }
```

### 12.2 SQL抽出ツールにとっての重要クラス一覧

| 重要度 | クラス | 役割 |
|-------|-------|------|
| ★★★ | Configuration | 全設定の中央管理。MappedStatementのリポジトリ |
| ★★★ | MappedStatement | 個々のSQL文の定義。getBoundSql()でSQL取得 |
| ★★★ | BoundSql | 最終的なSQL文とパラメータ情報 |
| ★★★ | DynamicSqlSource | 動的SQLの評価エンジン |
| ★★★ | DynamicContext | 動的SQL評価のコンテキスト。ダミーパラメータを設定 |
| ★★★ | XMLMapperBuilder | Mapper XMLのパーサー |
| ★★★ | XMLScriptBuilder | SQL要素のパーサー。SqlNodeツリーを構築 |
| ★★☆ | SqlNode各実装 | 動的SQLタグの処理。評価結果がSQL出力に直結 |
| ★★☆ | SqlSourceBuilder | #{...} → ? 変換。ParameterMapping生成 |
| ★★☆ | OgnlCache / ExpressionEvaluator | OGNL式評価。if条件やforeach等に使用 |
| ★★☆ | MetaObject | パラメータオブジェクトのプロパティアクセス |
| ★★☆ | XPathParser / XNode | XMLパーシング基盤 |
| ★★☆ | ParameterMapping | パラメータのメタデータ（プロパティ名、型情報） |
| ★☆☆ | RawSqlSource | 静的SQLの処理 |
| ★☆☆ | StaticSqlSource | 解析済みSQLの保持 |
| ★☆☆ | BaseBuilder | Builder共通ユーティリティ |
| ★☆☆ | TypeAliasRegistry | 型エイリアス解決 |
| ★☆☆ | TypeHandlerRegistry | TypeHandler解決（ParameterMapping構築に必要） |
| ★☆☆ | Environment | ダミーEnvironment構築に必要 |
| ☆☆☆ | Executor各実装 | JDBC実行（SQL抽出では不使用） |
| ☆☆☆ | DataSource各実装 | DB接続（SQL抽出では不使用） |

### 12.3 ダミーパラメータに関する考慮事項

SQL抽出ツールでは、`getBoundSql()` に渡すダミーパラメータの設計が重要:

1. **OGNL式の評価**: `<if test="id != null">` のような条件をtrueにするには、`id`プロパティに非null値を設定する必要がある
2. **型の互換性**: OGNL式で型チェック（`instanceof`等）が行われる場合、適切な型のダミー値が必要
3. **コレクション**: `<foreach collection="list">` のような要素には、反復可能なコレクションが必要
4. **ネストプロパティ**: `user.name` のようなネストアクセスには、ネストされたオブジェクトまたはMapが必要
5. **MetaObjectの動作**: パラメータがMapの場合は`MapWrapper`、POJOの場合は`BeanWrapper`が使用される。Map形式が最も柔軟

**推奨アプローチ**: `HashMap<String, Object>` をパラメータとして使用し、必要なプロパティにダミー値を設定する。MapWrapperは任意のキーに対して`hasSetter()=true`を返すため、プロパティ不足によるエラーを回避しやすい。
