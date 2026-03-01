# 分岐網羅SQL抽出 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** MyBatis動的SQLの`<if>`条件に対して全分岐ON/全分岐OFFの2パターンでSQLを抽出し、クラスパス指定による正確な型情報を活用する機能を追加する。

**Architecture:** 1つのMappedStatementに対して2種類のダミーパラメータ（all-set / all-null）でBoundSqlを2回取得する。`--classpath`オプションで対象プロジェクトのjar/classesを指定し、parameterTypeのフィールド情報をリフレクションで取得してダミー値の精度を向上させる。

**Tech Stack:** Java 21, MyBatis 3.5.16, JUnit 5, Gradle 8.5

---

### Task 1: BranchPattern enum

**Files:**
- Create: `src/main/java/com/example/mybatis/extractor/BranchPattern.java`
- Test: `src/test/java/com/example/mybatis/extractor/BranchPatternTest.java`

**Step 1: Write the failing test**

```java
package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BranchPatternTest {

    @Test
    void testEnumValues() {
        assertEquals(2, BranchPattern.values().length);
        assertNotNull(BranchPattern.ALL_SET);
        assertNotNull(BranchPattern.ALL_NULL);
    }

    @Test
    void testLabel() {
        assertEquals("ALL_SET", BranchPattern.ALL_SET.name());
        assertEquals("ALL_NULL", BranchPattern.ALL_NULL.name());
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.mybatis.extractor.BranchPatternTest" --info`
Expected: FAIL with compilation error (BranchPattern not found)

**Step 3: Write minimal implementation**

```java
package com.example.mybatis.extractor;

/**
 * 動的SQLの分岐パターンを表す列挙型。
 */
public enum BranchPattern {
    /** 全分岐ON（全<if>条件がtrue） - 最大SQL */
    ALL_SET,
    /** 全分岐OFF（全<if>条件がfalse） - 最小SQL */
    ALL_NULL
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.mybatis.extractor.BranchPatternTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/example/mybatis/extractor/BranchPattern.java src/test/java/com/example/mybatis/extractor/BranchPatternTest.java
git commit -m "feat: add BranchPattern enum for branch coverage"
```

---

### Task 2: NullParameterMap

**Files:**
- Create: `src/main/java/com/example/mybatis/parameter/NullParameterMap.java`
- Test: `src/test/java/com/example/mybatis/parameter/NullParameterMapTest.java`

**Step 1: Write the failing test**

```java
package com.example.mybatis.parameter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NullParameterMapTest {

    @Test
    void testGetReturnsNull() {
        NullParameterMap map = new NullParameterMap();
        assertNull(map.get("name"));
        assertNull(map.get("anything"));
    }

    @Test
    void testContainsKeyReturnsTrue() {
        NullParameterMap map = new NullParameterMap();
        assertTrue(map.containsKey("name"));
        assertTrue(map.containsKey("nonexistent"));
    }

    @Test
    void testExplicitPutValueIsReturned() {
        NullParameterMap map = new NullParameterMap();
        map.put("key", "value");
        assertEquals("value", map.get("key"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.mybatis.parameter.NullParameterMapTest" --info`
Expected: FAIL with compilation error

**Step 3: Write minimal implementation**

