# MyBatis SQL Extractor

## Overview
MyBatis Mapper XMLファイルからSQL文を抽出するJavaツール。
MyBatis Configuration APIを使用し、動的SQL（if, choose, foreach等）を含むXMLから実行可能SQLを取得する。

## Tech Stack
- Java 21
- Gradle 8.5
- MyBatis 3.5.16
- JUnit 5

## Build & Test
```bash
./gradlew build     # ビルド
./gradlew test      # テスト実行
./gradlew run --args="--input <path>"  # 実行
```

## Directory Structure
- `src/main/java/com/example/mybatis/` - メインソース
  - `Main.java` - CLIエントリーポイント
  - `extractor/` - SQL抽出ロジック
  - `config/` - MyBatis Configuration構築
  - `parameter/` - ダミーパラメータ生成
  - `formatter/` - SQL整形
- `src/test/` - テストコード・テスト用Mapper XML
- `docs/` - ドキュメント（用語解説・アルゴリズム・リファレンス）
