package com.example.mybatis.extractor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableExtractorTest {

    @Test
    void testSimpleSelect() {
        List<String> tables = TableExtractor.extractTables("SELECT id, name FROM users WHERE id = ?");
        assertEquals(List.of("users"), tables);
    }

    @Test
    void testSelectWithAlias() {
        List<String> tables = TableExtractor.extractTables("SELECT u.id, u.name FROM users u WHERE u.id = ?");
        assertEquals(List.of("users"), tables);
    }

    @Test
    void testSelectWithAsAlias() {
        List<String> tables = TableExtractor.extractTables("SELECT u.id FROM users AS u WHERE u.id = ?");
        assertEquals(List.of("users"), tables);
    }

    @Test
    void testJoin() {
        List<String> tables = TableExtractor.extractTables(
                "SELECT u.*, d.name FROM users u JOIN departments d ON u.dept_id = d.id");
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("departments"));
        assertEquals(2, tables.size());
    }

    @Test
    void testLeftJoin() {
        List<String> tables = TableExtractor.extractTables(
                "SELECT u.*, o.id FROM users u LEFT JOIN orders o ON u.id = o.user_id");
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("orders"));
    }

    @Test
    void testInsert() {
        List<String> tables = TableExtractor.extractTables("INSERT INTO users (name, email) VALUES (?, ?)");
        assertEquals(List.of("users"), tables);
    }

    @Test
    void testUpdate() {
        List<String> tables = TableExtractor.extractTables("UPDATE users SET name = ? WHERE id = ?");
        assertEquals(List.of("users"), tables);
    }

    @Test
    void testDelete() {
        List<String> tables = TableExtractor.extractTables("DELETE FROM users WHERE id = ?");
        assertEquals(List.of("users"), tables);
    }

    @Test
    void testSubquery() {
        List<String> tables = TableExtractor.extractTables(
                "SELECT * FROM users WHERE id IN (SELECT user_id FROM orders WHERE amount > ?)");
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("orders"));
    }

    @Test
    void testSubqueryInFrom() {
        List<String> tables = TableExtractor.extractTables(
                "SELECT u.*, sub.cnt FROM users u JOIN (SELECT user_id, COUNT(*) as cnt FROM orders GROUP BY user_id) sub ON u.id = sub.user_id");
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("orders"));
    }

    @Test
    void testMultipleJoins() {
        List<String> tables = TableExtractor.extractTables(
                "SELECT u.name, d.name, r.role_name FROM users u "
                + "INNER JOIN departments d ON u.dept_id = d.id "
                + "LEFT JOIN roles r ON u.role_id = r.id");
        assertTrue(tables.contains("users"));
        assertTrue(tables.contains("departments"));
        assertTrue(tables.contains("roles"));
        assertEquals(3, tables.size());
    }

    @Test
    void testNoDuplicates() {
        List<String> tables = TableExtractor.extractTables(
                "SELECT * FROM users u WHERE u.id IN (SELECT user_id FROM users WHERE active = 1)");
        assertEquals(1, tables.stream().filter(t -> t.equals("users")).count());
    }
}