```java
package com.example.mybatis.parameter;

import java.util.HashMap;

/**
 * 全キーに対してnullを返すMap実装。
 * MyBatisのOGNL評価で <if test="x != null"> を全てfalseにする。
 * containsKey()はtrueを返し、プロパティ存在チェックを通過させる。
 */
public class NullParameterMap extends HashMap<String, Object> {

    @Override
    public Object get(Object key) {
        // 明示的にputされた値がある場合はそれを返す
        if (super.containsKey(key)) {
            return super.get(key);
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        return true;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.mybatis.parameter.NullParameterMapTest" --info`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/example/mybatis/parameter/NullParameterMap.java src/test/java/com/example/mybatis/parameter/NullParameterMapTest.java
git commit -m "feat: add NullParameterMap for all-null branch pattern"
```

---

### Task 3: SqlResult に branchPattern と parameterValues を追加

**Files:**
- Modify: `src/main/java/com/example/mybatis/extractor/SqlResult.java`
- Test: 既存テスト `src/test/java/com/example/mybatis/extractor/SqlExtractorTest.java` が壊れないことを確認

**Step 1: Write the failing test**

`src/test/java/com/example/mybatis/extractor/SqlResultTest.java` を新規作成:

```java
package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlResultTest {

    @Test
    void testBranchPatternField() {
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1",
                Collections.emptyList(), BranchPattern.ALL_SET, Map.of("name", "dummy"));
        assertEquals(BranchPattern.ALL_SET, result.getBranchPattern());
    }

    @Test
    void testParameterValuesField() {
        Map<String, Object> paramValues = Map.of("name", "dummy", "age", 1);
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1",
                Collections.emptyList(), BranchPattern.ALL_NULL, paramValues);
        assertEquals(paramValues, result.getParameterValues());
    }

    @Test
    void testNullBranchPatternForBackwardCompat() {
        // 既存の5引数コンストラクタは引き続き動作する
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1", Collections.emptyList());
        assertNull(result.getBranchPattern());
        assertNull(result.getParameterValues());
    }

    @Test
    void testToStringWithBranchPattern() {
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1",
                Collections.emptyList(), BranchPattern.ALL_SET, Map.of());
        String text = result.toString();
        assertTrue(text.contains("[ALL_SET]"));
    }

    @Test
    void testToStringWithoutBranchPattern() {
        SqlResult result = new SqlResult("ns", "id", "SELECT", "SELECT 1", Collections.emptyList());
        String text = result.toString();
        assertFalse(text.contains("[ALL_SET]"));
        assertFalse(text.contains("[ALL_NULL]"));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.mybatis.extractor.SqlResultTest" --info`
Expected: FAIL (6引数コンストラクタが存在しない)

**Step 3: Write minimal implementation**

`SqlResult.java` を修正。既存の5引数コンストラクタは維持し、新しい7引数コンストラクタを追加:

```java
// 以下のフィールドを追加 (sqlフィールドの後に):
private final BranchPattern branchPattern;
private final Map<String, Object> parameterValues;

// 既存コンストラクタ (後方互換性)
public SqlResult(String namespace, String id, String sqlCommandType, String sql, List<ParameterInfo> parameters) {
    this(namespace, id, sqlCommandType, sql, parameters, null, null);
}

// 新コンストラクタ
public SqlResult(String namespace, String id, String sqlCommandType, String sql,
                 List<ParameterInfo> parameters, BranchPattern branchPattern,
                 Map<String, Object> parameterValues) {
    this.namespace = namespace;
    this.id = id;
    this.sqlCommandType = sqlCommandType;
    this.sql = sql;
    this.parameters = parameters;
    this.branchPattern = branchPattern;
    this.parameterValues = parameterValues;
}

// getter追加
public BranchPattern getBranchPattern() { return branchPattern; }
public Map<String, Object> getParameterValues() { return parameterValues; }
```

`toString()` を修正:

```java
@Override
public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("=== ").append(getFullId());
    if (branchPattern != null) {
        sb.append(" [").append(branchPattern.name()).append("]");
    }
    sb.append(" ===\n");
    sb.append("Type: ").append(sqlCommandType).append("\n");
    sb.append("SQL:\n  ").append(sql.replace("\n", "\n  ")).append("\n");
    if (parameters != null && !parameters.isEmpty()) {
        sb.append("Parameters: ").append(parameters).append("\n");
    }
    if (parameterValues != null && !parameterValues.isEmpty()) {
        sb.append("Parameter Values: ").append(parameterValues).append("\n");
    }
    return sb.toString();
}
```

`import java.util.Map;` を追加。

**Step 4: Run all tests to verify nothing breaks**

Run: `./gradlew test --info`
Expected: ALL PASS (既存テストも新テストも)

**Step 5: Commit**

```bash
git add src/main/java/com/example/mybatis/extractor/SqlResult.java src/test/java/com/example/mybatis/extractor/SqlResultTest.java
git commit -m "feat: add branchPattern and parameterValues to SqlResult"
```

---

### Task 4: SqlExtractor に2パターン抽出を追加

**Files:**
- Modify: `src/main/java/com/example/mybatis/extractor/SqlExtractor.java`
- Test: `src/test/java/com/example/mybatis/extractor/SqlExtractorBranchTest.java` (新規)

**Step 1: Write the failing test**

```java
package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlExtractorBranchTest {

    private File getMapperFile(String name) {
        URL url = getClass().getClassLoader().getResource("mappers/" + name);
        assertNotNull(url, "Test resource not found: mappers/" + name);
        return new File(url.getFile());
    }

    @Test
    void testBranchExtractionDynamicSql() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("dynamic-mapper.xml"));

        // selectByCondition には <if> があるので2パターン出力される
        List<SqlResult> selectByCondition = results.stream()
                .filter(r -> r.getId().equals("selectByCondition"))
                .toList();

        // ALL_SET と ALL_NULL の2パターン
        assertEquals(2, selectByCondition.size(),
                "Dynamic SQL should produce 2 branch patterns");

        SqlResult allSet = selectByCondition.stream()
                .filter(r -> r.getBranchPattern() == BranchPattern.ALL_SET)
                .findFirst().orElseThrow();
        SqlResult allNull = selectByCondition.stream()
                .filter(r -> r.getBranchPattern() == BranchPattern.ALL_NULL)
                .findFirst().orElseThrow();

        // ALL_SET: name と email 条件が含まれる
        assertTrue(allSet.getSql().contains("name = ?"), "ALL_SET should contain name condition");

        // ALL_NULL: name と email 条件が含まれない（WHERE 1=1 のみ）
        assertFalse(allNull.getSql().contains("name = ?"), "ALL_NULL should not contain name condition");
    }

    @Test
    void testBranchExtractionStaticSql() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("simple-mapper.xml"));

        // selectAll は静的SQL → 1パターンのみ
        List<SqlResult> selectAll = results.stream()
                .filter(r -> r.getId().equals("selectAll"))
                .toList();

        assertEquals(1, selectAll.size(),
                "Static SQL should produce only 1 pattern");
        assertEquals(BranchPattern.ALL_SET, selectAll.get(0).getBranchPattern());
    }

    @Test
    void testBranchExtractionDeduplicatesSameSQL() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("simple-mapper.xml"));

        // selectById は静的SQL → ALL_SET/ALL_NULLで同じSQL → 1つだけ出力
        List<SqlResult> selectById = results.stream()
                .filter(r -> r.getId().equals("selectById"))
                .toList();

        assertEquals(1, selectById.size(),
                "Same SQL for both patterns should be deduplicated");
    }

    @Test
    void testBranchExtractionHasParameterValues() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("dynamic-mapper.xml"));

        SqlResult allSet = results.stream()
                .filter(r -> r.getId().equals("selectByCondition") && r.getBranchPattern() == BranchPattern.ALL_SET)
                .findFirst().orElseThrow();

        assertNotNull(allSet.getParameterValues(), "Should have parameter values");
    }

    @Test
    void testBranchExtractionAllNullForeachSkipped() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("foreach-mapper.xml"));

        // foreach を含むSQLのALL_NULLパターンは例外でスキップされるかもしれない
        List<SqlResult> selectByIds = results.stream()
                .filter(r -> r.getId().equals("selectByIds"))
                .toList();

        // 最低1パターン（ALL_SET）は出力される
        assertTrue(selectByIds.size() >= 1, "Should have at least ALL_SET pattern");
    }

    @Test
    void testExistingExtractAllUnchanged() throws IOException {
        // 既存メソッドは影響を受けない
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        assertEquals(5, results.size());
        assertNull(results.get(0).getBranchPattern(), "Existing method should not set branchPattern");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.mybatis.extractor.SqlExtractorBranchTest" --info`
Expected: FAIL (extractAllWithBranches メソッドが存在しない)

**Step 3: Write minimal implementation**

`SqlExtractor.java` に以下を追加:

```java
// 新規import
import com.example.mybatis.parameter.NullParameterMap;
import java.util.Map;

/**
 * 指定されたMapper XMLファイルから全てのSQL文を分岐パターン別に抽出する。
 * 動的SQLに対してALL_SET（全分岐ON）とALL_NULL（全分岐OFF）の2パターンを生成する。
 */
public List<SqlResult> extractAllWithBranches(File mapperFile) throws IOException {
    configBuilder.addMapper(mapperFile);
    Configuration configuration = configBuilder.getConfiguration();
    return extractFromConfigurationWithBranches(configuration);
}

/**
 * 指定ディレクトリから分岐パターン別にSQLを抽出する。
 */
public List<SqlResult> extractFromDirectoryWithBranches(File directory) throws IOException {
    if (!directory.isDirectory()) {
        throw new IllegalArgumentException("Not a directory: " + directory);
    }

    File[] xmlFiles = directory.listFiles((dir, name) -> name.endsWith(".xml"));
    if (xmlFiles == null || xmlFiles.length == 0) {
        return Collections.emptyList();
    }

    for (File xmlFile : xmlFiles) {
        try {
            configBuilder.addMapper(xmlFile);
        } catch (Exception e) {
            System.err.println("Warning: Failed to parse " + xmlFile.getName() + ": " + e.getMessage());
        }
    }

    return extractFromConfigurationWithBranches(configBuilder.getConfiguration());
}

private List<SqlResult> extractFromConfigurationWithBranches(Configuration configuration) {
    List<SqlResult> results = new ArrayList<>();
    Set<String> processedIds = new HashSet<>();

    for (Object obj : configuration.getMappedStatements()) {
        if (!(obj instanceof MappedStatement)) {
            continue;
        }
        MappedStatement ms = (MappedStatement) obj;

        if (!processedIds.add(ms.getId())) {
            continue;
        }

        try {
            List<SqlResult> branchResults = extractWithBranches(ms);
            results.addAll(branchResults);
        } catch (Exception e) {
            System.err.println("Warning: Failed to extract SQL from " + ms.getId() + ": " + e.getMessage());
        }
    }

    results.sort(Comparator.comparing(SqlResult::getFullId)
            .thenComparing(r -> r.getBranchPattern() != null ? r.getBranchPattern().ordinal() : 0));
    return results;
}

private List<SqlResult> extractWithBranches(MappedStatement ms) {
    List<SqlResult> results = new ArrayList<>();

    String fullId = ms.getId();
    String namespace = "";
    String id = fullId;
    int lastDot = fullId.lastIndexOf('.');
    if (lastDot > 0) {
        namespace = fullId.substring(0, lastDot);
        id = fullId.substring(lastDot + 1);
    }
    String commandType = ms.getSqlCommandType().name();
    SqlSource sqlSource = ms.getSqlSource();

    // ALL_SET パターン
    Map<String, Object> allSetParams = createDummyParams(ms);
    BoundSql allSetBoundSql = sqlSource.getBoundSql(allSetParams);
    String allSetSql = SqlFormatter.normalize(allSetBoundSql.getSql());
    List<SqlResult.ParameterInfo> allSetParamInfos = extractParameterInfos(allSetBoundSql);

    // パラメータ値のスナップショット（DummyParamMapは動的なのでBoundSqlから取得した名前で値を収集）
    Map<String, Object> allSetValues = collectParameterValues(allSetBoundSql, allSetParams);

    results.add(new SqlResult(namespace, id, commandType, allSetSql,
            allSetParamInfos, BranchPattern.ALL_SET, allSetValues));

    // ALL_NULL パターン（動的SQLの場合のみ）
    if (sqlSource instanceof DynamicSqlSource) {
        try {
            NullParameterMap allNullParams = new NullParameterMap();
            BoundSql allNullBoundSql = sqlSource.getBoundSql(allNullParams);
            String allNullSql = SqlFormatter.normalize(allNullBoundSql.getSql());

            // SQLが同一なら重複を排除
            if (!allNullSql.equals(allSetSql)) {
                List<SqlResult.ParameterInfo> allNullParamInfos = extractParameterInfos(allNullBoundSql);
                Map<String, Object> allNullValues = collectParameterValues(allNullBoundSql, allNullParams);
                results.add(new SqlResult(namespace, id, commandType, allNullSql,
                        allNullParamInfos, BranchPattern.ALL_NULL, allNullValues));
            }
        } catch (Exception e) {
            System.err.println("Warning: ALL_NULL pattern failed for " + ms.getId()
                    + " (skipping): " + e.getMessage());
        }
    }

    return results;
}

private List<SqlResult.ParameterInfo> extractParameterInfos(BoundSql boundSql) {
    return boundSql.getParameterMappings().stream()
            .map(pm -> new SqlResult.ParameterInfo(
                    pm.getProperty(),
                    pm.getJavaType() != null ? pm.getJavaType().getSimpleName() : null,
                    pm.getJdbcType() != null ? pm.getJdbcType().name() : null
            ))
            .collect(Collectors.toList());
}

private Map<String, Object> collectParameterValues(BoundSql boundSql, Map<String, Object> params) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (ParameterMapping pm : boundSql.getParameterMappings()) {
        String prop = pm.getProperty();
        // __frch_ はforeachの内部変数なのでスキップ
        if (!prop.startsWith("__frch_")) {
            values.put(prop, params.get(prop));
        }
    }
    return values;
}
```

`import java.util.LinkedHashMap;` を追加。

**Step 4: Run all tests**

Run: `./gradlew test --info`
Expected: ALL PASS (既存テストも新テストも)

**Step 5: Commit**

```bash
git add src/main/java/com/example/mybatis/extractor/SqlExtractor.java src/test/java/com/example/mybatis/extractor/SqlExtractorBranchTest.java
git commit -m "feat: add branch coverage extraction (ALL_SET/ALL_NULL patterns)"
```

---

### Task 5: ClasspathTypeResolver

**Files:**
- Create: `src/main/java/com/example/mybatis/parameter/ClasspathTypeResolver.java`
- Test: `src/test/java/com/example/mybatis/parameter/ClasspathTypeResolverTest.java`

テスト用にダミーのパラメータクラスを用意:
- Create: `src/test/java/com/example/mybatis/testmodel/UserSearchParam.java`

**Step 1: テスト用モデルを作成**

```java
package com.example.mybatis.testmodel;

import java.util.List;

/**
 * テスト用のパラメータクラス。
 */
public class UserSearchParam {
    private String name;
    private Integer age;
    private List<Integer> ids;
    private boolean active;

    // getter/setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public List<Integer> getIds() { return ids; }
    public void setIds(List<Integer> ids) { this.ids = ids; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
```

**Step 2: Write the failing test**

```java
package com.example.mybatis.parameter;

import com.example.mybatis.testmodel.UserSearchParam;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClasspathTypeResolverTest {

    @Test
    void testResolveFieldsFromClass() {
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Map<String, Class<?>> fields = resolver.resolveFields(UserSearchParam.class);

        assertEquals(String.class, fields.get("name"));
        assertEquals(Integer.class, fields.get("age"));
        assertEquals(List.class, fields.get("ids"));
        assertEquals(boolean.class, fields.get("active"));
    }

    @Test
    void testGenerateTypedDummyValuesAllSet() {
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Map<String, Object> values = resolver.generateAllSetParams(UserSearchParam.class);

        assertNotNull(values.get("name"));
        assertInstanceOf(String.class, values.get("name"));
        assertNotNull(values.get("age"));
        assertInstanceOf(Integer.class, values.get("age"));
        assertNotNull(values.get("ids"));
        assertInstanceOf(List.class, values.get("ids"));
        assertNotNull(values.get("active"));
    }

    @Test
    void testGenerateDummyValueForType() {
        assertEquals(1, ClasspathTypeResolver.dummyValueForType(int.class));
        assertEquals(1, ClasspathTypeResolver.dummyValueForType(Integer.class));
        assertEquals(1L, ClasspathTypeResolver.dummyValueForType(Long.class));
        assertEquals(1L, ClasspathTypeResolver.dummyValueForType(long.class));
        assertInstanceOf(String.class, ClasspathTypeResolver.dummyValueForType(String.class));
        assertEquals(true, ClasspathTypeResolver.dummyValueForType(boolean.class));
        assertEquals(true, ClasspathTypeResolver.dummyValueForType(Boolean.class));
        assertInstanceOf(List.class, ClasspathTypeResolver.dummyValueForType(List.class));
    }

    @Test
    void testLoadClassFromClasspath() throws Exception {
        // テスト実行時のクラスパスからロード可能
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Class<?> clazz = resolver.loadClass("com.example.mybatis.testmodel.UserSearchParam");
        assertNotNull(clazz);
        assertEquals("UserSearchParam", clazz.getSimpleName());
    }

    @Test
    void testLoadClassFromExternalClasspath() throws Exception {
        // 外部クラスパスが未設定の場合、現在のクラスローダーにフォールバック
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Class<?> clazz = resolver.loadClass("java.lang.String");
        assertEquals(String.class, clazz);
    }

    @Test
    void testLoadClassNotFoundReturnsNull() {
        ClasspathTypeResolver resolver = new ClasspathTypeResolver();
        Class<?> clazz = resolver.loadClass("com.nonexistent.FakeClass");
        assertNull(clazz);
    }
}
```

**Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.mybatis.parameter.ClasspathTypeResolverTest" --info`
Expected: FAIL

**Step 4: Write minimal implementation**

```java
package com.example.mybatis.parameter;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;

/**
 * parameterType/resultTypeのJavaクラスをクラスパスから読み込み、
 * フィールド情報を取得して型に応じたダミー値を生成する。
 */
public class ClasspathTypeResolver {

    private ClassLoader classLoader;

    public ClasspathTypeResolver() {
        this.classLoader = getClass().getClassLoader();
    }

    /**
     * 外部クラスパスを設定する。
     * @param classpathEntries jar/classesディレクトリのパスリスト
     */
    public void setClasspath(List<String> classpathEntries) {
        try {
            URL[] urls = new URL[classpathEntries.size()];
            for (int i = 0; i < classpathEntries.size(); i++) {
                urls[i] = Path.of(classpathEntries.get(i)).toUri().toURL();
            }
            this.classLoader = new URLClassLoader(urls, getClass().getClassLoader());
        } catch (Exception e) {
            System.err.println("Warning: Failed to set classpath: " + e.getMessage());
        }
    }

    /**
     * クラス名からClassをロードする。見つからない場合はnullを返す。
     */
    public Class<?> loadClass(String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * クラスのgetterメソッドからフィールド名と型のマッピングを取得する。
     */
    public Map<String, Class<?>> resolveFields(Class<?> clazz) {
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        for (Method method : clazz.getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 0) continue;
            if (method.getDeclaringClass() == Object.class) continue;

            String propertyName = null;
            if (name.startsWith("get") && name.length() > 3) {
                propertyName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
            } else if (name.startsWith("is") && name.length() > 2
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                propertyName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
            }

            if (propertyName != null) {
                fields.put(propertyName, method.getReturnType());
            }
        }
        return fields;
    }

    /**
     * クラスのフィールド情報に基づいてALL_SET用のダミー値Mapを生成する。
     */
    public Map<String, Object> generateAllSetParams(Class<?> clazz) {
        Map<String, Class<?>> fields = resolveFields(clazz);
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<String, Class<?>> entry : fields.entrySet()) {
            params.put(entry.getKey(), dummyValueForType(entry.getValue()));
        }
        return params;
    }

    /**
     * Java型に応じたダミー値を返す。
     */
    public static Object dummyValueForType(Class<?> type) {
        if (type == int.class || type == Integer.class) return 1;
        if (type == long.class || type == Long.class) return 1L;
        if (type == double.class || type == Double.class) return 1.0;
        if (type == float.class || type == Float.class) return 1.0f;
        if (type == boolean.class || type == Boolean.class) return true;
        if (type == short.class || type == Short.class) return (short) 1;
        if (type == byte.class || type == Byte.class) return (byte) 1;
        if (type == String.class) return "dummy";
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return Arrays.asList(1, 2, 3);
        }
        if (type.isArray()) return new Object[]{1, 2, 3};
        // その他のオブジェクト型
        return "dummy";
    }
}
```

`import java.util.Collection;` を追加。

**Step 5: Run tests**

Run: `./gradlew test --info`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/com/example/mybatis/parameter/ClasspathTypeResolver.java \
       src/test/java/com/example/mybatis/parameter/ClasspathTypeResolverTest.java \
       src/test/java/com/example/mybatis/testmodel/UserSearchParam.java
git commit -m "feat: add ClasspathTypeResolver for type-aware dummy parameter generation"
```

---

### Task 6: Main.java に --classpath, --branches オプション追加

**Files:**
- Modify: `src/main/java/com/example/mybatis/Main.java`
- Modify: `src/main/java/com/example/mybatis/extractor/SqlExtractor.java` (ClasspathTypeResolver統合)

**Step 1: SqlExtractorにClasspathTypeResolverを統合するテスト**

`src/test/java/com/example/mybatis/extractor/SqlExtractorBranchTest.java` に追加:

```java
@Test
void testBranchExtractionWithClasspathResolver() throws IOException {
    ClasspathTypeResolver resolver = new ClasspathTypeResolver();
    SqlExtractor extractor = new SqlExtractor(resolver);
    List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("dynamic-mapper.xml"));

    assertFalse(results.isEmpty());
}
```

import追加: `import com.example.mybatis.parameter.ClasspathTypeResolver;`

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.mybatis.extractor.SqlExtractorBranchTest.testBranchExtractionWithClasspathResolver" --info`
Expected: FAIL (コンストラクタが存在しない)

