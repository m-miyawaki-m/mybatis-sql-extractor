# MyBatis SQL Extractor リファレンス

## 概要

MyBatis Mapper XMLファイルからSQL文を抽出するCLIツール。
MyBatisのConfiguration APIを使用し、動的SQL（`<if>`, `<choose>`, `<foreach>`等）を含むMapper XMLから実行可能なSQL文を取得する。

---

## インストール

### 前提条件
- Java 21以上
- Gradle 8.5以上（ビルド時）

### ビルド

```bash
cd mybatis-sql-extractor
./gradlew build
```

ビルド成果物: `build/libs/mybatis-sql-extractor-1.0.0.jar`

### 実行可能JAR

```bash
./gradlew installDist
```

実行スクリプト: `build/install/mybatis-sql-extractor/bin/mybatis-sql-extractor`

---

## 使い方

### 基本構文

```bash
java -jar mybatis-sql-extractor.jar [options] <input>
```

または Gradle経由:

```bash
./gradlew run --args="[options] <input>"
```

### オプション

| オプション | 短縮形 | 説明 | デフォルト |
|-----------|--------|------|-----------|
| `--input <path>` | `-i` | Mapper XMLファイルまたはディレクトリ | 必須 |
| `--output <path>` | `-o` | 出力先ファイルパス | 標準出力 |
| `--format <type>` | `-f` | 出力形式: `text` / `json` | `text` |
| `--formatted` | | SQLを整形（改行・インデント付き）で出力 | なし |
| `--help` | `-h` | ヘルプを表示 | |

### 使用例

#### 単一ファイルからSQL抽出

```bash
./gradlew run --args="--input path/to/UserMapper.xml"
```

#### ディレクトリ内の全Mapper XMLを処理

```bash
./gradlew run --args="--input path/to/mappers/"
```

#### JSON形式でファイルに出力

```bash
./gradlew run --args="--input path/to/mappers/ --format json --output result.json"
```

#### 整形済みSQL出力

```bash
./gradlew run --args="--input path/to/UserMapper.xml --formatted"
```

---

## 出力形式

### テキスト形式（デフォルト）

```
=== com.example.mapper.UserMapper.selectById ===
Type: SELECT
SQL:
  SELECT id, name, email FROM users WHERE id = ?
Parameters: [id:INTEGER]

=== com.example.mapper.UserMapper.insert ===
Type: INSERT
SQL:
  INSERT INTO users (name, email) VALUES (?, ?)
Parameters: [name:Object, email:Object]
```

### テキスト形式（--formatted）

```
=== com.example.mapper.UserMapper.selectById ===
Type: SELECT
SQL:
  SELECT id, name, email
  FROM users
  WHERE id = ?
Parameters: [id:INTEGER]
```

### JSON形式

```json
[
  {
    "namespace": "com.example.mapper.UserMapper",
    "id": "selectById",
    "type": "SELECT",
    "sql": "SELECT id, name, email FROM users WHERE id = ?",
    "parameters": [
      {"property": "id", "javaType": "Integer", "jdbcType": "INTEGER"}
    ]
  }
]
```

---

## Java API

ライブラリとしてプログラムから使用する場合。

### SqlExtractor

メインのSQL抽出クラス。

```java
import com.example.mybatis.extractor.SqlExtractor;
import com.example.mybatis.extractor.SqlResult;

SqlExtractor extractor = new SqlExtractor();

// 単一ファイルから抽出
List<SqlResult> results = extractor.extractAll(new File("UserMapper.xml"));

// ディレクトリから一括抽出
List<SqlResult> results = extractor.extractFromDirectory(new File("mappers/"));
```

**注意**: SqlExtractorインスタンスは1回のみ使用すること（内部的にConfigurationの状態が変化するため）。

### SqlResult

抽出結果を保持するデータクラス。

```java
SqlResult result = results.get(0);

result.getNamespace();      // "com.example.mapper.UserMapper"
result.getId();             // "selectById"
result.getFullId();         // "com.example.mapper.UserMapper.selectById"
result.getSqlCommandType(); // "SELECT"
result.getSql();            // "SELECT id, name, email FROM users WHERE id = ?"
result.getParameters();     // List<ParameterInfo>
```

### SqlResult.ParameterInfo

パラメータ情報。

```java
SqlResult.ParameterInfo param = result.getParameters().get(0);

param.getProperty();  // "id"
param.getJavaType();  // "Integer"
param.getJdbcType();  // "INTEGER"
```

### SqlFormatter

SQL整形ユーティリティ。

```java
import com.example.mybatis.formatter.SqlFormatter;

// 整形（キーワード前で改行）
String formatted = SqlFormatter.format("SELECT id, name FROM users WHERE id = ?");

// 正規化（余分な空白を除去）
String normalized = SqlFormatter.normalize(rawSql);
```

