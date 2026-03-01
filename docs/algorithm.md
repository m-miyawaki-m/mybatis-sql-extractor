# MyBatis SQL抽出アルゴリズム解説

## 全体フロー

```
Mapper XML → XMLMapperBuilder → MappedStatement → SqlSource → BoundSql → SQL文字列
```

本ツールは上記のMyBatis内部処理パイプラインを利用してSQL文を抽出する。

---

## 1. Configurationの構築

### 目的
MyBatisの解析エンジンを利用するための土台を構築する。

### アルゴリズム

```
1. ダミーDataSourceを作成（UnpooledDataSource、JDBC URL: jdbc:h2:mem:dummy）
2. JdbcTransactionFactoryを作成
3. Environment("extraction", transactionFactory, dataSource) を作成
4. Configuration(environment) を作成
```

### ポイント
- **DB接続は発生しない**: DataSourceはConfigurationの構築に必要だが、SQL抽出時には接続しない
- MyBatisはConfigurationの存在をバリデーションするため、最小限の設定が必要

---

## 2. Mapper XMLの解析

### 目的
XMLファイルを読み込み、各SQL定義をMappedStatementとして登録する。

### アルゴリズム

```
1. FileInputStreamでMapper XMLを読み込み
2. XMLMapperBuilder(inputStream, configuration, resource, sqlFragments) を構築
3. mapperBuilder.parse() を実行
   3.1. <mapper>のnamespace属性を取得
   3.2. <cache>, <cache-ref> を処理
   3.3. <resultMap> を処理
   3.4. <sql> 断片を登録（sqlFragmentsに格納）
   3.5. 各CRUD要素を XMLStatementBuilder で処理
        → MappedStatement を生成し Configuration に登録
```

### XMLStatementBuilderの処理詳細

```
1. 要素の属性を解析（id, parameterType, resultType, statementType等）
2. <include> タグを展開（sqlFragmentsから断片を取得して置換）
3. 動的SQLタグの判定
   - <if>, <choose>, <foreach>, <where>, <set>, <trim>, <bind> が含まれるか？
   - ${} を含むテキストがあるか？
4. SqlSourceの生成
   - 動的タグあり → DynamicSqlSource(configuration, rootSqlNode)
   - 動的タグなし → RawSqlSource(configuration, rootSqlNode, parameterType)
5. MappedStatement.Builder で MappedStatement を構築
6. Configuration.addMappedStatement() で登録
```

---

## 3. SqlNodeツリーの構築

### 目的
XMLの動的SQLタグをSqlNodeの木構造に変換する。

### アルゴリズム

Mapper XMLの各SQL要素は、以下のSqlNodeツリーに変換される：

```
MixedSqlNode（ルート）
├── StaticTextSqlNode("SELECT id, name FROM users WHERE 1=1")
├── IfSqlNode(test="name != null")
│   └── MixedSqlNode
│       └── TextSqlNode("AND name = #{name}")
├── IfSqlNode(test="email != null")
│   └── MixedSqlNode
│       └── TextSqlNode("AND email = #{email}")
└── StaticTextSqlNode("ORDER BY id")
```

### 各SqlNodeの処理

#### IfSqlNode
```
apply(context):
  if OGNL.evaluate(test, context.bindings) == true:
    contents.apply(context)  // 内部ノードを評価
```

#### ChooseSqlNode
```
apply(context):
  for each whenNode in whenNodes:
    if whenNode.apply(context) returns true:
      return  // 最初にマッチした when で終了
  if otherwise != null:
    otherwise.apply(context)
```

#### ForEachSqlNode
```
apply(context):
  collection = OGNL.evaluate(collectionExpression, context.bindings)
  for each (index, item) in collection:
    context.bind(indexName, index)
    context.bind(itemName, item)
    context.appendSql(open)   // 最初のみ
    context.appendSql(separator) // 2番目以降
    body.apply(context)
  context.appendSql(close)
```

#### WhereSqlNode（TrimSqlNodeのサブクラス）
```
apply(context):
  content = evaluateInnerNodes()
  if content is not empty:
    remove leading "AND " or "OR "
    prepend "WHERE "
```

#### SetSqlNode（TrimSqlNodeのサブクラス）
```
apply(context):
  content = evaluateInnerNodes()
  if content is not empty:
    remove trailing ","
    prepend "SET "
```

---

## 4. BoundSqlの生成

### 目的
SqlNodeツリーをパラメータに基づいて評価し、実行可能なSQL文を生成する。

### DynamicSqlSourceの場合

```
getBoundSql(parameterObject):
  1. DynamicContext context = new DynamicContext(configuration, parameterObject)
  2. rootSqlNode.apply(context)
     → 各SqlNodeが再帰的にcontextにSQL断片を追記
     → 動的タグが評価され、条件に合致するSQL部分のみが生成
  3. String sql = context.getSql()
     → "SELECT ... WHERE name = #{name} AND email = #{email}"
  4. SqlSourceBuilder.buildSqlSource(configuration, sql, parameterMappings)
     → #{name} → ? に変換
     → ParameterMapping("name", Object.class) を生成
  5. return new BoundSql(configuration, processedSql, parameterMappings, parameterObject)
```

### RawSqlSourceの場合

```
コンストラクタ時（1回のみ）:
  1. SqlSourceBuilder で #{...} → ? に変換
  2. StaticSqlSource として保持

getBoundSql(parameterObject):
  return staticSqlSource.getBoundSql(parameterObject)
  // 既に変換済みのSQLをそのまま返す
```

---

## 5. ダミーパラメータ戦略

### 目的
動的SQLの全条件分岐を通過させ、できるだけ多くのSQL部分を出力する。

### アルゴリズム

```
DummyParamMapの動作:
  get(key):
    if key matches "*List" or "*ids" or "collection":
      return Arrays.asList(1, 2, 3)   // foreach用
    if key matches "*id":
      return 1                          // ID系
    if key matches "is*" or "has*":
      return true                       // boolean系
    default:
      return "dummy_" + key             // 文字列

  containsKey(key):
    return true  // 全てのキーが存在するように見せる
```

### ポイント
- `containsKey()`が常にtrueを返すことで、OGNL式の`name != null`が常にtrueになる
- `<if test="name != null">`の条件を全て通過させ、全ブランチのSQLを出力
- `<choose>/<when>`は最初にマッチした条件のみが出力される点に注意
- `<foreach>`はダミーリスト[1,2,3]の3要素分が展開される

### 制限事項
- `<choose>`の`<otherwise>`は他の`<when>`がマッチするため出力されない
- `test`属性に`==`比較（例: `type == 'admin'`）がある場合、ダミー値とマッチしない可能性がある
- ネストしたプロパティアクセス（例: `user.name`）はDummyParamMapではサポートしていない

---

## 6. 重複排除

### 問題
MyBatisのConfigurationは内部的にStrictMapを使用し、同一のMappedStatementを2つのキーで登録する：
1. フルネーム: `com.example.mapper.UserMapper.selectById`
2. 短縮名: `selectById`（一意の場合のみ）

`getMappedStatements()`はMapの全valueを返すため、同一Statementが重複して返される。

### 解決
処理済みIDのSetで重複を排除する。

```
processedIds = new HashSet()
for each statement in configuration.getMappedStatements():
  if processedIds.add(statement.getId()):  // 新規IDの場合のみ
    extractSql(statement)
```