**Step 3: SqlExtractorにコンストラクタを追加**

```java
private final ClasspathTypeResolver typeResolver;

public SqlExtractor() {
    this.configBuilder = new MyBatisConfigBuilder();
    this.typeResolver = null;
}

public SqlExtractor(ClasspathTypeResolver typeResolver) {
    this.configBuilder = new MyBatisConfigBuilder();
    this.typeResolver = typeResolver;
}
```

`import com.example.mybatis.parameter.ClasspathTypeResolver;` を追加。

**Step 4: Main.javaに新オプションを追加**

```java
// 変数宣言に追加:
String classpath = null;
boolean branches = false;

// switch文に追加:
case "--classpath":
case "-cp":
    if (i + 1 < args.length) classpath = args[++i];
    break;
case "--branches":
case "-b":
    branches = true;
    break;

// 抽出処理を変更:
ClasspathTypeResolver typeResolver = null;
if (classpath != null) {
    typeResolver = new ClasspathTypeResolver();
    typeResolver.setClasspath(Arrays.asList(classpath.split(File.pathSeparator)));
}

SqlExtractor extractor = typeResolver != null ? new SqlExtractor(typeResolver) : new SqlExtractor();
List<SqlResult> results;

if (branches) {
    if (input.isDirectory()) {
        results = extractor.extractFromDirectoryWithBranches(input);
    } else {
        results = extractor.extractAllWithBranches(input);
    }
} else {
    if (input.isDirectory()) {
        results = extractor.extractFromDirectory(input);
    } else {
        results = extractor.extractAll(input);
    }
}

// toText, toJsonにbranchPattern対応を追加
```

