package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlExtractorのテスト。
 * 各種Mapper XMLパターンでSQL抽出が正しく動作することを検証する。
 */
class SqlExtractorTest {

    private File getMapperFile(String name) {
        URL url = getClass().getClassLoader().getResource("mappers/" + name);
        assertNotNull(url, "Test resource not found: mappers/" + name);
        return new File(url.getFile());
    }

    private Optional<SqlResult> findById(List<SqlResult> results, String id) {
        return results.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }

    // ========== 基本CRUD テスト ==========

    @Test
    void testSimpleSelect() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        assertFalse(results.isEmpty(), "Should extract at least one SQL");

        Optional<SqlResult> selectById = findById(results, "selectById");
        assertTrue(selectById.isPresent(), "Should contain selectById");

        SqlResult result = selectById.get();
        assertEquals("SELECT", result.getSqlCommandType());
        assertTrue(result.getSql().contains("SELECT"), "SQL should contain SELECT");
        assertTrue(result.getSql().contains("users"), "SQL should reference users table");
        assertTrue(result.getSql().contains("?"), "SQL should contain ? placeholder");
        assertEquals("com.example.mapper.UserMapper", result.getNamespace());
    }

    @Test
    void testSimpleSelectAll() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        Optional<SqlResult> selectAll = findById(results, "selectAll");
        assertTrue(selectAll.isPresent(), "Should contain selectAll");

        SqlResult result = selectAll.get();
        assertEquals("SELECT", result.getSqlCommandType());
        assertFalse(result.getSql().contains("?"), "selectAll should not have parameters");
    }

    @Test
    void testSimpleInsert() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        Optional<SqlResult> insert = findById(results, "insert");
        assertTrue(insert.isPresent(), "Should contain insert");

        SqlResult result = insert.get();
        assertEquals("INSERT", result.getSqlCommandType());
        assertTrue(result.getSql().contains("INSERT INTO users"));
    }

    @Test
    void testSimpleUpdate() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        Optional<SqlResult> update = findById(results, "update");
        assertTrue(update.isPresent(), "Should contain update");
        assertEquals("UPDATE", update.get().getSqlCommandType());
    }

    @Test
    void testSimpleDelete() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        Optional<SqlResult> delete = findById(results, "deleteById");
        assertTrue(delete.isPresent(), "Should contain deleteById");
        assertEquals("DELETE", delete.get().getSqlCommandType());
    }

    @Test
    void testSimpleMapperExtractsAllStatements() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        // simple-mapper.xml has 5 statements
        assertEquals(5, results.size(), "Should extract 5 SQL statements");
    }

    // ========== 動的SQL テスト ==========

    @Test
    void testDynamicIf() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("dynamic-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectByCondition");
        assertTrue(result.isPresent(), "Should contain selectByCondition");

        String sql = result.get().getSql();
        assertTrue(sql.contains("SELECT"), "Should contain SELECT");
        assertTrue(sql.contains("users"), "Should reference users table");
        // ダミーパラメータによりif条件が通過するので、name と email の条件が含まれる
        assertTrue(sql.contains("name = ?"), "Should contain name condition (if evaluated to true)");
    }

    @Test
    void testDynamicWhere() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("dynamic-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectWithWhere");
        assertTrue(result.isPresent(), "Should contain selectWithWhere");

        String sql = result.get().getSql();
        assertTrue(sql.contains("WHERE"), "Should contain WHERE clause");
    }

    @Test
    void testDynamicChoose() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("dynamic-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectWithChoose");
        assertTrue(result.isPresent(), "Should contain selectWithChoose");

        String sql = result.get().getSql();
        // chooseはfirst matchなので、idが設定されていればid条件が使われる
        assertTrue(sql.contains("?"), "Should contain at least one parameter");
    }

    @Test
    void testDynamicSet() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("dynamic-mapper.xml"));

        Optional<SqlResult> result = findById(results, "updateSelective");
        assertTrue(result.isPresent(), "Should contain updateSelective");

        SqlResult r = result.get();
        assertEquals("UPDATE", r.getSqlCommandType());
        assertTrue(r.getSql().contains("SET"), "Should contain SET clause");
    }

    @Test
    void testDynamicTrim() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("dynamic-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectWithTrim");
        assertTrue(result.isPresent(), "Should contain selectWithTrim");

        String sql = result.get().getSql();
        assertTrue(sql.contains("WHERE"), "Trim should generate WHERE prefix");
    }

    // ========== foreach テスト ==========

    @Test
    void testForeachInClause() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("foreach-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectByIds");
        assertTrue(result.isPresent(), "Should contain selectByIds");

        String sql = result.get().getSql();
        assertTrue(sql.contains("IN"), "Should contain IN clause");
        assertTrue(sql.contains("("), "Should contain opening paren");
        assertTrue(sql.contains(")"), "Should contain closing paren");
    }

    @Test
    void testForeachBulkInsert() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("foreach-mapper.xml"));

        Optional<SqlResult> result = findById(results, "bulkInsert");
        assertTrue(result.isPresent(), "Should contain bulkInsert");

        SqlResult r = result.get();
        assertEquals("INSERT", r.getSqlCommandType());
        assertTrue(r.getSql().contains("INSERT INTO users"), "Should contain INSERT INTO");
    }

    // ========== include テスト ==========

    @Test
    void testInclude() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("include-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectActiveUsers");
        assertTrue(result.isPresent(), "Should contain selectActiveUsers");

        String sql = result.get().getSql();
        // include refidで展開されたカラムが含まれること
        assertTrue(sql.contains("id"), "Should contain 'id' from included columns");
        assertTrue(sql.contains("name"), "Should contain 'name' from included columns");
        assertTrue(sql.contains("email"), "Should contain 'email' from included columns");
        assertTrue(sql.contains("created_at"), "Should contain 'created_at' from included columns");
        // activeConditionも展開される
        assertTrue(sql.contains("active = 1"), "Should contain active condition from include");
    }

    @Test
    void testIncludeWithDynamicSql() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("include-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectByNameWithInclude");
        assertTrue(result.isPresent(), "Should contain selectByNameWithInclude");

        String sql = result.get().getSql();
        assertTrue(sql.contains("created_at"), "Should include expanded columns");
    }

    // ========== 複合パターン テスト ==========

    @Test
    void testComplexJoinQuery() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("complex-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectUserWithDepartment");
        assertTrue(result.isPresent(), "Should contain selectUserWithDepartment");

        String sql = result.get().getSql();
        assertTrue(sql.contains("JOIN"), "Should contain JOIN");
        assertTrue(sql.contains("departments"), "Should reference departments table");
    }

    @Test
    void testComplexSubquery() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("complex-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectUsersWithOrderCount");
        assertTrue(result.isPresent(), "Should contain selectUsersWithOrderCount");

        String sql = result.get().getSql();
        assertTrue(sql.contains("SELECT COUNT(*)"), "Should contain subquery");
        assertTrue(sql.contains("orders"), "Should reference orders table");
    }

    @Test
    void testComplexAdvancedSearch() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("complex-mapper.xml"));

        Optional<SqlResult> result = findById(results, "advancedSearch");
        assertTrue(result.isPresent(), "Should contain advancedSearch");

        String sql = result.get().getSql();
        assertTrue(sql.contains("ORDER BY"), "Should contain ORDER BY");
    }

    // ========== パラメータ情報テスト ==========

    @Test
    void testParameterInfoExtraction() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectById");
        assertTrue(result.isPresent());

        List<SqlResult.ParameterInfo> params = result.get().getParameters();
        assertNotNull(params, "Parameters should not be null");
        assertFalse(params.isEmpty(), "Should have at least one parameter");
    }

    // ========== ディレクトリスキャン テスト ==========

    @Test
    void testDirectoryScan() throws IOException {
        URL url = getClass().getClassLoader().getResource("mappers/");
        assertNotNull(url, "mappers directory should exist");

        File dir = new File(url.getFile());
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractFromDirectory(dir);

        // 全XMLファイルから抽出
        assertTrue(results.size() > 10, "Should extract multiple statements from directory");
    }

    // ========== toString / 出力形式テスト ==========

    @Test
    void testSqlResultToString() throws IOException {
        SqlExtractor extractor = new SqlExtractor();
        List<SqlResult> results = extractor.extractAll(getMapperFile("simple-mapper.xml"));

        Optional<SqlResult> result = findById(results, "selectById");
        assertTrue(result.isPresent());

        String text = result.get().toString();
        assertTrue(text.contains("==="), "Should contain separator");
        assertTrue(text.contains("Type:"), "Should contain type label");
        assertTrue(text.contains("SQL:"), "Should contain SQL label");
    }
}