### MyBatisConfigBuilder

MyBatis Configuration構築ユーティリティ。

```java
import com.example.mybatis.config.MyBatisConfigBuilder;

MyBatisConfigBuilder builder = new MyBatisConfigBuilder();
builder.addMapper(new File("UserMapper.xml"));

Configuration config = builder.getConfiguration();
// config.getMappedStatements() で登録済みのSQL文にアクセス
```

### DummyParameterGenerator

ダミーパラメータ生成ユーティリティ。

```java
import com.example.mybatis.parameter.DummyParameterGenerator;

// パラメータ名からダミー値を推定
Object value = DummyParameterGenerator.generateDummyValue("userId");  // → 1
Object value = DummyParameterGenerator.generateDummyValue("name");    // → "dummy_name"
Object value = DummyParameterGenerator.generateDummyValue("ids");     // → [1, 2, 3]

// 一括生成
Map<String, Object> params = DummyParameterGenerator.generateDummyMap(
    Set.of("id", "name", "isActive")
);
```

---

## 対応している動的SQLタグ

| タグ | 対応状況 | 説明 |
|-----|---------|------|
| `<if>` | ○ | 条件付きSQL断片（ダミーパラメータにより常にtrue） |
| `<choose>/<when>/<otherwise>` | ○ | 複数条件の分岐（最初にマッチしたwhenが出力） |
| `<where>` | ○ | 自動WHERE生成・先頭AND/OR除去 |
| `<set>` | ○ | 自動SET生成・末尾カンマ除去 |
| `<trim>` | ○ | prefix/suffix制御 |
| `<foreach>` | ○ | コレクション展開（ダミーリスト[1,2,3]を使用） |
| `<sql>/<include>` | ○ | SQL断片の再利用（XMLパース時に展開） |
| `<bind>` | ○ | 変数バインド |

### 制限事項

- `<choose>`ブロックでは最初にマッチした`<when>`のみが出力される（`<otherwise>`は出力されない場合がある）
- `test`属性に特定の値との比較（`type == 'admin'`）がある場合、ダミー値とマッチしないことがある
- `${}` (文字列置換) はダミー値がそのまま埋め込まれる
- ネストしたプロパティ（`user.address.city`）はサポート対象外

---

## プロジェクト構成

```
mybatis-sql-extractor/
├── build.gradle                    # Gradleビルド設定
├── settings.gradle
├── docs/
│   ├── plans/implementation-plan.md  # 実装計画
│   ├── glossary.md                   # 用語解説
│   ├── algorithm.md                  # アルゴリズム解説
│   └── reference.md                  # このファイル
└── src/
    ├── main/java/com/example/mybatis/
    │   ├── Main.java                 # CLIエントリーポイント
    │   ├── config/
    │   │   └── MyBatisConfigBuilder.java
    │   ├── extractor/
    │   │   ├── SqlExtractor.java     # SQL抽出メインロジック
    │   │   └── SqlResult.java        # 抽出結果データ
    │   ├── formatter/
    │   │   └── SqlFormatter.java     # SQL整形
    │   └── parameter/
    │       └── DummyParameterGenerator.java
    └── test/
        ├── java/com/example/mybatis/
        │   ├── extractor/SqlExtractorTest.java
        │   └── parameter/DummyParameterGeneratorTest.java
        └── resources/mappers/
            ├── simple-mapper.xml     # 基本CRUD
            ├── dynamic-mapper.xml    # 動的SQL
            ├── foreach-mapper.xml    # foreach
            ├── include-mapper.xml    # sql/include
            └── complex-mapper.xml    # 複合パターン
```

---

## 依存ライブラリ

| ライブラリ | バージョン | 用途 |
|-----------|-----------|------|
| org.mybatis:mybatis | 3.5.16 | MyBatis本体（XML解析・SQL生成エンジン） |
| org.junit.jupiter:junit-jupiter | 5.10.2 | テストフレームワーク |

## MyBatis公式リファレンス

- [MyBatis 3 公式ドキュメント](https://mybatis.org/mybatis-3/)
- [Mapper XML Files](https://mybatis.org/mybatis-3/sqlmap-xml.html)
- [Dynamic SQL](https://mybatis.org/mybatis-3/dynamic-sql.html)
- [Java API](https://mybatis.org/mybatis-3/java-api.html)
- [Configuration](https://mybatis.org/mybatis-3/configuration.html)
- [MyBatis GitHub](https://github.com/mybatis/mybatis-3)
- [MappedStatement API](https://mybatis.org/mybatis-3/apidocs/org/apache/ibatis/mapping/MappedStatement.html)
- [SqlSource API](https://mybatis.org/mybatis-3/apidocs/org/apache/ibatis/mapping/SqlSource.html)