toTextの変更:

```java
private static String toText(List<SqlResult> results, boolean formatted) {
    StringBuilder sb = new StringBuilder();
    for (SqlResult result : results) {
        sb.append("=== ").append(result.getFullId());
        if (result.getBranchPattern() != null) {
            sb.append(" [").append(result.getBranchPattern().name()).append("]");
        }
        sb.append(" ===\n");
        sb.append("Type: ").append(result.getSqlCommandType()).append("\n");
        sb.append("SQL:\n");

        String sql = formatted ? SqlFormatter.format(result.getSql()) : result.getSql();
        for (String line : sql.split("\n")) {
            sb.append("  ").append(line).append("\n");
        }

        if (result.getParameters() != null && !result.getParameters().isEmpty()) {
            sb.append("Parameters: ").append(result.getParameters()).append("\n");
        }
        if (result.getParameterValues() != null && !result.getParameterValues().isEmpty()) {
            sb.append("Parameter Values: ").append(result.getParameterValues()).append("\n");
        }
        sb.append("\n");
    }
    return sb.toString();
}
```

toJsonの変更（`"branchPattern"` と `"parameterValues"` フィールドを追加）:

```java
// sb.append("    \"type\": \"")... の後に追加:
if (r.getBranchPattern() != null) {
    sb.append("    \"branchPattern\": \"").append(r.getBranchPattern().name()).append("\",\n");
}
// "parameters" 配列の後に追加:
if (r.getParameterValues() != null && !r.getParameterValues().isEmpty()) {
    sb.append(",\n    \"parameterValues\": {");
    int pvIdx = 0;
    for (Map.Entry<String, Object> entry : r.getParameterValues().entrySet()) {
        if (pvIdx > 0) sb.append(",");
        sb.append("\n      \"").append(escapeJson(entry.getKey())).append("\": ");
        Object val = entry.getValue();
        if (val instanceof String) {
            sb.append("\"").append(escapeJson(val.toString())).append("\"");
        } else if (val == null) {
            sb.append("null");
        } else {
            sb.append(val);
        }
        pvIdx++;
    }
    sb.append("\n    }");
}
```

