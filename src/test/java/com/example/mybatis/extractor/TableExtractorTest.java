package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TableExtractorTest {

    private List<String> tablesFor(List<TableUsage> usages, String operation) {
        return usages.stream()
                .filter(u -> u.getOperation().equals(operation))
                .map(TableUsage::getTableName)
                .collect(Collectors.toList());
    }

    // ========== SELECT ==========

    @Test
    void testSimpleSelect() {
        List<TableUsage> usages = TableExtractor.extractTableUsages("SELECT id, name FROM users WHERE id = ?");
        assertEquals(List.of("users"), tablesFor(usages, "SELECT"));
    }

    @Test
    void testSelectWithAlias() {
        List<TableUsage> usages = TableExtractor.extractTableUsages("SELECT u.id FROM users u WHERE u.id = ?");
        assertEquals(List.of("users"), tablesFor(usages, "SELECT"));
    }

    @Test
    void testJoin() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "SELECT u.*, d.name FROM users u JOIN departments d ON u.dept_id = d.id");
        List<String> selectTables = tablesFor(usages, "SELECT");
        assertTrue(selectTables.contains("users"));
        assertTrue(selectTables.contains("departments"));
        assertEquals(2, selectTables.size());
    }

    @Test
    void testLeftJoin() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "SELECT u.*, o.id FROM users u LEFT JOIN orders o ON u.id = o.user_id");
        List<String> selectTables = tablesFor(usages, "SELECT");
        assertTrue(selectTables.contains("users"));
        assertTrue(selectTables.contains("orders"));
    }

    @Test
    void testSubquery() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE amount > ?)");
        List<String> selectTables = tablesFor(usages, "SELECT");
        assertTrue(selectTables.contains("users"));
        assertTrue(selectTables.contains("orders"));
    }

    @Test
    void testSubqueryInFrom() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "SELECT u.*, sub.cnt FROM users u JOIN (SELECT user_id, COUNT(*) as cnt FROM orders GROUP BY user_id) sub ON u.id = sub.user_id");
        List<String> selectTables = tablesFor(usages, "SELECT");
        assertTrue(selectTables.contains("users"));
        assertTrue(selectTables.contains("orders"));
    }

    @Test
    void testMultipleJoins() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "SELECT u.name, d.name, r.role_name FROM users u "
                + "INNER JOIN departments d ON u.dept_id = d.id "
                + "LEFT JOIN roles r ON u.role_id = r.id");
        List<String> selectTables = tablesFor(usages, "SELECT");
        assertEquals(3, selectTables.size());
        assertTrue(selectTables.contains("users"));
        assertTrue(selectTables.contains("departments"));
        assertTrue(selectTables.contains("roles"));
    }

    // ========== INSERT ==========

    @Test
    void testInsert() {
        List<TableUsage> usages = TableExtractor.extractTableUsages("INSERT INTO users (name, email) VALUES (?, ?)");
        assertEquals(List.of("users"), tablesFor(usages, "INSERT"));
        assertTrue(tablesFor(usages, "SELECT").isEmpty());
    }

    @Test
    void testInsertSelect() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "INSERT INTO user_archive (id, name, email) SELECT id, name, email FROM users WHERE active = 0");
        assertEquals(List.of("user_archive"), tablesFor(usages, "INSERT"));
        assertEquals(List.of("users"), tablesFor(usages, "SELECT"));
    }

    @Test
    void testInsertSelectWithJoin() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "INSERT INTO report (user_name, dept_name) "
                + "SELECT u.name, d.name FROM users u JOIN departments d ON u.dept_id = d.id");
        assertEquals(List.of("report"), tablesFor(usages, "INSERT"));
        List<String> selectTables = tablesFor(usages, "SELECT");
        assertTrue(selectTables.contains("users"));
        assertTrue(selectTables.contains("departments"));
    }

    // ========== UPDATE ==========

    @Test
    void testUpdate() {
        List<TableUsage> usages = TableExtractor.extractTableUsages("UPDATE users SET name = ? WHERE id = ?");
        assertEquals(List.of("users"), tablesFor(usages, "UPDATE"));
    }

    @Test
    void testUpdateWithSubquery() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "UPDATE users SET status = 'inactive' WHERE id IN (SELECT user_id FROM blacklist)");
        assertEquals(List.of("users"), tablesFor(usages, "UPDATE"));
        assertEquals(List.of("blacklist"), tablesFor(usages, "SELECT"));
    }

    // ========== DELETE ==========

    @Test
    void testDelete() {
        List<TableUsage> usages = TableExtractor.extractTableUsages("DELETE FROM users WHERE id = ?");
        assertEquals(List.of("users"), tablesFor(usages, "DELETE"));
    }

    @Test
    void testDeleteWithSubquery() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "DELETE FROM users WHERE id IN (SELECT user_id FROM temp_delete_list)");
        assertEquals(List.of("users"), tablesFor(usages, "DELETE"));
        assertEquals(List.of("temp_delete_list"), tablesFor(usages, "SELECT"));
    }

    // ========== 重複排除 ==========

    @Test
    void testNoDuplicates() {
        List<TableUsage> usages = TableExtractor.extractTableUsages(
                "SELECT * FROM users u WHERE u.id IN (SELECT user_id FROM users WHERE active = 1)");
        List<String> selectTables = tablesFor(usages, "SELECT");
        assertEquals(1, selectTables.stream().filter(t -> t.equals("users")).count());
    }
}
