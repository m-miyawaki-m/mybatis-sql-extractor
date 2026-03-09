# MyBatis SQL Extractor

MyBatis Mapper XMLからSQL文を抽出するJavaライブラリ。
XML文字列を渡すだけで、実行SQLの取得・利用テーブルのCRUD分類が可能。

## 必要環境

- Java 11
- Gradle 7.2

## セットアップ

### 1. クローン

```bash
git clone https://github.com/m-miyawaki-m/mybatis-sql-extractor.git
cd mybatis-sql-extractor
```

### 2. IDE用クラスパス生成（VSCode / Eclipse）

```bash
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew eclipse
```

これにより `.classpath`, `.project`, `.settings/` が生成され、IDEが `libs/` 内のJARを認識します。

VSCodeの場合、生成後に `Ctrl+Shift+P` → `Java: Clean Java Language Server Workspace` を実行するとエラーが消えます。

### 3. ビルド確認

```bash
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew build
```

## テスト実行

```bash
# 全テスト
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew clean test

# 特定テストクラス
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew test --tests "*SqlExtractorTest*"

# 特定テストメソッド
JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 ./gradlew test --tests "*SqlExtractorTest.testInsertSelect"
```

テスト実行時、抽出されたSQLがコンソールに出力されます。

## 使い方

### 基本：XML文字列からSQL抽出

```java
SqlExtractor extractor = new SqlExtractor();
String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
    + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\"\n"
    + "  \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
    + "<mapper namespace=\"com.example.UserMapper\">\n"
    + "  <select id=\"findById\" resultType=\"map\">\n"
    + "    SELECT * FROM users WHERE id = #{id}\n"
    + "  </select>\n"
    + "</mapper>";

List<SqlResult> results = extractor.extractFromString(xml);

for (SqlResult r : results) {
    System.out.println(r.getId());              // findById
    System.out.println(r.getSqlCommandType());  // SELECT
    System.out.println(r.getSql());             // SELECT * FROM users WHERE id = ?
    System.out.println(r.getTables());          // [users]
    System.out.println(r.getTableUsages());     // [SELECT: users]
}
```

### テーブル利用情報（CRUD分類）

`SqlResult.getTableUsages()` でテーブルごとのCRUD操作を取得できます。

```java
// INSERT SELECT の場合
List<TableUsage> usages = result.getTableUsages();
// [TableUsage("user_archive", "INSERT"), TableUsage("users", "SELECT"), TableUsage("orders", "SELECT")]
```

出力例：

```
=== com.example.mapper.ComplexMapper.archiveInactiveUsers ===
Type: INSERT
Tables:
  INSERT: [user_archive]
  SELECT: [orders, users]
SQL:
  INSERT INTO user_archive (id, name, email, archived_at)
  SELECT u.id, u.name, u.email, NOW() FROM users u
  LEFT JOIN orders o ON u.id = o.user_id WHERE ...
Parameters: [inactiveDays:Object]
```

### テーブル抽出のみ（単独利用）

```java
// SQL文字列から直接テーブルを抽出
List<String> tables = TableExtractor.extractTables("SELECT * FROM users u JOIN orders o ON u.id = o.user_id");
// [users, orders]

// CRUD操作付き
List<TableUsage> usages = TableExtractor.extractTableUsages("INSERT INTO archive SELECT * FROM users");
// [TableUsage("archive", "INSERT"), TableUsage("users", "SELECT")]
```

## 対応するSQLパターン

- 基本CRUD（SELECT / INSERT / UPDATE / DELETE）
- 動的SQL（if, where, choose, set, trim, foreach）
- JOIN（INNER / LEFT / RIGHT）
- サブクエリ（WHERE句内、FROM句内）
- INSERT SELECT
- include（SQL断片の展開）
- エイリアス（`users u`, `users AS u`）

## 依存ライブラリ

`libs/` ディレクトリに配置済み（Maven Centralは使用しない）：

| ライブラリ | 用途 |
|---|---|
| mybatis-3.5.16.jar | MyBatis XML解析 |
| jsqlparser-4.9.jar | SQLパース・テーブル抽出 |
| junit-jupiter-*.jar | テスト |
| opentest4j-1.3.0.jar | テスト |
| apiguardian-api-1.1.0.jar | テスト |

## ディレクトリ構成

```
src/main/java/com/example/mybatis/
  extractor/
    SqlExtractor.java      -- XML文字列からSQL抽出
    SqlResult.java         -- 抽出結果（SQL, テーブル, パラメータ）
    TableExtractor.java    -- SQLからテーブル名抽出
    TableUsage.java        -- テーブル利用情報（テーブル名+操作種別）
  config/
    MyBatisConfigBuilder.java  -- MyBatis Configuration構築
  parameter/
    DummyParameterGenerator.java  -- ダミーパラメータ生成
  formatter/
    SqlFormatter.java      -- SQL整形

src/test/resources/mappers/  -- テスト用Mapper XML
libs/                        -- 依存JAR
```