printUsageの変更:

```java
System.out.println("  --branches, -b        Enable branch coverage output (ALL_SET/ALL_NULL patterns)");
System.out.println("  --classpath, -cp <path> Classpath for parameterType/resultType resolution (: separated)");
```

import追加:

```java
import com.example.mybatis.parameter.ClasspathTypeResolver;
import java.util.Arrays;
import java.util.Map;
```

**Step 5: Run all tests**

Run: `./gradlew test --info`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/com/example/mybatis/Main.java src/main/java/com/example/mybatis/extractor/SqlExtractor.java src/test/java/com/example/mybatis/extractor/SqlExtractorBranchTest.java
git commit -m "feat: add --branches and --classpath CLI options"
```

---

### Task 7: 統合テスト

**Files:**
- Modify: `src/test/java/com/example/mybatis/extractor/SqlExtractorBranchTest.java`

**Step 1: 追加の統合テストを記述**

```java
@Test
void testBranchExtractionWhereTag() throws IOException {
    SqlExtractor extractor = new SqlExtractor();
    List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("dynamic-mapper.xml"));

    List<SqlResult> selectWithWhere = results.stream()
            .filter(r -> r.getId().equals("selectWithWhere"))
            .toList();

    // ALL_SET: WHERE name = ? AND email = ?
    SqlResult allSet = selectWithWhere.stream()
            .filter(r -> r.getBranchPattern() == BranchPattern.ALL_SET)
            .findFirst().orElseThrow();
    assertTrue(allSet.getSql().contains("WHERE"));

    // ALL_NULL: WHERE句なし（<where>内の全<if>がfalseなのでWHERE自体が消える）
    SqlResult allNull = selectWithWhere.stream()
            .filter(r -> r.getBranchPattern() == BranchPattern.ALL_NULL)
            .findFirst().orElseThrow();
    assertFalse(allNull.getSql().contains("WHERE"), "ALL_NULL should have no WHERE clause");
}

