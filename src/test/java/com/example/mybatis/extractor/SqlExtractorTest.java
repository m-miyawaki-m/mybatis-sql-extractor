package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlExtractorのテスト。
 * XMLファイルをString化してextractFromStringで処理する（実利用と同じ方式）。
 */
class SqlExtractorTest {

    private String loadXml(String name) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("mappers/" + name)) {
            assertNotNull(is, "Test resource not found: mappers/" + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<SqlResult> extract(String xmlFileName) throws IOException {
        String xml = loadXml(xmlFileName);
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractFromString(xml);
        results.forEach(r -> System.out.println(r));
        return results;
    }

    private Optional<SqlResult> findById(List<SqlResult> results, String id) {
        return results.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }

    // ========== 基本CRUD テスト ==========

    @Test
    void testSimpleSelect() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");

        SqlResult result = findById(results, "selectById").orElseThrow();
        assertEquals("SELECT", result.getSqlCommandType());
        assertTrue(result.getSql().contains("SELECT"));
        assertTrue(result.getSql().contains("?"));
        assertEquals("com.example.mapper.UserMapper", result.getNamespace());
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testSimpleSelectAll() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");

        SqlResult result = findById(results, "selectAll").orElseThrow();
        assertEquals("SELECT", result.getSqlCommandType());
        assertFalse(result.getSql().contains("?"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testSimpleInsert() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");

        SqlResult result = findById(results, "insert").orElseThrow();
        assertEquals("INSERT", result.getSqlCommandType());
        assertTrue(result.getSql().contains("INSERT INTO users"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testSimpleUpdate() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");

        SqlResult result = findById(results, "update").orElseThrow();
        assertEquals("UPDATE", result.getSqlCommandType());
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testSimpleDelete() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");

        SqlResult result = findById(results, "deleteById").orElseThrow();
        assertEquals("DELETE", result.getSqlCommandType());
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testSimpleMapperExtractsAllStatements() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");
        assertEquals(5, results.size());
    }

    // ========== 動的SQL テスト ==========

    @Test
    void testDynamicIf() throws IOException {
        List<SqlResult> results = extract("dynamic-mapper.xml");

        SqlResult result = findById(results, "selectByCondition").orElseThrow();
        assertTrue(result.getSql().contains("name = ?"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testDynamicWhere() throws IOException {
        List<SqlResult> results = extract("dynamic-mapper.xml");

        SqlResult result = findById(results, "selectWithWhere").orElseThrow();
        assertTrue(result.getSql().contains("WHERE"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testDynamicChoose() throws IOException {
        List<SqlResult> results = extract("dynamic-mapper.xml");

        SqlResult result = findById(results, "selectWithChoose").orElseThrow();
        assertTrue(result.getSql().contains("?"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testDynamicSet() throws IOException {
        List<SqlResult> results = extract("dynamic-mapper.xml");

        SqlResult result = findById(results, "updateSelective").orElseThrow();
        assertEquals("UPDATE", result.getSqlCommandType());
        assertTrue(result.getSql().contains("SET"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testDynamicTrim() throws IOException {
        List<SqlResult> results = extract("dynamic-mapper.xml");

        SqlResult result = findById(results, "selectWithTrim").orElseThrow();
        assertTrue(result.getSql().contains("WHERE"));
        assertEquals(List.of("users"), result.getTables());
    }

    // ========== foreach テスト ==========

    @Test
    void testForeachInClause() throws IOException {
        List<SqlResult> results = extract("foreach-mapper.xml");

        SqlResult result = findById(results, "selectByIds").orElseThrow();
        assertTrue(result.getSql().contains("IN"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testForeachBulkInsert() throws IOException {
        List<SqlResult> results = extract("foreach-mapper.xml");

        SqlResult result = findById(results, "bulkInsert").orElseThrow();
        assertEquals("INSERT", result.getSqlCommandType());
        assertTrue(result.getSql().contains("INSERT INTO users"));
        assertEquals(List.of("users"), result.getTables());
    }

    // ========== include テスト ==========

    @Test
    void testInclude() throws IOException {
        List<SqlResult> results = extract("include-mapper.xml");

        SqlResult result = findById(results, "selectActiveUsers").orElseThrow();
        assertTrue(result.getSql().contains("created_at"));
        assertTrue(result.getSql().contains("active = 1"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testIncludeWithDynamicSql() throws IOException {
        List<SqlResult> results = extract("include-mapper.xml");

        SqlResult result = findById(results, "selectByNameWithInclude").orElseThrow();
        assertTrue(result.getSql().contains("created_at"));
        assertEquals(List.of("users"), result.getTables());
    }

    // ========== 複合パターン テスト ==========

    @Test
    void testComplexJoinQuery() throws IOException {
        List<SqlResult> results = extract("complex-mapper.xml");

        SqlResult result = findById(results, "selectUserWithDepartment").orElseThrow();
        assertTrue(result.getSql().contains("JOIN"));
        assertTrue(result.getTables().contains("users"));
        assertTrue(result.getTables().contains("departments"));
    }

    @Test
    void testComplexSubquery() throws IOException {
        List<SqlResult> results = extract("complex-mapper.xml");

        SqlResult result = findById(results, "selectUsersWithOrderCount").orElseThrow();
        assertTrue(result.getSql().contains("SELECT COUNT(*)"));
        assertTrue(result.getTables().contains("users"));
        assertTrue(result.getTables().contains("orders"));
    }

    @Test
    void testComplexAdvancedSearch() throws IOException {
        List<SqlResult> results = extract("complex-mapper.xml");

        SqlResult result = findById(results, "advancedSearch").orElseThrow();
        assertTrue(result.getSql().contains("ORDER BY"));
        assertEquals(List.of("users"), result.getTables());
    }

    @Test
    void testInsertSelect() throws IOException {
        List<SqlResult> results = extract("complex-mapper.xml");

        SqlResult result = findById(results, "archiveInactiveUsers").orElseThrow();
        assertEquals("INSERT", result.getSqlCommandType());
        assertTrue(result.getSql().contains("INSERT INTO user_archive"));
        assertTrue(result.getSql().contains("SELECT"));

        // テーブル名の検証
        assertTrue(result.getTables().contains("user_archive"), "Should contain INSERT target");
        assertTrue(result.getTables().contains("users"), "Should contain SELECT source");
        assertTrue(result.getTables().contains("orders"), "Should contain JOIN table");

        // 操作別テーブルの検証
        List<TableUsage> usages = result.getTableUsages();
        List<String> insertTables = usages.stream()
                .filter(u -> u.getOperation().equals("INSERT"))
                .map(TableUsage::getTableName).collect(Collectors.toList());
        List<String> selectTables = usages.stream()
                .filter(u -> u.getOperation().equals("SELECT"))
                .map(TableUsage::getTableName).collect(Collectors.toList());
        assertEquals(List.of("user_archive"), insertTables);
        assertTrue(selectTables.contains("users"));
        assertTrue(selectTables.contains("orders"));
    }

    // ========== SQL行数テスト ==========

    @Test
    void testSqlLines_simpleSelect() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");

        // SELECT id, name, email / FROM users / WHERE id = #{id} → 3行
        SqlResult result = findById(results, "selectById").orElseThrow();
        assertTrue(result.getSqlLines() > 0);
    }

    @Test
    void testSqlLines_complexJoin() throws IOException {
        List<SqlResult> results = extract("complex-mapper.xml");

        // JOINクエリはシンプルSELECTより行数が多い
        SqlResult join = findById(results, "selectUserWithDepartment").orElseThrow();
        SqlResult simple = extract("simple-mapper.xml").stream()
                .filter(r -> r.getId().equals("selectAll")).findFirst().orElseThrow();
        assertTrue(join.getSqlLines() > simple.getSqlLines());
    }

    @Test
    void testSqlLines_appearsInToString() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");
        SqlResult result = findById(results, "selectById").orElseThrow();
        assertTrue(result.toString().contains("Lines:"));
    }

    // ========== パラメータ情報テスト ==========

    @Test
    void testParameterInfoExtraction() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");

        SqlResult result = findById(results, "selectById").orElseThrow();
        List<SqlResult.ParameterInfo> params = result.getParameters();
        assertNotNull(params);
        assertFalse(params.isEmpty());
    }

    // ========== toString テスト ==========

    @Test
    void testSqlResultToString() throws IOException {
        List<SqlResult> results = extract("simple-mapper.xml");

        SqlResult result = findById(results, "selectById").orElseThrow();
        String text = result.toString();
        assertTrue(text.contains("==="));
        assertTrue(text.contains("Type:"));
        assertTrue(text.contains("Tables:"));
        assertTrue(text.contains("SQL:"));
    }
}