@Test
void testBranchExtractionSetTag() throws IOException {
    SqlExtractor extractor = new SqlExtractor();
    List<SqlResult> results = extractor.extractAllWithBranches(getMapperFile("dynamic-mapper.xml"));

    List<SqlResult> updateSelective = results.stream()
            .filter(r -> r.getId().equals("updateSelective"))
            .toList();

    assertTrue(updateSelective.size() >= 1);
    // ALL_SET: SET name = ?, email = ?
    SqlResult allSet = updateSelective.stream()
            .filter(r -> r.getBranchPattern() == BranchPattern.ALL_SET)
            .findFirst().orElseThrow();
    assertTrue(allSet.getSql().contains("SET"));
}

@Test
void testBranchExtractionDirectoryScan() throws IOException {
    URL url = getClass().getClassLoader().getResource("mappers/");
    assertNotNull(url);

    File dir = new File(url.getFile());
    SqlExtractor extractor = new SqlExtractor();
    List<SqlResult> results = extractor.extractFromDirectoryWithBranches(dir);

    // 分岐ありのStatementが2パターン出るので、既存より多い
    assertTrue(results.size() > 10, "Should have many results with branch patterns");

    // 全結果にbranchPatternが設定されている
    for (SqlResult r : results) {
        assertNotNull(r.getBranchPattern(), "All branch results should have a pattern");
    }
}
```

**Step 2: Run tests**

Run: `./gradlew test --info`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add src/test/java/com/example/mybatis/extractor/SqlExtractorBranchTest.java
git commit -m "test: add integration tests for branch coverage extraction"
```

---

### Task 8: 既存テストの確認と最終動作確認

**Step 1: 全テスト実行**

Run: `./gradlew test --info`
Expected: ALL PASS (既存テスト全て + 新規テスト全て)

**Step 2: CLI動作確認（手動）**

```bash
# 既存動作（後方互換）
./gradlew run --args="--input src/test/resources/mappers/dynamic-mapper.xml"

# 分岐パターン出力
./gradlew run --args="--input src/test/resources/mappers/dynamic-mapper.xml --branches"

# JSON形式で分岐パターン出力
./gradlew run --args="--input src/test/resources/mappers/dynamic-mapper.xml --branches --format json"

# ディレクトリ一括 + 分岐パターン
./gradlew run --args="--input src/test/resources/mappers/ --branches"
```

**Step 3: ビルド**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A
git commit -m "feat: complete branch coverage SQL extraction feature"
```
